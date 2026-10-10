FROM python:3.12-slim
WORKDIR /app
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
COPY backend/requirements.txt /app/backend/requirements.txt
RUN pip install --no-cache-dir -r backend/requirements.txt
COPY backend /app/backend
COPY scripts/update_game_feed.py /app/scripts/update_game_feed.py
CMD ["sh", "-c", "exec uvicorn backend.app:create_app --factory --host 0.0.0.0 --port ${PORT:-8000} --workers 1 --proxy-headers"]
