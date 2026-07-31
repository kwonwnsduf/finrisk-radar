# Day 17: Docker, Nginx, AWS ECR

## 1. 목표와 범위

Day 17은 현재 애플리케이션을 운영 이미지로 패키징하고, Nginx 단일 진입점과
운영 유사 로컬 Compose를 구성한 뒤 AWS ECR에 이미지를 저장하는 단계다.

현재 독립 실행 단위는 `finrisk-backend`와 `finrisk-frontend` 두 개다.
`workers/`에는 실행 가능한 프로젝트가 없고 비동기 Consumer는 백엔드 JVM 안에
있으므로 Worker 이미지나 ECR Repository를 만들지 않는다.

EC2, RDS, VPC, ALB, Route53, CloudFront, Terraform remote state와 CI/CD는 이후
Day 범위다.

## 2. 이미지 구조

### Backend

`backend/Dockerfile`은 Java 17 JDK builder와 Java 17 JRE runner를 분리한다.
Gradle dependency와 `bootJar -x test`는 BuildKit cache를 사용한다. 테스트는
이미지 빌드와 분리해 호스트에서 실행한다.

runner는 non-root `finrisk` 사용자와 `docker` Spring profile을 사용한다.
JVM 옵션은 runtime의 `JAVA_OPTS`로 전달한다. JRE 이미지에 포함된 `wget`으로
`/actuator/health`를 확인한다.

### Frontend

`frontend/Dockerfile`은 pnpm dependency, Next.js build, standalone runner
stage로 분리되어 있다. non-root `nextjs` 사용자로 실행하며 `public`,
`.next/standalone`, `.next/static`만 runtime으로 복사한다. Node 이미지에 포함된
`wget`으로 인증이 필요 없는 `/`를 확인한다.

## 3. 브라우저 URL 정책

로컬 개발은 기존 `/backend-api` rewrite를 유지한다. 운영 이미지는 다음 build
argument를 빈 문자열로 전달한다.

```text
NEXT_PUBLIC_API_BASE_URL=
NEXT_PUBLIC_OAUTH_BASE_URL=
```

현재 API client는 `/api/**` 절대 경로를 사용하고 OAuth 코드는 base URL의
trailing slash를 제거한 뒤 `/oauth2/**`를 붙인다. 따라서 운영 요청은 정확히
`/api/**`와 `/oauth2/authorization/google`이 된다.

`NEXT_PUBLIC_*`는 build-time 공개값이다. Secret을 넣어서는 안 되며 값을 바꾸면
이미지를 다시 빌드해야 한다.

## 4. Nginx route

| 요청 | 대상 |
| --- | --- |
| `/` | `frontend:3000` |
| `/api/` | `backend:8080` |
| `/oauth2/` | `backend:8080` |
| `/login/oauth2/` | `backend:8080` |
| 정확히 `/actuator/health` | `backend:8080` |
| `/actuator`, 그 외 `/actuator/**` | Nginx 404 |

Swagger와 `/actuator/prometheus`는 외부에 공개하지 않는다. Prometheus는 내부
network에서 `backend:8080/actuator/prometheus`를 직접 수집한다.

## 5. 운영 유사 Compose

`infra/docker/docker-compose.prod.yml`은 PostgreSQL pgvector, Redis, ZooKeeper,
Kafka, Backend, Frontend, Nginx, Prometheus, Grafana를 실행한다.

Nginx만 애플리케이션 포트를 공개한다. Prometheus와 Grafana는 loopback에만
바인딩한다. 나머지 서비스는 내부 network에서 service name으로 접근한다.
Compose는 전체 env 파일을 모든 컨테이너에 전달하지 않고 서비스마다 필요한
변수만 명시한다.

## 6. 환경 파일

```powershell
Copy-Item .env.prod.example .env.prod
```

실행 전에 `.env.prod`의 `replace-with-*` 값을 교체한다. PostgreSQL/Redis/JWT,
OAuth, Grafana 및 활성화한 외부 연동의 credential은 Secret이다.
`.env.prod`는 Git에서 제외되고 `.env.prod.example`만 커밋한다.

## 7. 로컬 빌드와 실행

```powershell
Set-Location C:\Projects\finrisk-radar

.\backend\gradlew.bat -p backend test bootJar
corepack pnpm@10.30.3 --dir frontend typecheck
corepack pnpm@10.30.3 --dir frontend test
corepack pnpm@10.30.3 --dir frontend lint
corepack pnpm@10.30.3 --dir frontend build

docker compose --env-file .env.prod -f infra/docker/docker-compose.prod.yml config --quiet
docker compose --env-file .env.prod -f infra/docker/docker-compose.prod.yml up -d --build
docker compose --env-file .env.prod -f infra/docker/docker-compose.prod.yml ps
```

주요 확인:

```powershell
Invoke-WebRequest http://localhost/ -UseBasicParsing
Invoke-RestMethod http://localhost/api/health
Invoke-RestMethod http://localhost/actuator/health

curl.exe -s -o NUL -w "%{http_code}" http://localhost/actuator
curl.exe -s -o NUL -w "%{http_code}" http://localhost/actuator/prometheus
curl.exe -s -o NUL -w "%{http_code}" http://localhost/actuator/health/
curl.exe -s -o NUL -w "%{http_code}" http://localhost/actuator/env
```

첫 세 요청은 성공해야 하며 마지막 네 요청은 모두 `404`여야 한다.

볼륨을 보존하며 종료한다.

```powershell
docker compose --env-file .env.prod -f infra/docker/docker-compose.prod.yml down
```

`down -v`는 데이터를 삭제하므로 명시적으로 초기화할 때만 사용한다.

## 8. Terraform ECR

Terraform은 `finrisk-backend`, `finrisk-frontend` Repository와 각각의 lifecycle
policy만 관리한다.

- Terraform `>= 1.14.0, < 2.0.0`
- AWS Provider `>= 6.14.1, < 7.0.0`
- 실제 Provider 버전은 `.terraform.lock.hcl`로 고정
- `IMMUTABLE_WITH_EXCLUSION`, `latest`만 mutable
- scan on push와 AES-256 encryption
- untagged 이미지는 7일 후 만료
- tagged 이미지는 최근 30개 보존
- `force_delete=false`

최초 검증:

```powershell
Set-Location C:\Projects\finrisk-radar\infra\aws\terraform
Copy-Item terraform.tfvars.example terraform.tfvars
Get-Content terraform.tfvars

terraform fmt -recursive
terraform init
terraform validate
terraform plan -var-file="terraform.tfvars"
terraform fmt -check -recursive
```

plan에는 Repository 2개와 lifecycle policy 2개만 추가되고 삭제가 없어야 한다.
검토 후 적용한다.

```powershell
terraform apply -var-file="terraform.tfvars"
```

AWS 인증은 표준 AWS CLI profile 또는 credential chain을 사용한다. Access Key와
Secret Key를 tfvars에 기록하지 않는다. local state와 실제 tfvars는 Git에서
제외한다.

## 9. 이미지 tag와 ECR push

기본 배포 tag는 현재 Git commit의 12자리 SHA다.

```text
sha-abcdef123456
```

선택적 tag는 `day17-abcdef123456`과 `latest`다. 고정 `day17` tag는 사용하지
않는다. `latest`만 mutable이고 나머지는 덮어쓸 수 없다. 스크립트는 dirty
worktree를 거부하므로 push 전에 Day 17 변경을 commit해야 한다.

ECR immutable Repository에서는 BuildKit provenance attestation과 최종 image
index가 같은 tag를 순차 등록하며 충돌할 수 있다. push 스크립트는
`--provenance=false`로 단일 image manifest를 빌드해 immutable tag를 한 번만
등록한다.

```powershell
Set-Location C:\Projects\finrisk-radar
.\infra\aws\scripts\push-ecr-images.ps1 `
  -Region ap-northeast-2 `
  -IncludeMilestoneTag `
  -PushLatest
```

Day 18 EC2는 `latest`가 아니라 출력된 `sha-<12자리 SHA>`를 pull한다.

## 10. ECR 확인

```powershell
aws ecr describe-repositories `
  --region ap-northeast-2 `
  --repository-names finrisk-backend finrisk-frontend

aws ecr describe-images `
  --region ap-northeast-2 `
  --repository-name finrisk-backend

aws ecr describe-images `
  --region ap-northeast-2 `
  --repository-name finrisk-frontend
```

scan은 push 직후 `IN_PROGRESS`일 수 있다. 완료 후
`aws ecr describe-image-scan-findings`로 결과를 확인한다.

## 11. 문제 해결

### Nginx 502

Backend/Frontend health와 동일 network 여부를 확인한다.

```powershell
docker compose --env-file .env.prod -f infra/docker/docker-compose.prod.yml ps
docker compose --env-file .env.prod -f infra/docker/docker-compose.prod.yml logs --tail 100 backend frontend nginx
```

### Kafka 연결 실패

`KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://kafka:9092`와 ZooKeeper health를 확인한다.

### `/backend-api/api` 또는 `//api`

운영 Frontend 이미지가 빈 public base URL로 빌드됐는지 확인하고 다시 빌드한다.

### Terraform 인증 실패

`aws sts get-caller-identity`와 profile/region을 확인한다. 인증정보를 Terraform
파일에 추가하지 않는다.

### immutable tag 충돌

고정 tag를 덮어쓰지 않는다. 변경을 commit하고 새 SHA tag로 push한다.

## 12. Day 18 인계

Day 18에서는 Terraform output의 Repository URL과 push 스크립트가 출력한 SHA
tag를 사용한다.

```text
<account>.dkr.ecr.ap-northeast-2.amazonaws.com/finrisk-backend:sha-<commit>
<account>.dkr.ecr.ap-northeast-2.amazonaws.com/finrisk-frontend:sha-<commit>
```

EC2 IAM role과 ECR pull 권한, EC2용 Compose 및 실제 운영 Secret 주입은 Day 18에서
구성한다.
