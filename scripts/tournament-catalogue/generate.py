"""Build the Pages catalogue from the public Results index and event pages."""
import argparse
from collections import defaultdict
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime
import json
from pathlib import Path
import re
import sys
from tempfile import NamedTemporaryFile
from time import sleep
from urllib.error import HTTPError, URLError
from urllib.parse import parse_qs, urlsplit
from urllib.request import Request, urlopen

from bs4 import BeautifulSoup, SoupStrainer

SITE = "https://tabletennis.az"
INDEX = "/matches/results"
EVENT_LINK = re.compile(r"/matches/results/event/([1-9][0-9]*)/?")
PLAYER_LINK = re.compile(r"/players/[1-9][0-9]*/?")
MATCH_LINK = re.compile(r"/matches/[1-9][0-9]*/?")
TEAM_NAME = re.compile(r"\b(?:komanda(?:lar)?|teams?)\b", re.IGNORECASE)
TEAM_MARKUP = ".team-ranking-list, .team-match-box, .team-matches-match"
DOUBLES_NAME = re.compile(r"\b(?:qoşa(?:lar)?|doubles|cüt(?:lər|lük|lüklər)?)\b", re.IGNORECASE)
DOUBLES_PODIUM = ".winners-div .rank-duo-box"
PAIR_COUNT_LABELS = ("Qoşa sayı", "Qoşalar", "Cüt sayı", "Cütlük sayı", "Cütlüklər")


def fetch(source, path):
    request = Request(source + path, headers={
        "User-Agent": "TableTennisAz tournament catalogue",
        "Accept-Language": "az-AZ,az;q=0.9",
    })
    for attempt in range(3):
        try:
            with urlopen(request, timeout=25) as response:
                return BeautifulSoup(response.read(), "html.parser", from_encoding="utf-8",
                                     parse_only=SoupStrainer(id="center"))
        except HTTPError as error:
            if error.code in (404, 410):
                return None
            if error.code not in (408, 429) and error.code < 500:
                raise
            if attempt == 2:
                raise
        except (URLError, TimeoutError):
            if attempt == 2:
                raise
        sleep(2 ** attempt)


def published_events(source):
    page = fetch(source, INDEX)
    if page is None:
        raise ValueError("Results index is unavailable")
    events = {}
    for title in page.select("#center .tournament-list .tournament-title"):
        link = title.find_parent("a", href=True)
        identity = EVENT_LINK.fullmatch(link["href"]) if link else None
        name = title.get_text(" ", strip=True)
        if not identity or not name:
            raise ValueError("Results index has an invalid event")
        event_id = int(identity[1])
        if event_id in events:
            raise ValueError(f"Results index repeats event {event_id}")
        events[event_id] = (name, fields(link))
    if not events:
        raise ValueError("Results index has no recognizable completed tournaments")
    return events


def fields(container):
    values = {}
    for label in container.select("span:first-child + span"):
        key = label.find_previous_sibling("span").get_text(" ", strip=True)
        values[key] = label.get_text(" ", strip=True)
    return values


def published_day(value):
    try:
        return datetime.strptime(value, "%d.%m.%Y").date().isoformat()
    except ValueError:
        return None


def individual_entrants(detail):
    # The player roster is also present for team and doubles events, so corroborate
    # it with the tournament standings and every published result page.
    players = {link["href"].rstrip("/") for link in detail.select(".tournament-players a[href]")
               if PLAYER_LINK.fullmatch(link["href"])}
    return len(players) >= 2 or detail.select_one(
        ".winners-div .first-place a[href^='/players/'], .winners-div .second-place a[href^='/players/'], "
        ".winners-div .third-place a[href^='/players/']"
    ) is not None


def published_results(source, event_id, side_size):
    path = f"{INDEX}/event/{event_id}"
    number = 1
    results = {}
    later_pages = set()
    complete = True
    while True:
        page_path = path if number == 1 else f"{path}?page={number}"
        page = fetch(source, page_path)
        if page is None:
            if number > 1:
                raise ValueError(f"Advertised result page is unavailable: {page_path}")
            return None
        if page.select_one(TEAM_MARKUP):
            return None
        if not page.select_one(".matches-box .match-box"):
            if number > 1:
                raise ValueError(f"Advertised result page has no match rows: {page_path}")
            return None
        parsed = matches_on_page(page, side_size)
        if parsed is None:
            return None
        page_matches, page_complete = parsed
        overlap = results.keys() & page_matches.keys()
        if side_size == 1 and overlap:
            return None
        page_complete = (page_complete and bool(page_matches.keys() - overlap)
                         and all(results[identity] == page_matches[identity] for identity in overlap))
        if not page_complete and (number == 1 or side_size == 1):
            return None
        complete = complete and page_complete
        results.update(page_matches)

        for link in page.select(".pagination li:not(.disabled) a[href]"):
            target = urlsplit(link["href"])
            if target.path.rstrip("/") != path:
                continue
            value = parse_qs(target.query).get("page", [""])[0]
            if value.isdecimal() and int(value) > number:
                later_pages.add(int(value))
        if not later_pages:
            return results, complete
        # Check every advertised page, including those linked only from earlier pages.
        number += 1
        if number not in later_pages:
            raise ValueError(f"Missing result page {number} in pagination for {path}")
        later_pages.remove(number)


def matches_on_page(page, side_size):
    matches = {}
    expected_sizes = (side_size, side_size)
    row_count = 0
    complete = True
    for day in page.select(".matches-box > *"):
        rows = day.select("a[href] > .match-box")
        if not rows:
            continue
        date = day.select_one(".divs-header")
        dated = date is not None
        try:
            if dated:
                datetime.strptime(date.get_text(" ", strip=True).replace(".", ""), "%d %b %Y")
        except ValueError:
            dated = False
        for row in rows:
            row_count += 1
            identity = row.parent["href"].rstrip("/")
            score = row.select_one(":scope > .col-2 .center")
            names = [[player.get_text(" ", strip=True) for player in side.find_all(recursive=False)]
                     for side in row.select(":scope > .col-3")]
            sides = sorted(tuple(sorted(name for name in side if name)) for side in names)
            sizes = tuple(map(len, sides))
            if sizes in ((1, 1), (2, 2)) and sizes != expected_sizes:
                return None
            if (not dated or not MATCH_LINK.fullmatch(identity) or not score
                    or not re.fullmatch(r"[0-9]+\s*-\s*[0-9]+", score.get_text(strip=True))
                    or sizes != expected_sizes):
                complete = False
                continue
            if identity in matches and (side_size == 1 or matches[identity] != sides):
                complete = False
            matches[identity] = sides
    return matches, complete and row_count == len(page.select(".matches-box .match-box"))


def published_count(event_fields, index_fields, event_labels, index_labels):
    for values, labels in ((event_fields, event_labels), (index_fields, index_labels)):
        for label in labels:
            count = values.get(label, "")
            if re.fullmatch(r"[0-9]+", count):
                return int(count)
    return None


def match_detail_pairs(source, path, match):
    detail = fetch(source, path)
    if detail is None:
        return None
    sides = detail.select(".match-players-box > .match-player-box")
    if len(sides) != 2:
        return None
    names = []
    pairs = []
    for side in sides:
        participants = [(name.get_text(" ", strip=True), name.parent["href"].rstrip("/"))
                        for name in side.select("a[href] > .match-player-name")]
        if len(participants) != 2 or any(not PLAYER_LINK.fullmatch(identity) for _, identity in participants):
            return None
        names.append(tuple(sorted(name for name, _ in participants)))
        pairs.append(frozenset(identity for _, identity in participants))
    return pairs if sorted(names) == match else None


def distinct_pairs(source, detail, results, player_count):
    # The result rows show names. The tournament roster links those names to
    # profiles. A partial roster could omit a player with the same name as
    # someone it lists, so verify each match directly in that case.
    roster = defaultdict(set)
    for player in detail.select(".tournament-players a[href]"):
        identity = player["href"].rstrip("/")
        if PLAYER_LINK.fullmatch(identity):
            roster[player.get_text(" ", strip=True)].add(identity)
    roster_complete = len({identity for identities in roster.values() for identity in identities}) == player_count
    pairs = set()
    for path, match in results.items():
        sides = [[roster[name] for name in side] for side in match]
        if roster_complete and all(len(identities) == 1 for side in sides for identities in side):
            entrants = [frozenset(next(iter(identities)) for identities in side) for side in sides]
        else:
            entrants = match_detail_pairs(source, path, match)
        if entrants is None or len(entrants[0] | entrants[1]) != 4:
            return None
        pairs.update(entrants)
    return len(pairs)


def verified_tournament(source, event_id, event):
    name, index_fields = event
    if TEAM_NAME.search(name):
        return None
    detail = fetch(source, f"/events/{event_id}")
    if detail is None or detail.select_one(TEAM_MARKUP):
        return None
    header = detail.select_one(".tournament-head-info")
    if header is None:
        return None
    title = header.select_one(".tournament-title")
    if title is None:
        return None
    detail_name = title.get_text(" ", strip=True)
    if TEAM_NAME.search(detail_name):
        return None
    event_fields = fields(header)
    if event_fields.get("Status", "Finished").casefold() != "finished":
        return None
    start = published_day(event_fields.get("Başlama tarixi", ""))
    end = published_day(event_fields.get("Bitmə tarixi", ""))
    if not start or not end or start > end:
        return None

    players = published_count(event_fields, index_fields, ("Oyunçular",), ("Oyunçu sayı",))
    pairs = published_count(event_fields, index_fields, PAIR_COUNT_LABELS, PAIR_COUNT_LABELS)
    doubles = (pairs is not None or DOUBLES_NAME.search(name) or DOUBLES_NAME.search(detail_name)
               or detail.select_one(f"{DOUBLES_PODIUM} .duo-id"))
    if doubles:
        published = published_results(source, event_id, side_size=2)
        if published is None:
            return None
        results, complete = published
        tournament = {"id": event_id, "name": name, "format": "doubles", "start": start, "end": end}
        if pairs is None and players is not None and complete:
            pairs = distinct_pairs(source, detail, results, players)
        if pairs is not None:
            tournament["pairs"] = pairs
    else:
        if (detail.select_one(DOUBLES_PODIUM) or not individual_entrants(detail)
                or published_results(source, event_id, side_size=1) is None):
            return None
        tournament = {"id": event_id, "name": name, "format": "singles", "start": start, "end": end}
        if players is not None:
            tournament["players"] = players
    for prize in (event_fields.get("Priz", ""), index_fields.get("Priz", "")):
        if re.fullmatch(r"[0-9]+(?:[.,][0-9]+)?\s+AZN", prize):
            tournament["prize"] = prize
            break
    return tournament


def build_catalogue(source):
    events = published_events(source)
    with ThreadPoolExecutor(max_workers=4) as fetchers:
        verified = fetchers.map(lambda item: verified_tournament(source, *item), events.items())
        tournaments = [tournament for tournament in verified if tournament is not None]
    if not tournaments:
        raise ValueError("No verified tournaments in the Results index")
    tournaments.sort(key=lambda tournament: tournament["end"], reverse=True)
    print(f"Verified {len(tournaments)} singles and doubles tournaments from {len(events)} completed event entries")
    return {"version": 1, "tournaments": tournaments}


def publish(output, catalogue):
    with NamedTemporaryFile(mode="w", encoding="utf-8", dir=output.parent, delete=False) as temporary:
        path = Path(temporary.name)
        try:
            json.dump(catalogue, temporary, ensure_ascii=False, separators=(",", ":"))
            temporary.write("\n")
            temporary.close()
            path.chmod(0o644)
            path.replace(output)
        finally:
            path.unlink(missing_ok=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", type=Path)
    parser.add_argument("--source", default=SITE, help="tabletennis.az or a source fixture server")
    arguments = parser.parse_args()
    try:
        publish(arguments.output, build_catalogue(arguments.source.rstrip("/")))
    except (OSError, ValueError) as error:
        sys.exit(f"Catalogue generation failed: {error}")


if __name__ == "__main__":
    main()
