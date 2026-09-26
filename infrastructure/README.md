# infrastructure

Configuration for the local services started by the root [`docker-compose.yml`](../docker-compose.yml), and later deployment files.

- `postgres/init/`: creates the `mbia` and `keycloak` databases, each with its own user, on first start.
- `keycloak/`: the `mbia` realm imported by Keycloak on first start, and its local test users (ADR-005).
- The `rustfs-init` service in `docker-compose.yml` creates the private `mbia-media` bucket and sets its CORS through the S3 API (ADR-009). It runs on every `docker compose up -d`, so an existing bucket gets the CORS too.

## Object storage in production

Photos are uploaded by the browser directly to S3-compatible storage with pre-signed `PUT` URLs, and viewed through pre-signed `GET` URLs (ADR-004, Phase 3 plan §3.5). The production bucket must be set up as follows:

- **Private:** no bucket policy, ACL or public access grant. Every read and write goes through a pre-signed URL or the backend's credentials.
- **CORS:** only the frontend origin may upload, with `PUT` and the `content-type` header. Images are shown with `<img>`, which needs no CORS rule for `GET`.

  ```json
  {"CORSRules": [{"AllowedOrigins": ["https://<frontend origin>"], "AllowedMethods": ["PUT"], "AllowedHeaders": ["content-type"], "MaxAgeSeconds": 3600}]}
  ```

  Apply it with `aws s3api put-bucket-cors --bucket <bucket> --cors-configuration file://cors.json` (add `--endpoint-url` for a non-AWS provider).
- **Backend configuration** (`mbia.storage.*`, see `backend/src/main/resources/application.yml`):
  - `bucket`, `region`: required, startup fails without them;
  - `endpoint`: the endpoint the backend reaches (empty for the provider's default S3 endpoint);
  - `public-endpoint`: the endpoint the browser reaches, for which pre-signed URLs are signed (empty when it is `endpoint`);
  - `access-key`, `secret-key`: static credentials; leave them empty to use the default AWS credentials chain (environment, instance or workload role);
  - `path-style-access`: `true` by default (`endpoint/bucket/key`), needed by most S3-compatible providers;
  - `upload-url-validity`: validity of an upload URL, `PT15M` by default.
- The backend's credentials need `s3:PutObject`, `s3:GetObject`, `s3:DeleteObject` and `s3:ListBucket` on this bucket only (completion and cleanup arrive with PR-36).
