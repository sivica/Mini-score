import copy
import json
import sys
import tempfile
import unittest
from datetime import date
from pathlib import Path
from urllib.parse import parse_qs, urlsplit
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from update_game_feed import build_feed, event_to_game, main, source_integer, team_code, update_file, validate_feed

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

    def test_missed_import_refetches_retained_unfinished_dates(self):
        for status in ("LIVE", "SCHEDULED", "POSTPONED"):
            with self.subTest(status=status):
                previous = copy.deepcopy(self.previous)
                previous["games"][0].update(status=status, id="legacy-game")
                previous["defaultGameId"] = "legacy-game"
                requests = []
                def fetch(url):
                    requests.append(url)
                    return self.fetch(url)
                result = build_feed(previous, date(2026, 10, 12), fetch)
                self.assertEqual("FINISHED", result["games"][0]["status"])
                self.assertEqual("legacy-game", result["games"][0]["id"])
                self.assertEqual(135, result["games"][0]["away"]["score"])
                self.assertEqual(1, sum("dates=20261009" in url for url in requests))

    def test_catch_up_dates_are_deduplicated_and_exclude_final_or_expired_games(self):
        previous = copy.deepcopy(self.previous)
        previous["games"][0]["status"] = "LIVE"
        another = copy.deepcopy(self.game)
        another.update(id="another", status="POSTPONED")
        another["away"]["code"] = "PHI"
        previous["games"].append(another)
        for gid, day, status in (("expired", "2026-10-01", "LIVE"), ("final", "2026-10-08", "FINISHED")):
            game = copy.deepcopy(self.game)
            game.update(id=gid, date=day, status=status)
            previous["games"].append(game)
        requests = []
        result = build_feed(previous, date(2026, 10, 12), lambda url: requests.append(url) or {"events": []})
        self.assertEqual(4, len(requests))
        self.assertEqual(1, sum("dates=20261009" in url for url in requests))
        self.assertFalse(any("dates=20261001" in url or "dates=20261008" in url for url in requests))
        self.assertNotIn("expired", [g["id"] for g in result["games"]])

    def test_failed_catch_up_preserves_exact_cache(self):
        self.previous["games"][0]["status"] = "LIVE"
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "games.json"
            original = json.dumps(self.previous, indent=3).encode()
            path.write_bytes(original)
            def fetch(url):
                if "dates=20261009" in url:
                    raise TimeoutError("Catch-up unavailable")
                return {"events": []}
            with self.assertRaises(TimeoutError):
                update_file(path, date(2026, 10, 12), fetch)
            self.assertEqual(original, path.read_bytes())
            self.assertEqual([path], list(path.parent.iterdir()))

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

    def finished_mode_fetch(self, events_by_day, requests):
        def fetch(url):
            requests.append(url)
            query = parse_qs(urlsplit(url).query)
            if "summary?" in url:
                summary = copy.deepcopy(self.summary)
                summary["header"]["id"] = query["event"][0]
                return summary
            return {"events": copy.deepcopy(events_by_day.get(query["dates"][0], []))}
        return fetch

    def test_finished_mode_keeps_only_today_and_yesterday_finals(self):
        previous = copy.deepcopy(self.previous)
        for gid, day, status in (("old", "2026-10-08", "FINISHED"),
                                 ("future", "2026-10-11", "FINISHED"),
                                 ("scheduled", "2026-10-10", "SCHEDULED"),
                                 ("live", "2026-10-10", "LIVE"),
                                 ("postponed", "2026-10-09", "POSTPONED"),
                                 ("cancelled", "2026-10-09", "CANCELLED")):
            game = copy.deepcopy(self.game)
            game.update(id=gid, date=day, status=status)
            game["away"]["code"] = {"old": "PHI", "future": "BKN", "scheduled": "SAS",
                                    "live": "UTA", "postponed": "GSW", "cancelled": "WAS"}[gid]
            previous["games"].append(game)
        today = copy.deepcopy(self.event)
        today.update(id="12345", date="2026-10-10T23:00Z")
        requests = []
        fetch = self.finished_mode_fetch({"20261009": [self.event], "20261010": [today]}, requests)
        feed = build_feed(previous, date(2026, 10, 10), fetch, finished_only=True)
        self.assertEqual(["2026-10-10", "2026-10-09"], [g["date"] for g in feed["games"]])
        self.assertTrue(all(g["status"] == "FINISHED" for g in feed["games"]))
        self.assertEqual("espn_12345", feed["defaultGameId"])
        scoreboard_dates = [parse_qs(urlsplit(url).query)["dates"][0]
                            for url in requests if "scoreboard?" in url]
        self.assertEqual(["20261009", "20261010"], scoreboard_dates)

    def test_finished_mode_skips_unfinished_events_without_requesting_boxscores(self):
        events = []
        for i, (name, state) in enumerate((("STATUS_SCHEDULED", "pre"),
                                         ("STATUS_IN_PROGRESS", "in"),
                                         ("STATUS_POSTPONED", "pre"),
                                         ("STATUS_CANCELED", "post"))):
            event = copy.deepcopy(self.event)
            event["id"] = str(100 + i)
            event["competitions"][0]["status"]["type"] = {
                "name": name, "state": state, "completed": False}
            events.append(event)
        requests = []
        result = build_feed(self.previous, date(2026, 10, 10),
                            self.finished_mode_fetch({"20261009": events}, requests), finished_only=True)
        self.assertEqual(self.previous, result)
        self.assertFalse(any("summary?" in url for url in requests))

    def test_finished_mode_filters_adjacent_out_of_window_dates_before_boxscore(self):
        older, future = copy.deepcopy(self.event), copy.deepcopy(self.event)
        older.update(id="111", date="2026-10-08T23:00Z")
        future.update(id="222", date="2026-10-11T23:00Z")
        requests = []
        fetch = self.finished_mode_fetch({"20261009": [older], "20261010": [future]}, requests)
        result = build_feed(self.previous, date(2026, 10, 10), fetch, finished_only=True)
        self.assertEqual(self.previous, result)
        self.assertFalse(any("summary?" in url for url in requests))

    def test_finished_mode_handles_utc_midnight_and_preserves_legacy_id(self):
        event = copy.deepcopy(self.event)
        event["date"] = "2026-10-10T00:00Z"
        self.previous["games"][0]["id"] = "legacy-game"
        self.previous["defaultGameId"] = "legacy-game"
        requests = []
        result = build_feed(self.previous, date(2026, 10, 10),
                            self.finished_mode_fetch({"20261009": [event], "20261010": [event]}, requests),
                            finished_only=True)
        self.assertEqual(["legacy-game"], [g["id"] for g in result["games"]])
        self.assertEqual("2026-10-09", result["games"][0]["date"])
        self.assertEqual(1, sum("summary?" in url for url in requests))

    def test_finished_mode_empty_window_preserves_exact_cache(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "games.json"
            original = json.dumps(self.previous, indent=3).encode()
            path.write_bytes(original)
            with self.assertRaisesRegex(ValueError, "No games available"):
                update_file(path, date(2026, 10, 12), lambda url: {"events": []}, finished_only=True)
            self.assertEqual(original, path.read_bytes())
            self.assertEqual([path], list(path.parent.iterdir()))

    def test_finished_mode_failed_or_rejected_feed_preserves_exact_cache(self):
        def unavailable(url):
            raise TimeoutError("Provider unavailable")
        malformed = copy.deepcopy(self.summary)
        malformed["boxscore"]["players"] = None
        stale = copy.deepcopy(self.event)
        stale["date"] = "2025-10-09T12:00Z"
        fetches = [unavailable,
                   lambda url: malformed if "summary?" in url else self.fetch(url),
                   lambda url: {"events": [stale]},
                   lambda url: {"events": None}]
        for fetch in fetches:
            with self.subTest(fetch=fetch), tempfile.TemporaryDirectory() as folder:
                path = Path(folder) / "games.json"
                original = json.dumps(self.previous, indent=3).encode()
                path.write_bytes(original)
                with self.assertRaises((ValueError, TimeoutError)):
                    update_file(path, date(2026, 10, 10), fetch, finished_only=True)
                self.assertEqual(original, path.read_bytes())
                self.assertEqual([path], list(path.parent.iterdir()))

    def test_finished_mode_removes_cached_unfinished_games_and_is_idempotent(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "games.json"
            unfinished = copy.deepcopy(self.game)
            unfinished.update(id="unstarted", date="2026-10-10", status="SCHEDULED")
            self.previous["games"].append(unfinished)
            path.write_text(json.dumps(self.previous))
            self.assertTrue(update_file(path, date(2026, 10, 10), self.fetch, finished_only=True))
            result = json.loads(path.read_bytes())
            self.assertEqual([self.game["id"]], [g["id"] for g in result["games"]])
            validate_feed(result)
            published = path.read_bytes()
            self.assertFalse(update_file(path, date(2026, 10, 10), self.fetch, finished_only=True))
            self.assertEqual(published, path.read_bytes())

    def test_cli_passes_finished_only_and_explicit_date(self):
        with patch("sys.argv", ["update_game_feed.py", "--feed", "games.json", "--date", "2026-10-10",
                                "--finished-only"]), patch("update_game_feed.update_file") as update:
            main()
        update.assert_called_once_with(Path("games.json"), date(2026, 10, 10), finished_only=True)


if __name__ == "__main__":
    unittest.main()
