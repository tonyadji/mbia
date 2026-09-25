#!/bin/sh
# Creates the Mbia and Keycloak databases, each owned by its own role.
# Run once by the postgres image, only when the data volume is empty.
set -eu

create_database() {
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
    -v db="$1" -v user="$2" -v password="$3" <<'EOSQL'
CREATE ROLE :"user" LOGIN PASSWORD :'password';
CREATE DATABASE :"db" OWNER :"user";
REVOKE ALL ON DATABASE :"db" FROM PUBLIC;
EOSQL
}

create_database "$MBIA_DB_NAME" "$MBIA_DB_USER" "$MBIA_DB_PASSWORD"
create_database "$KEYCLOAK_DB_NAME" "$KEYCLOAK_DB_USER" "$KEYCLOAK_DB_PASSWORD"
