"""Send one Firebase test alert for an event in the newly deployed Pages catalogue."""
import argparse
import json
import os
from pathlib import Path
import re
import sys
from time import time_ns

from publish_alerts import WINDOW_MS, read_deployment, send_message


def send_test_alert(arguments, token):
    previous = json.loads(arguments.previous.read_bytes())
    if any(entry["id"] == arguments.event_id for entry in previous["tournaments"]):
        raise ValueError(f"Event {arguments.event_id} was already in the live catalogue before this deployment")
    catalogue, _ = read_deployment(arguments.catalogue, arguments.sha256, arguments.wait)
    tournament = next((entry for entry in catalogue["tournaments"]
                       if entry["id"] == arguments.event_id), None)
    if tournament is None or tournament["format"] not in ("singles", "doubles"):
        raise ValueError(f"Event {arguments.event_id} is missing from the verified singles and doubles catalogue")
    published_at = time_ns() // 1_000_000
    send_message(arguments.fcm_origin, arguments.project, token,
                 {"eventId": tournament["id"], "name": tournament["name"], "publishedAt": published_at},
                 WINDOW_MS // 1000)
    print(f"Firebase accepted test alert for event {arguments.event_id} at {published_at}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("event_id", type=int)
    parser.add_argument("--catalogue", required=True)
    parser.add_argument("--previous", type=Path, required=True)
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
