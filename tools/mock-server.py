#!/usr/bin/env python3
"""Disposable in-memory FishSync fixture server; no production accounts or files."""
import hashlib
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse

files = {"hello.txt": b"123"}
base = "http://10.0.2.2:18080"
class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass
    def send(self, value, code=200):
        payload = json.dumps(value).encode()
        self.send_response(code); self.send_header("Content-Type", "application/json"); self.send_header("Content-Length", str(len(payload))); self.end_headers(); self.wfile.write(payload)
    def body(self):
        return self.rfile.read(int(self.headers.get("Content-Length", "0")))
    def do_GET(self):
        path = urlparse(self.path).path
        if path == "/api/buckets": return self.send({"buckets": ["test-bucket"]})
        if path == "/api/buckets/common/test-bucket/files":
            return self.send({"id": "test-bucket", "files": [{"path": name, "location": base + "/downloads/" + name, "metadata": {"size": len(data), "checksum": hashlib.md5(data).hexdigest()}} for name, data in sorted(files.items())]})
        if path.startswith("/downloads/"):
            name = path.removeprefix("/downloads/")
            if name not in files: return self.send({}, 404)
            data = files[name]; self.send_response(200); self.send_header("Content-Length", str(len(data))); self.end_headers(); self.wfile.write(data); return
        return self.send({}, 404)
    def do_POST(self):
        body = json.loads(self.body())
        if self.path == "/api/auth/login": return self.send({"token": "fixture-token"})
        if self.path == "/api/buckets/common/test-bucket/sync":
            actions = []
            requested = {f["path"]: f for f in body["files"]}
            for name, item in requested.items():
                if name not in files or hashlib.md5(files[name]).hexdigest() != item["checksum"]:
                    actions.append({"path": name, "action": {"type": "Http", "parameters": {"method": "PUT", "url": base + "/uploads/" + name, "headers.Content-Type": "application/octet-stream"}}})
            if not actions:
                for name in list(files):
                    if name not in requested: del files[name]
            return self.send({"isSuccess": not actions, "requiredActions": actions, "updatedAt": "2026-10-15T00:00:00Z"})
        return self.send({}, 404)
    def do_PUT(self):
        name = self.path.removeprefix("/uploads/")
        if not self.path.startswith("/uploads/") or "/" in name: return self.send({}, 400)
        files[name] = self.body(); return self.send({})

if __name__ == "__main__":
    print("Fixture server: http://127.0.0.1:18080/api; emulator: " + base + "/api", flush=True)
    ThreadingHTTPServer(("127.0.0.1", 18080), Handler).serve_forever()
