import copy
import asyncio
import json
import threading
from datetime import datetime, timezone
from pathlib import Path

import pytest
import httpx
from fastapi.testclient import TestClient

from backend.app import create_app, daily_date
from backend.store import Conflict, Store

SEED = json.loads((Path(__file__).parents[1] / "seed.json").read_text())


@pytest.fixture
def store(tmp_path):
    result = Store(f"sqlite:///{tmp_path}/test.db")
    result.initialize(copy.deepcopy(SEED))
    yield result
    result.dispose()


def finished(store):
    return copy.deepcopy(next(g for g in store.read()["published"]["games"] if g["status"] == "FINISHED"))


def test_rejected_import_preserves_exact_publication_and_revision(store):
    before = store.read()
    def broken(previous):
        previous["games"][0]["away"]["score"] = "5"
        return previous
    with pytest.raises(ValueError):
        store.import_feed(broken)
    after = store.read()
    assert after["published"] == before["published"]
    assert after["raw"] == before["raw"]
    assert after["revision"] == before["revision"]
    assert after["importError"] and not after["importing"]


@pytest.mark.parametrize("value", [1.0, 1.5, True, "1", None, -1, 5000])
def test_invalid_stats_cannot_replace_published_games(store, value):
    game = finished(store)
    before = store.read()
    game["away"]["players"][0]["stats"]["minutes"] = value
    with pytest.raises(ValueError):
        store.save_game(game, before["revision"], publish=True)
    assert store.read() == before


def test_final_score_mismatch_rejected(store):
    game = finished(store)
    game["away"]["score"] += 1
    before = store.read()
    with pytest.raises(ValueError, match="player points"):
        store.save_game(game, before["revision"], publish=True)
    assert store.read() == before


def test_draft_is_not_public_and_restore_clears_correction(store):
    game = finished(store)
    before = store.read()
    game["venue"] = "Corrected arena"
    store.save_game(game, 1)
    assert store.read()["published"] == before["published"]
    store.save_game(game, 1, publish=True, draft_version=1)
    assert store.read()["revision"] == 2
    store.restore(1, 2)
    assert store.read()["published"] == before["published"]
    assert store.read()["corrections"] == {}


def test_correction_follows_player_id_after_import_reorders_roster(store):
    game = finished(store)
    pid = game["away"]["players"][0]["id"]
    game["away"]["players"][0]["stats"]["minutes"] = 44
    store.save_game(game, 1, publish=True)
    def reordered(previous):
        next(g for g in previous["games"] if g["id"] == game["id"])["away"]["players"].reverse()
        return previous
    store.import_feed(reordered)
    actual = next(g for g in store.read()["published"]["games"] if g["id"] == game["id"])
    assert next(p for p in actual["away"]["players"] if p["id"] == pid)["stats"]["minutes"] == 44
    store.clear_corrections(game["id"], 3)
    assert store.read()["published"] == store.read()["raw"]


def test_optional_photo_can_be_removed(store):
    game = finished(store)
    del game["away"]["players"][0]["portraitUrl"]
    store.save_game(game, 1, publish=True)
    actual = next(g for g in store.read()["published"]["games"] if g["id"] == game["id"])
    assert actual["away"]["players"][0]["portraitUrl"] is None


def test_stale_editor_rejected_without_overwriting_import(store):
    game = finished(store)
    store.import_feed(lambda previous: previous)
    with pytest.raises(Conflict):
        store.save_game(game, 1, publish=True)
    assert store.read()["revision"] == 2


def test_draft_keeps_original_base_after_import(store):
    original = finished(store)
    edited = copy.deepcopy(original)
    edited["venue"] = "Draft arena"
    store.save_game(edited, 1)
    def imported(previous):
        game = next(g for g in previous["games"] if g["id"] == edited["id"])
        game["venue"] = "Provider arena"
        game["away"]["players"].reverse()
        return previous
    store.import_feed(imported)
    state = store.read()
    assert state["drafts"][edited["id"]]["game"] == edited
    assert state["drafts"][edited["id"]]["base"] == original
    assert state["drafts"][edited["id"]]["revision"] == 1
    with pytest.raises(Conflict):
        store.save_game(edited, 1, publish=True)


@pytest.mark.parametrize("publish", [False, True])
def test_concurrent_draft_editor_cannot_overwrite_saved_work(store, publish):
    game = finished(store)
    first = copy.deepcopy(game)
    first["venue"] = "First saved draft"
    store.save_game(first, 1, draft_version=0)
    before = store.read()
    game["venue"] = "Stale tab edit"
    with pytest.raises(Conflict, match="saved draft changed"):
        store.save_game(game, 1, publish=publish, draft_version=0)
    assert store.read() == before
    assert before["revision"] == 1
    assert before["draftVersions"][game["id"]] == 1
    store.save_game(game, 1, publish=publish, draft_version=1)
    after = store.read()
    assert after["draftVersions"][game["id"]] == 2
    assert after["revision"] == (2 if publish else 1)


def test_existing_drafts_receive_versions_on_upgrade_without_changing_data(store):
    game = finished(store)
    store.save_game(game, 1)
    store.mutate(lambda state: state.pop("draftVersions"))
    before = store.read()
    store.initialize(copy.deepcopy(SEED))
    after = store.read()
    assert after.pop("draftVersions") == {game["id"]: 1}
    assert after == before
    with pytest.raises(Conflict, match="saved draft changed"):
        store.save_game(game, 1, draft_version=0)


def test_draft_versions_are_independent_per_game(store):
    games = copy.deepcopy(store.read()["published"]["games"][:2])
    assert len(games) == 2
    for game in games:
        store.save_game(game, 1, draft_version=0)
    assert store.read()["draftVersions"] == {game["id"]: 1 for game in games}


def test_scheduler_database_wait_does_not_block_http(tmp_path, monkeypatch):
    started, release, timed_out = threading.Event(), threading.Event(), threading.Event()
    app = create_app(f"sqlite:///{tmp_path}/scheduler.db", seed=copy.deepcopy(SEED))
    original_read = app.state.store.read
    def slow_read():
        started.set()
        if not release.wait(1):
            timed_out.set()
        return original_read()
    monkeypatch.setattr(app.state.store, "read", slow_read)
    monkeypatch.setattr("backend.app.daily_date", lambda now: "2026-10-10")
    async def check():
        async with app.router.lifespan_context(app):
            app.state.store.mutate(lambda state: state.update(lastDailyAttempt="2026-10-10"))
            try:
                async with asyncio.timeout(2):
                    while not started.is_set():
                        await asyncio.sleep(0.01)
                    async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://testserver") as client:
                        response = await client.get("/admin")
                    assert response.status_code == 401
                    assert not timed_out.is_set(), "Database read blocked the API event loop"
            finally:
                release.set()
    asyncio.run(check())


def test_failed_provider_call_and_duplicate_daily_run(store):
    def offline(previous):
        raise TimeoutError("Offline")
    with pytest.raises(TimeoutError):
        store.import_feed(offline, "2026-10-10")
    assert store.import_feed(lambda previous: pytest.fail("Second daily import"), "2026-10-10") is False
    assert store.read()["published"] == SEED


def test_nested_import_cannot_run(store):
    with store.importer():
        with pytest.raises(Conflict):
            store.import_feed(lambda previous: previous)


def test_restart_recovers_interrupted_import_status_without_retrying_same_day(store):
    def interrupted(state):
        state["importing"] = True
        state["lastDailyAttempt"] = "2026-10-10"
    store.mutate(interrupted)
    store.initialize(copy.deepcopy(SEED))
    state = store.read()
    assert state["published"] == SEED
    assert state["importing"] is False
    assert "interrupted" in state["importError"]
    assert store.import_feed(lambda previous: pytest.fail("Unexpected retry"), "2026-10-10") is False


def test_default_change_and_restore(store):
    gid = SEED["games"][0]["id"]
    store.set_default(gid, 1)
    assert store.read()["published"]["defaultGameId"] == gid
    store.import_feed(lambda previous: previous)
    assert store.read()["published"]["defaultGameId"] == gid
    store.restore(1, 3)
    assert store.read()["published"] == SEED


@pytest.mark.parametrize("utc,expected", [
    ("2026-10-10T05:59:00+00:00", None),
    ("2026-10-10T06:00:00+00:00", "2026-10-10"),
    ("2026-12-10T06:59:00+00:00", None),
    ("2026-12-10T07:00:00+00:00", "2026-12-10"),
])
def test_8am_schedule_handles_summer_and_winter_time(utc, expected):
    assert daily_date(datetime.fromisoformat(utc)) == expected


@pytest.fixture
def client(tmp_path, monkeypatch):
    monkeypatch.setenv("ADMIN_USERNAME", "owner")
    monkeypatch.setenv("ADMIN_PASSWORD", "a-long-local-test-password")
    app = create_app(f"sqlite:///{tmp_path}/api.db", scheduler=False, seed=copy.deepcopy(SEED))
    with TestClient(app) as result:
        yield result


def test_public_api_requires_no_admin_and_supports_conditional_fetch(client):
    response = client.get("/games")
    assert response.status_code == 200 and response.json() == SEED
    assert client.get("/games", headers={"If-None-Match": response.headers["etag"]}).status_code == 304
    assert client.get("/games/latest").json()["id"] == SEED["defaultGameId"]
    assert client.get("/games/missing").status_code == 404
    assert client.get("/games?date=2026-02-30").status_code == 422
    assert client.get("/health").json()["ok"] is True


def test_dashboard_auth_and_cross_origin_writes_blocked(client):
    auth = ("owner", "a-long-local-test-password")
    assert client.get("/admin").status_code == 401
    assert client.get("/admin/state", auth=("owner", "bad")).status_code == 401
    response = client.get("/admin", auth=auth)
    assert response.status_code == 200 and response.headers["cache-control"] == "no-store"
    assert "frame-ancestors 'none'" in response.headers["content-security-policy"]
    data = {"gameId": SEED["defaultGameId"], "revision": 1}
    assert client.post("/admin/default", auth=auth, json=data).status_code == 403
    assert client.post("/admin/default", auth=auth, json=data,
                       headers={"X-Miniscore-Admin": "1", "Origin": "https://evil.example"}).status_code == 403
    assert client.post("/admin/default", auth=auth, json=data,
                       headers={"X-Miniscore-Admin": "1", "Origin": "http://testserver"}).status_code == 200


def test_api_rejects_invalid_json_instead_of_server_error(client):
    auth = ("owner", "a-long-local-test-password")
    headers = {"X-Miniscore-Admin": "1", "Content-Type": "application/json"}
    assert client.post("/admin/game", auth=auth, content="{", headers=headers).status_code == 422
    assert client.post("/admin/game", auth=auth, json={"game": {}, "revision": 1}, headers=headers).status_code == 422
    assert client.get("/games").json() == SEED


def test_api_draft_conflict_preserves_publication_and_first_draft(client):
    auth = ("owner", "a-long-local-test-password")
    headers = {"X-Miniscore-Admin": "1"}
    state = client.get("/admin/state", auth=auth).json()
    game = copy.deepcopy(state["published"]["games"][0])
    game["venue"] = "First tab"
    request = {"game": game, "revision": state["revision"], "draftVersion": 0}
    assert client.post("/admin/game", auth=auth, headers=headers, json=request).status_code == 200
    before = client.get("/admin/state", auth=auth).json()
    for publish in (False, True):
        game["venue"] = "Other tab"
        request["publish"] = publish
        response = client.post("/admin/game", auth=auth, headers=headers, json=request)
        assert response.status_code == 409
        assert client.get("/admin/state", auth=auth).json() == before
    for version in (None, True, -1, "1", 1.0):
        request["draftVersion"] = version
        assert client.post("/admin/game", auth=auth, headers=headers, json=request).status_code == 422
    assert client.get("/games").json() == state["published"]
