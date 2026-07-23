#!/usr/bin/env python3
"""Tiny local review server for docs/plan interactive review pages.

Serves the review HTML and persists owner comments to a JSON file next to it,
so a Claude session can read the comments directly. Localhost-only.
"""
import datetime
import http.server
import json
import os
import re
import socketserver
import urllib.parse

PORT = 8765
BASE = os.path.dirname(os.path.abspath(__file__))
DEFAULT_DOC = "issue-3-plan-review"
SAFE_DOC = re.compile(r"[a-zA-Z0-9._-]+$")


def doc_name(path):
    """Resolve the ?doc= query (or an /<name>.html path) to a safe doc slug."""
    parsed = urllib.parse.urlparse(path)
    requested = urllib.parse.parse_qs(parsed.query).get("doc", [""])[0]
    if not requested and parsed.path.endswith(".html"):
        requested = parsed.path.lstrip("/")[: -len(".html")]
    if not requested:
        return DEFAULT_DOC
    # Reject traversal / absolute paths outright rather than sanitising them.
    return requested if SAFE_DOC.fullmatch(requested) else DEFAULT_DOC


def html_path(doc):
    return os.path.join(BASE, f"{doc}.html")


def data_path(doc):
    return os.path.join(BASE, f"{doc.removesuffix('-plan-review')}-comments.json")


def load(doc):
    path = data_path(doc)
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    return []


def save(doc, data):
    path = data_path(doc)
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    os.replace(tmp, path)


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
        path = urllib.parse.urlparse(self.path).path
        if path.startswith("/img/"):
            name = path[len("/img/"):]
            if not SAFE_DOC.fullmatch(name) or not name.endswith((".png", ".jpg")):
                return self._send(404, '{"error":"not found"}')
            shot = os.path.join(BASE, "..", "..", "design", "screenshots", name)
            if not os.path.exists(shot):
                return self._send(404, '{"error":"no such image"}')
            ctype = "image/png" if name.endswith(".png") else "image/jpeg"
            with open(shot, "rb") as f:
                return self._send(200, f.read(), ctype)
        if path == "/api/comments":
            self._send(200, json.dumps(load(doc_name(self.path)), ensure_ascii=False))
        elif path in ("/", "/index.html") or path.endswith(".html"):
            page = html_path(doc_name(self.path))
            if not os.path.exists(page):
                return self._send(404, '{"error":"no such review page"}')
            with open(page, "rb") as f:
                self._send(200, f.read(), "text/html; charset=utf-8")
        else:
            self._send(404, '{"error":"not found"}')

    def do_POST(self):
        if urllib.parse.urlparse(self.path).path != "/api/comments":
            return self._send(404, '{"error":"not found"}')
        try:
            n = int(self.headers.get("Content-Length", 0))
            req = json.loads(self.rfile.read(n).decode("utf-8"))
        except (ValueError, json.JSONDecodeError):
            return self._send(400, '{"error":"bad json"}')

        doc = doc_name(self.path)
        data = load(doc)
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

        save(doc, data)
        self._send(200, json.dumps({"ok": True, "count": len(data)}, ensure_ascii=False))

    def log_message(self, *args):  # keep the console quiet
        pass


if __name__ == "__main__":
    socketserver.TCPServer.allow_reuse_address = True
    with socketserver.TCPServer(("127.0.0.1", PORT), Handler) as httpd:
        print(f"plan-review server on http://127.0.0.1:{PORT}")
        httpd.serve_forever()
