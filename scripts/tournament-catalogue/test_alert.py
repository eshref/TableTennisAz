import hashlib
import json
import os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import subprocess
import sys
from tempfile import TemporaryDirectory
from threading import Thread
import time
import unittest

import test_catalogue
from test_catalogue import ROOT


class TestAlertPublisher(unittest.TestCase):
    def test_sends_verified_singles_and_doubles_only_from_the_successful_deployment(self):
        # arrange
        with TemporaryDirectory() as directory:
            output = Path(directory) / "tournaments-v1.json"
            generated = test_catalogue.CatalogueTest().generate(ROOT / "fixtures", output)
            self.assertEqual(generated.returncode, 0, generated.stderr)
            catalogue = output.read_bytes()
            requests = []
            scenarios = [
                (944, 200, catalogue, "BTTC Şəxsi Reytinq Turniri"),
                (941, 200, catalogue, "BTTC Qoşa Reytinq Turniri #120"),
                (944, 503, b"", None),
                (944, 200, catalogue + b" ", None),
                (990, 200, catalogue, None),
                (805, 200, catalogue, None),
            ]

            class Handler(BaseHTTPRequestHandler):
                def do_GET(self):
                    code, body = self.server.deployment
                    self.send_response(code)
                    self.end_headers()
                    self.wfile.write(body)

                def do_POST(self):
                    message = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
                    requests.append((self.path, self.headers["Authorization"], message))
                    self.send_response(200)
                    self.end_headers()
                    self.wfile.write(b'{"name":"projects/test/messages/1"}')

                def log_message(self, format, *args):
                    pass

            with ThreadingHTTPServer(("127.0.0.1", 0), Handler) as server:
                thread = Thread(target=server.serve_forever, daemon=True)
                thread.start()
                endpoint = f"http://127.0.0.1:{server.server_port}"
                try:
                    for event_id, code, body, name in scenarios:
                        with self.subTest(event_id=event_id, deployment=code, expected=name):
                            server.deployment = code, body
                            requests.clear()
                            before = time.time_ns() // 1_000_000

                            # act
                            result = subprocess.run(
                                [sys.executable, str(ROOT / "send_test_alert.py"), str(event_id),
                                 "--catalogue", f"{endpoint}/tournaments-v1.json",
                                 "--sha256", hashlib.sha256(catalogue).hexdigest(),
                                 "--project", "test", "--fcm-origin", endpoint, "--wait", "0"],
                                env={**os.environ, "FCM_ACCESS_TOKEN": "test-token"},
                                capture_output=True, text=True, timeout=10,
                            )

                            # assert
                            if name is None:
                                self.assertNotEqual(result.returncode, 0)
                                self.assertEqual(requests, [])
                            else:
                                self.assertEqual(result.returncode, 0, result.stderr)
                                self.assertEqual(len(requests), 1)
                                path, authorization, payload = requests[0]
                                self.assertEqual(path, "/v1/projects/test/messages:send")
                                self.assertEqual(authorization, "Bearer test-token")
                                message = payload["message"]
                                self.assertEqual(message["topic"], "tournament-results-test")
                                self.assertEqual(message["android"], {"priority": "high", "ttl": "86400s"})
                                publication = int(message["data"]["publishedAt"])
                                self.assertGreaterEqual(publication, before)
                                self.assertLessEqual(publication, time.time_ns() // 1_000_000)
                                self.assertEqual(message["data"], {
                                    "eventId": str(event_id), "name": name, "publishedAt": str(publication),
                                })
                finally:
                    server.shutdown()
                    thread.join()
