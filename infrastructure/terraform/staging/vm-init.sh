#!/bin/bash
# First boot of the staging VM (ADR-010), run once by cloud-init. Amazon Linux 2023 ships the SSM agent.
# The Docker Compose plugin, the Compose files and the env files are installed by the deployment (PR-68).
set -euo pipefail

dnf install -y docker
systemctl enable --now docker

# backend.env and keycloak.env, rendered from SSM Parameter Store at deployment (root only).
install -d -m 0700 -o root -g root /etc/mbia/staging
