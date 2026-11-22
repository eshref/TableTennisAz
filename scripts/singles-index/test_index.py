import json
import subprocess
import sys
import tempfile
import unittest
from contextlib import contextmanager
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from threading import Thread


GENERATOR = Path(__file__).with_name("generate.py")
DAY = "12 Nov. 2026"


def results(*rows, pages=""):
    return f'<main id="center"><div class="matches-box">{"".join(rows)}</div>{pages}</main>'


def row(match, team=False):
    kind, side = ("team-matches-match", "col-5") if team else ("match-box", "col-3")
    return (f'<a href="/matches/{match}"><div class="{kind}">'
            f'<div class="{side}"><div>Əşrəf Səfərov</div></div>'
            f'<div class="{side}"><div>Etibar Yunusov</div></div></div></a>')


def details(left=15, right=20, score=(3, 1), event="Liqa"):
    return f"""<main id="center"><div class="match-players-box">
      <div class="match-player-box"><a href="/players/{left}">
        <span class="match-player-name">Əşrəf Səfərov</span></a></div>
      <div class="match-date">{DAY}</div><div class="match-tournament">{event}</div>
      <div class="match-player-score">{score[0]}</div><div class="match-player-score">{score[1]}</div>
      <div class="match-player-box"><a href="/players/{right}">
        <span class="match-player-name">Etibar Yunusov</span></a></div>
    </div></main>"""


def pagination(path, *numbers):
    return '<ul class="pagination">' + "".join(
        f'<li><a href="{path}?page={n}">{n}</a></li>' for n in numbers) + '</ul>'


@contextmanager
def website(pages):
    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            page = pages.get(self.path)
            self.send_response(200 if page is not None else 404)
            self.end_headers()
            if page is not None:
                self.wfile.write(page.encode())

        def log_message(self, *args):
            pass

    with ThreadingHTTPServer(("127.0.0.1", 0), Handler) as server:
        thread = Thread(target=server.serve_forever)
        thread.start()
        try:
            yield f"http://127.0.0.1:{server.server_port}"
        finally:
            server.shutdown()
            thread.join()


class DailySinglesIndexTest(unittest.TestCase):
    def test_publishes_verified_singles_and_retains_publication_after_failed_refresh(self):
        # arrange
        duels = "/matches/duellos"
        event = "/matches/results/event/7"
        history = "/matches/results/player/15"
        directory = ('<main id="center"><div class="ranking-page-list">'
                     '<a href="/players/15">Əşrəf Səfərov</a></div></main>')
        pages = {
            "/players?level=amateur": directory,
            "/players?level=pro": directory,
            "/matches/results": f'<main id="center"><div class="tournament-list">'
                                f'<a href="{event}"><span class="tournament-title">Liqa</span></a></div></main>',
            duels: results(row(11), pages=pagination(duels, 2, 3)),
            duels + "?page=2": results(row(12)),
            duels + "?page=3": results(row(13)),
            event: results(row(14), pages=pagination(event, 2, 3)),
            event + "?page=2": results('<div class="team-match-box"><div class="child-matches-group">'
                                      + row(15, team=True) + row(16, team=True) + '</div></div>'),
            event + "?page=3": results('<div class="team-match-box"><div class="child-matches-group"></div></div>'),
            history: results(row(11), pages=pagination(history, 2)),
            history + "?page=2": results(row(17)),
            "/matches/results/player/20": results(),
            "/matches/results/player/115": results(),
            "/matches/11": details(event="Duello"),
            "/matches/12": details(event="Duello"),
            "/matches/13": details(event="Duello"),
            "/matches/14": details(),
            "/matches/15": details(),
            "/matches/16": details(left=115),
            "/matches/17": details(),
        }

        with tempfile.TemporaryDirectory() as temporary, website(pages) as source:
            output = Path(temporary) / "singles-index-v1"
            shard = output / "15.json"

            def refresh():
                return subprocess.run([sys.executable, str(GENERATOR), "--source", source, str(output)],
                                      capture_output=True, text=True)

            # act
            first = refresh()

            # assert
            self.assertEqual(first.returncode, 0, first.stderr)
            published = json.loads(shard.read_text())
            self.assertEqual(set(row["id"] for row in published["matches"]), {11, 12, 13, 14, 15, 17})
            self.assertEqual(len(published["matches"]), 6)
            team_game = next(row for row in published["matches"] if row["id"] == 15)
            self.assertEqual((team_game["date"], team_game["eventId"], team_game["event"]), (DAY, 7, "Liqa"))
            self.assertTrue(all(row["left"]["id"] == 15 and row["right"]["id"] == 20
                                for row in published["matches"]))
            self.assertEqual([(row["id"], row["event"]) for row in published["matches"] if row["duel"]],
                             [(11, "Duello"), (12, "Duello"), (13, "Duello")])
            namesake = json.loads((output / "20.json").read_text())["matches"]
            self.assertEqual([(row["id"], row["left"]["id"]) for row in namesake], [(16, 115)])
            self.assertEqual(json.loads((output / "0.json").read_text())["matches"], [])

            # arrange
            pages["/matches/15"] = details(score=(2, 3))
            pages[history + "?page=2"] = results(row(17), row(18))
            pages["/matches/18"] = details()

            # act
            corrected = refresh()

            # assert
            self.assertEqual(corrected.returncode, 0, corrected.stderr)
            updated = json.loads(shard.read_text())
            self.assertGreater(updated["refreshedAt"], published["refreshedAt"])
            self.assertEqual({row["id"]: row["score"] for row in updated["matches"]},
                             {11: "3-1", 12: "3-1", 13: "3-1", 14: "3-1", 15: "2-3", 17: "3-1", 18: "3-1"})
            snapshot = {path.name: path.read_bytes() for path in output.iterdir()}
            self.assertEqual(len(snapshot), 256)
            self.assertEqual({json.loads(shard)["refreshedAt"] for shard in snapshot.values()},
                             {updated["refreshedAt"]})

            # arrange
            del pages[event + "?page=2"]

            # act
            failed = refresh()

            # assert
            self.assertNotEqual(failed.returncode, 0)
            self.assertIn(event + "?page=2", failed.stderr)
            self.assertEqual({path.name: path.read_bytes() for path in output.iterdir()}, snapshot)
