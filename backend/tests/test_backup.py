import copy
import json
import os
from datetime import datetime, timezone
from pathlib import Path

import pytest
from cryptography.exceptions import InvalidTag

from backend.backup import pack, unpack, restore_state, verify_restore
from backend.store import Store

SEED = json.loads((Path(__file__).parents[1] / "seed.json").read_text())


def test_encrypted_backup_restores_all_private_state(tmp_path):
    source = Store(f"sqlite:///{tmp_path}/source.db")
    target = Store(f"sqlite:///{tmp_path}/restored.db")
    try:
        source.initialize(copy.deepcopy(SEED))
        game = copy.deepcopy(source.read()["published"]["games"][0])
        game["venue"] = "Manual correction"
        source.save_game(game, 1, publish=True)
        game["venue"] = "Private draft"
        source.save_game(game, 2, draft_version=1)
        state = source.read()
        key = os.urandom(32)
        archive = pack(state, key, datetime.now(timezone.utc))
        assert b"Private draft" not in archive and b"Manual correction" not in archive
        document = unpack(archive, key)
        verify_restore(document)
        restore_state(document["state"], target)
        assert target.read() == state
        assert state["drafts"] and state["corrections"] and state["history"] and state["draftVersions"]
        # A valid backup must not overwrite an existing target, even if its contents match.
        with pytest.raises(ValueError, match="must be empty"):
            restore_state(document["state"], target)
        assert target.read() == state
    finally:
        source.dispose()
        target.dispose()


def test_tampering_and_wrong_key_are_rejected(tmp_path):
    source = Store(f"sqlite:///{tmp_path}/source.db")
    try:
        source.initialize(copy.deepcopy(SEED))
        key = os.urandom(32)
        archive = pack(source.read(), key, datetime.now(timezone.utc))
        for data, candidate_key in ((archive[:-1] + bytes([archive[-1] ^ 1]), key), (archive, os.urandom(32))):
            with pytest.raises(InvalidTag):
                unpack(data, candidate_key)
    finally:
        source.dispose()
