#!/usr/bin/env python3
"""A Nexus, as much of one as the end-to-end suite needs.

The client the backend uses talks to ``https://<host>/service/rest/v1/...`` with the scheme fixed,
so this serves TLS on 443 under a name and certificate the backend is given to trust. Three
endpoints are enough for a download: find the assets of an artifact, read one of them, and fetch its
bytes.

The artifact itself is a real jar, because the proxy rewrites strings inside the archive on the way
out and anything that is not a zip would take a different path through it.
"""
import io
import json
import re
import ssl
import zipfile
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

HOST_NAME = "nexus-e2e"
REPOSITORY = "releases"
GROUP_ID = "de.chojo"
ARTIFACT_ID = "e2e-plugin"

# Two versions, so "newest first" and the 25-version limit have something to order and cut.
VERSIONS = [
    ("1.1.0", "2026-02-01T12:00:00Z"),
    ("1.0.0", "2026-01-01T12:00:00Z"),
]


with open("/stub/UserData.class", "rb") as fixture:
    USER_DATA_CLASS = fixture.read()


def jar_bytes(version):
    """A jar the proxy can really work on.

    The class is the fixture the unit tests use: the proxy rewrites string constants in the constant
    pool of ``.class`` entries and nothing else, so a jar of plain text files would take the
    fallback path and prove nothing about the rewrite.
    """
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n")
        archive.writestr("plugin.yml", f"name: e2e-plugin\nversion: {version}\n")
        archive.writestr("de/chojo/lyna/UserData.class", USER_DATA_CLASS)
    return buffer.getvalue()


ASSETS = {}
for version, published in VERSIONS:
    asset_id = f"asset-{version}"
    payload = jar_bytes(version)
    ASSETS[asset_id] = {
        "downloadUrl": f"https://{HOST_NAME}/repository/{REPOSITORY}/"
                       f"{GROUP_ID.replace('.', '/')}/{ARTIFACT_ID}/{version}/{ARTIFACT_ID}-{version}.jar",
        "path": f"{GROUP_ID.replace('.', '/')}/{ARTIFACT_ID}/{version}/{ARTIFACT_ID}-{version}.jar",
        "id": asset_id,
        "repository": REPOSITORY,
        "format": "maven2",
        "contentType": "application/java-archive",
        "lastModified": published,
        "lastDownloaded": None,
        "checksum": {"sha1": "0" * 40, "md5": "0" * 32},
        "uploader": "e2e",
        "uploaderIp": "127.0.0.1",
        "fileSize": len(payload),
        "maven2": {
            "extension": "jar",
            "groupId": GROUP_ID,
            "classifier": None,
            "artifactId": ARTIFACT_ID,
            "version": version,
            "baseVersion": version,
        },
        "_bytes": payload,
    }


def without_bytes(asset):
    return {key: value for key, value in asset.items() if key != "_bytes"}


class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        print(f"{self.address_string()} {fmt % args}", flush=True)

    def _json(self, payload, status=200):
        body = json.dumps(payload).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        url = urlparse(self.path)
        query = parse_qs(url.query)

        if url.path == "/service/rest/v1/search/assets":
            wanted = query.get("maven.baseVersion", [None])[0]
            items = [without_bytes(asset) for asset in ASSETS.values()
                     if wanted is None or asset["maven2"]["version"] == wanted]
            # The client sorts by version descending; answering in that order keeps it honest.
            items.sort(key=lambda item: item["maven2"]["version"], reverse=True)
            self._json({"items": items, "continuationToken": None})
            return

        asset_match = re.fullmatch(r"/service/rest/v1/assets/([^/]+)", url.path)
        if asset_match:
            asset = ASSETS.get(asset_match.group(1))
            if asset is None:
                self._json({"message": "no such asset"}, status=404)
                return
            self._json(without_bytes(asset))
            return

        download_match = re.fullmatch(r"/repository/[^/]+/.*/([^/]+)-([^/]+)\.jar", url.path)
        if download_match:
            asset = ASSETS.get(f"asset-{download_match.group(2)}")
            if asset is None:
                self._json({"message": "no such version"}, status=404)
                return
            payload = asset["_bytes"]
            self.send_response(200)
            self.send_header("Content-Type", "application/java-archive")
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)
            return

        self._json({"message": f"unhandled {url.path}"}, status=404)


def main():
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    context.load_cert_chain("/tls/server.crt", "/tls/server.key")
    server = ThreadingHTTPServer(("0.0.0.0", 443), Handler)
    server.socket = context.wrap_socket(server.socket, server_side=True)
    print(f"Nexus stub listening on https://{HOST_NAME}/", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
