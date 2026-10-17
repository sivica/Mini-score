#!/usr/bin/env python3
"""Verify the exact Railway deployment, then refresh through the existing owner API."""
import argparse
import base64
import json
import os
import subprocess
import time
import urllib.error
import urllib.request
from datetime import datetime, timedelta

from update_game_feed import EASTERN, MAX_BYTES, require, validate_feed

PROJECT = "43e82cd3-ad0e-4e4c-8de1-8e093a36d642"
ENVIRONMENT = "61aa1f20-b04c-40da-bebd-896faf2f2a2a"
SERVICE = "f19c876d-b933-48c7-ab21-be7a0d8bf002"
BASE_URL = "https://miniscore-api-production.up.railway.app"


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        # Never forward the owner credentials to a redirect destination.
        raise ValueError("Unexpected redirect from Miniscore API")


def request_json(path, *, method="GET", auth=None):
    headers = {"Cache-Control": "no-cache", "User-Agent": "Miniscore-deploy/1.0"}
    if auth:
        headers.update(Authorization="Basic " + base64.b64encode(auth.encode()).decode(),
                       **{"X-Miniscore-Admin": "1"})
    request = urllib.request.Request(BASE_URL + path, headers=headers, method=method)
    with urllib.request.build_opener(NoRedirect()).open(request, timeout=180) as response:
        data = response.read(MAX_BYTES + 1)
    require(len(data) <= MAX_BYTES, "API response too large")
    return json.loads(data)


def deployments():
    result = subprocess.run(["railway", "deployment", "list", "--project", PROJECT,
                             "--environment", ENVIRONMENT, "--service", SERVICE,
                             "--limit", "20", "--json"], check=True, capture_output=True, text=True)
    return json.loads(result.stdout)


def wait_for_deployment(message, *, fetch=deployments, health=request_json, sleep=time.sleep,
                        clock=time.monotonic, timeout=900):
    deadline = clock() + timeout
    while clock() < deadline:
        # A unique workflow run/attempt marker prevents accepting an older healthy deployment.
        matching = [d for d in fetch() if d.get("meta", {}).get("cliMessage") == message]
        if matching:
            latest = max(matching, key=lambda d: d["createdAt"])
            status = latest["status"]
            if status in ("FAILED", "CRASHED", "REMOVED", "SKIPPED"):
                raise ValueError("Railway deployment failed: " + status)
            if status == "SUCCESS":
                require(health("/health").get("ok") is True, "Railway health check failed")
                return latest["id"]
        sleep(10)
    raise TimeoutError("Timed out waiting for this Railway deployment")


def refresh_feed(*, request=request_json, sleep=time.sleep):
    username, password = os.environ.get("ADMIN_USERNAME"), os.environ.get("ADMIN_PASSWORD")
    require(bool(username) and bool(password), "Railway owner credentials unavailable")
    # Only a definite lock conflict is retried. A network error after a POST can be ambiguous.
    for attempt in range(12):
        try:
            result = request("/admin/import", method="POST", auth=username + ":" + password)
            require(result.get("ok") is True, "Import did not report success")
            break
        except urllib.error.HTTPError as exc:
            if exc.code != 409 or attempt == 11:
                raise ValueError(f"Stats import failed (HTTP {exc.code}); previous feed is preserved") from None
            sleep(10)
    feed = request("/games")
    validate_feed(feed)
    today = datetime.now(EASTERN).date()
    allowed = {today.isoformat(), (today - timedelta(days=1)).isoformat()}
    require(all(g["status"] == "FINISHED" and g["date"] in allowed for g in feed["games"]),
            "Published games are outside the finished today/yesterday window")
    return len(feed["games"])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("wait").add_argument("--message", required=True)
    commands.add_parser("refresh")
    args = parser.parse_args()
    if args.command == "wait":
        print("Healthy deployment:", wait_for_deployment(args.message))
    else:
        print("Validated finished games published:", refresh_feed())


if __name__ == "__main__":
    main()
