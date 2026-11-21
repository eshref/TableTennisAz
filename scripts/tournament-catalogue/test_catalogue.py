import functools
import json
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import subprocess
import sys
from tempfile import TemporaryDirectory
from threading import Thread
import unittest

ROOT = Path(__file__).resolve().parent


class FixtureHandler(SimpleHTTPRequestHandler):
    def translate_path(self, path):
        route, _, query = path.partition("?page=")
        suffix = f"-page-{query}" if query else ""
        return super().translate_path(f"{route}{suffix}.html")

    def log_message(self, format, *args):
        pass


class CatalogueTest(unittest.TestCase):
    def test_publishes_verified_singles_with_source_fields(self):
        # arrange
        with TemporaryDirectory() as directory:
            output = Path(directory) / "tournaments-v1.json"

            # act
            result = self.generate(ROOT / "fixtures", output)

            # assert
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(json.loads(output.read_text(encoding="utf-8")), {
                "version": 1,
                "tournaments": [
                    {"id": 944, "name": "BTTC Şəxsi Reytinq Turniri", "format": "singles",
                     "start": "2026-11-19", "end": "2026-11-20", "players": 9, "prize": "0 AZN"},
                    {"id": 7, "name": "Table Tennis Baku Cup #3", "format": "singles",
                     "start": "2021-10-11", "end": "2021-10-11"},
                ],
            })

    def test_a_broken_index_leaves_the_previous_output_in_place(self):
        # arrange
        with TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "matches").mkdir()
            (root / "matches/results.html").write_text("<div id='center'>Maintenance</div>", encoding="utf-8")
            output = root / "tournaments-v1.json"
            output.write_text("previous publication", encoding="utf-8")

            # act
            result = self.generate(root, output)

            # assert
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("Results index", result.stderr)
            self.assertEqual(output.read_text(encoding="utf-8"), "previous publication")

    def generate(self, fixtures, output):
        handler = functools.partial(FixtureHandler, directory=fixtures)
        with ThreadingHTTPServer(("127.0.0.1", 0), handler) as server:
            thread = Thread(target=server.serve_forever, daemon=True)
            thread.start()
            try:
                return subprocess.run(
                    [sys.executable, str(ROOT / "generate.py"),
                     "--source", f"http://127.0.0.1:{server.server_port}", str(output)],
                    capture_output=True, text=True, timeout=15,
                )
            finally:
                server.shutdown()
                thread.join()
