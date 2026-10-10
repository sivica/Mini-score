#!/usr/bin/env python3
"""Publish ESPN NBA data in Miniscore's existing schema; fail before replacing a feed."""
import argparse
import copy
import json
import os
import re
import tempfile
import time
import urllib.error
import urllib.request
from datetime import date, datetime, timedelta, timezone
from pathlib import Path
from urllib.parse import urlencode, urlsplit
from zoneinfo import ZoneInfo

API = "https://site.api.espn.com/apis/site/v2/sports/basketball/nba/"
EASTERN = ZoneInfo("America/New_York")
TEAM_CODES = {"GS": "GSW", "NY": "NYK", "NO": "NOP", "SA": "SAS", "UTAH": "UTA", "WSH": "WAS"}
MAX_BYTES = 2 * 1024 * 1024
STAT_LIMITS = {"points": 200, "rebounds": 100, "assists": 100, "steals": 100,
               "blocks": 100, "turnovers": 100, "fgMade": 100, "fgAttempted": 100,
               "minutes": 100, "seconds": 59}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def integer(value, maximum):
    # bool is an int subclass in Python; JSON booleans are never stats.
    require(type(value) is int and 0 <= value <= maximum, "Expected bounded JSON integer")
    return value


def source_integer(value, maximum):
    # ESPN encodes box-score counts as digit strings. Convert explicitly, never round.
    if isinstance(value, str) and re.fullmatch(r"[0-9]+", value):
        value = int(value)
    return integer(value, maximum)


def text(value):
    require(isinstance(value, str) and 0 < len(value.strip()) <= 200, "Expected nonempty text")
    return value.strip()


def array(value, maximum):
    require(type(value) is list and len(value) <= maximum, "Expected bounded JSON array")
    return value


def image_url(value):
    url = urlsplit(text(value))
    require(url.scheme == "https" and url.hostname and not url.username and not url.password,
            "Image must use HTTPS without credentials")
    return value


def team_code(team):
    code = text(team["abbreviation"])
    return TEAM_CODES.get(code, code)


def validate_feed(feed):
    require(type(feed) is dict, "Expected feed object")
    require(integer(feed["schemaVersion"], 1) == 1, "Unsupported schema")
    games = array(feed["games"], 100)
    require(games, "Empty feed")
    ids = set()
    for game in games:
        gid = text(game["id"])
        require(gid not in ids and gid not in ("nyk_cle", "lal_gsw", "bos_mia"), "Duplicate/demo game")
        ids.add(gid)
        require(re.fullmatch(r"\d{4}-\d{2}-\d{2}", text(game["date"])), "Invalid date")
        date.fromisoformat(game["date"])
        text(game["competition"])
        require(game["status"] in ("SCHEDULED", "LIVE", "FINISHED", "POSTPONED", "CANCELLED"),
                "Unknown game status")
        for key in ("venue", "tipoffUTC"):
            if game.get(key) is not None:
                text(game[key])
        if game.get("tipoffUTC"):
            datetime.strptime(game["tipoffUTC"], "%Y-%m-%dT%H:%M:%SZ")
        player_ids = set()
        quarter_counts = []
        for side in ("away", "home"):
            team = game[side]
            require(re.fullmatch(r"[A-Z]{2,4}", text(team["code"])), "Invalid team code")
            text(team["name"])
            integer(team["score"], 500)
            for key in ("primaryColor", "secondaryColor"):
                if key in team:
                    require(re.fullmatch(r"#[0-9a-fA-F]{6}", team[key]), "Invalid color")
            if "shortName" in team:
                text(team["shortName"])
            if team.get("logoUrl") is not None:
                image_url(team["logoUrl"])
            quarters = array(team.get("quarters", []), 15)
            for score in quarters:
                integer(score, 100)
            quarter_counts.append(len(quarters))
            players = array(team["players"], 30)
            starters = 0
            for player in players:
                pid = text(player["id"])
                require(pid not in player_ids, "Duplicate player")
                player_ids.add(pid)
                text(player["name"])
                text(player["number"])
                require(type(player["starter"]) is bool, "Starter must be boolean")
                starters += player["starter"]
                if player.get("portraitUrl") is not None:
                    image_url(player["portraitUrl"])
                stats = player["stats"]
                for field, limit in STAT_LIMITS.items():
                    integer(stats[field], limit)
                require(stats["fgMade"] <= stats["fgAttempted"], "FGM exceeds FGA")
            require(starters <= 5, "More than five starters")
        require(game["away"]["code"] != game["home"]["code"], "Teams must differ")
        require(quarter_counts[0] == quarter_counts[1], "Mismatched periods")
    require(text(feed["defaultGameId"]) in ids, "Missing default game")
    require(len(encode(feed)) <= MAX_BYTES, "Feed too large")


def encode(feed):
    return (json.dumps(feed, ensure_ascii=False, indent=2, allow_nan=False) + "\n").encode("utf-8")


def fetch_json(url):
    for attempt in range(3):
        try:
            request = urllib.request.Request(url, headers={"User-Agent": "Miniscore-feed/1.0"})
            with urllib.request.urlopen(request, timeout=30) as response:
                data = response.read(8 * MAX_BYTES + 1)
            require(len(data) <= 8 * MAX_BYTES, "Provider response too large")
            return json.loads(data)
        except (urllib.error.URLError, TimeoutError):
            if attempt == 2:
                raise
            time.sleep(2 ** attempt)


def provider_status(competition):
    kind = competition["status"]["type"]
    name = kind["name"]
    if name == "STATUS_POSTPONED":
        return "POSTPONED"
    if name in ("STATUS_CANCELED", "STATUS_CANCELLED"):
        return "CANCELLED"
    if kind.get("completed") is True:
        return "FINISHED"
    require(kind["state"] in ("pre", "in"), "Unrecognized provider status")
    return "LIVE" if kind["state"] == "in" else "SCHEDULED"


def parse_players(team_box):
    groups = array(team_box["statistics"], 10)
    require(len(groups) == 1, "Unexpected box-score groups")
    names = groups[0]["names"]
    require(type(names) is list and len(set(names)) == len(names), "Invalid stat labels")
    required = {"MIN", "PTS", "FG", "REB", "AST", "STL", "BLK", "TO"}
    require(required <= set(names), "Missing box-score stats")
    players = []
    for entry in array(groups[0]["athletes"], 30):
        require(type(entry["didNotPlay"]) is bool, "Invalid DNP flag")
        if entry["didNotPlay"]:
            continue
        values = entry["stats"]
        require(type(values) is list and len(values) == len(names), "Incomplete player stats")
        stats = dict(zip(names, values))
        require(isinstance(stats["FG"], str) and re.fullmatch(r"\d+-\d+", stats["FG"]), "Invalid FG")
        fg = stats["FG"].split("-")
        minutes = stats["MIN"]
        require(isinstance(minutes, str) and re.fullmatch(r"\d+(?::[0-5]\d)?", minutes), "Invalid minutes")
        parts = minutes.split(":")
        athlete = entry["athlete"]
        player = {"id": "espn_" + text(athlete["id"]), "name": text(athlete["displayName"]),
                  "number": text(athlete["jersey"]), "starter": entry["starter"],
                  "stats": {"points": source_integer(stats["PTS"], 200),
                            "rebounds": source_integer(stats["REB"], 100),
                            "assists": source_integer(stats["AST"], 100),
                            "steals": source_integer(stats["STL"], 100),
                            "blocks": source_integer(stats["BLK"], 100),
                            "turnovers": source_integer(stats["TO"], 100),
                            "fgMade": source_integer(fg[0], 100),
                            "fgAttempted": source_integer(fg[1], 100),
                            "minutes": source_integer(parts[0], 100),
                            "seconds": source_integer(parts[1], 59) if len(parts) == 2 else 0}}
        if athlete.get("headshot", {}).get("href"):
            player["portraitUrl"] = image_url(athlete["headshot"]["href"])
        players.append(player)
    return players


def event_to_game(event, existing, fetch):
    event_id = text(event["id"])
    require(re.fullmatch(r"\d+", event_id), "Invalid event ID")
    competitions = array(event["competitions"], 1)
    require(len(competitions) == 1, "Missing competition")
    competition = competitions[0]
    status = provider_status(competition)
    tipoff = datetime.fromisoformat(event["date"].replace("Z", "+00:00"))
    require(tipoff.tzinfo is not None, "Missing tipoff timezone")
    game_date = tipoff.astimezone(EASTERN).date().isoformat()
    competitors = array(competition["competitors"], 2)
    sides = {c["homeAway"]: c for c in competitors}
    require(set(sides) == {"away", "home"}, "Missing team")
    identity = (game_date, team_code(sides["away"]["team"]), team_code(sides["home"]["team"]))
    prior = existing.get(identity)
    game = {"id": prior["id"] if prior else "espn_" + event_id, "date": game_date,
            "competition": {1: "NBA Preseason", 2: "NBA", 3: "NBA Playoffs"}[event["season"]["type"]],
            "status": status, "tipoffUTC": tipoff.astimezone(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
            "sources": [f"https://www.espn.com/nba/boxscore/_/gameId/{event_id}"]}
    if competition.get("venue", {}).get("fullName"):
        game["venue"] = text(competition["venue"]["fullName"])
    boxes = {}
    if status in ("FINISHED", "LIVE"):
        summary = fetch(API + "summary?" + urlencode({"event": event_id}))
        require(summary["header"]["id"] == event_id, "Wrong box score")
        header = summary["header"]["competitions"][0]
        require(provider_status(header) == status, "Game changed during fetch; retry later")
        header_sides = {c["homeAway"]: c for c in header["competitors"]}
        for side in sides:
            require(team_code(header_sides[side]["team"]) == team_code(sides[side]["team"]),
                    "Box-score team mismatch")
            require(source_integer(header_sides[side]["score"], 500) == source_integer(sides[side]["score"], 500),
                    "Score changed during fetch; retry later")
        for box in array(summary["boxscore"]["players"], 2):
            code = team_code(box["team"])
            require(code not in boxes, "Duplicate box-score team")
            boxes[code] = parse_players(box)
        require(set(boxes) == {identity[1], identity[2]}, "Missing box-score team")
    for side, competitor in sides.items():
        team = competitor["team"]
        code = team_code(team)
        score = source_integer(competitor["score"], 500)
        quarters = [source_integer(q["displayValue"], 100)
                    for q in array(competitor.get("linescores", []), 15)] if boxes else []
        players = boxes.get(code, [])
        if status == "FINISHED":
            require(players and sum(p["stats"]["points"] for p in players) == score, "Incomplete final box score")
            require(quarters and sum(quarters) == score, "Incomplete final periods")
        output = {"code": code, "name": text(team["displayName"]), "shortName": text(team["name"]),
                  "score": score if boxes else 0, "quarters": quarters, "players": players}
        if team.get("logo"):
            output["logoUrl"] = image_url(team["logo"])
        for field, source in (("primaryColor", "color"), ("secondaryColor", "alternateColor")):
            if team.get(source):
                output[field] = "#" + team[source]
        game[side] = output
    # Never downgrade a previously completed game because a source has stale status.
    if prior and prior["status"] == "FINISHED":
        require(status == "FINISHED", "Provider attempted to downgrade final result")
    return game


def build_feed(previous, today, fetch=fetch_json):
    validate_feed(previous)
    cutoff = (today - timedelta(days=7)).isoformat()
    normalized = copy.deepcopy(previous["games"])
    for game in normalized:
        for side in ("away", "home"):
            game[side]["code"] = TEAM_CODES.get(game[side]["code"], game[side]["code"])
    retained = [g for g in normalized if g["date"] >= cutoff]
    identity = lambda g: (g["date"], g["away"]["code"], g["home"]["code"])
    existing = {identity(g): g for g in normalized}
    games = {identity(g): g for g in retained}
    seen = set()
    # Catch up retained games after a missed daily run, including legacy IDs
    # which cannot be used directly with ESPN's summary endpoint.
    days = {today + timedelta(days=offset) for offset in (-1, 0, 1)}
    days.update(date.fromisoformat(g["date"]) for g in retained
                if g["status"] in ("SCHEDULED", "LIVE", "POSTPONED")
                and g["date"] < today.isoformat())
    for day in sorted(days):
        scoreboard = fetch(API + "scoreboard?" + urlencode({"dates": day.strftime("%Y%m%d"), "limit": 100}))
        for event in array(scoreboard["events"], 100):
            if event["id"] in seen:
                continue
            seen.add(event["id"])
            game = event_to_game(event, existing, fetch)
            require(abs((date.fromisoformat(game["date"]) - day).days) <= 1, "Stale scoreboard date")
            games[identity(game)] = game
    require(games, "No games available; preserving previous feed")
    ordered = sorted(games.values(), key=lambda g: (g["date"], g.get("tipoffUTC", ""), g["id"]), reverse=True)
    ordered = ordered[:100]
    finals = [g for g in ordered if g["status"] == "FINISHED"]
    upcoming = [g for g in ordered if g["status"] == "SCHEDULED" and g["date"] >= today.isoformat()]
    default = finals[0] if finals else min(upcoming, key=lambda g: g.get("tipoffUTC", "9999")) if upcoming else ordered[0]
    output = {"schemaVersion": 1, "defaultGameId": default["id"], "games": ordered}
    validate_feed(output)
    return output


def update_file(path, today, fetch=fetch_json):
    original = path.read_bytes()
    require(len(original) <= MAX_BYTES, "Existing feed too large")
    previous = json.loads(original)
    result = build_feed(previous, today, fetch)
    if previous == result:
        return False
    # All reads and validation happen before the atomic replace. Failures leave bytes untouched.
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(dir=path.parent, delete=False) as output:
            temporary = Path(output.name)
            output.write(encode(result))
            output.flush()
            os.fsync(output.fileno())
        os.replace(temporary, path)
    finally:
        if temporary and temporary.exists():
            temporary.unlink()
    return True


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--feed", type=Path, required=True)
    parser.add_argument("--date", type=date.fromisoformat, default=datetime.now(EASTERN).date(),
                        help="NBA Eastern date; defaults to today")
    args = parser.parse_args()
    changed = update_file(args.feed, args.date)
    print("Validated and updated games.json" if changed else "No data changes; games.json preserved")


if __name__ == "__main__":
    main()
