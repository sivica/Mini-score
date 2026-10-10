"""Miniscore public read API and authenticated owner dashboard."""
import asyncio
import hashlib
import json
import logging
import os
import secrets
from contextlib import asynccontextmanager
from datetime import datetime
from pathlib import Path
from urllib.parse import urlsplit
from zoneinfo import ZoneInfo

from fastapi import Depends, FastAPI, HTTPException, Request
from fastapi.responses import FileResponse, JSONResponse, Response
from fastapi.security import HTTPBasic, HTTPBasicCredentials
from starlette.concurrency import run_in_threadpool

from backend.store import Conflict, Store
from scripts.update_game_feed import build_feed, encode, EASTERN, MAX_BYTES

LOG = logging.getLogger("miniscore")
LOCAL = ZoneInfo("Europe/Skopje")
ROOT = Path(__file__).parent


def daily_date(now):
    """Catch up after 8 AM local time; DST is handled by ZoneInfo."""
    local = now.astimezone(LOCAL)
    return local.date().isoformat() if local.hour >= 8 else None


def create_app(database_url=None, scheduler=True, seed=None):
    url = database_url or os.environ.get("DATABASE_URL")
    if not url:
        raise RuntimeError("DATABASE_URL is required; Railway data must use persistent PostgreSQL")
    store = Store(url)
    basic = HTTPBasic()

    def owner(credentials: HTTPBasicCredentials = Depends(basic)):
        username = os.environ.get("ADMIN_USERNAME", "")
        password = os.environ.get("ADMIN_PASSWORD", "")
        if not username or len(password) < 20:
            raise HTTPException(503, "Admin access has not been configured")
        if not (secrets.compare_digest(credentials.username.encode(), username.encode()) and
                secrets.compare_digest(credentials.password.encode(), password.encode())):
            raise HTTPException(401, "Invalid login", headers={"WWW-Authenticate": "Basic"})

    async def schedule():
        while True:
            try:
                due = daily_date(datetime.now(LOCAL))
                if due and store.read()["lastDailyAttempt"] != due:
                    await run_in_threadpool(store.import_feed,
                                            lambda previous: build_feed(previous, datetime.now(EASTERN).date()), due)
            except Conflict:
                pass
            except Exception:
                LOG.exception("Daily import failed; previous publication retained")
            await asyncio.sleep(30)

    @asynccontextmanager
    async def lifespan(app):
        store.initialize(seed if seed is not None else json.loads((ROOT / "seed.json").read_text()))
        task = asyncio.create_task(schedule()) if scheduler else None
        yield
        if task:
            task.cancel()
            try:
                await task
            except asyncio.CancelledError:
                pass
        store.dispose()

    app = FastAPI(title="Miniscore", lifespan=lifespan, docs_url=None, redoc_url=None, openapi_url=None)
    app.state.store = store

    @app.middleware("http")
    async def boundaries(request, call_next):
        if request.url.path.startswith("/admin"):
            if request.method != "GET":
                if request.headers.get("X-Miniscore-Admin") != "1":
                    return JSONResponse({"detail": "Missing admin request header"}, status_code=403)
                origin = request.headers.get("Origin")
                if origin and urlsplit(origin).netloc != request.headers.get("host"):
                    return JSONResponse({"detail": "Cross-origin changes are forbidden"}, status_code=403)
            response = await call_next(request)
            response.headers["Cache-Control"] = "no-store"
            response.headers["Content-Security-Policy"] = (
                "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' https:; "
                "connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'")
        else:
            response = await call_next(request)
        response.headers["X-Content-Type-Options"] = "nosniff"
        response.headers["Referrer-Policy"] = "no-referrer"
        return response

    @app.exception_handler(Conflict)
    async def conflict(request, exc):
        return JSONResponse({"detail": str(exc)}, status_code=409)

    @app.exception_handler(ValueError)
    async def invalid(request, exc):
        return JSONResponse({"detail": str(exc)}, status_code=422)

    async def payload(request):
        if not request.headers.get("content-type", "").startswith("application/json"):
            raise HTTPException(415, "Use application/json")
        body = bytearray()
        async for chunk in request.stream():
            body.extend(chunk)
            if len(body) > MAX_BYTES:
                raise HTTPException(413, "Request too large")
        try:
            result = json.loads(body, parse_constant=lambda value: (_ for _ in ()).throw(ValueError("Invalid number")))
            if type(result) is not dict:
                raise ValueError("Expected an object")
            return result
        except (ValueError, UnicodeDecodeError) as exc:
            raise HTTPException(422, "Invalid JSON body") from exc

    @app.get("/health")
    def health():
        return {"ok": True, "revision": store.read()["revision"]}

    @app.get("/games")
    def games(request: Request, date: str | None = None):
        state = store.read()
        feed = state["published"]
        if date:
            try:
                parsed = datetime.strptime(date, "%Y-%m-%d")
                if parsed.strftime("%Y-%m-%d") != date:
                    raise ValueError("Invalid date format")
            except ValueError:
                raise HTTPException(422, "Date must be YYYY-MM-DD")
            selected = [g for g in feed["games"] if g["date"] == date]
            feed = {"schemaVersion": 1, "defaultGameId": feed["defaultGameId"], "games": selected}
            if selected and not any(g["id"] == feed["defaultGameId"] for g in selected):
                feed["defaultGameId"] = selected[0]["id"]
            if not selected:
                feed["defaultGameId"] = None
        content = encode(feed)
        etag = '"' + hashlib.sha256(content).hexdigest() + '"'
        headers = {"ETag": etag, "Cache-Control": "no-cache", "X-Miniscore-Revision": str(state["revision"]),
                   "X-Miniscore-Updated-At": state["updatedAt"]}
        if request.headers.get("If-None-Match") == etag:
            return Response(status_code=304, headers=headers)
        return Response(content, media_type="application/json", headers=headers)

    @app.get("/games/latest")
    def latest():
        feed = store.read()["published"]
        return next(g for g in feed["games"] if g["id"] == feed["defaultGameId"])

    @app.get("/games/{gid}")
    def game(gid: str):
        result = next((g for g in store.read()["published"]["games"] if g["id"] == gid), None)
        if result is None:
            raise HTTPException(404, "Unknown game")
        return result

    @app.get("/admin", dependencies=[Depends(owner)])
    def admin():
        return FileResponse(ROOT / "static/index.html")

    @app.get("/admin/assets/{name}", dependencies=[Depends(owner)])
    def asset(name: str):
        if name not in ("admin.js", "admin.css", "logo.svg"):
            raise HTTPException(404)
        return FileResponse(ROOT / "static" / name)

    @app.get("/admin/state", dependencies=[Depends(owner)])
    def state():
        result = store.read()
        result["history"] = [{k: h[k] for k in ("revision", "updatedAt", "source")} for h in result["history"]]
        result.pop("raw")
        return result

    @app.post("/admin/game", dependencies=[Depends(owner)])
    async def save(request: Request):
        data = await payload(request)
        if type(data.get("publish", False)) is not bool or type(data.get("game")) is not dict:
            raise HTTPException(422, "Expected game object and boolean publish")
        try:
            revision = await run_in_threadpool(store.save_game, data["game"], data.get("revision"), data.get("publish", False))
        except (KeyError, TypeError, StopIteration) as exc:
            raise HTTPException(422, "Missing or invalid game fields") from exc
        return {"revision": revision}

    @app.post("/admin/default", dependencies=[Depends(owner)])
    async def default(request: Request):
        data = await payload(request)
        return {"revision": await run_in_threadpool(store.set_default, data.get("gameId"), data.get("revision"))}

    @app.post("/admin/corrections/clear", dependencies=[Depends(owner)])
    async def clear(request: Request):
        data = await payload(request)
        return {"revision": await run_in_threadpool(store.clear_corrections, data.get("gameId"), data.get("revision"))}

    @app.post("/admin/restore", dependencies=[Depends(owner)])
    async def restore(request: Request):
        data = await payload(request)
        return {"revision": await run_in_threadpool(store.restore, data.get("targetRevision"), data.get("revision"))}

    @app.post("/admin/import", dependencies=[Depends(owner)])
    async def import_now():
        try:
            await run_in_threadpool(store.import_feed,
                                    lambda previous: build_feed(previous, datetime.now(EASTERN).date()))
        except Conflict:
            raise
        except Exception as exc:
            LOG.exception("Manual import failed; previous publication retained")
            raise HTTPException(502, "Import failed. Previous stats were preserved.") from exc
        return {"ok": True}

    return app
