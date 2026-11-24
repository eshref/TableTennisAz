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


def generate(fixtures, output, *, unavailable=(), previous=None, options=()):
    handler = functools.partial(FixtureHandler, directory=fixtures, unavailable=unavailable)
    with ThreadingHTTPServer(("127.0.0.1", 0), handler) as server:
        thread = Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            return subprocess.run(
                [sys.executable, str(ROOT / "generate.py"),
                 "--source", f"http://127.0.0.1:{server.server_port}",
                 *(["--previous", str(previous)] if previous else []), *options, str(output)],
                capture_output=True, text=True, timeout=15,
            )
        finally:
            server.shutdown()
            thread.join()


def podium_ids(path, event_id):
    entries = json.loads(path.read_bytes())["tournaments"]
    tournament = next(entry for entry in entries if entry["id"] == event_id)
    return {medal: [player["id"] for player in players]
            for medal, players in tournament.get("podium", {}).items()}


class CatalogueTest(unittest.TestCase):
    def test_publishes_verified_tournaments_with_source_fields_in_end_date_order(self):
        # arrange
        with TemporaryDirectory() as directory:
            output = Path(directory) / "tournaments-v1.json"

            # act
            result = generate(ROOT / "fixtures", output)

            # assert
            self.assertEqual(result.returncode, 0, result.stderr)
            catalogue = json.loads(output.read_text(encoding="utf-8"))
            first_listed = catalogue.pop("firstListed")
            self.assertEqual(set(first_listed), {"7", "805", "941", "944", "990", "991", "992", "993"})
            self.assertEqual(len(set(first_listed.values())), 1)
            self.assertEqual({key: catalogue[key] for key in ("version", "tournaments")}, {
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
            initial = generate(fixtures, output)

            # assert
            self.assertEqual(initial.returncode, 0, initial.stderr)
            baseline = json.loads(output.read_text(encoding="utf-8"))

            # arrange
            next_index = BeautifulSoup(complete_index, "html.parser")
            next_index.select_one('a[href="/matches/results/event/805"]').decompose()
            index.write_text(str(next_index), encoding="utf-8")
            event.write_bytes(published_detail)

            # act
            updated = generate(fixtures, output, previous=output)

            # assert
            self.assertEqual(updated.returncode, 0, updated.stderr)
            self.assertNotIn(944, [entry["id"] for entry in baseline["tournaments"]])
            self.assertIn("944", baseline["firstListed"])
            catalogue = json.loads(output.read_text(encoding="utf-8"))
            self.assertIn(944, [entry["id"] for entry in catalogue["tournaments"]])
            self.assertEqual({key: catalogue["firstListed"][key] for key in baseline["firstListed"]},
                             baseline["firstListed"])
            self.assertGreater(catalogue["firstListed"]["7"], max(baseline["firstListed"].values()))

            # arrange
            index.write_text(complete_index, encoding="utf-8")

            # act
            reappeared = generate(fixtures, output, previous=output)

            # assert
            self.assertEqual(reappeared.returncode, 0, reappeared.stderr)
            self.assertEqual(json.loads(output.read_text(encoding="utf-8"))["firstListed"], catalogue["firstListed"])

    def test_migrates_a_legacy_publication_with_an_id_that_left_the_results_index(self):
        # arrange
        with TemporaryDirectory() as directory:
            output = Path(directory) / "tournaments-v1.json"
            output.write_text('{"version":1,"tournaments":[{"id":123}]}', encoding="utf-8")

            # act
            result = generate(ROOT / "fixtures", output, previous=output)

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
            result = generate(fixtures, output)

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
            result = generate(ROOT / "fixtures", output, unavailable=("/api/getDuoInfo?id=413",))

            # assert
            self.assertEqual(result.returncode, 0, result.stderr)
            entries = json.loads(output.read_text(encoding="utf-8"))["tournaments"]
            podium = next(entry for entry in entries if entry["id"] == 941)["podium"]
            self.assertEqual(set(podium), {"gold", "bronze"})
            self.assertEqual([player["id"] for player in podium["bronze"]], [457, 144])

    def test_pair_lookup_outage_preserves_verified_podiums_and_publishes_new_results(self):
        # arrange
        with TemporaryDirectory() as directory:
            root = Path(directory)
            fixtures = copytree(ROOT / "fixtures", root / "fixtures")
            event = fixtures / "events/944.html"
            detail = event.read_bytes()
            event.unlink()
            index = fixtures / "matches/results.html"
            complete_index = index.read_text(encoding="utf-8")
            initial_index = BeautifulSoup(complete_index, "html.parser")
            initial_index.select_one('a[href="/matches/results/event/993"]').decompose()
            index.write_text(str(initial_index), encoding="utf-8")
            previous = root / "previous.json"
            baseline = generate(fixtures, previous)
            if baseline.returncode != 0:
                self.fail(baseline.stderr)
            event.write_bytes(detail)
            index.write_text(complete_index, encoding="utf-8")
            # Event 993 loses both its explicit doubles label and its winners section.
            event_without_podium = fixtures / "events/993.html"
            page = BeautifulSoup(event_without_podium.read_bytes(), "html.parser", from_encoding="utf-8")
            page.find(string="Qoşa sayı").parent.parent.decompose()
            page.select_one(".winners-div").decompose()
            event_without_podium.write_text(str(page), encoding="utf-8")
            output = root / "tournaments-v1.json"

            # act
            result = generate(fixtures, output, previous=previous, unavailable=(
                "/api/getDuoInfo?id=447", "/api/getDuoInfo?id=413", "/api/getDuoInfo?id=883",
            ))

            # assert
            self.assertEqual(result.returncode, 0, result.stderr)
            entries = {entry["id"]: entry for entry in json.loads(output.read_bytes())["tournaments"]}
            self.assertEqual(podium_ids(output, 941),
                             {"gold": [13, 37], "silver": [99, 636], "bronze": [457, 144]})
            self.assertEqual(entries[944]["format"], "singles")
            self.assertEqual(entries[993]["format"], "doubles")
            self.assertNotIn("podium", entries[993])

    def test_corrected_pair_stays_hidden_until_verified_while_other_medals_survive(self):
        # arrange
        with TemporaryDirectory() as directory:
            root = Path(directory)
            fixtures = copytree(ROOT / "fixtures", root / "fixtures")
            output = root / "tournaments-v1.json"
            baseline = generate(fixtures, output)
            if baseline.returncode != 0:
                self.fail(baseline.stderr)
            recovery = root / "earlier-catalogue.json"
            recovery.write_bytes(output.read_bytes())
            event = fixtures / "events/941.html"
            corrected_event = event.read_text(encoding="utf-8").replace("447", "1234")
            event.write_text(corrected_event, encoding="utf-8")
            (fixtures / "api/getDuoInfo-883.json").write_text("<html>Unavailable</html>", encoding="utf-8")

            # act
            correction = generate(fixtures, output, previous=output)

            # assert
            self.assertEqual(correction.returncode, 0, correction.stderr)
            self.assertEqual(podium_ids(output, 941), {"silver": [99, 636], "bronze": [457, 144]})

            # arrange
            event.write_text(corrected_event.replace('class="duo-id" value="1234"', 'class="unavailable"'),
                             encoding="utf-8")

            # act
            unresolved = generate(fixtures, output, previous=output, options=("--recovery", str(recovery)))

            # assert
            self.assertEqual(unresolved.returncode, 0, unresolved.stderr)
            self.assertEqual(podium_ids(output, 941), {"silver": [99, 636], "bronze": [457, 144]})

            # arrange
            event.write_text(corrected_event, encoding="utf-8")
            (fixtures / "api/getDuoInfo-1234.json").write_text(json.dumps({
                "player1Id": 40, "player1FullName": "Çingiz Nadirov",
                "player2Id": 1118, "player2FullName": "Əli Şəhgəldiyev",
                "resultsUrl": "/matches/results/duo/1234",
            }), encoding="utf-8")

            # act
            verified = generate(fixtures, output, previous=output)

            # assert
            self.assertEqual(verified.returncode, 0, verified.stderr)
            self.assertEqual(podium_ids(output, 941), {
                "gold": [40, 1118], "silver": [99, 636], "bronze": [457, 144],
            })

            # arrange
            index = fixtures / "matches/results.html"
            complete_index = index.read_text(encoding="utf-8")
            page = BeautifulSoup(complete_index, "html.parser")
            page.select_one('a[href="/matches/results/event/941"]').decompose()
            index.write_text(str(page), encoding="utf-8")
            absent = generate(fixtures, output, previous=output)
            if absent.returncode != 0:
                self.fail(absent.stderr)
            index.write_text(complete_index, encoding="utf-8")
            (fixtures / "api/getDuoInfo-1234.json").unlink()

            # act
            reappeared = generate(fixtures, output, previous=output, options=("--recovery", str(recovery)))

            # assert
            self.assertEqual(reappeared.returncode, 0, reappeared.stderr)
            self.assertEqual(podium_ids(output, 941), {
                "gold": [40, 1118], "silver": [99, 636], "bronze": [457, 144],
            })

    def test_recovery_withholds_archived_medal_when_another_known_pair_now_occupies_it(self):
        # arrange
        with TemporaryDirectory() as directory:
            root = Path(directory)
            fixtures = copytree(ROOT / "fixtures", root / "fixtures")
            recovery = root / "historical.json"
            historical = generate(fixtures, recovery)
            if historical.returncode != 0:
                self.fail(historical.stderr)
            (fixtures / "api/getDuoInfo-447.json").unlink()
            previous = root / "previous.json"
            baseline = generate(fixtures, previous)
            if baseline.returncode != 0:
                self.fail(baseline.stderr)
            event = fixtures / "events/941.html"
            detail = event.read_text(encoding="utf-8")
            event.write_text(detail.replace('class="duo-id" value="413"', 'class="unavailable"')
                             .replace("447", "413"), encoding="utf-8")
            (fixtures / "api/getDuoInfo-413.json").unlink()
            output = root / "tournaments-v1.json"

            # act
            current = generate(fixtures, output, previous=previous, options=("--recovery", str(recovery)))

            # assert
            self.assertEqual(current.returncode, 0, current.stderr)
            self.assertNotIn("gold", podium_ids(output, 941))

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
            result = generate(fixtures, output)

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
            result = generate(fixtures, output)

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
            result = generate(root, output)

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
            result = generate(fixtures, output)

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
            result = generate(fixtures, output)

            # assert
            self.assertEqual(result.returncode, 0, result.stderr)
            entries = json.loads(output.read_text(encoding="utf-8"))["tournaments"]
            self.assertEqual([entry["id"] for entry in entries], [944, 941, 993])
