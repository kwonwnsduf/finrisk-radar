#!/usr/bin/env bash
set -Eeuo pipefail

BACKEND_IMAGE="${1:?backend image digest is required}"
FRONTEND_IMAGE="${2:?frontend image digest is required}"
COMPOSE_FILE=/opt/finrisk/docker-compose.aws.yml

image_pattern='^[0-9]{12}\.dkr\.ecr\.[a-z0-9-]+\.amazonaws\.com/finrisk-(backend|frontend)@sha256:[0-9a-f]{64}$'
[[ "$BACKEND_IMAGE" =~ $image_pattern ]] || { echo "Invalid backend image reference" >&2; exit 1; }
[[ "$FRONTEND_IMAGE" =~ $image_pattern ]] || { echo "Invalid frontend image reference" >&2; exit 1; }
[[ -f "$COMPOSE_FILE" ]] || { echo "Missing $COMPOSE_FILE" >&2; exit 1; }
[[ -x /opt/finrisk/deploy.sh ]] || { echo "Missing /opt/finrisk/deploy.sh" >&2; exit 1; }

sed -i -E "s#^    image: .*finrisk-backend(@|:).*$#    image: $BACKEND_IMAGE#" "$COMPOSE_FILE"
sed -i -E "s#^    image: .*finrisk-frontend(@|:).*$#    image: $FRONTEND_IMAGE#" "$COMPOSE_FILE"

grep -Fq "image: $BACKEND_IMAGE" "$COMPOSE_FILE"
grep -Fq "image: $FRONTEND_IMAGE" "$COMPOSE_FILE"

/opt/finrisk/deploy.sh

cd /opt/finrisk
docker compose --env-file .env.prod -f docker-compose.aws.yml ps
