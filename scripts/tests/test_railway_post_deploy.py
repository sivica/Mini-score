import copy
import json
import sys
import unittest
import urllib.error
import urllib.request
from datetime import datetime
from pathlib import Path
from unittest.mock import Mock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from railway_post_deploy import BASE_URL, NoRedirect, refresh_feed, wait_for_deployment
from update_game_feed import EASTERN, event_to_game


class RailwayDeploymentTest(unittest.TestCase):
    def deployment(self, message, status="SUCCESS", created="2026-10-17T12:00:00Z"):
        return {"id": message, "status": status, "createdAt": created, "meta": {"cliMessage": message}}

    def test_old_healthy_deployment_cannot_satisfy_new_deploy(self):
        fetch = Mock(side_effect=[[self.deployment("old")], [self.deployment("new", "BUILDING")],
                                  [self.deployment("new")]])
        sleep, health = Mock(), Mock(return_value={"ok": True})
        self.assertEqual("new", wait_for_deployment("new", fetch=fetch, sleep=sleep, health=health))
        self.assertEqual(2, sleep.call_count)
        health.assert_called_once_with("/health")

    def test_failed_deployment_or_health_prevents_post_deploy_import(self):
        health = Mock(return_value={"ok": False})
        with self.assertRaisesRegex(ValueError, "FAILED"):
            wait_for_deployment("new", fetch=lambda: [self.deployment("new", "FAILED")], health=health)
        health.assert_not_called()
        with self.assertRaisesRegex(ValueError, "health check failed"):
            wait_for_deployment("new", fetch=lambda: [self.deployment("new")], health=health)

    def test_deployment_wait_is_bounded(self):
        sleep = Mock()
        with self.assertRaises(TimeoutError):
            wait_for_deployment("new", fetch=lambda: [], sleep=sleep,
                                clock=Mock(side_effect=[0, 0, 10]), timeout=5)
        sleep.assert_called_once()

    def feed(self):
        fixtures = Path(__file__).parent / "fixtures"
        event = json.loads((fixtures / "scoreboard-event.json").read_text())
        summary = json.loads((fixtures / "boxscore.json").read_text())
        game = event_to_game(event, {}, lambda url: summary)
        game["date"] = datetime.now(EASTERN).date().isoformat()
        return {"schemaVersion": 1, "defaultGameId": game["id"], "games": [game]}

    @patch.dict("os.environ", {"ADMIN_USERNAME": "owner", "ADMIN_PASSWORD": "isolated-test-password"})
    def test_import_lock_is_retried_then_public_feed_is_validated_without_auth(self):
        request = Mock(side_effect=[urllib.error.HTTPError(BASE_URL, 409, "Busy", {}, None),
                                    {"ok": True}, self.feed()])
        sleep = Mock()
        self.assertEqual(1, refresh_feed(request=request, sleep=sleep))
        sleep.assert_called_once_with(10)
        self.assertEqual("/admin/import", request.call_args_list[0].args[0])
        self.assertEqual("POST", request.call_args_list[0].kwargs["method"])
        request.assert_called_with("/games")

    @patch.dict("os.environ", {"ADMIN_USERNAME": "owner", "ADMIN_PASSWORD": "isolated-test-password"})
    def test_rejected_feed_does_not_retry_or_print_remote_error_body(self):
        request = Mock(side_effect=urllib.error.HTTPError(BASE_URL, 502, "private response", {}, None))
        with self.assertRaisesRegex(ValueError, "HTTP 502") as error:
            refresh_feed(request=request)
        self.assertNotIn("private response", str(error.exception))
        self.assertEqual(1, request.call_count)

    @patch.dict("os.environ", {"ADMIN_USERNAME": "owner", "ADMIN_PASSWORD": "isolated-test-password"})
    def test_ambiguous_network_failure_is_not_retried(self):
        request = Mock(side_effect=TimeoutError("Response lost after import"))
        with self.assertRaises(TimeoutError):
            refresh_feed(request=request)
        self.assertEqual(1, request.call_count)

    @patch.dict("os.environ", {"ADMIN_USERNAME": "owner", "ADMIN_PASSWORD": "isolated-test-password"})
    def test_unfinished_or_stale_published_games_fail_verification(self):
        for change in ({"status": "SCHEDULED"}, {"date": "2020-01-01"}):
            feed = copy.deepcopy(self.feed())
            feed["games"][0].update(change)
            with self.subTest(change=change), self.assertRaises(ValueError):
                refresh_feed(request=Mock(side_effect=[{"ok": True}, feed]))

    @patch.dict("os.environ", {}, clear=True)
    def test_missing_credentials_fail_before_network_request(self):
        request = Mock()
        with self.assertRaises(ValueError):
            refresh_feed(request=request)
        request.assert_not_called()

    def test_redirect_cannot_forward_owner_credentials(self):
        request = urllib.request.Request(BASE_URL + "/admin/import", method="POST",
                                         headers={"Authorization": "Basic isolated-test-value"})
        with self.assertRaisesRegex(ValueError, "Unexpected redirect"):
            NoRedirect().redirect_request(request, None, 302, "Redirect", {}, "https://other.example/")


if __name__ == "__main__":
    unittest.main()
