# ADR-004 — S3-compatible object storage

**Status:** Accepted

## Context

Photos must not be stored in PostgreSQL, and large uploads should not transit through the application server.

## Decision

Binary media lives in S3-compatible storage (MinIO locally, managed S3-compatible storage in production). The browser uploads directly with pre-signed PUT URLs; the backend then verifies and processes the upload (ADR-007). Viewing uses short-lived pre-signed GET URLs.

## Consequences

- PostgreSQL stores only metadata and storage keys.
- Storage keys are never exposed as permanent public URLs.
- The same code runs against MinIO and production storage.
