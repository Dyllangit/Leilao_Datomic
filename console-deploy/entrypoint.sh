#!/bin/bash
set -e

TRANSACTOR_HOST="${TRANSACTOR_HOST:-datomic-transactor.railway.internal}"
TRANSACTOR_PORT="${TRANSACTOR_PORT:-4334}"
WEB_PORT="${PORT:-8080}"

URI="datomic:dev://${TRANSACTOR_HOST}:${TRANSACTOR_PORT}/?password=${STORAGE_DATOMIC_PASSWORD}"

echo "Iniciando Datomic Console na porta $WEB_PORT, apontando para $URI"
exec bin/console -p "$WEB_PORT" dev "$URI"
