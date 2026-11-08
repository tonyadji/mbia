#!/usr/bin/env python3
"""SES SMTP password of an IAM secret access key (ADR-010, runbook: infrastructure/staging/README.md).

Reads the secret access key of the user mbia-staging-ses-smtp on stdin, prints the SMTP password for the
region given as argument (default eu-west-3). The algorithm is the one published by AWS in the SES guide
("Obtaining Amazon SES SMTP credentials"). Nothing is written to disk.

    read -rs SECRET && printf %s "$SECRET" | python3 -I ses-smtp-password.py eu-west-3
"""
import base64
import hashlib
import hmac
import sys

DATE = "11111111"
SERVICE = "ses"
MESSAGE = "SendRawEmail"
TERMINAL = "aws4_request"
VERSION = 0x04


def sign(key: bytes, msg: str) -> bytes:
    return hmac.new(key, msg.encode("utf-8"), hashlib.sha256).digest()


def smtp_password(secret_access_key: str, region: str) -> str:
    signature = sign(("AWS4" + secret_access_key).encode("utf-8"), DATE)
    for part in (region, SERVICE, TERMINAL, MESSAGE):
        signature = sign(signature, part)
    return base64.b64encode(bytes([VERSION]) + signature).decode("utf-8")


def main() -> None:
    region = sys.argv[1] if len(sys.argv) > 1 else "eu-west-3"
    secret = sys.stdin.read().strip()
    if not secret:
        sys.exit("no secret access key on stdin")
    print(smtp_password(secret, region))


if __name__ == "__main__":
    main()
