#!/usr/bin/env python3
"""Checks the staging Keycloak realm (Phase 7 plan PR-65): no user, no local URL, no secret value.

    python3 infrastructure/staging/check-staging-realm.py [path/to/realm-mbia-staging.json]

Exits 1 and lists every problem found. Standard library only (run by CI, job `staging-config`).
"""

import json
import re
import sys
from pathlib import Path

DEFAULT_REALM = Path(__file__).resolve().parents[1] / "keycloak" / "realm-mbia-staging.json"

# Local addresses and the local test domain of realm-mbia.json.
LOCAL_PATTERN = re.compile(r"localhost|127\.0\.0\.1|\[::1\]|0\.0\.0\.0|mailpit|\.local\b", re.IGNORECASE)
PLACEHOLDER = re.compile(r"^\$\{[A-Z0-9_]+(:[^}]*)?\}$")

# Realm settings required by ADR-005 and OQ-070, whatever the environment.
REQUIRED_SETTINGS = {
    "realm": "mbia",
    "registrationAllowed": True,
    "registrationEmailAsUsername": True,
    "duplicateEmailsAllowed": False,
    "verifyEmail": True,
    "resetPasswordAllowed": True,
    "bruteForceProtected": True,
    "internationalizationEnabled": True,
    "defaultLocale": "fr",
}


def strings(node, path="$"):
    """Yields (json path, value) for every string of the document, keys included."""
    if isinstance(node, dict):
        for key, value in node.items():
            yield f"{path}.{key} (key)", key
            yield from strings(value, f"{path}.{key}")
    elif isinstance(node, list):
        for index, value in enumerate(node):
            yield from strings(value, f"{path}[{index}]")
    elif isinstance(node, str):
        yield path, node


def check(realm):
    problems = []

    if realm.get("users"):
        problems.append(f"$.users: {len(realm['users'])} user(s); the staging realm must contain none")
    if "credentials" in json.dumps(realm):
        problems.append("credentials found; the staging realm must contain no password")

    for path, value in strings(realm):
        if LOCAL_PATTERN.search(value):
            problems.append(f"{path}: local address {value!r}")

    for key, expected in REQUIRED_SETTINGS.items():
        if realm.get(key) != expected:
            problems.append(f"$.{key}: {realm.get(key)!r}, expected {expected!r}")

    smtp = realm.get("smtpServer", {})
    for key in ("host", "from", "user", "password"):
        if not PLACEHOLDER.match(str(smtp.get(key, ""))):
            problems.append(f"$.smtpServer.{key}: must be an environment placeholder ${{...}}")

    clients = [c for c in realm.get("clients", []) if c.get("clientId") == "mbia-web"]
    if len(clients) != 1:
        problems.append("$.clients: exactly one mbia-web client expected")
    else:
        client = clients[0]
        urls = [
            client.get("rootUrl", ""),
            *client.get("redirectUris", []),
            *client.get("webOrigins", []),
            client.get("attributes", {}).get("post.logout.redirect.uris", ""),
        ]
        for url in urls:
            if "${MBIA_WEB_ORIGIN}" not in url:
                problems.append(f"mbia-web: {url!r} must be built from ${{MBIA_WEB_ORIGIN}}")
        if not client.get("publicClient") or client.get("directAccessGrantsEnabled"):
            problems.append("mbia-web: must be public, without direct access grants")
        if client.get("attributes", {}).get("pkce.code.challenge.method") != "S256":
            problems.append("mbia-web: PKCE S256 required")

    return problems


def main():
    path = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_REALM
    problems = check(json.loads(path.read_text(encoding="utf-8")))
    for problem in problems:
        print(f"{path.name}: {problem}", file=sys.stderr)
    if problems:
        return 1
    print(f"{path.name}: no user, no local address, settings and placeholders as expected")
    return 0


if __name__ == "__main__":
    sys.exit(main())
