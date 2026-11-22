"""Build the Pages singles index from the public website."""

import argparse
import json
import re
from collections import defaultdict
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass
from datetime import datetime, timezone
from functools import lru_cache
from pathlib import Path
from threading import local
from urllib.parse import parse_qs, urlsplit

from lxml import html
from lxml.cssselect import CSSSelector
from lxml.etree import ParserError
from requests import Session
from requests.adapters import HTTPAdapter
from requests.exceptions import RequestException
from urllib3.util.retry import Retry


BUCKETS = 256
PLAYER_LINK = re.compile(r"/players/([1-9][0-9]*)/?")
EVENT_LINK = re.compile(r"/matches/results/event/([1-9][0-9]*)/?")
MATCH_LINK = re.compile(r"/matches/([1-9][0-9]*)/?")
RESULT_ROWS = ".match-box, .team-matches-match"


def select(node, selector):
    return compiled_selector(selector)(node)


@lru_cache
def compiled_selector(selector):
    return CSSSelector(selector)


def content(node):
    return " ".join(node.text_content().split())


def linked_id(link, pattern):
    match = pattern.fullmatch(link or "")
    if match is None:
        raise ValueError(f"Unexpected source link {link!r}")
    return int(match[1])


class MissingPage(ValueError):
    pass


class Website:
    def __init__(self, source):
        self.source = source.rstrip("/")
        self.connection = local()

    def page(self, path):
        if not hasattr(self.connection, "session"):
            session = Session()
            session.headers.update({"User-Agent": "TableTennisAz daily singles index",
                                    "Accept-Language": "az-AZ,az;q=0.9"})
            session.mount(self.source + "/", HTTPAdapter(max_retries=Retry(
                total=3, backoff_factor=1, status_forcelist=(408, 429, 500, 502, 503, 504))))
            self.connection.session = session
        try:
            response = self.connection.session.get(self.source + path, timeout=(10, 25))
            if response.status_code in (404, 410):
                raise MissingPage(f"{path}: HTTP {response.status_code}")
            response.raise_for_status()
            document = html.fromstring(response.content, parser=html.HTMLParser(encoding="utf-8"))
        except (RequestException, ParserError) as error:
            raise ValueError(f"{path}: {error}") from error
        # Removed profiles serve the website's standard not-found page with HTTP 200.
        if select(document, ".not-found-page .page-not-found-div"):
            raise MissingPage(f"{path}: source page not found")
        center = select(document, "#center")
        if len(center) != 1:
            raise ValueError(f"{path}: missing page content")
        return center[0]

    def pages(self, path, unlisted_profile=0):
        # The last-page link can advertise pages outside the current viewport.
        separator = "&" if "?" in path else "?"
        base = urlsplit(path)
        filters = parse_qs(base.query)
        last = number = 1
        while number <= last:
            current = path if number == 1 else f"{path}{separator}page={number}"
            try:
                page = self.page(current)
            except MissingPage:
                if number == 1 and unlisted_profile and self.profile_removed(unlisted_profile):
                    return
                raise
            for link in select(page, ".pagination li:not(.disabled) a[href]"):
                target = urlsplit(link.get("href"))
                if target.path.rstrip("/") == base.path.rstrip("/"):
                    query = parse_qs(target.query)
                    value = query.pop("page", [""])[0]
                    if query == filters and value.isdecimal():
                        last = max(last, int(value))
            yield current, page
            number += 1

    def profile_removed(self, identity):
        try:
            self.page(f"/players/{identity}")
        except MissingPage:
            return True
        return False

    def players(self, level):
        ids = set()
        for path, page in self.pages(f"/players?level={level}"):
            listing = select(page, ".ranking-page-list")
            if not listing:
                if select(page, ".no-info"):
                    continue
                raise ValueError(f"{path}: missing player directory")
            ids.update(linked_id(link.get("href"), PLAYER_LINK)
                       for link in select(page, ".ranking-page-list > a"))
        return ids

    def events(self):
        events = {}
        for path, page in self.pages("/matches/results"):
            links = select(page, ".tournament-list a:has(.tournament-title)")
            if not links:
                raise ValueError(f"{path}: missing Results index")
            for link in links:
                name = content(select(link, ".tournament-title")[0])
                if not name:
                    raise ValueError(f"{path}: missing tournament title")
                events[linked_id(link.get("href"), EVENT_LINK)] = name
        return events

    def result_rows(self, feed):
        rows = {}
        seen_pages = set()
        for path, page in self.pages(feed.path, feed.unlisted_profile):
            boxes = select(page, ".matches-box")
            if not boxes:
                raise ValueError(f"{path}: missing result list")
            published = []
            for box in boxes:
                rows_in_box = select(box, RESULT_ROWS)
                if not rows_in_box and content(box) and not select(box, ".no-info, .team-match-box"):
                    raise ValueError(f"{path}: unrecognized result list")
                published.extend(rows_in_box)
            if not published and path != feed.path and not select(page, ".team-match-box"):
                raise ValueError(f"{path}: advertised result page has no rows")
            identities = tuple(linked_id(row.getparent().get("href"), MATCH_LINK) for row in published)
            if identities and identities in seen_pages:
                raise ValueError(f"{path}: repeats results from another page")
            seen_pages.add(identities)
            for match_id, row in zip(identities, published):
                team = "team-matches-match" in row.classes
                sides = select(row, ":scope > .col-5" if team else ":scope > .col-3")
                sizes = [sum(bool(content(child)) for child in side) for side in sides]
                if sizes == [2, 2]:
                    continue
                if sizes != [1, 1]:
                    raise ValueError(f"{path}: unrecognized sides of match {match_id}")
                event = select(row, ":scope > .col-4")
                stage = select(row, ".match-box-stage")
                rows[match_id] = Context(feed.event_id, feed.event or (content(event[0]) if event else ""),
                                         feed.duel, content(stage[0]) if stage else "")
        return rows

    def match(self, identity):
        path = f"/matches/{identity}"
        page = self.page(path)
        boxes = select(page, ".match-players-box")
        if len(boxes) != 1:
            raise ValueError(f"{path}: missing match details")
        box = boxes[0]
        players = []
        for side in select(box, ".match-player-box"):
            names = select(side, ".match-player-name")
            links = list(names[0].iterancestors("a")) if len(names) == 1 else []
            if len(links) != 1 or not content(names[0]):
                raise ValueError(f"{path}: missing singles participant profile")
            players.append({"id": linked_id(links[0].get("href"), PLAYER_LINK), "name": content(names[0])})
        scores = [content(score) for score in select(box, ".match-player-score")]
        if (len(players) != 2 or players[0]["id"] == players[1]["id"] or len(scores) != 2
                or not all(re.fullmatch(r"[0-9]+", score) for score in scores)):
            raise ValueError(f"{path}: unusable singles match details")
        date = select(box, ".match-date")
        if len(date) != 1:
            raise ValueError(f"{path}: missing published date")
        day = content(date[0])
        try:
            datetime.strptime(day.replace(".", ""), "%d %b %Y")
        except ValueError as error:
            raise ValueError(f"{path}: invalid published date {day!r}") from error
        event = select(box, ".match-tournament")
        return {"id": identity, "left": players[0], "right": players[1], "date": day,
                "score": "-".join(scores), "event": content(event[0]) if event else ""}


@dataclass(frozen=True)
class Feed:
    path: str
    event_id: int = 0
    event: str = ""
    duel: bool = False
    unlisted_profile: int = 0


@dataclass(frozen=True)
class Context:
    event_id: int
    event: str
    duel: bool
    stage: str

    def combine(self, other):
        preferred = self if self.event_id or not other.event_id else other
        return Context(preferred.event_id, preferred.event or self.event or other.event,
                       self.duel or other.duel, preferred.stage or self.stage or other.stage)


def build(website, workers):
    contexts = {}
    matches = {}
    with ThreadPoolExecutor(max_workers=workers) as pool:
        ranked = set().union(*pool.map(website.players, ("amateur", "pro")))
        if not ranked:
            raise ValueError("The source player directory is empty")
        players = set(ranked)
        events = website.events()
        feeds = [Feed("/matches/duellos", event="Duello", duel=True)]
        feeds.extend(Feed(f"/matches/results/event/{identity}", identity, name) for identity, name in events.items())
        checked_players = set()
        while feeds or players - checked_players:
            new_players = players - checked_players
            checked_players.update(new_players)
            feeds.extend(Feed(f"/matches/results/player/{identity}",
                              unlisted_profile=identity if identity not in ranked else 0)
                         for identity in sorted(new_players))
            for results in pool.map(website.result_rows, feeds):
                for identity, context in results.items():
                    previous = contexts.get(identity)
                    contexts[identity] = context if previous is None else previous.combine(context)
            pending = sorted(contexts.keys() - matches.keys())
            print(f"Read {len(feeds)} result feeds. Verifying {len(pending)} singles matches.", flush=True)
            for verified, match in enumerate(pool.map(website.match, pending), start=1):
                matches[match["id"]] = match
                players.update((match["left"]["id"], match["right"]["id"]))
                if verified % 5000 == 0:
                    print(f"Verified {verified} of {len(pending)} singles matches in this batch.", flush=True)
            feeds = []

    buckets = defaultdict(list)
    for identity, match in sorted(matches.items()):
        context = contexts[identity]
        match["event"] = match["event"] or context.event
        if not match["event"]:
            raise ValueError(f"/matches/{identity}: missing match context")
        match["eventId"] = context.event_id
        match["duel"] = context.duel or match["event"].casefold() == "duello"
        if context.stage and not match["duel"]:
            match["stage"] = context.stage
        bucket = min(match["left"]["id"], match["right"]["id"]) % BUCKETS
        buckets[bucket].append(match)
    print(f"Verified {len(matches)} singles matches across {len(checked_players)} player histories.", flush=True)
    return buckets


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", type=Path)
    parser.add_argument("--source", default="https://tabletennis.az")
    parser.add_argument("--workers", type=int, default=10)
    args = parser.parse_args()
    buckets = build(Website(args.source), args.workers)
    refreshed = datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
    # The Pages workflow deploys the entire staging tree as one publication.
    args.output.mkdir(parents=True, exist_ok=True)
    for bucket in range(BUCKETS):
        payload = {"schema": 1, "bucket": bucket, "refreshedAt": refreshed, "matches": buckets[bucket]}
        (args.output / f"{bucket}.json").write_text(
            json.dumps(payload, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
