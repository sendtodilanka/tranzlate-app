#!/usr/bin/env python3
"""Tiny local review server for docs/plan interactive review pages.

Serves the review HTML and persists owner comments to a JSON file next to it,
so a Claude session can read the comments directly. Localhost-only.
"""
import datetime
import http.server
import json
import os
import socketserver

PORT = 8765
BASE = os.path.dirname(os.path.abspath(__file__))
HTML = os.path.join(BASE, "issue-3-plan-review.html")
DATA = os.path.join(BASE, "issue-3-comments.json")


def load():
    if os.path.exists(DATA):
        with open(DATA, encoding="utf-8") as f:
            return json.load(f)
    return []


def save(data):
    tmp = DATA + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    os.replace(tmp, DATA)


class Handler(http.server.BaseHTTPRequestHandler):
    def _send(self, code, body, ctype="application/json; charset=utf-8"):
        payload = body if isinstance(body, bytes) else body.encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def do_GET(self):
        if self.path in ("/", "/index.html"):
            with open(HTML, "rb") as f:
                self._send(200, f.read(), "text/html; charset=utf-8")
        elif self.path == "/api/comments":
            self._send(200, json.dumps(load(), ensure_ascii=False))
        else:
            self._send(404, '{"error":"not found"}')

    def do_POST(self):
        if self.path != "/api/comments":
            return self._send(404, '{"error":"not found"}')
        try:
            n = int(self.headers.get("Content-Length", 0))
            req = json.loads(self.rfile.read(n).decode("utf-8"))
        except (ValueError, json.JSONDecodeError):
            return self._send(400, '{"error":"bad json"}')

        data = load()
        action = req.get("action", "add")
        now = datetime.datetime.now().isoformat(timespec="seconds")

        if action == "add":
            data.append({
                "id": max((c["id"] for c in data), default=0) + 1,
                "section": req.get("section", ""),
                "section_title": req.get("section_title", ""),
                "quote": (req.get("quote") or "")[:1000],
                "comment": (req.get("comment") or "")[:8000],
                "kind": req.get("kind", "comment"),  # comment | approve
                "created_at": now,
                "status": "open",
            })
        elif action == "delete":
            data = [c for c in data if c["id"] != req.get("id")]
        elif action == "edit":
            for c in data:
                if c["id"] == req.get("id"):
                    c["comment"] = (req.get("comment") or c["comment"])[:8000]
                    c["edited_at"] = now
        else:
            return self._send(400, '{"error":"unknown action"}')

        save(data)
        self._send(200, json.dumps({"ok": True, "count": len(data)}, ensure_ascii=False))

    def log_message(self, *args):  # keep the console quiet
        pass


if __name__ == "__main__":
    socketserver.TCPServer.allow_reuse_address = True
    with socketserver.TCPServer(("127.0.0.1", PORT), Handler) as httpd:
        print(f"plan-review server on http://127.0.0.1:{PORT}")
        httpd.serve_forever()
