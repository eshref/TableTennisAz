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


def row(match, team=False, doubles=False):
    kind, side = ("team-matches-match", "col-5") if team else ("match-box", "col-3")
    partner = "<div>Teammate</div>" if doubles else ""
    return (f'<a href="/matches/{match}"><div class="{kind}">'
            f'<div class="{side}"><div>Əşrəf Səfərov</div>{partner}</div>'
            f'<div class="{side}"><div>Etibar Yunusov</div>{partner}</div></div></a>')


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
        not_found = '<div class="not-found-page"><div class="page-not-found-div">Səhifə mövcud deyil</div></div>'
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
            "/matches/results/player/20": results(row(19)),
            "/matches/results/player/115": not_found,
            "/players/115": not_found,
            "/matches/11": details(event="Duello"),
            "/matches/12": details(event="Duello"),
            "/matches/13": details(event="Duello"),
            "/matches/14": details(),
            "/matches/15": details(),
            "/matches/16": details(left=115),
            "/matches/17": details(),
            "/matches/19": details(),
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
            self.assertEqual(set(row["id"] for row in published["matches"]), {11, 12, 13, 14, 15, 17, 19})
            self.assertEqual(len(published["matches"]), 7)
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
                             {11: "3-1", 12: "3-1", 13: "3-1", 14: "3-1", 15: "2-3", 17: "3-1", 18: "3-1", 19: "3-1"})
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

            # arrange
            pages[event + "?page=2"] = results(row(15, team=True), row(16, team=True))
            pages[history] = results("<div>The player history is unavailable</div>")

            # act
            malformed = refresh()

            # assert
            self.assertNotEqual(malformed.returncode, 0)
            self.assertIn(history + ": unrecognized result list", malformed.stderr)
            self.assertEqual({path.name: path.read_bytes() for path in output.iterdir()}, snapshot)

            # arrange
            pages[history] = results(row(11))
            pages["/matches/results/player/20"] = not_found
            pages["/players/20"] = '<main id="center">Etibar Yunusov</main>'

            # act
            missing_history = refresh()

            # assert
            self.assertNotEqual(missing_history.returncode, 0)
            self.assertIn("/matches/results/player/20: source page not found", missing_history.stderr)
            self.assertEqual({path.name: path.read_bytes() for path in output.iterdir()}, snapshot)

    def test_publishes_doubles_from_later_duel_tournament_and_historical_player_pages(self):
        # arrange
        duels = "/matches/duellos"
        event = "/matches/results/event/7"
        history = "/matches/results/player/15"
        older_player = "/matches/results/player/30"
        directory = ('<main id="center"><div class="ranking-page-list">'
                     '<a href="/players/15">Əşrəf Səfərov</a></div></main>')

        def doubles(left, right, event_name="Liqa"):
            def side(players):
                return '<div class="match-player-box">' + "".join(
                    f'<a href="/players/{identity}"><span class="match-player-name">{name}</span></a>'
                    for identity, name in players) + '</div>'

            return (f'<main id="center"><div class="match-players-box">{side(left)}'
                    f'<div class="match-date">{DAY}</div><div class="match-tournament">{event_name}</div>'
                    '<div class="match-player-score">3</div><div class="match-player-score">1</div>'
                    f'{side(right)}</div></main>')

        left = [(15, "Əşrəf Səfərov"), (30, "Tarixi tərəfdaş")]
        right = [(20, "Etibar Yunusov"), (21, "Digər tərəfdaş")]
        pages = {
            "/players?level=amateur": directory,
            "/players?level=pro": directory,
            "/matches/results": f'<main id="center"><div class="tournament-list">'
                                f'<a href="{event}"><span class="tournament-title">Liqa</span></a></div></main>',
            duels: results(row(11), pages=pagination(duels, 2)),
            duels + "?page=2": results(row(31, doubles=True)),
            event: results(row(32, doubles=True), pages=pagination(event, 2)),
            event + "?page=2": results('<div class="team-match-box"><div class="child-matches-group">'
                                        + row(33, team=True, doubles=True) + row(34, team=True, doubles=True)
                                        + '</div></div>'),
            history: results(row(31, doubles=True)),
            older_player: results(row(31, doubles=True), pages=pagination(older_player, 2)),
            older_player + "?page=2": results(row(35, doubles=True)),
            "/matches/results/player/20": results(),
            "/matches/results/player/21": results(),
            "/matches/results/player/130": results(),
            "/matches/11": details(event="Duello"),
            "/matches/31": doubles(left, right, "Duello"),
            "/matches/32": doubles(right, left[::-1]),
            "/matches/33": doubles(left, right),
            "/matches/34": doubles([(15, "Əşrəf Səfərov"), (130, "Tarixi tərəfdaş")], right),
            "/matches/35": doubles(left, right),
        }

        with tempfile.TemporaryDirectory() as temporary, website(pages) as source:
            singles = Path(temporary) / "singles-index-v1"
            output = Path(temporary) / "doubles-index-v1"

            # act
            refresh = subprocess.run([sys.executable, str(GENERATOR), "--source", source, str(singles),
                                      "--doubles-output", str(output)], capture_output=True, text=True)

            # assert
            self.assertEqual(refresh.returncode, 0, refresh.stderr)
            published = json.loads((output / "15.json").read_text())
            self.assertEqual([match["id"] for match in published["matches"]], [31, 32, 33, 34, 35])
            duel, archive, team, namesake, older = published["matches"]
            self.assertTrue(duel["duel"])
            self.assertEqual([player["id"] for player in archive["left"]], [20, 21])
            self.assertEqual([player["id"] for player in archive["right"]], [30, 15])
            self.assertEqual((team["eventId"], team["event"], team["date"], team["score"]), (7, "Liqa", DAY, "3-1"))
            self.assertEqual(namesake["left"][1], {"id": 130, "name": "Tarixi tərəfdaş"})
            self.assertEqual(older["left"][1], {"id": 30, "name": "Tarixi tərəfdaş"})
            self.assertEqual(json.loads((output / "0.json").read_text())["matches"], [])
            self.assertEqual(len(list(output.iterdir())), 256)
            singles_shard = json.loads((singles / "15.json").read_text())
            self.assertEqual([match["id"] for match in singles_shard["matches"]], [11])
            self.assertEqual(singles_shard["refreshedAt"], published["refreshedAt"])
