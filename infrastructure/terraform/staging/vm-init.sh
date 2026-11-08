#!/bin/bash
# First boot of the staging VM (ADR-010), run once by cloud-init. Amazon Linux 2023 ships the SSM agent.
# The Docker Compose plugin is installed by the runbook (infrastructure/staging/README.md, first installation);
# the Compose files and the env files by each deployment.
set -euo pipefail

# The VM may boot before it can reach the package repositories: the Elastic IP is associated after the instance
# is created (until then the VM has no route to the Internet). Wait up to 15 minutes instead of failing.
for attempt in $(seq 45); do
  if dnf -y makecache >/dev/null 2>&1; then break; fi
  echo "vm-init: package repositories not reachable yet (attempt $attempt/45)"
  sleep 20
done

dnf install -y docker
systemctl enable --now docker

# backend.env and keycloak.env, rendered from SSM Parameter Store at deployment (root only).
install -d -m 0700 -o root -g root /etc/mbia/staging
