#!/usr/bin/env bash
set -Eeuo pipefail
exec > >(tee -a /var/log/finrisk-deploy.log) 2>&1

APP_DIR=/opt/finrisk
RELEASE_FILE="$APP_DIR/release.env"
REPOSITORY=kwonwnsduf/finrisk-radar

if [[ "${1:-}" == "--reuse" ]]; then
  [[ -f "$RELEASE_FILE" ]] || { echo "Missing $RELEASE_FILE" >&2; exit 1; }
  # shellcheck disable=SC1090
  source "$RELEASE_FILE"
else
  BACKEND_IMAGE="${1:?backend image digest is required}"
  FRONTEND_IMAGE="${2:?frontend image digest is required}"
  SOURCE_REF="${3:?GitHub source commit is required}"
fi

image_pattern='^[0-9]{12}\.dkr\.ecr\.[a-z0-9-]+\.amazonaws\.com/'
image_pattern+='finrisk-(backend|frontend)@sha256:[0-9a-f]{64}$'

if [[ ! "$BACKEND_IMAGE" =~ $image_pattern ]]; then
  echo "Invalid backend image reference" >&2
  exit 1
fi
if [[ ! "$FRONTEND_IMAGE" =~ $image_pattern ]]; then
  echo "Invalid frontend image reference" >&2
  exit 1
fi
if [[ ! "$SOURCE_REF" =~ ^[0-9a-f]{40}$ ]]; then
  echo "Invalid GitHub source commit" >&2
  exit 1
fi

mkdir -p "$APP_DIR"

if [[ "${1:-}" != "--reuse" ]]; then
  raw_base="https://raw.githubusercontent.com/$REPOSITORY/$SOURCE_REF"
  deploy_base="$raw_base/infra/aws/deploy/day18"
  curl -fsSL "$deploy_base/docker-compose.yml" \
    -o "$APP_DIR/docker-compose.yml.tmp"
  curl -fsSL "$raw_base/infra/nginx/nginx.conf" \
    -o "$APP_DIR/nginx.conf.tmp"
  curl -fsSL "$deploy_base/cloudwatch-agent.json" \
    -o "$APP_DIR/cloudwatch-agent.json.tmp"

  install -m 0644 "$APP_DIR/docker-compose.yml.tmp" "$APP_DIR/docker-compose.yml"
  install -m 0644 "$APP_DIR/nginx.conf.tmp" "$APP_DIR/nginx.conf"
  install -m 0644 \
    "$APP_DIR/cloudwatch-agent.json.tmp" \
    "$APP_DIR/cloudwatch-agent.json"
  install -m 0700 "$0" "$APP_DIR/deploy.sh"
  rm -f "$APP_DIR"/*.tmp

  umask 077
  printf 'BACKEND_IMAGE=%q\nFRONTEND_IMAGE=%q\nSOURCE_REF=%q\n' \
    "$BACKEND_IMAGE" "$FRONTEND_IMAGE" "$SOURCE_REF" > "$RELEASE_FILE"
fi

TOKEN=$(curl -fsS -X PUT \
  -H 'X-aws-ec2-metadata-token-ttl-seconds: 300' \
  http://169.254.169.254/latest/api/token)
metadata() {
  curl -fsS -H "X-aws-ec2-metadata-token: $TOKEN" "http://169.254.169.254/latest/$1"
}
instance_tag() {
  metadata "meta-data/tags/instance/$1"
}

INSTANCE_ID=$(metadata meta-data/instance-id)
PUBLIC_IP=$(metadata meta-data/public-ipv4)
identity_document=$(metadata dynamic/instance-identity/document)
region_pattern='s/.*"region"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p'
AWS_REGION=$(sed -n "$region_pattern" <<< "$identity_document")
DB_ADDRESS=$(instance_tag FinriskDbAddress)
APPLICATION_BUCKET=$(instance_tag FinriskApplicationBucket)
GOOGLE_CLIENT_ID=$(instance_tag FinriskGoogleClientId)
TOSS_WIDGET_CLIENT_KEY=$(instance_tag FinriskTossClientKey)
NAVER_CLIENT_ID=$(instance_tag FinriskNaverClientId)
OPENAI_LLM_MODEL=$(instance_tag FinriskOpenAiModel)
CONTAINER_LOG_GROUP_NAME=$(instance_tag FinriskContainerLogGroup)

get_secret() {
  local parameter_name="$1"
  local secret_value
  secret_value=$(aws ssm get-parameter \
    --region "$AWS_REGION" \
    --name "$parameter_name" \
    --with-decryption \
    --query 'Parameter.Value' \
    --output text)
  if [[ -z "$secret_value" || "$secret_value" == "None" ]]; then
    echo "Empty SSM parameter: $parameter_name" >&2
    return 1
  fi
  printf '%s' "$secret_value"
}

POSTGRES_PASSWORD=$(get_secret /finrisk/day18/postgres/password)
REDIS_PASSWORD=$(get_secret /finrisk/day18/redis/password)
JWT_SECRET=$(get_secret /finrisk/day18/jwt/secret)
GOOGLE_CLIENT_SECRET=$(get_secret /finrisk/day18/google/client-secret)
TOSS_SECRET_KEY=$(get_secret /finrisk/day18/toss/widget-secret-key)
DART_API_KEY=$(get_secret /finrisk/day18/dart/api-key)
NAVER_CLIENT_SECRET=$(get_secret /finrisk/day18/naver/client-secret)
OPENAI_API_KEY=$(get_secret /finrisk/day18/openai/api-key)

umask 077
cat > "$APP_DIR/.env.prod" <<EOF
INSTANCE_ID=$INSTANCE_ID
BACKEND_IMAGE=$BACKEND_IMAGE
FRONTEND_IMAGE=$FRONTEND_IMAGE
CONTAINER_LOG_GROUP_NAME=$CONTAINER_LOG_GROUP_NAME
SPRING_DATASOURCE_URL=jdbc:postgresql://$DB_ADDRESS:5432/finrisk
POSTGRES_USER=finrisk
POSTGRES_PASSWORD=$POSTGRES_PASSWORD
REDIS_PASSWORD=$REDIS_PASSWORD
JWT_SECRET=$JWT_SECRET
JWT_ACCESS_TOKEN_EXPIRATION=30m
JWT_REFRESH_TOKEN_EXPIRATION=14d
GOOGLE_CLIENT_ID=$GOOGLE_CLIENT_ID
GOOGLE_CLIENT_SECRET=$GOOGLE_CLIENT_SECRET
OAUTH_FRONTEND_REDIRECT_URI=http://$PUBLIC_IP/login
OAUTH_CODE_TTL=180s
PAYMENT_ENABLED=true
PAYMENT_FRONTEND_BASE_URL=http://$PUBLIC_IP
TOSS_SECRET_KEY=$TOSS_SECRET_KEY
TOSS_WIDGET_CLIENT_KEY=$TOSS_WIDGET_CLIENT_KEY
AWS_REGION=$AWS_REGION
AWS_S3_BUCKET=$APPLICATION_BUCKET
DART_API_KEY=$DART_API_KEY
NAVER_NEWS_BASE_URL=https://naverapihub.apigw.ntruss.com
NAVER_CLIENT_ID=$NAVER_CLIENT_ID
NAVER_CLIENT_SECRET=$NAVER_CLIENT_SECRET
DOCUMENT_COLLECTION_SCHEDULER_ENABLED=false
OPENAI_API_KEY=$OPENAI_API_KEY
OPENAI_BASE_URL=https://api.openai.com
OPENAI_EMBEDDING_MODEL=text-embedding-3-small
OPENAI_EMBEDDING_DIMENSIONS=1536
OPENAI_EMBEDDING_BATCH_SIZE=32
OPENAI_LLM_MODEL=$OPENAI_LLM_MODEL
EOF
chmod 0600 "$APP_DIR/.env.prod" "$RELEASE_FILE"

cat > /etc/systemd/system/finrisk-deploy.service <<'EOF'
[Unit]
Description=FinRisk Radar containers
Wants=network-online.target
After=network-online.target docker.service
Requires=docker.service

[Service]
Type=oneshot
RemainAfterExit=yes
ExecStart=/opt/finrisk/deploy.sh --reuse
ExecStop=/usr/bin/docker compose --env-file /opt/finrisk/.env.prod \
  -f /opt/finrisk/docker-compose.yml down
TimeoutStartSec=900

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload
systemctl enable finrisk-deploy.service

/opt/aws/amazon-cloudwatch-agent/bin/amazon-cloudwatch-agent-ctl \
  -a fetch-config -m ec2 -s -c file:"$APP_DIR/cloudwatch-agent.json"

registry=${BACKEND_IMAGE%%/*}
aws ecr get-login-password --region "$AWS_REGION" \
  | docker login --username AWS --password-stdin "$registry"

cd "$APP_DIR"
compose=(docker compose --env-file .env.prod -f docker-compose.yml)
"${compose[@]}" config --quiet
"${compose[@]}" pull
"${compose[@]}" up -d --remove-orphans
"${compose[@]}" ps
