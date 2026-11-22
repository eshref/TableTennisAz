import hashlib
import json
import os
from datetime import datetime, timedelta, timezone
from email.utils import format_datetime
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from shutil import copytree
import subprocess
import sys
from tempfile import TemporaryDirectory
from threading import Thread
import time
import unittest

from bs4 import BeautifulSoup
from test_catalogue import ROOT, generate


class TestAlertPublisher(unittest.TestCase):
    def test_sends_verified_singles_and_doubles_only_from_the_successful_deployment(self):
        # arrange
        with TemporaryDirectory() as directory:
            output = Path(directory) / "tournaments-v1.json"
            generated = generate(ROOT / "fixtures", output)
            self.assertEqual(generated.returncode, 0, generated.stderr)
            catalogue = output.read_bytes()
            previous = output.with_name("previous-catalogue.json")
            previous.write_text('{"version":1,"tournaments":[{"id":7}]}', encoding="utf-8")
            requests = []
            scenarios = [
                (944, 200, catalogue, "BTTC Şəxsi Reytinq Turniri", None),
                (941, 200, catalogue, "BTTC Qoşa Reytinq Turniri #120", None),
                (944, 503, b"", None, "does not match this run's build"),
                (944, 200, catalogue + b" ", None, "does not match this run's build"),
                (990, 200, catalogue, None, "missing from the verified"),
                (805, 200, catalogue, None, "missing from the verified"),
                (7, 200, catalogue, None, "already in the live catalogue"),
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
                    for event_id, code, body, name, error in scenarios:
                        with self.subTest(event_id=event_id, deployment=code, expected=name):
                            server.deployment = code, body
                            requests.clear()
                            before = time.time_ns() // 1_000_000

                            # act
                            result = subprocess.run(
                                [sys.executable, str(ROOT / "send_test_alert.py"), str(event_id),
                                 "--catalogue", f"{endpoint}/tournaments-v1.json",
                                 "--sha256", hashlib.sha256(catalogue).hexdigest(),
                                 "--previous", str(previous),
                                 "--project", "test", "--fcm-origin", endpoint, "--wait", "0"],
                                env={**os.environ, "FCM_ACCESS_TOKEN": "test-token"},
                                capture_output=True, text=True, timeout=10,
                            )

                            # assert
                            if error:
                                self.assertNotEqual(result.returncode, 0)
                                self.assertIn(error, result.stderr)
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


class BatchAlertPublisher(unittest.TestCase):
    def setUp(self):
        directory = TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.directory = Path(directory.name)
        self.fixtures = copytree(ROOT / "fixtures", self.directory / "fixtures")
        self.index = self.fixtures / "matches/results.html"
        self.complete_index = self.index.read_text(encoding="utf-8")
        self.receipts = self.directory / "receipts.json"
        self.catalogue = self.directory / "candidate.json"
        self.live = self.directory / "live.json"
        self.requests = []
        self.rejected = set()
        self.modified = datetime.now(timezone.utc).replace(microsecond=0)
        fixture = self

        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                self.send_response(200)
                self.send_header("Last-Modified", format_datetime(fixture.modified, usegmt=True))
                self.end_headers()
                self.wfile.write(fixture.live.read_bytes())

            def do_POST(self):
                message = json.loads(self.rfile.read(int(self.headers["Content-Length"])))["message"]
                fixture.requests.append(message)
                self.send_response(503 if message["data"]["eventId"] in fixture.rejected else 200)
                self.end_headers()
                self.wfile.write(b'{"name":"projects/test/messages/1"}')

            def log_message(self, format, *args):
                pass

        server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        self.endpoint = f"http://127.0.0.1:{server.server_port}"
        thread = Thread(target=server.serve_forever, daemon=True)
        thread.start()
        self.addCleanup(server.server_close)
        self.addCleanup(thread.join)
        self.addCleanup(server.shutdown)

    def set_index(self, *omitted):
        index = BeautifulSoup(self.complete_index, "html.parser")
        for event_id in omitted:
            index.select_one(f'a[href="/matches/results/event/{event_id}"]').decompose()
        self.index.write_text(str(index), encoding="utf-8")

    def build(self, track_alerts=True):
        options = ["--track-alerts"] if track_alerts else []
        if self.live.exists():
            options += ["--previous-modified", format_datetime(self.modified, usegmt=True)]
        result = generate(
            self.fixtures, self.catalogue, previous=self.live if self.live.exists() else None, options=options,
        )
        if result.returncode != 0:
            raise RuntimeError(result.stderr)

    def deploy(self, modified):
        self.live.write_bytes(self.catalogue.read_bytes())
        self.modified = modified

    def send(self, check_digest=True):
        accepted = self.receipts.with_suffix(".pending")
        digest = ["--sha256", hashlib.sha256(self.catalogue.read_bytes()).hexdigest()] if check_digest else []
        result = subprocess.run(
            [sys.executable, str(ROOT / "publish_alerts.py"),
             "--catalogue", f"{self.endpoint}/tournaments-v1.json", *digest,
             "--receipts", str(self.receipts), "--accepted", str(accepted),
             "--project", "test", "--fcm-origin", self.endpoint, "--wait", "0"],
            env={**os.environ, "FCM_ACCESS_TOKEN": "test-token"},
            capture_output=True, text=True, timeout=10,
        )
        if accepted.exists():
            accepted.replace(self.receipts)
        return result

    def test_rollout_and_late_verification_survive_deployment_interruption_and_partial_firebase_failure(self):
        # arrange
        rollout = self.modified - timedelta(hours=2)
        first_publication = self.modified - timedelta(hours=1)
        event = self.fixtures / "events/944.html"
        event_page = event.read_text(encoding="utf-8")
        event.unlink()
        self.set_index(941)
        self.build(track_alerts=False)
        self.deploy(rollout)
        self.build()
        self.deploy(rollout)

        # act
        seeded = self.send()

        # assert
        self.assertEqual(seeded.returncode, 0, seeded.stderr)
        self.assertEqual(self.requests, [])

        # arrange
        event.write_text(event_page, encoding="utf-8")
        self.set_index()
        self.build()

        # act
        unpublished = self.send()

        # assert
        self.assertIn("does not match this run's build", unpublished.stderr)
        self.assertEqual(self.requests, [])

        # arrange
        # This deployment succeeds, but its runner stops before calling Firebase.
        self.deploy(first_publication)
        self.set_index(941)
        self.index.write_text(self.index.read_text(encoding="utf-8").replace(
            "BTTC Şəxsi Reytinq Turniri", "Corrected singles name"), encoding="utf-8")
        self.rejected.add("944")
        self.build()
        self.deploy(first_publication + timedelta(minutes=30))

        # act
        failed = self.send()

        # assert
        self.assertNotEqual(failed.returncode, 0)
        by_id = {message["data"]["eventId"]: message for message in self.requests}
        self.assertEqual(len(self.requests), 2)
        self.assertEqual(set(by_id), {"944", "941"})
        published_at = str(int(first_publication.timestamp()) * 1000)
        for event_id, name in [("944", "BTTC Şəxsi Reytinq Turniri"), ("941", "BTTC Qoşa Reytinq Turniri #120")]:
            self.assertEqual(by_id[event_id]["data"], {"eventId": event_id, "name": name, "publishedAt": published_at})
            self.assertEqual(by_id[event_id]["topic"], "tournament-results-test")
            self.assertEqual(by_id[event_id]["android"]["priority"], "high")
            self.assertAlmostEqual(int(by_id[event_id]["android"]["ttl"][:-1]), 82800, delta=30)

        # arrange
        self.requests.clear()
        self.rejected.clear()
        self.set_index()
        self.build()
        self.deploy(first_publication + timedelta(minutes=45))

        # act
        recovered = self.send()
        repeated = self.send()

        # assert
        self.assertEqual(recovered.returncode, 0, recovered.stderr)
        self.assertEqual(repeated.returncode, 0, repeated.stderr)
        self.assertEqual([message["data"] for message in self.requests], [by_id["944"]["data"]])

    def test_a_failed_deployment_can_retry_earlier_live_publications(self):
        # arrange
        rollout = self.modified - timedelta(hours=2)
        first_publication = self.modified - timedelta(hours=1)
        self.set_index(941, 944)
        self.build(track_alerts=False)
        self.deploy(rollout)
        self.set_index(941)
        self.build()
        self.deploy(first_publication)
        self.set_index()
        self.build()

        # act
        fallback = self.send(check_digest=False)

        # assert
        self.assertEqual(fallback.returncode, 0, fallback.stderr)
        self.assertEqual([message["data"]["eventId"] for message in self.requests], ["944"])

    def test_pending_sends_expire_from_first_publication_even_after_a_new_deployment(self):
        # arrange
        first_publication = self.modified - timedelta(hours=24)
        self.set_index(944)
        self.build(track_alerts=False)
        self.deploy(first_publication - timedelta(minutes=1))
        self.set_index()
        self.build()
        self.deploy(first_publication)
        self.build()
        self.deploy(first_publication + timedelta(hours=24))

        # act
        expired = self.send()

        # assert
        self.assertEqual(expired.returncode, 0, expired.stderr)
        self.assertEqual(self.requests, [])
