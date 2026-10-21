#!/usr/bin/env python3
"""A Nexus, as much of one as the end-to-end suite needs, and GitHub's README endpoint.

The client the backend uses talks to ``https://<host>/service/rest/v1/...`` with the scheme fixed,
so this serves TLS on 443 under a name and certificate the backend is given to trust. Three
endpoints are enough for a download: find the assets of an artifact, read one of them, and fetch its
bytes.

The artifact itself is a real jar, because the proxy rewrites strings inside the archive on the way
out and anything that is not a zip would take a different path through it.
"""
import base64
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


# What GitHub would answer as the README of the stories' repository, with a relative image and link
# for the backend to make absolute.
README = """# E2E Plugin from GitHub

Fetched from the **repository**, not written in Lyna.

![logo](assets/logo.png)

See [the setup guide](docs/setup.md).

[Download the latest release](https://github.com/e2e-owner/e2e-plugin/releases/latest)
"""

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


def discord_user(access_token):
    """The person a stand-in Discord access token belongs to.

    Tokens are minted as ``access-<id>`` from a code of ``user-<id>``, so a story picks who signs in
    by the code it hands the callback, and every story can be somebody new.
    """
    match = re.fullmatch(r"access-(\d+)(?:-\d+)?", access_token or "")
    if match is None:
        return None
    user_id = match.group(1)
    return {"id": user_id, "username": f"stub-{user_id}", "global_name": None, "email": None, "verified": False}


def discord_grant(user_id, generation):
    """A grant the way Discord's token endpoint answers it."""
    return {
        "access_token": f"access-{user_id}-{generation}",
        "refresh_token": f"refresh-{user_id}-{generation}",
        "token_type": "Bearer",
        "scope": "identify email",
        "expires_in": 604800,
    }


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

    def _readme(self, owner, repository):
        """GitHub's README endpoint, for the one repository the stories name. Anything else is
        unknown, the way GitHub answers for a repository without a README."""
        if (owner, repository) != ("e2e-owner", "e2e-plugin"):
            self._json({"message": "Not Found"}, status=404)
            return
        self._json({
            "path": "README.md",
            "encoding": "base64",
            "content": base64.b64encode(README.encode()).decode(),
            "html_url": f"https://github.com/{owner}/{repository}/blob/main/README.md",
        })

    def _releases(self, owner, repository):
        """GitHub's releases endpoint, for the same repository: a release, a pre-release with
        markdown notes, and a draft Lyna must leave out. Answers a matching If-None-Match with 304,
        the way GitHub spares a client that already has the list."""
        if (owner, repository) != ("e2e-owner", "e2e-plugin"):
            self._json({"message": "Not Found"}, status=404)
            return
        etag = '"e2e-releases-1"'
        if self.headers.get("If-None-Match") == etag:
            self.send_response(304)
            self.send_header("ETag", etag)
            self.end_headers()
            return
        base = f"https://github.com/{owner}/{repository}/releases/tag"
        body = json.dumps([
            {"id": 3, "tag_name": "v2.0.0-beta", "name": "Spooky beta", "draft": False, "prerelease": True,
             "body": "Adds **ghosts**.\n\n- haunts\n- spooks", "html_url": f"{base}/v2.0.0-beta",
             "published_at": "2026-10-02T10:00:00Z"},
            {"id": 2, "tag_name": "v1.0.0", "name": None, "draft": False, "prerelease": False,
             "body": "", "html_url": f"{base}/v1.0.0", "published_at": "2026-09-01T10:00:00Z"},
            {"id": 9, "tag_name": "v3.0.0", "name": "Unfinished draft", "draft": True, "prerelease": False,
             "body": "Not yet", "html_url": f"{base}/v3.0.0", "published_at": None},
        ]).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("ETag", etag)
        self.end_headers()
        self.wfile.write(body)

    def _discord_token(self):
        """Discord's token endpoint: a code of ``user-<id>`` or a refresh token minted here is traded
        for a grant; anything else is refused the way Discord refuses it."""
        length = int(self.headers.get("Content-Length", 0))
        form = parse_qs(self.rfile.read(length).decode())
        grant_type = form.get("grant_type", [None])[0]
        if grant_type == "authorization_code":
            match = re.fullmatch(r"user-(\d+)", form.get("code", [""])[0])
        elif grant_type == "refresh_token":
            match = re.fullmatch(r"refresh-(\d+)-\d+", form.get("refresh_token", [""])[0])
        else:
            match = None
        if match is None:
            self._json({"error": "invalid_grant"}, status=400)
            return
        self._json(discord_grant(match.group(1), int(datetime.now(timezone.utc).timestamp())))

    def do_POST(self):
        if urlparse(self.path).path == "/discord/api/oauth2/token":
            self._discord_token()
            return
        self._json({"message": f"unhandled {self.path}"}, status=404)

    def do_GET(self):
        url = urlparse(self.path)
        query = parse_qs(url.query)

        if url.path == "/discord/api/users/@me":
            authorization = self.headers.get("Authorization", "")
            user = discord_user(authorization.removeprefix("Bearer "))
            if user is None:
                self._json({"message": "401: Unauthorized", "code": 0}, status=401)
            else:
                self._json(user)
            return

        if url.path == "/service/rest/v1/search/assets":
            wanted = query.get("maven.baseVersion", [None])[0]
            items = [without_bytes(asset) for asset in ASSETS.values()
                     if wanted is None or asset["maven2"]["version"] == wanted]
            # The client sorts by version descending; answering in that order keeps it honest.
            items.sort(key=lambda item: item["maven2"]["version"], reverse=True)
            self._json({"items": items, "continuationToken": None})
            return

        readme_match = re.fullmatch(r"/repos/([^/]+)/([^/]+)/readme", url.path)
        if readme_match:
            self._readme(readme_match.group(1), readme_match.group(2))
            return

        releases_match = re.fullmatch(r"/repos/([^/]+)/([^/]+)/releases", url.path)
        if releases_match:
            self._releases(releases_match.group(1), releases_match.group(2))
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
