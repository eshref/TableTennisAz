"""Build the Pages catalogue from the public Results index and event pages."""
import argparse
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
TEAM_NAME = re.compile(r"\b(?:komanda|teams?)\b", re.IGNORECASE)
TEAM_MARKUP = ".team-ranking-list, .team-match-box, .team-matches-match"


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


def published_singles_results(source, event_id):
    path = f"{INDEX}/event/{event_id}"
    number = 1
    match_ids = set()
    while True:
        page_path = path if number == 1 else f"{path}?page={number}"
        page = fetch(source, page_path)
        if page is None:
            if number > 1:
                raise ValueError(f"Advertised result page is unavailable: {page_path}")
            return False
        if page.select_one(TEAM_MARKUP):
            return False
        if not page.select_one(".matches-box .match-box"):
            if number > 1:
                raise ValueError(f"Advertised result page has no match rows: {page_path}")
            return False
        page_matches = singles_match_ids(page)
        if not page_matches or not match_ids.isdisjoint(page_matches):
            return False
        match_ids.update(page_matches)

        later_pages = set()
        for link in page.select(".pagination li:not(.disabled) a[href]"):
            target = urlsplit(link["href"])
            if target.path.rstrip("/") != path:
                continue
            value = parse_qs(target.query).get("page", [""])[0]
            if value.isdecimal() and int(value) > number:
                later_pages.add(int(value))
        if not later_pages:
            return True
        # Verifying a format requires every advertised page in order.
        number += 1
        if number not in later_pages:
            raise ValueError(f"Missing result page {number} in pagination for {path}")


def singles_match_ids(page):
    match_ids = set()
    for day in page.select(".matches-box > *"):
        rows = day.select("a[href] > .match-box")
        if not rows:
            continue
        date = day.select_one(".divs-header")
        if date is None:
            return None
        try:
            datetime.strptime(date.get_text(" ", strip=True).replace(".", ""), "%d %b %Y")
        except ValueError:
            return None
        for row in rows:
            identity = row.parent["href"].rstrip("/")
            sides = row.select(":scope > .col-3")
            score = row.select_one(":scope > .col-2 .center")
            if (not MATCH_LINK.fullmatch(identity) or not score
                    or not re.fullmatch(r"[0-9]+\s*-\s*[0-9]+", score.get_text(strip=True))
                    or len(sides) != 2 or any(not single_player(side) for side in sides)):
                return None
            match_ids.add(identity)
    return match_ids if len(match_ids) == len(page.select(".matches-box .match-box")) else None


def single_player(side):
    names = [player for player in side.find_all(recursive=False) if player.get_text(strip=True)]
    return len(names) == 1


def singles_tournament(source, event_id, event):
    name, index_fields = event
    if TEAM_NAME.search(name):
        return None
    detail = fetch(source, f"/events/{event_id}")
    if detail is None or detail.select_one(f"{TEAM_MARKUP}, .winners-div .rank-duo-box"):
        return None
    header = detail.select_one(".tournament-head-info")
    if header is None or not individual_entrants(detail):
        return None
    title = header.select_one(".tournament-title")
    if title is None or TEAM_NAME.search(title.get_text(" ", strip=True)):
        return None
    event_fields = fields(header)
    if event_fields.get("Status", "Finished").casefold() != "finished":
        return None
    start = published_day(event_fields.get("Başlama tarixi", ""))
    end = published_day(event_fields.get("Bitmə tarixi", ""))
    if not start or not end or start > end or not published_singles_results(source, event_id):
        return None
    tournament = {"id": event_id, "name": name, "format": "singles", "start": start, "end": end}
    for count in (event_fields.get("Oyunçular", ""), index_fields.get("Oyunçu sayı", "")):
        if re.fullmatch(r"[0-9]+", count):
            tournament["players"] = int(count)
            break
    for prize in (event_fields.get("Priz", ""), index_fields.get("Priz", "")):
        if re.fullmatch(r"[0-9]+(?:[.,][0-9]+)?\s+AZN", prize):
            tournament["prize"] = prize
            break
    return tournament


def build_catalogue(source):
    events = published_events(source)
    with ThreadPoolExecutor(max_workers=4) as fetchers:
        tournaments = list(fetchers.map(lambda item: singles_tournament(source, *item), events.items()))
    singles = [tournament for tournament in tournaments if tournament is not None]
    if not singles:
        raise ValueError("No verified singles tournaments in the Results index")
    singles.sort(key=lambda tournament: tournament["end"], reverse=True)
    print(f"Verified {len(singles)} singles tournaments from {len(events)} completed event entries")
    return {"version": 1, "tournaments": singles}


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
