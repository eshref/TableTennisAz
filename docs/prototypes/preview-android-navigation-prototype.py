"""THROWAWAY preview server for android-navigation-prototype.html.

Run: ~/.codex/bin/skill-python docs/prototypes/preview-android-navigation-prototype.py
Optional: the HTML can also be opened directly without this server.
Open the printed URL. No third-party dependencies or persistent application state.
"""

import argparse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path


PROTOTYPE = Path(__file__).with_name("android-navigation-prototype.html")

class Preview(BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path.split("?", 1)[0] != "/":
            self.send_error(404)
            return
        output = PROTOTYPE.read_bytes()
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(output)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(output)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=8765)
    args = parser.parse_args()
    server = ThreadingHTTPServer(("127.0.0.1", args.port), Preview)
    print(f"Throwaway Android navigation preview: http://127.0.0.1:{server.server_port}/?variant=bottom", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
