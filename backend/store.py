"""Transactional publications and identity-based corrections; no GitHub dependency."""
import copy
import json
import threading
from contextlib import contextmanager
from datetime import datetime, timezone

from sqlalchemy import Column, Integer, JSON, MetaData, Table, create_engine, select, update

from scripts.update_game_feed import validate_feed, require


def stamp():
    return datetime.now(timezone.utc).isoformat()


def validate_publication(feed):
    validate_feed(feed)
    for game in feed["games"]:
        if game["status"] == "FINISHED":
            for side in ("away", "home"):
                team = game[side]
                require(team["players"] and sum(p["stats"]["points"] for p in team["players"]) == team["score"],
                        f"{team['code']}: player points must equal the final score")
                require(team.get("quarters") and sum(team["quarters"]) == team["score"],
                        f"{team['code']}: quarters must equal the final score")


def leaves(value, path=()):
    """Players use stable IDs, never array positions that can change on import."""
    if type(value) is dict:
        for key, child in value.items():
            yield from leaves(child, path + (key,))
    elif type(value) is list and path and path[-1] == "players":
        for player in value:
            yield from leaves(player, path + (player["id"],))
    else:
        yield path, value


def apply_corrections(raw, corrections, default_id):
    feed = copy.deepcopy(raw)
    for game in feed["games"]:
        for key, value in corrections.get(game["id"], {}).items():
            path = json.loads(key)
            target = game
            try:
                for part in path[:-1]:
                    target = next(p for p in target if p["id"] == part) if type(target) is list else target[part]
                target[path[-1]] = copy.deepcopy(value)
            except (KeyError, StopIteration):
                # A player may no longer be in this game's provider roster.
                continue
    if default_id and any(g["id"] == default_id for g in feed["games"]):
        feed["defaultGameId"] = default_id
    validate_publication(feed)
    return feed


class Conflict(ValueError):
    pass


class Store:
    def __init__(self, url):
        if url.startswith("postgres://"):
            url = url.replace("postgres://", "postgresql+psycopg://", 1)
        elif url.startswith("postgresql://"):
            url = url.replace("postgresql://", "postgresql+psycopg://", 1)
        self.engine = create_engine(url, pool_pre_ping=True)
        self.table = Table("miniscore_state", MetaData(), Column("id", Integer, primary_key=True),
                           Column("state", JSON, nullable=False))
        self.import_lock = threading.Lock()

    def initialize(self, seed):
        validate_publication(seed)
        self.table.metadata.create_all(self.engine)
        with self.engine.begin() as conn:
            if self.engine.dialect.name == "postgresql":
                conn.exec_driver_sql("SELECT pg_advisory_xact_lock(760011)")
            if conn.execute(select(self.table.c.id)).first() is None:
                conn.execute(self.table.insert().values(id=1, state={
                    "raw": seed, "published": seed, "corrections": {}, "defaultOverride": None,
                    "drafts": {}, "history": [], "revision": 1, "updatedAt": stamp(),
                    "lastImport": None, "lastDailyAttempt": None, "importError": None,
                    "source": "bootstrap snapshot", "importing": False}))
            else:
                # A terminated worker releases its advisory lock. Clear a stale
                # in-progress flag without disturbing an old deployment still importing.
                state = copy.deepcopy(conn.execute(select(self.table.c.state).where(self.table.c.id == 1)
                                                   .with_for_update()).scalar_one())
                unlocked = (self.engine.dialect.name != "postgresql" or
                            conn.exec_driver_sql("SELECT pg_try_advisory_xact_lock(760012)").scalar())
                if state["importing"] and unlocked:
                    state["importing"] = False
                    state["importError"] = "Previous import was interrupted. The last publication was preserved."
                    conn.execute(update(self.table).where(self.table.c.id == 1).values(state=state))

    def read(self):
        with self.engine.connect() as conn:
            return copy.deepcopy(conn.execute(select(self.table.c.state).where(self.table.c.id == 1)).scalar_one())

    def mutate(self, action):
        with self.engine.begin() as conn:
            state = copy.deepcopy(conn.execute(select(self.table.c.state).where(self.table.c.id == 1)
                                               .with_for_update()).scalar_one())
            result = action(state)
            conn.execute(update(self.table).where(self.table.c.id == 1).values(state=state))
            return result

    @staticmethod
    def check_revision(state, revision):
        require(type(revision) is int, "revision must be an integer")
        if revision != state["revision"]:
            raise Conflict("The games changed. Reload before saving or publishing.")

    @staticmethod
    def publication(state, source):
        published = apply_corrections(state["raw"], state["corrections"], state["defaultOverride"])
        state["history"] = ([{"revision": state["revision"], "updatedAt": state["updatedAt"],
                              "source": state["source"], "raw": state["raw"],
                              "corrections": state["corrections"], "defaultOverride": state["defaultOverride"]}]
                            + state["history"])[:10]
        # Independent copies are needed because later default/correction edits must not alter history.
        state["history"] = copy.deepcopy(state["history"])
        state["published"] = published
        state["revision"] += 1
        state["updatedAt"] = stamp()
        state["source"] = source

    def save_game(self, game, revision, publish=False):
        def action(state):
            self.check_revision(state, revision)
            current = next(g for g in state["published"]["games"] if g["id"] == game["id"])
            require(game["id"] == current["id"], "Game ID cannot change")
            for side in ("away", "home"):
                require(game[side]["code"] == current[side]["code"], "Team identity cannot change")
                require([p["id"] for p in game[side]["players"]] == [p["id"] for p in current[side]["players"]],
                        "Player identities/order cannot change")
            preview = copy.deepcopy(state["published"])
            preview["games"] = [game if g["id"] == game["id"] else g for g in preview["games"]]
            validate_publication(preview)
            state["drafts"][game["id"]] = {"game": game, "revision": revision, "savedAt": stamp()}
            if publish:
                before = dict(leaves(current))
                old_corrections = copy.deepcopy(state["corrections"])
                changes = state["corrections"].setdefault(game["id"], {})
                after = dict(leaves(game))
                for path, value in after.items():
                    if path not in before or before[path] != value:
                        changes[json.dumps(path)] = value
                for path in before.keys() - after.keys():
                    changes[json.dumps(path)] = None
                self.publication(state, "manual correction")
                state["history"][0]["corrections"] = old_corrections
                del state["drafts"][game["id"]]
            return state["revision"]
        return self.mutate(action)

    def set_default(self, gid, revision):
        def action(state):
            self.check_revision(state, revision)
            require(any(g["id"] == gid for g in state["published"]["games"]), "Unknown game")
            self.publication_before_default(state, gid)
            return state["revision"]
        return self.mutate(action)

    def publication_before_default(self, state, gid):
        # Record the old default in history before switching the override.
        old = state["defaultOverride"]
        self.publication(state, "default game changed")
        state["defaultOverride"] = gid
        state["published"]["defaultGameId"] = gid
        state["history"][0]["defaultOverride"] = old

    def clear_corrections(self, gid, revision):
        def action(state):
            self.check_revision(state, revision)
            old = copy.deepcopy(state["corrections"])
            state["corrections"].pop(gid, None)
            self.publication(state, "manual corrections cleared")
            state["history"][0]["corrections"] = old
            state["drafts"].pop(gid, None)
            return state["revision"]
        return self.mutate(action)

    def restore(self, target_revision, revision):
        def action(state):
            self.check_revision(state, revision)
            target = next((h for h in state["history"] if h["revision"] == target_revision), None)
            require(target is not None, "Publication is no longer in history")
            old = {key: copy.deepcopy(state[key]) for key in ("raw", "corrections", "defaultOverride")}
            for key in old:
                state[key] = copy.deepcopy(target[key])
            self.publication(state, f"restored revision {target_revision}")
            state["history"][0].update(old)
            state["drafts"] = {}
            return state["revision"]
        return self.mutate(action)

    @contextmanager
    def importer(self):
        if not self.import_lock.acquire(blocking=False):
            raise Conflict("An import is already running")
        try:
            with self.engine.connect() as conn:
                postgres = self.engine.dialect.name == "postgresql"
                if postgres and not conn.exec_driver_sql("SELECT pg_try_advisory_lock(760012)").scalar():
                    raise Conflict("An import is already running")
                try:
                    yield
                finally:
                    if postgres:
                        conn.exec_driver_sql("SELECT pg_advisory_unlock(760012)")
                        conn.commit()
        finally:
            self.import_lock.release()

    def import_feed(self, build, daily_date=None):
        with self.importer():
            def begin(state):
                if daily_date and state["lastDailyAttempt"] == daily_date:
                    return False
                state["importing"] = True
                if daily_date:
                    state["lastDailyAttempt"] = daily_date
                return True
            if not self.mutate(begin):
                return False
            try:
                candidate = build(copy.deepcopy(self.read()["raw"]))
                validate_publication(candidate)
                def commit(state):
                    old_raw = copy.deepcopy(state["raw"])
                    state["raw"] = candidate
                    self.publication(state, "ESPN import")
                    state["history"][0]["raw"] = old_raw
                    state["lastImport"] = stamp()
                    state["importError"] = None
                    state["importing"] = False
                self.mutate(commit)
                return True
            except Exception:
                def failure(state):
                    state["importing"] = False
                    state["importError"] = "Import failed. The last validated publication was preserved."
                self.mutate(failure)
                raise

    def dispose(self):
        self.engine.dispose()
