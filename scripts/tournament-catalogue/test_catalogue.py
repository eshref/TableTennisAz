import functools
import json
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from shutil import copytree
import subprocess
import sys
from tempfile import TemporaryDirectory
from threading import Thread
import unittest

from bs4 import BeautifulSoup

ROOT = Path(__file__).resolve().parent


class FixtureHandler(SimpleHTTPRequestHandler):
    def __init__(self, *args, unavailable, **kwargs):
        self.unavailable = unavailable
        super().__init__(*args, **kwargs)

    def do_GET(self):
        if self.path in self.unavailable:
            self.send_error(503)
        else:
            super().do_GET()

    def translate_path(self, path):
        if path.startswith("/api/getDuoInfo?id="):
            pair_id = path.partition("=")[2]
            return super().translate_path(f"/api/getDuoInfo-{pair_id}.json")
        route, _, query = path.partition("?page=")
        suffix = f"-page-{query}" if query else ""
        return super().translate_path(f"{route}{suffix}.html")

    def log_message(self, format, *args):
        pass


class CatalogueTest(unittest.TestCase):
    def test_publishes_verified_tournaments_with_source_fields_in_end_date_order(self):
        # arrange
        with TemporaryDirectory() as directory:
            output = Path(directory) / "tournaments-v1.json"

            # act
            result = self.generate(ROOT / "fixtures", output)

            # assert
            self.assertEqual(result.returncode, 0, result.stderr)
            catalogue = json.loads(output.read_text(encoding="utf-8"))
            first_listed = catalogue.pop("firstListed")
            self.assertEqual(set(first_listed), {"7", "805", "941", "944", "990", "991", "992", "993"})
            self.assertEqual(len(set(first_listed.values())), 1)
            self.assertEqual(catalogue, {
                "version": 1,
                "tournaments": [
                    {"id": 944, "name": "BTTC Şəxsi Reytinq Turniri", "format": "singles",
                     "start": "2026-11-19", "end": "2026-11-20", "players": 9, "prize": "0 AZN",
                     "podium": {
                         "gold": [{"id": 4, "name": "Məzahir Həsənov", "photo": "/uploads/players-4.png"}],
                         "silver": [{"id": 20, "name": "Etibar Yunusov"}],
                         "bronze": [{"id": 877, "name": "Səid Quluzadə", "photo": "/uploads/players-877.png"}],
                     }},
                    {"id": 941, "name": "BTTC Qoşa Reytinq Turniri #120", "format": "doubles",
                     "start": "2026-11-18", "end": "2026-11-18", "pairs": 4, "prize": "100 AZN",
                     "podium": {
                         "gold": [{"id": 13, "name": "İlqar Həsənov", "photo": "/uploads/players-13.png"},
                                  {"id": 37, "name": "Məmmədağa Rüstəmov", "photo": "/uploads/players-37.png"}],
                         "silver": [{"id": 99, "name": "Əli Heydərli"}, {"id": 636, "name": "Tatiana Aliulina"}],
                         "bronze": [{"id": 457, "name": "Xaliq Becanov"}, {"id": 144, "name": "Oskar Məhərrəmov"}],
                     }},
                    {"id": 993, "name": "Bakı Kuboku #993", "format": "doubles",
                     "start": "2026-11-15", "end": "2026-11-16", "pairs": 8, "prize": "0 AZN"},
                    {"id": 7, "name": "Table Tennis Baku Cup #3", "format": "singles",
                     "start": "2021-10-11", "end": "2021-10-11"},
                ],
            })

    def test_publication_preserves_first_index_observations_across_late_classification_and_reappearance(self):
        # arrange
        with TemporaryDirectory() as directory:
            root = Path(directory)
            fixtures = copytree(ROOT / "fixtures", root / "fixtures")
            index = fixtures / "matches/results.html"
            complete_index = index.read_text(encoding="utf-8")
            # Event 7 is absent in the first crawl. Event 944 is listed but has no event page yet.
            initial_index = BeautifulSoup(complete_index, "html.parser")
            initial_index.select_one('a[href="/matches/results/event/7"]').decompose()
            index.write_text(str(initial_index), encoding="utf-8")
            event = fixtures / "events/944.html"
            published_detail = event.read_bytes()
            event.unlink()
            output = root / "tournaments-v1.json"

            # act
            initial = self.generate(fixtures, output)
            baseline = json.loads(output.read_text(encoding="utf-8"))
            next_index = BeautifulSoup(complete_index, "html.parser")
            next_index.select_one('a[href="/matches/results/event/805"]').decompose()
            index.write_text(str(next_index), encoding="utf-8")
            event.write_bytes(published_detail)
            updated = self.generate(fixtures, output, previous=output)

            # assert
            self.assertEqual(initial.returncode, 0, initial.stderr)
            self.assertEqual(updated.returncode, 0, updated.stderr)
            self.assertNotIn(944, [entry["id"] for entry in baseline["tournaments"]])
            self.assertIn("944", baseline["firstListed"])
            catalogue = json.loads(output.read_text(encoding="utf-8"))
            self.assertIn(944, [entry["id"] for entry in catalogue["tournaments"]])
            self.assertEqual({key: catalogue["firstListed"][key] for key in baseline["firstListed"]},
                             baseline["firstListed"])
            self.assertGreater(catalogue["firstListed"]["7"], max(baseline["firstListed"].values()))

            # act
            index.write_text(complete_index, encoding="utf-8")
            reappeared = self.generate(fixtures, output, previous=output)

            # assert
            self.assertEqual(reappeared.returncode, 0, reappeared.stderr)
            self.assertEqual(json.loads(output.read_text(encoding="utf-8"))["firstListed"], catalogue["firstListed"])

    def test_migrates_a_legacy_publication_with_an_id_that_left_the_results_index(self):
        # arrange
        with TemporaryDirectory() as directory:
            output = Path(directory) / "tournaments-v1.json"
            output.write_text('{"version":1,"tournaments":[{"id":123}]}', encoding="utf-8")

            # act
            result = self.generate(ROOT / "fixtures", output, previous=output)

            # assert
            self.assertEqual(result.returncode, 0, result.stderr)
            first_listed = json.loads(output.read_text(encoding="utf-8"))["firstListed"]
            self.assertEqual(first_listed["123"], first_listed["944"])

    def test_verified_doubles_with_unresolved_partners_keeps_the_card_without_a_count(self):
        # arrange
        with TemporaryDirectory() as directory:
            root = Path(directory)
            fixtures = copytree(ROOT / "fixtures", root / "fixtures")
            (fixtures / "matches/30912.html").unlink()
            output = root / "tournaments-v1.json"

            # act
            result = self.generate(fixtures, output)

            # assert
            self.assertEqual(result.returncode, 0, result.stderr)
            entries = json.loads(output.read_text(encoding="utf-8"))["tournaments"]
            tournament = next(entry for entry in entries if entry["id"] == 941)
            self.assertEqual(tournament["format"], "doubles")
            self.assertNotIn("pairs", tournament)
            self.assertNotIn("players", tournament)

    def test_unavailable_pair_metadata_does_not_hide_other_published_placements(self):
        # arrange
        with TemporaryDirectory() as directory:
            root = Path(directory)
            output = root / "tournaments-v1.json"

            # act
            result = self.generate(ROOT / "fixtures", output, unavailable=("/api/getDuoInfo?id=413",))

            # assert
            self.assertEqual(result.returncode, 0, result.stderr)
            entries = json.loads(output.read_text(encoding="utf-8"))["tournaments"]
            podium = next(entry for entry in entries if entry["id"] == 941)["podium"]
            self.assertEqual(set(podium), {"gold", "bronze"})
            self.assertEqual([player["id"] for player in podium["bronze"]], [457, 144])

    def test_partial_roster_can_distinguish_partners_who_share_a_published_name(self):
        # arrange
        with TemporaryDirectory() as directory:
            root = Path(directory)
            fixtures = copytree(ROOT / "fixtures", root / "fixtures")
            event = fixtures / "events/941.html"
            event.write_text(event.read_text(encoding="utf-8").replace(">6<", ">7<"), encoding="utf-8")
            match = fixtures / "matches/30912.html"
            match.write_text(match.read_text(encoding="utf-8").replace("/players/40", "/players/41"), encoding="utf-8")
            output = root / "tournaments-v1.json"

            # act
            result = self.generate(fixtures, output)

            # assert
            self.assertEqual(result.returncode, 0, result.stderr)
            entries = json.loads(output.read_text(encoding="utf-8"))["tournaments"]
            tournament = next(entry for entry in entries if entry["id"] == 941)
            self.assertEqual(tournament["pairs"], 5)

    def test_verified_doubles_with_an_unusable_later_match_keeps_the_card_without_a_count(self):
        # arrange
        with TemporaryDirectory() as directory:
            root = Path(directory)
            fixtures = copytree(ROOT / "fixtures", root / "fixtures")
            page = fixtures / "matches/results/event/941-page-2.html"
            page.write_text(page.read_text(encoding="utf-8").replace(">3-1<", ">—<"), encoding="utf-8")
            output = root / "tournaments-v1.json"

            # act
            result = self.generate(fixtures, output)

            # assert
            self.assertEqual(result.returncode, 0, result.stderr)
            entries = json.loads(output.read_text(encoding="utf-8"))["tournaments"]
            tournament = next(entry for entry in entries if entry["id"] == 941)
            self.assertEqual(tournament["format"], "doubles")
            self.assertNotIn("pairs", tournament)

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

    def test_an_unavailable_advertised_page_leaves_the_previous_output_in_place(self):
        # arrange
        with TemporaryDirectory() as directory:
            root = Path(directory)
            fixtures = copytree(ROOT / "fixtures", root / "fixtures")
            (fixtures / "matches/results/event/991-page-2.html").unlink()
            output = root / "tournaments-v1.json"
            output.write_text("previous publication", encoding="utf-8")

            # act
            result = self.generate(fixtures, output)

            # assert
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("/matches/results/event/991?page=2", result.stderr)
            self.assertEqual(output.read_text(encoding="utf-8"), "previous publication")

    def test_a_match_row_without_a_usable_source_date_cannot_qualify_a_tournament(self):
        # arrange
        with TemporaryDirectory() as directory:
            root = Path(directory)
            fixtures = copytree(ROOT / "fixtures", root / "fixtures")
            page = fixtures / "matches/results/event/7.html"
            page.write_text(page.read_text(encoding="utf-8").replace("divs-header", "no-date"), encoding="utf-8")
            output = root / "tournaments-v1.json"

            # act
            result = self.generate(fixtures, output)

            # assert
            self.assertEqual(result.returncode, 0, result.stderr)
            entries = json.loads(output.read_text(encoding="utf-8"))["tournaments"]
            self.assertEqual([entry["id"] for entry in entries], [944, 941, 993])

    def generate(self, fixtures, output, *, unavailable=(), previous=None):
        handler = functools.partial(FixtureHandler, directory=fixtures, unavailable=unavailable)
        with ThreadingHTTPServer(("127.0.0.1", 0), handler) as server:
            thread = Thread(target=server.serve_forever, daemon=True)
            thread.start()
            try:
                return subprocess.run(
                    [sys.executable, str(ROOT / "generate.py"),
                     "--source", f"http://127.0.0.1:{server.server_port}",
                     *(["--previous", str(previous)] if previous else []), str(output)],
                    capture_output=True, text=True, timeout=15,
                )
            finally:
                server.shutdown()
                thread.join()
