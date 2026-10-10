"""Encrypted logical backups of Miniscore's complete application database row.

The database contains one JSON state row. This captures published/provider data,
all corrections, drafts, counters and history; credentials are not database data.
Restoration only accepts an empty target database, never replaces a live row.
"""
import argparse
import base64
import gzip
import hashlib
import io
import json
import os
from datetime import datetime, timezone

import boto3
from botocore.config import Config
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from sqlalchemy import select

from backend.store import Store, apply_corrections, validate_publication
from scripts.update_game_feed import require

MAGIC = b"MINISCORE-BACKUP-1\x00"
MAX_SNAPSHOT_BYTES = 40 * 1024 * 1024


def validate_state(state):
    require(type(state) is dict and type(state["revision"]) is int and state["revision"] > 0,
            "Invalid backup state")
    validate_publication(state["raw"])
    validate_publication(state["published"])
    require(apply_corrections(state["raw"], state["corrections"], state["defaultOverride"]) == state["published"],
            "Backup corrections do not match publication")
    require(type(state["drafts"]) is dict and type(state["history"]) is list, "Invalid backup drafts/history")
    for item in state["history"]:
        apply_corrections(item["raw"], item["corrections"], item["defaultOverride"])


def encryption_key():
    key = base64.b64decode(os.environ["BACKUP_ENCRYPTION_KEY"], validate=True)
    require(len(key) == 32, "BACKUP_ENCRYPTION_KEY must encode 32 bytes")
    return key


def pack(state, key, now):
    validate_state(state)
    document = {"format": 1, "table": "miniscore_state", "createdAt": now.isoformat(), "state": state}
    content = json.dumps(document, sort_keys=True, separators=(",", ":"), ensure_ascii=False).encode()
    require(len(content) <= MAX_SNAPSHOT_BYTES, "Backup state is too large")
    nonce = os.urandom(12)
    return MAGIC + nonce + AESGCM(key).encrypt(nonce, gzip.compress(content), MAGIC)


def unpack(archive, key):
    require(archive.startswith(MAGIC) and len(archive) <= MAX_SNAPSHOT_BYTES, "Invalid backup archive")
    offset = len(MAGIC)
    compressed = AESGCM(key).decrypt(archive[offset:offset + 12], archive[offset + 12:], MAGIC)
    with gzip.GzipFile(fileobj=io.BytesIO(compressed)) as stream:
        content = stream.read(MAX_SNAPSHOT_BYTES + 1)
    require(len(content) <= MAX_SNAPSHOT_BYTES, "Expanded backup is too large")
    document = json.loads(content)
    require(type(document["format"]) is int and document["format"] == 1 and document["table"] == "miniscore_state",
            "Unsupported backup format")
    validate_state(document["state"])
    return document


def restore_state(state, target):
    validate_state(state)
    target.table.metadata.create_all(target.engine)
    with target.engine.begin() as conn:
        require(conn.execute(select(target.table.c.id)).first() is None,
                "Restore target must be empty; existing database data was preserved")
        conn.execute(target.table.insert().values(id=1, state=state))
    require(target.read() == state, "Restored state does not match backup")


def verify_restore(document):
    target = Store("sqlite://")
    try:
        restore_state(document["state"], target)
    finally:
        target.dispose()


def storage():
    endpoint = os.environ["BACKUP_S3_ENDPOINT"]
    require(endpoint.startswith("https://"), "Backup storage requires HTTPS")
    return boto3.client("s3", endpoint_url=endpoint,
                        region_name=os.environ["BACKUP_S3_REGION"],
                        aws_access_key_id=os.environ["BACKUP_S3_ACCESS_KEY_ID"],
                        aws_secret_access_key=os.environ["BACKUP_S3_SECRET_ACCESS_KEY"],
                        config=Config(signature_version="s3v4", retries={"max_attempts": 3},
                                      connect_timeout=15, read_timeout=30,
                                      s3={"addressing_style": "path"})), os.environ["BACKUP_S3_BUCKET"]


def download(client, bucket, name):
    require(name.startswith("miniscore/") and name.endswith(".aesgcm"), "Invalid backup object key")
    response = client.get_object(Bucket=bucket, Key=name)
    try:
        data = response["Body"].read(MAX_SNAPSHOT_BYTES + 1)
    finally:
        response["Body"].close()
    require(len(data) <= MAX_SNAPSHOT_BYTES, "Backup object is too large")
    return data


def backup():
    key = encryption_key()
    source = Store(os.environ["DATABASE_URL"])
    try:
        state = source.read()  # One transactionally consistent JSON row; no DB writes.
    finally:
        source.dispose()
    now = datetime.now(timezone.utc)
    archive = pack(state, key, now)
    name = "miniscore/" + now.strftime("%Y/%m/%d/%H%M%S%f") + ".aesgcm"
    client, bucket = storage()
    client.put_object(Bucket=bucket, Key=name, Body=archive, ContentType="application/octet-stream")
    retrieved = download(client, bucket, name)
    require(retrieved == archive, "Stored backup differs from uploaded archive")
    verify_restore(unpack(retrieved, key))
    summary = {"objectKey": name, "createdAt": now.isoformat(), "revision": state["revision"],
               "sha256": hashlib.sha256(archive).hexdigest(), "bytes": len(archive), "restoreVerified": True}
    client.put_object(Bucket=bucket, Key="miniscore/latest.json", Body=json.dumps(summary).encode(),
                      ContentType="application/json")
    print(json.dumps(summary))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("operation", choices=("backup", "verify", "restore"))
    parser.add_argument("--object-key")
    args = parser.parse_args()
    if args.operation == "backup":
        backup()
        return
    require(args.object_key is not None, "--object-key is required")
    client, bucket = storage()
    document = unpack(download(client, bucket, args.object_key), encryption_key())
    if args.operation == "verify":
        verify_restore(document)
    else:
        target = Store(os.environ["BACKUP_RESTORE_DATABASE_URL"])
        try:
            restore_state(document["state"], target)
        finally:
            target.dispose()
    print(json.dumps({"objectKey": args.object_key, "revision": document["state"]["revision"],
                      "restoreVerified": True}))


if __name__ == "__main__":
    main()
