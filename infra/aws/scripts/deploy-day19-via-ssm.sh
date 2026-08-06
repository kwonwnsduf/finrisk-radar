#!/usr/bin/env bash
set -Eeuo pipefail
exec > >(tee -a /var/log/finrisk-deploy.log) 2>&1

APP_DIR=/opt/finrisk
RELEASE_FILE="$APP_DIR/release.env"

if [[ "${1:-}" == "--reuse" ]]; then
  [[ -f "$RELEASE_FILE" ]] || { echo "Missing $RELEASE_FILE" >&2; exit 1; }
  # shellcheck disable=SC1090
  source "$RELEASE_FILE"
else
  ROLE="${1:?deployment role is required}"
  BACKEND_IMAGE="${2:?backend image digest is required}"
  FRONTEND_IMAGE="${3:?frontend image digest is required}"
  SOURCE_REF="${4:?GitHub source commit is required}"
fi

[[ "$ROLE" == "application" || "$ROLE" == "runtime" ]] || {
  echo "Role must be application or runtime" >&2
  exit 1
}

image_pattern='^[0-9]{12}\.dkr\.ecr\.[a-z0-9-]+\.amazonaws\.com/'
image_pattern+='finrisk-(backend|frontend)@sha256:[0-9a-f]{64}$'
[[ "$BACKEND_IMAGE" =~ $image_pattern ]] || { echo "Invalid backend image reference" >&2; exit 1; }
[[ "$FRONTEND_IMAGE" =~ $image_pattern ]] || { echo "Invalid frontend image reference" >&2; exit 1; }
[[ "$SOURCE_REF" =~ ^[0-9a-f]{40}$ ]] || { echo "Invalid GitHub source commit" >&2; exit 1; }

mkdir -p "$APP_DIR"

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
PUBLIC_BASE_URL=$(instance_tag FinriskPublicBaseUrl)

if [[ "$ROLE" == "application" ]]; then
  RUNTIME_HOST=$(instance_tag FinriskRuntimeHost)
else
  RUNTIME_HOST=$(metadata meta-data/local-hostname)
fi

if [[ "${1:-}" != "--reuse" ]]; then
  artifact_base="s3://$APPLICATION_BUCKET/deploy/day19/$SOURCE_REF"
  compose_name="docker-compose.$ROLE.yml"

  aws s3 cp "$artifact_base/$compose_name" "$APP_DIR/docker-compose.yml.tmp" --region "$AWS_REGION"
  aws s3 cp "$artifact_base/cloudwatch-agent.json" "$APP_DIR/cloudwatch-agent.json.tmp" --region "$AWS_REGION"
  if [[ "$ROLE" == "application" ]]; then
    aws s3 cp "$artifact_base/nginx.conf" "$APP_DIR/nginx.conf.tmp" --region "$AWS_REGION"
  else
    monitoring_files=(
      prometheus.yml
      grafana-datasource.yml
      grafana-dashboard-provider.yml
      finrisk-aws-overview.json
    )
    for monitoring_file in "${monitoring_files[@]}"; do
      aws s3 cp "$artifact_base/$monitoring_file" \
        "$APP_DIR/$monitoring_file.tmp" --region "$AWS_REGION"
    done
  fi

  install -m 0644 "$APP_DIR/docker-compose.yml.tmp" "$APP_DIR/docker-compose.yml"
  install -m 0644 "$APP_DIR/cloudwatch-agent.json.tmp" "$APP_DIR/cloudwatch-agent.json"
  if [[ "$ROLE" == "application" ]]; then
    install -m 0644 "$APP_DIR/nginx.conf.tmp" "$APP_DIR/nginx.conf"
  else
    for monitoring_file in "${monitoring_files[@]}"; do
      install -m 0644 "$APP_DIR/$monitoring_file.tmp" "$APP_DIR/$monitoring_file"
    done
  fi
  install -m 0700 "$0" "$APP_DIR/deploy.sh"
  rm -f "$APP_DIR"/*.tmp
fi

get_secret() {
  local parameter_name="$1"
  local secret_value
  secret_value=$(aws ssm get-parameter \
    --region "$AWS_REGION" \
    --name "$parameter_name" \
    --with-decryption \
    --query 'Parameter.Value' \
    --output text)
  [[ -n "$secret_value" && "$secret_value" != "None" ]] || {
    echo "Empty SSM parameter: $parameter_name" >&2
    return 1
  }
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

if [[ "$ROLE" == "runtime" ]]; then
  grafana_password_file="$APP_DIR/grafana-admin-password"
  if [[ ! -s "$grafana_password_file" ]]; then
    password_tmp="$grafana_password_file.tmp"
    openssl rand -hex 24 > "$password_tmp"
    install -m 0600 "$password_tmp" "$grafana_password_file"
    rm -f "$password_tmp"
  fi
  GRAFANA_ADMIN_PASSWORD=$(<"$grafana_password_file")
else
  GRAFANA_ADMIN_PASSWORD=unused
fi

umask 077
cat > "$APP_DIR/.env.prod" <<EOF
INSTANCE_ID=$INSTANCE_ID
BACKEND_IMAGE=$BACKEND_IMAGE
FRONTEND_IMAGE=$FRONTEND_IMAGE
RUNTIME_HOST=$RUNTIME_HOST
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
OAUTH_FRONTEND_REDIRECT_URI=$PUBLIC_BASE_URL/login
OAUTH_CODE_TTL=180s
PAYMENT_ENABLED=true
PAYMENT_FRONTEND_BASE_URL=$PUBLIC_BASE_URL
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
GRAFANA_ADMIN_PASSWORD=$GRAFANA_ADMIN_PASSWORD
EOF
chmod 0600 "$APP_DIR/.env.prod"

unit_name="finrisk-$ROLE.service"
cat > "/etc/systemd/system/$unit_name" <<EOF
[Unit]
Description=FinRisk Radar $ROLE containers
Wants=network-online.target
After=network-online.target docker.service
Requires=docker.service

[Service]
Type=oneshot
RemainAfterExit=yes
ExecStart=/opt/finrisk/deploy.sh --reuse
ExecStop=/usr/bin/docker compose --env-file /opt/finrisk/.env.prod -f /opt/finrisk/docker-compose.yml down
TimeoutStartSec=900

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload
systemctl enable "$unit_name"
if [[ "$ROLE" == "runtime" ]]; then
  systemctl disable finrisk-deploy.service 2>/dev/null || true
fi

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

if [[ "$ROLE" == "application" ]]; then
  curl --fail --retry 60 --retry-delay 5 --retry-all-errors http://127.0.0.1/readyz
  curl --fail --retry 12 --retry-delay 5 --retry-all-errors http://127.0.0.1/
  curl --fail --retry 12 --retry-delay 5 --retry-all-errors http://127.0.0.1/api/health
else
  curl --fail --retry 60 --retry-delay 5 --retry-all-errors http://127.0.0.1:18080/readyz
  curl --fail --retry 30 --retry-delay 3 --retry-all-errors http://127.0.0.1:9090/-/ready
  curl --fail --retry 30 --retry-delay 3 --retry-all-errors http://127.0.0.1:3001/api/health
  "${compose[@]}" exec -T redis redis-cli --no-auth-warning -a "$REDIS_PASSWORD" ping | grep -q PONG
  "${compose[@]}" exec -T kafka kafka-broker-api-versions --bootstrap-server localhost:9092 >/dev/null
fi

"${compose[@]}" ps

release_tmp="$RELEASE_FILE.tmp"
printf 'ROLE=%q\nBACKEND_IMAGE=%q\nFRONTEND_IMAGE=%q\nSOURCE_REF=%q\n' \
  "$ROLE" "$BACKEND_IMAGE" "$FRONTEND_IMAGE" "$SOURCE_REF" > "$release_tmp"
install -m 0600 "$release_tmp" "$RELEASE_FILE"
rm -f "$release_tmp"
