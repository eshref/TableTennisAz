"""Verify a real Firebase alert on the GitHub Actions Google APIs emulator."""
from functools import partial
from hashlib import sha256
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
import os
from pathlib import Path
import re
import subprocess
import sys
from tempfile import TemporaryDirectory
from threading import Event, Thread
from time import monotonic, sleep, time_ns
from xml.etree import ElementTree

from generate import SITE, publish, published_events, verified_tournament
from publish_alerts import PUBLICATIONS

PACKAGE = "az.tabletennis"
EVENT_ID = 944
TOURNAMENT = "BTTC Şəxsi Reytinq Turniri"
RESULT = "Faiq Bağırov — Etibar Yunusov · 0-3"
PORT = 8765
CATALOGUE = "tournaments-v1.json"


def adb(*args):
    return subprocess.check_output(["adb", *args], timeout=45)


def screen():
    try:
        dump = adb("exec-out", "uiautomator", "dump", "/dev/tty")
        xml, end, _ = dump.partition(b"</hierarchy>")
        if end:
            return ElementTree.fromstring(xml + end)
    except (subprocess.SubprocessError, ElementTree.ParseError):
        pass
    return ElementTree.Element("hierarchy")


def await_view(description, find, timeout=60):
    deadline = monotonic() + timeout
    while monotonic() < deadline:
        view = find(list(screen().iter("node")))
        if view is not None:
            return view
        sleep(1)
    raise RuntimeError(f"Android did not display {description}")


def by_id(nodes, resource_id, **attributes):
    return next((node for node in nodes if node.get("resource-id", "").endswith(f":id/{resource_id}")
                 and all(node.get(name) == value for name, value in attributes.items())), None)


def tap(view):
    left, top, right, bottom = map(int, re.findall(r"\d+", view.get("bounds")))
    adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))


def capture(path):
    png = adb("exec-out", "screencap", "-p")
    if not png.startswith(b"\x89PNG\r\n\x1a\n"):
        raise RuntimeError(f"Android did not capture {path.name}")
    path.write_bytes(png)


def save_failure_evidence(evidence):
    capture(evidence / "failure.png")
    ElementTree.ElementTree(screen()).write(evidence / "failure-ui.xml", encoding="utf-8")
    logs = adb("logcat", "-d", "-v", "raw", "-s", "FirebaseMessaging:W", "Firebase-Installations:W").decode()
    # Record known error messages without retaining raw client logs.
    errors = set(re.findall(
        r"\b(?:SERVICE_NOT_AVAILABLE|MISSING_INSTANCEID_SERVICE|FIS_AUTH_ERROR|BAD_CONFIG|"
        r"API_KEY_SERVICE_BLOCKED|API_KEY_ANDROID_APP_BLOCKED|AUTHENTICATION_FAILED|INVALID_SENDER|"
        r"MISMATCH_SENDER_ID|INVALID_PARAMETERS|INVALID_ARGUMENT|INTERNAL_SERVER_ERROR|"
        r"PHONE_REGISTRATION_ERROR|TOO_MANY_REGISTRATIONS|TOO_MANY_SUBSCRIBERS|TOO_MANY_TOPICS|"
        r"TOO_MANY_REQUESTS|PERMISSION_DENIED|DEVELOPER_ERROR|RST|TIMEOUT|"
        r"UnknownHostException|SocketTimeoutException|SSLHandshakeException|"
        r"Google Play services missing or without correct permission|Failed to sync topics)\b", logs))
    if errors:
        print(f"Firebase client errors: {', '.join(sorted(errors))}", flush=True)


def opt_in_and_return_home():
    adb("install", "app/build/outputs/apk/debug/app-debug.apk")
    adb("shell", "am", "start", "-W", "-n", f"{PACKAGE}/.RankingsActivity")
    for resource in ("open_menu", "tournament_alerts", "tournament_alerts_switch"):
        tap(await_view(resource, lambda nodes: by_id(nodes, resource, enabled="true")))
    tap(await_view("the notification permission prompt", lambda nodes: by_id(nodes, "permission_allow_button")))
    await_view("the enabled tournament alerts switch",
               lambda nodes: by_id(nodes, "tournament_alerts_switch", checked="true", enabled="true"), timeout=180)
    adb("shell", "input", "keyevent", "KEYCODE_HOME")
    await_view("the emulator launcher", lambda nodes: next(
        (node for node in nodes if "launcher" in node.get("package", "")), None))
    adb("shell", "am", "kill", PACKAGE)


def publish_and_send(path, tournament):
    # The HTTP Last-Modified header has one-second precision. Publish after the opt-in.
    sleep(2)
    publish(path, {
        "version": 1, "tournaments": [tournament], "firstListed": {str(EVENT_ID): time_ns() // 1_000_000},
        PUBLICATIONS: {str(EVENT_ID): {"name": tournament["name"], "publishedAt": None}},
    })
    subprocess.run([
        sys.executable, str(Path(__file__).with_name("publish_alerts.py")),
        "--catalogue", f"http://127.0.0.1:{PORT}/{CATALOGUE}", "--sha256", sha256(path.read_bytes()).hexdigest(),
        "--project", os.environ["FIREBASE_PROJECT"], "--test-topic",
        "--receipts", str(path.with_name("previous-receipts.json")),
        "--accepted", str(path.with_name("accepted-receipts.json")),
    ], check=True, timeout=180)


def verify_notification_and_results(evidence):
    def notification(nodes):
        if not any(node.get("package") == "com.android.systemui" and node.get("text") == "Matç nəticələrinə bax"
                   for node in nodes):
            return None
        return next((node for node in nodes if node.get("package") == "com.android.systemui"
                     and node.get("text") == TOURNAMENT), None)

    adb("shell", "cmd", "statusbar", "expand-notifications")
    title = await_view("the tournament notification in the Android notification shade", notification, timeout=180)
    capture(evidence / "notification.png")
    tap(title)

    def results(nodes):
        app_nodes = [node for node in nodes if node.get("package") == PACKAGE]
        if any(node.get("text") == TOURNAMENT for node in app_nodes):
            return next((node for node in app_nodes if RESULT in node.get("content-desc", "")), None)
        return None

    await_view("the selected tournament heading and its recorded match score", results, timeout=120)
    capture(evidence / "match-results.png")


def main():
    evidence = Path(os.environ["FIREBASE_LIVE_EVIDENCE"])
    evidence.mkdir(parents=True, exist_ok=True)
    event = published_events(SITE).get(EVENT_ID)
    verified = verified_tournament(SITE, EVENT_ID, event) if event is not None else None
    tournament, _ = verified if verified is not None else (None, None)
    if tournament is None or tournament["name"] != TOURNAMENT or tournament["format"] != "singles":
        raise RuntimeError(f"Completed singles tournament {EVENT_ID} is no longer available for verification")

    with TemporaryDirectory() as directory:
        catalogue = Path(directory) / CATALOGUE
        publish(catalogue, {"version": 1, "tournaments": [], "firstListed": {}, PUBLICATIONS: {}})
        device_read = Event()

        class CatalogueHandler(SimpleHTTPRequestHandler):
            def copyfile(self, source, output):
                super().copyfile(source, output)
                if self.path == f"/{CATALOGUE}":
                    device_read.set()

        handler = partial(CatalogueHandler, directory=directory)
        with ThreadingHTTPServer(("127.0.0.1", PORT), handler) as server:
            thread = Thread(target=server.serve_forever, daemon=True)
            thread.start()
            try:
                adb("reverse", f"tcp:{PORT}", f"tcp:{PORT}")
                print("Opting in from Android and closing the app to the launcher", flush=True)
                opt_in_and_return_home()
                if not device_read.wait(15):
                    raise RuntimeError("The emulator did not load the isolated baseline catalogue")
                print(f"Publishing event {EVENT_ID} to the isolated catalogue and sending through Firebase", flush=True)
                publish_and_send(catalogue, tournament)
                verify_notification_and_results(evidence)
                print("Android displayed the notification and opened the expected match results", flush=True)
            except Exception:
                try:
                    save_failure_evidence(evidence)
                except (OSError, RuntimeError, subprocess.SubprocessError) as error:
                    print(f"Unable to collect all device diagnostics: {error}", file=sys.stderr)
                raise
            finally:
                server.shutdown()
                thread.join()


if __name__ == "__main__":
    main()
