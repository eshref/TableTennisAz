"""Send pending tournament alerts from the live GitHub Pages catalogue."""
import argparse
from email.utils import parsedate_to_datetime
from hashlib import sha256
import json
import os
from pathlib import Path
import re
import sys
from time import monotonic, sleep, time_ns
from urllib.error import URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

TOPIC = "tournament-results-test"
WINDOW_MS = 24 * 60 * 60 * 1000
PUBLICATIONS = "alertPublications"


def read_deployment(url, expected_digest, wait):
    deadline = monotonic() + wait
    check = time_ns()
    attempt = 0
    while True:
        attempt += 1
        cache_key = urlencode({"check": check, "attempt": attempt})
        request = Request(f"{url}?{cache_key}", headers={"Cache-Control": "no-cache"})
        try:
            with urlopen(request, timeout=15) as response:
                body = response.read()
                modified = response.headers.get("Last-Modified")
            if expected_digest is None or sha256(body).hexdigest() == expected_digest:
                return json.loads(body), modified
        except (URLError, TimeoutError):
            pass
        if monotonic() >= deadline:
            if expected_digest is not None:
                raise ValueError("The deployed catalogue does not match this run's build")
            raise ValueError("The live catalogue could not be read")
        sleep(min(5, max(0, deadline - monotonic())))


def publication_history(live, last_modified):
    if PUBLICATIONS not in live:
        # These IDs were live before alerts were tracked and cannot become eligible.
        return {str(entry["id"]): None for entry in live["tournaments"]}

    history = live[PUBLICATIONS].copy()
    for event_id, publication in history.items():
        if publication is not None and publication["publishedAt"] is None:
            # The ID and name travelled with the deployment, so another run can
            # recover the publication time even if this deployment's sender stopped.
            if not last_modified:
                raise ValueError("The live catalogue has no Last-Modified publication time")
            deployment_time = int(parsedate_to_datetime(last_modified).timestamp()) * 1000
            history[event_id] = {**publication, "publishedAt": deployment_time}
    return history


def send_message(origin, project, token, data, ttl):
    payload = {"message": {
        "topic": TOPIC,
        "data": {"eventId": str(data["eventId"]), "name": data["name"], "publishedAt": str(data["publishedAt"])},
        "android": {"priority": "high", "ttl": f"{ttl}s"},
    }}
    request = Request(
        f"{origin}/v1/projects/{project}/messages:send",
        data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json; charset=utf-8"},
    )
    with urlopen(request, timeout=30) as response:
        result = json.load(response)
        if not isinstance(result, dict) or not result.get("name"):
            raise ValueError("Firebase did not return a message name")


def publish_alerts(arguments, token):
    live, modified = read_deployment(arguments.catalogue, arguments.sha256, arguments.wait)
    history = publication_history(live, modified)
    stored = json.loads(arguments.receipts.read_bytes()) if arguments.receipts.exists() else {}
    now = time_ns() // 1_000_000
    receipts = {event_id: expires for event_id, expires in stored.items() if expires > now}
    failures = []
    for event_id, publication in history.items():
        if publication is None or event_id in receipts:
            continue
        expires = publication["publishedAt"] + WINDOW_MS
        remaining = expires - time_ns() // 1_000_000
        if remaining <= 0:
            continue
        try:
            send_message(arguments.fcm_origin, arguments.project, token,
                         {**publication, "eventId": event_id}, min(remaining, WINDOW_MS) // 1000)
        except (OSError, ValueError) as error:
            failures.append(event_id)
            print(f"Firebase alert for event {event_id} is pending: {error}", file=sys.stderr)
            continue
        receipts[event_id] = expires
        snapshot = arguments.accepted.with_suffix(".tmp")
        snapshot.write_text(json.dumps(receipts), encoding="utf-8")
        snapshot.replace(arguments.accepted)
        print(f"Firebase accepted alert for event {event_id}")
    if failures:
        raise ValueError(f"Firebase did not accept {len(failures)} alert(s)")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--catalogue", required=True)
    parser.add_argument("--sha256")
    parser.add_argument("--receipts", type=Path, required=True)
    parser.add_argument("--accepted", type=Path, required=True)
    parser.add_argument("--project", required=True)
    parser.add_argument("--fcm-origin", default="https://fcm.googleapis.com")
    parser.add_argument("--wait", type=int, default=120)
    arguments = parser.parse_args()
    if arguments.sha256 is not None and not re.fullmatch(r"[0-9a-f]{64}", arguments.sha256):
        parser.error("--sha256 must be a SHA-256 digest")
    if not re.fullmatch(r"[a-z0-9-]+", arguments.project):
        parser.error("--project must be a Firebase project ID")
    token = os.environ.get("FCM_ACCESS_TOKEN")
    if not token:
        parser.error("FCM_ACCESS_TOKEN is required")
    try:
        publish_alerts(arguments, token)
    except (OSError, ValueError, KeyError, TypeError) as error:
        sys.exit(f"Tournament alerts failed: {error}")


if __name__ == "__main__":
    main()
