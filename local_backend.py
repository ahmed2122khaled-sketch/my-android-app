#!/usr/bin/env python3
"""Local read-only backend harness for mr.x capacity testing.

This is a test harness, not the production backend. It exposes Supabase-like
read paths backed by SQLite so the load generator can be exercised without
network credentials or a live Supabase project.
"""
from __future__ import annotations
import argparse, json, sqlite3, threading, time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

DB = None

SCHEMA = {
    "posts": """CREATE TABLE IF NOT EXISTS posts (id INTEGER PRIMARY KEY, user_id INTEGER NOT NULL, created_at INTEGER NOT NULL, body TEXT NOT NULL)""",
    "stories": """CREATE TABLE IF NOT EXISTS stories (id INTEGER PRIMARY KEY, user_id INTEGER NOT NULL, created_at INTEGER NOT NULL, expires_at INTEGER NOT NULL)""",
    "profiles": """CREATE TABLE IF NOT EXISTS profiles (id INTEGER PRIMARY KEY, created_at INTEGER NOT NULL, username TEXT NOT NULL)""",
    "notifications": """CREATE TABLE IF NOT EXISTS notifications (id INTEGER PRIMARY KEY, user_id INTEGER NOT NULL, created_at INTEGER NOT NULL, is_read INTEGER NOT NULL)""",
    "conversations": """CREATE TABLE IF NOT EXISTS conversations (id INTEGER PRIMARY KEY, updated_at INTEGER NOT NULL)""",
    "messages": """CREATE TABLE IF NOT EXISTS messages (id INTEGER PRIMARY KEY, conversation_id INTEGER NOT NULL, sender_id INTEGER NOT NULL, created_at INTEGER NOT NULL, body TEXT NOT NULL)""",
}

SEED = {
    "posts": 20000, "stories": 5000, "profiles": 10000,
    "notifications": 20000, "conversations": 5000, "messages": 30000,
}

class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"
    def log_message(self, *_):
        return

    def do_GET(self):
        started = time.perf_counter()
        path = urlparse(self.path).path
        table = path.rsplit("/", 1)[-1]
        if not path.startswith("/rest/v1/") or table not in SCHEMA:
            return self.send_json(404, {"error": "not_found"})
        q = parse_qs(urlparse(self.path).query)
        try:
            limit = min(max(int(q.get("limit", ["20"])[0]), 1), 100)
        except ValueError:
            return self.send_json(400, {"error": "invalid_limit"})
        # Simulate common PostgREST-style ordering/pagination without writes.
        order = {
            "posts": "created_at DESC, id DESC",
            "stories": "created_at DESC, id DESC",
            "profiles": "created_at DESC, id DESC",
            "notifications": "created_at DESC, id DESC",
            "conversations": "updated_at DESC, id DESC",
            "messages": "created_at ASC, id ASC",
        }[table]
        offset = max(int(q.get("offset", ["0"])[0]), 0)
        conn = sqlite3.connect(DB, timeout=5)
        conn.execute("PRAGMA busy_timeout=5000")
        conn.row_factory = sqlite3.Row
        rows = conn.execute(f"SELECT * FROM {table} ORDER BY {order} LIMIT ? OFFSET ?", (limit, offset)).fetchall()
        conn.close()
        payload = [dict(r) for r in rows]
        payload_bytes = json.dumps(payload, separators=(",", ":")).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload_bytes)))
        self.send_header("X-Backend-Latency-Ms", f"{(time.perf_counter()-started)*1000:.3f}")
        self.end_headers()
        self.wfile.write(payload_bytes)

    def do_POST(self):
        return self.send_json(405, {"error": "read_only_harness"})
    do_PATCH = do_POST
    do_DELETE = do_POST

    def send_json(self, code, obj):
        data = json.dumps(obj, separators=(",", ":")).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)


def seed(db_path: str):
    conn = sqlite3.connect(db_path)
    conn.execute("PRAGMA journal_mode=WAL")
    conn.execute("PRAGMA synchronous=NORMAL")
    conn.execute("PRAGMA busy_timeout=5000")
    for sql in SCHEMA.values():
        conn.execute(sql)
    now = int(time.time())
    for table, count in SEED.items():
        exists = conn.execute(f"SELECT COUNT(*) FROM {table}").fetchone()[0]
        if exists:
            continue
        if table == "posts":
            conn.executemany("INSERT INTO posts VALUES (?,?,?,?)", ((i, i%1000, now-i, f"post {i}") for i in range(1,count+1)))
        elif table == "stories":
            conn.executemany("INSERT INTO stories VALUES (?,?,?,?)", ((i, i%1000, now-i, now+86400-i) for i in range(1,count+1)))
        elif table == "profiles":
            conn.executemany("INSERT INTO profiles VALUES (?,?,?)", ((i, now-i, f"user{i}") for i in range(1,count+1)))
        elif table == "notifications":
            conn.executemany("INSERT INTO notifications VALUES (?,?,?,?)", ((i, i%1000, now-i, i%3==0) for i in range(1,count+1)))
        elif table == "conversations":
            conn.executemany("INSERT INTO conversations VALUES (?,?)", ((i, now-i) for i in range(1,count+1)))
        elif table == "messages":
            conn.executemany("INSERT INTO messages VALUES (?,?,?,?,?)", ((i, i%5000, i%1000, now-i, f"message {i}") for i in range(1,count+1)))
    # Match the production migration's hot-path intent for the harness.
    indexes = [
        "CREATE INDEX IF NOT EXISTS idx_posts_created_at_desc ON posts(created_at DESC, id DESC)",
        "CREATE INDEX IF NOT EXISTS idx_posts_user_created_at ON posts(user_id, created_at DESC)",
        "CREATE INDEX IF NOT EXISTS idx_stories_expires_at ON stories(expires_at)",
        "CREATE INDEX IF NOT EXISTS idx_stories_created_at_desc ON stories(created_at DESC, id DESC)",
        "CREATE INDEX IF NOT EXISTS idx_profiles_created_at_desc ON profiles(created_at DESC, id DESC)",
        "CREATE INDEX IF NOT EXISTS idx_notifications_user_created_at ON notifications(user_id, created_at DESC)",
        "CREATE INDEX IF NOT EXISTS idx_conversations_updated_at_desc ON conversations(updated_at DESC, id DESC)",
        "CREATE INDEX IF NOT EXISTS idx_messages_conversation_created_at ON messages(conversation_id, created_at ASC)",
    ]
    for sql in indexes:
        conn.execute(sql)
    conn.commit(); conn.close()


def main():
    global DB
    p=argparse.ArgumentParser()
    p.add_argument("--host", default="127.0.0.1")
    p.add_argument("--port", type=int, default=18080)
    p.add_argument("--db", default="tools/loadtest/mrx-loadtest.sqlite3")
    args=p.parse_args(); DB=args.db
    seed(DB)
    server=ThreadingHTTPServer((args.host,args.port),Handler)
    print(f"LOCAL_BACKEND_READY http://{args.host}:{args.port}/rest/v1/posts?limit=20")
    try: server.serve_forever()
    except KeyboardInterrupt: pass
    finally: server.server_close()

if __name__ == "__main__": main()
