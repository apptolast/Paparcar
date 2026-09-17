#!/usr/bin/env python3
"""Local preview of the built site, with Firebase Hosting's `cleanUrls` behaviour.

`firebase.json` sets `"cleanUrls": true`, so in production `/terms` serves `terms.html`. A plain
static server does not do that, and previewing without it hides exactly the class of bug the
footer links are made of.

    python web/tools/serve.py [port]
"""
import sys
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2] / "hosting" / "public"


class CleanUrlHandler(SimpleHTTPRequestHandler):
    def translate_path(self, path: str) -> str:
        local = Path(super().translate_path(path))
        if not local.exists() and not local.suffix:
            html = local.with_suffix(".html")
            if html.is_file():
                return str(html)
        return str(local)

    def end_headers(self) -> None:
        self.send_header("Cache-Control", "no-store")
        super().end_headers()

    def log_message(self, fmt, *args):  # quieter output
        if "404" in (fmt % args):
            super().log_message(fmt, *args)


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 5173
    handler = partial(CleanUrlHandler, directory=str(ROOT))
    # Plain ASCII on purpose: the Windows console defaults to cp1252 and a nicer arrow here
    # crashes the server before it ever binds.
    print(f"paparcar.com preview -> http://localhost:{port}  (serving {ROOT})")
    # ⛔ Threading, not plain HTTPServer: a headless Chrome holding one keep-alive connection blocks
    # a single-threaded server completely — every later request, from any client, just hangs. It
    # looks like the capture tool is broken and it is the server that is stuck.
    ThreadingHTTPServer(("127.0.0.1", port), handler).serve_forever()
