#!/usr/bin/env python3
"""실제 계정이나 파일을 사용하지 않는 메모리 기반 FishSync 테스트 서버."""

import hashlib
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse

files = {"hello.txt": b"123"}
base = "http://10.0.2.2:18080"


def bucket_files():
    entries = []

    for name, data in sorted(files.items()):
        entries.append(
            {
                "path": name,
                "location": base + "/downloads/" + name,
                "metadata": {
                    "size": len(data),
                    "checksum": hashlib.md5(data).hexdigest(),
                },
            }
        )

    return {"id": "test-bucket", "files": entries}


def upload_actions(requested):
    actions = []

    for name, item in requested.items():
        if name in files and hashlib.md5(files[name]).hexdigest() == item["checksum"]:
            continue

        actions.append(
            {
                "path": name,
                "action": {
                    "type": "Http",
                    "parameters": {
                        "method": "PUT",
                        "url": base + "/uploads/" + name,
                        "headers.Content-Type": "application/octet-stream",
                    },
                },
            }
        )

    return actions


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def send(self, value, code=200):
        payload = json.dumps(value).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def body(self):
        length = int(self.headers.get("Content-Length", "0"))
        return self.rfile.read(length)

    def do_GET(self):
        path = urlparse(self.path).path

        if path == "/api/buckets":
            return self.send({"buckets": ["test-bucket"]})

        if path == "/api/buckets/common/test-bucket/files":
            return self.send(bucket_files())

        if path.startswith("/downloads/"):
            return self.download(path.removeprefix("/downloads/"))

        return self.send({}, 404)

    def download(self, name):
        if name not in files:
            return self.send({}, 404)

        data = files[name]
        self.send_response(200)
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_POST(self):
        body = json.loads(self.body())

        if self.path == "/api/auth/login":
            return self.send({"token": "fixture-token"})

        if self.path == "/api/buckets/common/test-bucket/sync":
            return self.synchronize(body)

        return self.send({}, 404)

    def synchronize(self, body):
        requested = {item["path"]: item for item in body["files"]}
        actions = upload_actions(requested)

        if not actions:
            for name in list(files):
                if name not in requested:
                    del files[name]

        return self.send(
            {
                "isSuccess": not actions,
                "requiredActions": actions,
                "updatedAt": "2026-10-15T00:00:00Z",
            }
        )

    def do_PUT(self):
        name = self.path.removeprefix("/uploads/")
        if not self.path.startswith("/uploads/") or "/" in name:
            return self.send({}, 400)

        files[name] = self.body()
        return self.send({})


if __name__ == "__main__":
    print(
        "Fixture server: http://127.0.0.1:18080/api; emulator: " + base + "/api",
        flush=True,
    )
    server = ThreadingHTTPServer(("127.0.0.1", 18080), Handler)
    server.serve_forever()
