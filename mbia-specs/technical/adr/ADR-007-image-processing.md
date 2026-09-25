# ADR-007 — Synchronous server-side image processing

**Status:** Accepted

## Context

Photos are uploaded from phones, often on expensive mobile data. Originals may contain GPS location in EXIF metadata. The MVP must show thumbnails quickly and must not introduce a job queue.

## Decision

1. **Client side:** before upload, the browser downscales images whose long edge exceeds 2560 px and re-encodes them as JPEG (quality ≈ 0.85). If the browser cannot process the file, the original is uploaded as is.
2. **Upload limit:** 15 MB (15 728 640 bytes) per file after client processing.
3. **Server side, synchronously in `completeMediaUpload`:**
   - check the object exists and its size ≤ 15 MB;
   - check magic bytes match an allowed type (JPEG, PNG, WEBP) and the declared MIME type;
   - reject images above 40 megapixels (decompression-bomb protection);
   - apply the EXIF orientation;
   - generate two JPEG derivatives without any metadata: `display` (long edge ≤ 2048 px) and `thumbnail` (long edge ≤ 480 px);
   - delete the uploaded original;
   - mark the asset `READY`, or `FAILED` with `MEDIA_INVALID`.
4. **Cleanup:** a scheduled task marks `PENDING_UPLOAD` assets older than 24 hours as `FAILED` and deletes their objects.
5. **Viewing:** pre-signed GET URLs valid for 60 minutes.

Libraries: TwelveMonkeys ImageIO (WEBP reading), `metadata-extractor` (EXIF orientation), standard `javax.imageio` for JPEG writing.

## Consequences

- No location metadata is ever served, and the original file is not kept.
- `completeMediaUpload` may take a few seconds; the UI shows progress.
- If processing time becomes a problem, move it to an asynchronous worker (new ADR).
