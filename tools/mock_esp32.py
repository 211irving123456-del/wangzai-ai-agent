#!/usr/bin/env python3
"""
旺仔 ESP32 模拟设备
仅监听本机 127.0.0.1:18080
不需要安装第三方 Python 库
"""

import json
import time
import uuid
import threading

from http.server import (
    BaseHTTPRequestHandler,
    ThreadingHTTPServer
)
from urllib.parse import urlparse, parse_qs

BOOT_ID = uuid.uuid4().hex[:16]
START_TIME = time.monotonic()
LOCK = threading.Lock()

state = {
    "expression": "happy",
    "command_id": 0,
    "commands": {}
}


class Handler(BaseHTTPRequestHandler):

    def send_json(self, data, status=200):
        body = json.dumps(
            data, ensure_ascii=False
        ).encode("utf-8")

        self.send_response(status)
        self.send_header(
            "Content-Type",
            "application/json; charset=utf-8"
        )
        self.send_header(
            "Content-Length",
            str(len(body))
        )
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        parsed = urlparse(self.path)

        if parsed.path == "/api/device/status":
            with LOCK:
                expression = state["expression"]

            self.send_json({
                "device": "wangzai",
                "online": True,
                "expression": expression,
                "uptime_seconds": int(
                    time.monotonic() - START_TIME
                ),
                "free_heap": 153832,
                "boot_id": BOOT_ID,
                "mode": "MOCK"
            })
            return

        if parsed.path == "/api/device/command":
            params = parse_qs(parsed.query)

            try:
                cid = int(params.get("id", ["0"])[0])
            except ValueError:
                self.send_json(
                    {"success": False, "error": "invalid id"},
                    400
                )
                return

            with LOCK:
                command = state["commands"].get(cid)

            if command is None:
                self.send_json(
                    {"success": False, "error": "not found"},
                    404
                )
                return

            self.send_json(command)
            return

        if parsed.path == "/health":
            self.send_json({
                "status": "ok",
                "mode": "MOCK"
            })
            return

        self.send_json(
            {"error": "not found"}, 404
        )

    def do_POST(self):
        parsed = urlparse(self.path)

        if parsed.path != "/mock-esp32/expression":
            self.send_json(
                {"error": "not found"}, 404
            )
            return

        try:
            length = int(
                self.headers.get("Content-Length", "0")
            )

            if length <= 0 or length > 4096:
                raise ValueError("invalid body length")

            payload = json.loads(
                self.rfile.read(length)
            )

            action = payload.get("action")

            if action not in ("happy", "sad", "sleep"):
                self.send_json({
                    "success": False,
                    "error": "unsupported expression"
                }, 400)
                return

            with LOCK:
                state["command_id"] += 1
                cid = state["command_id"]

                state["expression"] = action

                command = {
                    "success": True,
                    "status": "completed",
                    "action": action,
                    "command_id": cid,
                    "boot_id": BOOT_ID,
                    "message": "Mock expression update completed"
                }

                state["commands"][cid] = command

            self.send_json({
                "success": True,
                "status": "accepted",
                "action": action,
                "command_id": cid,
                "boot_id": BOOT_ID
            })

            print(
                f"[MOCK] command_id={cid}, "
                f"expression={action}",
                flush=True
            )

        except (ValueError, json.JSONDecodeError,
                AttributeError, TypeError):
            self.send_json(
                {"success": False, "error": "invalid JSON"},
                400
            )


if __name__ == "__main__":
    server = ThreadingHTTPServer(
        ("127.0.0.1", 18080),
        Handler
    )

    print("========== WANGZAI MOCK ESP32 ==========")
    print("Mode: MOCK")
    print("Address: http://127.0.0.1:18080")
    print("Expressions: happy / sad / sleep")
    print("Press Ctrl+C to stop")
    print("=======================================")

    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n[MOCK] Stopped")
    finally:
        server.server_close()
