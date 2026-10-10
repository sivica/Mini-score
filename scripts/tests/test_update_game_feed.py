import copy
import json
import sys
import tempfile
import unittest
from datetime import date
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from update_game_feed import build_feed, event_to_game, source_integer, team_code, update_file, validate_feed

FIXTURES = Path(__file__).parent / "fixtures"


class DailyFeedTest(unittest.TestCase):
    def setUp(self):
        self.event = json.loads((FIXTURES / "scoreboard-event.json").read_text())
        self.summary = json.loads((FIXTURES / "boxscore.json").read_text())
        self.game = event_to_game(self.event, {}, lambda url: copy.deepcopy(self.summary))
        self.previous = {"schemaVersion": 1, "defaultGameId": self.game["id"], "games": [self.game]}

    def fetch(self, url):
        if "summary?" in url:
            return copy.deepcopy(self.summary)
        return {"events": [copy.deepcopy(self.event)]} if "dates=20261009" in url else {"events": []}

    def test_real_boxscore_counts_images_and_starters(self):
        validate_feed(self.previous)
        self.assertEqual(135, self.game["away"]["score"])
        self.assertEqual(117, self.game["home"]["score"])
        self.assertEqual([27, 38, 34, 36], self.game["away"]["quarters"])
        kd = next(p for p in self.game["away"]["players"] if p["name"] == "Kevin Durant")
        self.assertEqual((15, 7, 12), (kd["stats"]["points"], kd["stats"]["fgMade"], kd["stats"]["fgAttempted"]))
        self.assertTrue(kd["starter"])
        self.assertTrue(kd["portraitUrl"].startswith("https://"))
        self.assertEqual(5, sum(p["starter"] for p in self.game["away"]["players"]))
        self.assertNotIn("Jae'Sean Tate", [p["name"] for p in self.game["away"]["players"]])

    def test_existing_nba_game_id_retained_without_duplicate(self):
        self.previous["games"][0]["id"] = "0012600012"
        self.previous["defaultGameId"] = "0012600012"
        feed = build_feed(self.previous, date(2026, 10, 10), self.fetch)
        self.assertEqual(["0012600012"], [g["id"] for g in feed["games"]])

    def test_scheduled_games_have_empty_arrays_and_real_tipoff(self):
        event = copy.deepcopy(self.event)
        event["competitions"][0]["status"]["type"] = {"name": "STATUS_SCHEDULED", "state": "pre", "completed": False}
        game = event_to_game(event, {}, lambda url: self.fail("Scheduled games must not request stats"))
        self.assertEqual("SCHEDULED", game["status"])
        self.assertEqual("2026-10-09T12:00:00Z", game["tipoffUTC"])
        for side in ("away", "home"):
            self.assertEqual((0, [], []), (game[side]["score"], game[side]["players"], game[side]["quarters"]))

    def test_eastern_date_is_preserved_across_utc_midnight(self):
        event = copy.deepcopy(self.event)
        event["date"] = "2026-10-10T00:00Z"
        self.assertEqual("2026-10-09", event_to_game(event, {}, self.fetch)["date"])

    def test_no_games_day_retains_recent_results(self):
        feed = build_feed(self.previous, date(2026, 10, 10), lambda url: {"events": []})
        self.assertEqual(self.previous, feed)

    def test_history_expires_after_one_week(self):
        old = copy.deepcopy(self.game)
        old.update(id="old", date="2026-10-01")
        self.previous["games"].append(old)
        feed = build_feed(self.previous, date(2026, 10, 10), self.fetch)
        self.assertEqual(1, len(feed["games"]))

    def test_default_is_latest_finished_game(self):
        event = copy.deepcopy(self.event)
        event.update(id="12345", date="2026-10-10T23:00Z")
        event["competitions"][0]["status"]["type"] = {"name": "STATUS_SCHEDULED", "state": "pre", "completed": False}
        def fetch(url):
            return {"events": [event]} if "dates=20261010" in url else self.fetch(url)
        feed = build_feed(self.previous, date(2026, 10, 10), fetch)
        self.assertEqual(self.game["id"], feed["defaultGameId"])
        self.assertEqual(2, len(feed["games"]))

    def assert_preserved(self, fetch, error=Exception):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "games.json"
            original = json.dumps(self.previous, indent=3).encode()
            path.write_bytes(original)
            with self.assertRaises(error):
                update_file(path, date(2026, 10, 10), fetch)
            self.assertEqual(original, path.read_bytes())
            self.assertEqual([path], list(path.parent.iterdir()))

    def test_network_failure_preserves_exact_published_bytes(self):
        def fetch(url):
            raise TimeoutError("Provider unavailable")
        self.assert_preserved(fetch, TimeoutError)

    def test_missing_stats_and_malformed_arrays_preserve_published_bytes(self):
        mutations = [
            lambda b: b["boxscore"].update(players=None),
            lambda b: b["boxscore"].update(players={}),
            lambda b: b["boxscore"]["players"][0].update(statistics="bad"),
            lambda b: b["boxscore"]["players"][0]["statistics"][0].update(athletes=None),
            lambda b: b["boxscore"]["players"][0]["statistics"][0]["athletes"][0].update(stats=[]),
            lambda b: b["header"].update(id="wrong"),
        ]
        for mutate in mutations:
            with self.subTest(mutate=mutate):
                summary = copy.deepcopy(self.summary)
                mutate(summary)
                self.assert_preserved(lambda url: summary if "summary?" in url else self.fetch(url))

    def test_bad_provider_stats_preserve_published_bytes(self):
        for value in (True, None, "15.0", "15e0", "-1", "15.5", 15.0, 201):
            with self.subTest(value=value):
                summary = copy.deepcopy(self.summary)
                summary["boxscore"]["players"][0]["statistics"][0]["athletes"][0]["stats"][1] = value
                self.assert_preserved(lambda url: summary if "summary?" in url else self.fetch(url))

    def test_inconsistent_final_points_preserve_published_bytes(self):
        summary = copy.deepcopy(self.summary)
        summary["boxscore"]["players"][0]["statistics"][0]["athletes"][0]["stats"][1] = "14"
        self.assert_preserved(lambda url: summary if "summary?" in url else self.fetch(url), ValueError)

    def test_stale_response_preserves_published_bytes(self):
        event = copy.deepcopy(self.event)
        event["date"] = "2025-10-09T12:00Z"
        self.assert_preserved(lambda url: self.summary if "summary?" in url else {"events": [event]}, ValueError)

    def test_output_validator_rejects_coerced_numbers_and_arrays(self):
        for value in (True, None, "15", 15.0, -1):
            feed = copy.deepcopy(self.previous)
            feed["games"][0]["away"]["players"][0]["stats"]["points"] = value
            with self.subTest(value=value), self.assertRaises(ValueError):
                validate_feed(feed)
        for field in ("players", "quarters"):
            for value in (None, {}, "[]", False):
                feed = copy.deepcopy(self.previous)
                feed["games"][0]["away"][field] = value
                with self.subTest(field=field, value=value), self.assertRaises(ValueError):
                    validate_feed(feed)

    def test_source_integer_converts_only_digit_strings(self):
        self.assertEqual(15, source_integer("15", 200))
        for value in (15.0, True, "15.0", "15e0"):
            with self.subTest(value=value), self.assertRaises(ValueError):
                source_integer(value, 200)

    def test_provider_team_codes_match_existing_nba_codes(self):
        for source, expected in (("GS", "GSW"), ("SA", "SAS"), ("WSH", "WAS"), ("DAL", "DAL")):
            with self.subTest(source=source):
                self.assertEqual(expected, team_code({"abbreviation": source}))

    def test_existing_alias_migrates_without_duplicate_id(self):
        self.previous["games"][0]["away"]["code"] = "SA"
        for c in self.event["competitions"][0]["competitors"]:
            if c["team"]["abbreviation"] == "HOU":
                c["team"]["abbreviation"] = "SA"
        for c in self.summary["header"]["competitions"][0]["competitors"]:
            if c["team"]["abbreviation"] == "HOU":
                c["team"]["abbreviation"] = "SA"
        self.summary["boxscore"]["players"][0]["team"]["abbreviation"] = "SA"
        feed = build_feed(self.previous, date(2026, 10, 10), self.fetch)
        self.assertEqual(1, len(feed["games"]))
        self.assertEqual("SAS", feed["games"][0]["away"]["code"])
        self.assertEqual(self.game["id"], feed["games"][0]["id"])

    def test_status_and_score_mismatch_preserve_published_bytes(self):
        for mismatch in ("status", "score"):
            summary = copy.deepcopy(self.summary)
            header = summary["header"]["competitions"][0]
            if mismatch == "status":
                header["status"]["type"] = {"name": "STATUS_IN_PROGRESS", "state": "in", "completed": False}
            else:
                header["competitors"][0]["score"] = "118"
            with self.subTest(mismatch=mismatch):
                self.assert_preserved(lambda url: summary if "summary?" in url else self.fetch(url), ValueError)

    def test_successful_update_is_valid_and_idempotent(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "games.json"
            self.previous["games"][0]["away"]["players"][0]["stats"]["points"] = 1
            path.write_text(json.dumps(self.previous))
            self.assertTrue(update_file(path, date(2026, 10, 10), self.fetch))
            validate_feed(json.loads(path.read_bytes()))
            published = path.read_bytes()
            self.assertFalse(update_file(path, date(2026, 10, 10), self.fetch))
            self.assertEqual(published, path.read_bytes())


if __name__ == "__main__":
    unittest.main()
