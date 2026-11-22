"""Send one Firebase test alert for an event in the newly deployed Pages catalogue."""
import argparse
from hashlib import sha256
import json
import os
import re
import sys
from time import monotonic, sleep, time_ns
from urllib.error import URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

TEST_TOPIC = "tournament-results-test"


def read_deployment(url, expected_digest, wait):
    deadline = monotonic() + wait
    attempt = 0
    while True:
        attempt += 1
        cache_key = urlencode({"deployment": expected_digest, "attempt": attempt})
        request = Request(f"{url}?{cache_key}", headers={"Cache-Control": "no-cache"})
        try:
            with urlopen(request, timeout=15) as response:
                body = response.read()
            if sha256(body).hexdigest() == expected_digest:
                return json.loads(body)
        except (URLError, TimeoutError):
            pass
        if monotonic() >= deadline:
            raise ValueError("The deployed catalogue does not match this run's build")
        sleep(min(5, max(0, deadline - monotonic())))


def send_test_alert(arguments, token):
    catalogue = read_deployment(arguments.catalogue, arguments.sha256, arguments.wait)
    tournament = next((entry for entry in catalogue["tournaments"]
                       if entry["id"] == arguments.event_id), None)
    if tournament is None or tournament["format"] not in ("singles", "doubles"):
        raise ValueError(f"Event {arguments.event_id} is missing from the verified singles and doubles catalogue")
    published_at = time_ns() // 1_000_000
    payload = {"message": {
        "topic": TEST_TOPIC,
        "data": {"eventId": str(tournament["id"]), "name": tournament["name"], "publishedAt": str(published_at)},
        "android": {"priority": "high", "ttl": "86400s"},
    }}
    request = Request(
        f"{arguments.fcm_origin}/v1/projects/{arguments.project}/messages:send",
        data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json; charset=utf-8"},
    )
    with urlopen(request, timeout=30) as response:
        if not json.load(response).get("name"):
            raise ValueError("Firebase did not return a message name")
    print(f"Firebase accepted test alert for event {arguments.event_id} at {published_at}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("event_id", type=int)
    parser.add_argument("--catalogue", required=True)
    parser.add_argument("--sha256", required=True)
    parser.add_argument("--project", required=True)
    parser.add_argument("--fcm-origin", default="https://fcm.googleapis.com")
    parser.add_argument("--wait", type=int, default=120)
    arguments = parser.parse_args()
    if arguments.event_id <= 0:
        parser.error("event_id must be positive")
    if not re.fullmatch(r"[0-9a-f]{64}", arguments.sha256):
        parser.error("--sha256 must be a SHA-256 digest")
    if not re.fullmatch(r"[a-z0-9-]+", arguments.project):
        parser.error("--project must be a Firebase project ID")
    token = os.environ.get("FCM_ACCESS_TOKEN")
    if not token:
        parser.error("FCM_ACCESS_TOKEN is required")
    try:
        send_test_alert(arguments, token)
    except (OSError, ValueError, KeyError, TypeError) as error:
        sys.exit(f"Test alert failed: {error}")


if __name__ == "__main__":
    main()
