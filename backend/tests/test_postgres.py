"""Exercise real PostgreSQL locks in CI using a disposable schema."""
import copy
import os
import threading
import uuid
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import json

import pytest
from sqlalchemy import create_engine

from backend.store import Conflict, Store
from backend.backup import restore_state


@pytest.fixture
def postgres_stores():
    url = os.environ.get("TEST_DATABASE_URL")
    if not url:
        pytest.skip("TEST_DATABASE_URL supplies isolated PostgreSQL in CI")
    schema = "test_" + uuid.uuid4().hex
    root = create_engine(url)
    with root.begin() as conn:
        conn.exec_driver_sql(f'CREATE SCHEMA "{schema}"')
    stores = [Store(url), Store(url)]
    for store in stores:
        store.engine = store.engine.execution_options(schema_translate_map={None: schema})
    seed = json.loads((Path(__file__).parents[1] / "seed.json").read_text())
    try:
        stores[0].initialize(copy.deepcopy(seed))
        yield stores
    finally:
        for store in stores:
            store.dispose()
        with root.begin() as conn:
            conn.exec_driver_sql(f'DROP SCHEMA "{schema}" CASCADE')
        root.dispose()


def test_postgres_concurrent_draft_save_has_one_winner(postgres_stores):
    first, second = postgres_stores
    game = copy.deepcopy(first.read()["published"]["games"][0])
    before = first.read()["published"]
    barrier = threading.Barrier(2)
    def save(store, venue):
        candidate = copy.deepcopy(game)
        candidate["venue"] = venue
        barrier.wait(timeout=5)
        try:
            store.save_game(candidate, 1, draft_version=0)
            return venue
        except Conflict:
            return None
    with ThreadPoolExecutor(max_workers=2) as executor:
        outcomes = list(executor.map(lambda args: save(*args), [(first, "First"), (second, "Second")]))
    winner = [value for value in outcomes if value is not None]
    assert len(winner) == 1
    state = first.read()
    assert state["drafts"][game["id"]]["game"]["venue"] == winner[0]
    assert state["draftVersions"][game["id"]] == 1
    assert state["published"] == before and state["revision"] == 1


def test_postgres_import_lock_works_across_store_instances(postgres_stores):
    first, second = postgres_stores
    with first.importer():
        with pytest.raises(Conflict, match="already running"):
            with second.importer():
                pytest.fail("Advisory lock allowed concurrent import")
    with second.importer():
        pass


def test_postgres_backup_restore_preserves_complete_state(postgres_stores):
    source, target = postgres_stores
    state = source.read()
    # The disposable target uses a different table in the same isolated schema.
    target.table.name = "restored_state"
    restore_state(state, target)
    assert target.read() == state
    with pytest.raises(ValueError, match="must be empty"):
        restore_state(state, target)
    assert source.read() == state and target.read() == state
