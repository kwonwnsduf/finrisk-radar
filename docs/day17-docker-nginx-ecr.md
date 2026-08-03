# Day 17: Docker, Nginx, Terraform, AWS ECR

이 문서는 Day 17 코드를 모두 작성한 뒤 무엇을 어떤 순서로 실행하는지 설명한다.
`push-ecr-images.ps1` 없이 명령을 직접 실행하는 과정을 기준으로 하며, 각 명령의 값이
어디서 나오고 어떤 프로그램이 실행하는지도 함께 설명한다.

## 1. Day 17 전체 흐름

```text
애플리케이션 코드 작성 완료
        ↓
Backend/Frontend 테스트
        ↓
Dockerfile로 로컬 이미지 빌드
        ↓
Docker Compose와 Nginx로 전체 서비스 검증
        ↓
Terraform으로 AWS ECR Repository 생성
        ↓
Terraform output에서 실제 ECR Repository URL 조회
        ↓
Docker가 AWS ECR Registry에 임시 로그인
        ↓
Git SHA 태그로 Backend/Frontend 이미지 빌드
        ↓
docker push로 ECR에 이미지 업로드
        ↓
AWS CLI로 업로드된 태그와 digest 확인
```

각 도구의 역할은 다음과 같다.

| 도구 | 역할 | 실행 주체 |
| --- | --- | --- |
| Terraform | ECR Repository와 보관 정책 생성 | `terraform.exe` |
| Dockerfile | 애플리케이션 이미지 생성 절차 | Docker Engine/BuildKit |
| Docker Compose | 여러 컨테이너를 함께 실행 | Docker Compose와 Docker Engine |
| Nginx | 브라우저 요청을 Frontend/Backend로 전달 | Nginx 컨테이너 |
| AWS CLI | AWS 계정·ECR 상태 조회와 임시 로그인 토큰 발급 | `aws.exe` |
| Git | 현재 코드 버전인 commit SHA 조회 | `git.exe` |
| PowerShell | 위 프로그램을 사람이 순서대로 실행하는 터미널 | `powershell.exe` |

코드를 파일에 작성해 놓는 것만으로는 실행되지 않는다. 사람이 PowerShell에서
`terraform`, `docker`, `aws`, `git` 명령을 실행하거나, 나중에 CI/CD가 같은 명령을
대신 실행해야 한다.

## 2. 실행 단위와 파일 구성

Day 17에서 만드는 애플리케이션 이미지는 두 개다.

```text
finrisk-backend
finrisk-frontend
```

`workers/`에는 별도의 실행 프로젝트가 없고 Kafka Consumer가 Backend JVM 안에 있으므로
Worker 이미지와 ECR Repository는 만들지 않는다.

주요 파일은 다음과 같다.

| 파일 | 의미 |
| --- | --- |
| `backend/Dockerfile` | Spring Boot Backend 이미지 생성 절차 |
| `frontend/Dockerfile` | Next.js Frontend 이미지 생성 절차 |
| `infra/nginx/nginx.conf` | 외부 요청을 Frontend/Backend로 분배하는 규칙 |
| `infra/docker/docker-compose.prod.yml` | 전체 로컬 운영 유사 환경 구성 |
| `.env.prod` | Compose 실행에 넣는 실제 환경변수, Git 제외 |
| `infra/aws/terraform/*.tf` | ECR 인프라 선언 |
| `infra/aws/terraform/terraform.tfvars` | Terraform 변수의 실제 값, Git 제외 |
| `infra/aws/terraform/.terraform.lock.hcl` | 실제 선택된 Provider 버전과 checksum |
| `infra/aws/terraform/terraform.tfstate` | Terraform이 관리하는 실제 AWS 리소스 상태, Git 제외 |
| `infra/aws/scripts/push-ecr-images.ps1` | 수동 명령을 묶은 선택적 자동화 스크립트 |

## 3. Backend Dockerfile 의미

`backend/Dockerfile`은 크게 build stage와 runtime stage로 나뉜다.

```text
build stage
→ Java 소스와 Gradle 설정을 사용해 실행 가능한 bootJar 생성

runtime stage
→ 생성된 JAR만 Java 17 JRE 이미지에 복사해 실행
```

멀티스테이지로 나누면 Gradle과 소스 전체를 운영 이미지에 넣지 않아도 된다. runtime은
non-root 사용자로 실행하며, `JAVA_OPTS`로 JVM 메모리 설정 등을 전달한다.

Healthcheck는 컨테이너 안의 `wget`으로 다음 주소를 검사한다.

```text
http://127.0.0.1:8080/actuator/health
```

## 4. Frontend Dockerfile과 빈 build argument

`frontend/Dockerfile`은 다음 stage로 구성된다.

```text
dependencies
→ pnpm dependency 설치

builder
→ Next.js production build

runner
→ standalone 결과만 복사해 non-root 사용자로 실행
```

현재 Dockerfile에는 다음 build argument 기본값이 있다.

```dockerfile
ARG NEXT_PUBLIC_API_BASE_URL=/backend-api
ARG NEXT_PUBLIC_OAUTH_BASE_URL=http://localhost:8080
```

이 기본값은 기존 로컬 Docker 실행을 유지하기 위한 값이다. 운영 Nginx 환경에서는 브라우저가
다음 경로로 요청해야 한다.

```text
/api/**
/oauth2/**
```

따라서 운영 이미지를 빌드할 때 기본값을 명시적으로 빈 문자열로 덮어쓴다.

```powershell
--build-arg "NEXT_PUBLIC_API_BASE_URL="
--build-arg "NEXT_PUBLIC_OAUTH_BASE_URL="
```

`이름=`에서 `=` 뒤가 비어 있으므로 전달값은 빈 문자열이다.

```text
API base URL = ""
API 요청 경로 = "/api/health"
최종 경로 = "" + "/api/health" = "/api/health"
```

OAuth도 같은 방식이다.

```text
OAuth base URL = ""
OAuth 경로 = "/oauth2/authorization/google"
최종 경로 = "/oauth2/authorization/google"
```

브라우저가 `/api/**`와 `/oauth2/**`를 현재 사이트로 요청하면 Nginx가 Backend로 전달한다.
`NEXT_PUBLIC_*` 값은 Next.js build 결과에 포함될 수 있는 공개값이다. Secret은
`--build-arg`로 전달하지 않는다.

Dockerfile의 기본값을 빈 문자열로 바꾸는 설계도 가능하다. 다만 그렇게 바꾸면 기존 로컬
빌드에서 `/backend-api`와 `http://localhost:8080`을 build argument로 명시해야 한다.
현재 구현은 기존 로컬 기본값을 유지하고 운영 빌드에서만 빈 값으로 덮어쓰는 방식을 사용한다.

## 5. Nginx 요청 흐름

브라우저는 Docker 내부의 `backend:8080` 주소를 알지 못한다. 브라우저는 Nginx에 요청하고
Nginx가 같은 Docker network의 Backend 또는 Frontend로 전달한다.

```text
브라우저 → Nginx → Frontend
브라우저 → Nginx → Backend
```

Route는 다음과 같다.

| 브라우저 요청 | Nginx 처리 |
| --- | --- |
| `/` | `frontend:3000` |
| `/api/**` | `backend:8080` |
| `/oauth2/**` | `backend:8080` |
| `/login/oauth2/**` | `backend:8080` |
| 정확히 `/actuator/health` | `backend:8080` |
| `/actuator`, 그 외 `/actuator/**` | 404 |

Prometheus는 외부 Nginx를 거치지 않고 Docker 내부 network에서
`backend:8080/actuator/prometheus`를 직접 수집한다.

## 6. Terraform 코드별 의미

Terraform은 같은 폴더의 모든 `.tf` 파일을 하나의 설정으로 합쳐 읽는다. `ecr.tf`를 직접
실행하는 것이 아니라 Terraform 폴더에서 `terraform plan` 또는 `terraform apply`를 실행한다.

### `versions.tf`

사용할 Terraform CLI와 AWS Provider 호환 범위를 선언한다.

```hcl
required_version = ">= 1.14.0, < 2.0.0"
```

```text
Terraform 1.14.0 이상, 2.0.0 미만 사용
```

AWS Provider는 Terraform 설정을 AWS API 요청으로 바꾸는 별도 프로그램이다.

### `providers.tf`

AWS 리전과 공통 resource tag를 설정한다.

```hcl
provider "aws" {
  region = var.aws_region
}
```

`var.aws_region`의 실제 값은 `terraform.tfvars`에서 읽는다.

### `variables.tf`와 `terraform.tfvars`

`variables.tf`는 받을 값의 이름, 타입, 기본값, 검증 규칙을 정의한다.
`terraform.tfvars`는 이번 실행에서 사용할 실제 값을 제공한다.

```text
variables.tf
→ aws_region이라는 string 값이 필요하다고 선언

terraform.tfvars
→ aws_region = "ap-northeast-2" 제공
```

파일명이 정확히 `terraform.tfvars`이면 `terraform plan`과 `terraform apply`가 자동으로
읽으므로 `-var-file` 옵션은 생략할 수 있다.

### `ecr.tf`

다음 두 Repository를 `for_each`로 만든다.

```text
finrisk-backend
finrisk-frontend
```

주요 정책은 다음과 같다.

```text
SHA와 milestone tag는 immutable
latest만 덮어쓰기 가능
push 시 이미지 scan
AES-256 암호화
untagged 이미지 7일 후 삭제
tagged 이미지 최근 30개 유지
```

### `outputs.tf`

AWS가 ECR Repository를 만든 뒤 반환한 실제 URL을 Terraform 출력으로 제공한다.

```powershell
terraform output -raw backend_repository_url
terraform output -raw frontend_repository_url
```

예상 형태:

```text
<AWS 계정 ID>.dkr.ecr.ap-northeast-2.amazonaws.com/finrisk-backend
<AWS 계정 ID>.dkr.ecr.ap-northeast-2.amazonaws.com/finrisk-frontend
```

### `.terraform.lock.hcl`

`terraform init`이 실제 선택한 AWS Provider 버전과 checksum을 기록한다. 다른 컴퓨터에서도
동일한 Provider 버전을 선택하게 하는 lockfile이다.

### `terraform.tfstate`

Terraform 코드의 resource와 실제 AWS resource를 연결하는 상태 장부다. `terraform apply`가
생성·갱신하며 Secret이 포함될 가능성이 있으므로 Git에 커밋하지 않는다.

## 7. Docker Registry, Repository, Tag, Digest

전체 이미지 주소는 다음 구조다.

```text
Registry/Repository:Tag
```

예:

```text
<account-id>.dkr.ecr.ap-northeast-2.amazonaws.com/finrisk-backend:sha-abcdef123456
```

| 부분 | 의미 |
| --- | --- |
| `<account-id>.dkr.ecr.ap-northeast-2.amazonaws.com` | AWS 계정·서울 리전의 ECR Registry 서버 |
| `finrisk-backend` | Registry 안의 Backend Repository |
| `sha-abcdef123456` | 사람이 지정한 이미지 버전 tag |
| `sha256:...` | Docker가 이미지 내용으로 계산한 digest |

AWS 계정 ID는 AWS가 계정을 만들 때 부여한 12자리 번호다. 직접 정하지 않는다.

```powershell
aws sts get-caller-identity --query Account --output text
```

수동 배포에서는 Registry 주소를 직접 조립하지 않고 `terraform output`이 반환한 Repository
URL에서 가져온다.

## 8. 코드 완성 후 실행 명령

아래 과정은 Windows PowerShell과 프로젝트 경로 `C:\Projects\finrisk-radar`를 기준으로 한다.

### 8.1 프로그램과 AWS 인증 확인

```powershell
cd C:\Projects\finrisk-radar

terraform version
aws --version
aws sts get-caller-identity
docker version
```

`docker version`에서 Client와 Server가 모두 나와야 한다. PowerShell의 `docker.exe`는 현재
Docker context를 통해 Docker Desktop이 실행하는 Docker Engine/BuildKit에 연결된다.

```powershell
docker context show
docker info
```

Docker Desktop이 꺼져 있으면 `docker.exe`가 설치되어 있어도 Engine에 연결할 수 없다.

### 8.2 애플리케이션 검사

```powershell
.\backend\gradlew.bat -p backend test bootJar
corepack pnpm@10.30.3 --dir frontend typecheck
corepack pnpm@10.30.3 --dir frontend test
corepack pnpm@10.30.3 --dir frontend lint
corepack pnpm@10.30.3 --dir frontend build
```

### 8.3 로컬 운영 유사 환경 검사

실제 `.env.prod`가 이미 있으면 복사하지 않는다. 내용을 직접 확인하고 placeholder와 Secret을
안전한 실제 값으로 바꾼다.

```powershell
notepad .env.prod
```

Compose 설정 검사와 실행:

```powershell
docker compose --env-file .env.prod -f infra/docker/docker-compose.prod.yml config --quiet
docker compose --env-file .env.prod -f infra/docker/docker-compose.prod.yml up -d --build
docker compose --env-file .env.prod -f infra/docker/docker-compose.prod.yml ps
```

Route 확인:

```powershell
Invoke-WebRequest http://localhost/ -UseBasicParsing
Invoke-RestMethod http://localhost/api/health
Invoke-RestMethod http://localhost/actuator/health

curl.exe -s -o NUL -w "%{http_code}" http://localhost/actuator
curl.exe -s -o NUL -w "%{http_code}" http://localhost/actuator/prometheus
curl.exe -s -o NUL -w "%{http_code}" http://localhost/actuator/health/
curl.exe -s -o NUL -w "%{http_code}" http://localhost/actuator/env
```

`/`, `/api/health`, 정확한 `/actuator/health`는 성공해야 한다. 나머지 Actuator 요청은 모두
404여야 한다.

볼륨을 보존하며 종료:

```powershell
docker compose --env-file .env.prod -f infra/docker/docker-compose.prod.yml down
```

`down -v`는 named volume의 데이터도 삭제하므로 초기화가 명확히 필요할 때만 사용한다.

### 8.4 Terraform으로 ECR 생성

Terraform 폴더로 이동한다.

```powershell
cd C:\Projects\finrisk-radar\infra\aws\terraform
```

`terraform.tfvars`가 이미 있으므로 복사하지 않는다. 필요하면 편집한다.

```powershell
notepad terraform.tfvars
```

처음 또는 Provider 설정이 달라졌을 때 초기화한다.

```powershell
terraform init
```

검사, 미리보기, 적용:

```powershell
terraform fmt -check -recursive
terraform validate
terraform plan
terraform apply
```

명령 의미:

```text
terraform fmt -check -recursive
→ 모든 Terraform 파일의 형식이 표준인지 검사, AWS 변경 없음

terraform validate
→ 문법·변수·resource 참조 검사, AWS 변경 없음

terraform plan
→ 생성·변경·삭제 예정 내용을 표시, AWS 변경 없음

terraform apply
→ 확인 후 AWS API를 호출하여 실제 resource 생성·변경
```

`terraform apply`가 `Enter a value:`를 물으면 계획을 다시 확인하고 `yes`를 입력한다.
예상하지 않은 삭제가 보이면 적용하지 않는다.

이미 ECR 구성이 적용된 상태라면 `terraform plan`과 `terraform apply`는 `No changes`를
표시한다.

### 8.5 실제 ECR 주소를 Terraform 출력에서 받기

Terraform 폴더에서 다음 값을 현재 PowerShell 창에 저장한다.

```powershell
$backendRepo = (terraform output -raw backend_repository_url).Trim()
$frontendRepo = (terraform output -raw frontend_repository_url).Trim()
$registry = $backendRepo.Split('/')[0]
$region = "ap-northeast-2"
```

확인:

```powershell
$backendRepo
$frontendRepo
$registry
$region
```

값의 출처:

```text
$backendRepo
→ AWS가 생성한 Backend Repository URL을 Terraform output으로 조회

$frontendRepo
→ AWS가 생성한 Frontend Repository URL을 Terraform output으로 조회

$registry
→ Backend Repository URL을 /로 나눈 뒤 첫 부분을 선택

$region
→ terraform.tfvars와 같은 서울 리전
```

PowerShell의 `$이름 = 값`은 현재 터미널 창에서 나중에 재사용할 값을 잠시 저장한다는 뜻이다.
창을 닫으면 이 변수들은 사라진다.

### 8.6 Git SHA 확인

프로젝트 루트로 돌아간다.

```powershell
cd C:\Projects\finrisk-radar
```

현재 commit의 12자리 SHA를 가져온다.

```powershell
$sha = (git rev-parse --short=12 HEAD).Trim()
$sha
git status --short
```

`git rev-parse`는 commit하거나 GitHub에 push하지 않는다. 현재 commit 번호를 읽기만 한다.
`git status --short`가 비어 있어야 SHA가 실제 빌드할 소스 상태와 일치한다.

SHA를 이미지 tag로 사용하는 이유:

```text
ECR tag sha-abcdef123456
→ Git commit abcdef123456
→ 어떤 소스로 만든 이미지인지 추적 가능
```

`latest`는 새 push로 대상이 바뀔 수 있지만 SHA tag는 바뀌지 않는다. Day 18 배포에서도
`latest` 대신 정확한 SHA tag를 사용한다.

### 8.7 Docker를 ECR Registry에 로그인

```powershell
aws ecr get-login-password --region $region |
    docker login --username AWS --password-stdin $registry
```

이 명령은 다음 순서로 동작한다.

```text
1. AWS CLI가 현재 PC의 AWS credential로 ECR에 요청
2. AWS가 IAM 권한을 검사하고 임시 ECR 로그인 비밀번호 발급
3. | 파이프가 비밀번호를 Docker의 표준 입력으로 전달
4. Docker가 username AWS와 임시 비밀번호로 $registry에 로그인
```

`AWS`는 실제 IAM 사용자 이름이 아니라 ECR Docker 인증에서 정한 고정 username이다.
비밀번호는 AWS 콘솔 비밀번호나 Secret Access Key가 아니라 AWS가 발급한 임시 ECR 인증값이다.
`--password-stdin`은 이 값을 명령문이나 화면에 직접 노출하지 않고 전달한다.

로그인 성공은 ECR Repository 생성이 아니라 로컬 Docker가 해당 Registry에 push/pull할 수
있도록 인증되었다는 뜻이다. 실제 권한 범위는 로그인 토큰을 요청한 IAM 사용자나 Role의
정책으로 결정된다.

### 8.8 Backend 이미지 빌드

```powershell
docker build `
    --provenance=false `
    -f backend/Dockerfile `
    -t "${backendRepo}:sha-${sha}" `
    backend
```

각 부분의 의미:

```text
docker build
→ Docker Engine/BuildKit에 이미지 생성을 요청

--provenance=false
→ 추가 build provenance attestation manifest 생성 비활성화
→ 현재 immutable ECR tag와의 push 충돌을 피하기 위해 사용

-f backend/Dockerfile
→ -f는 --file의 축약형이며 사용할 Dockerfile 지정

-t "${backendRepo}:sha-${sha}"
→ -t는 --tag의 축약형이며 로컬 이미지에 전체 ECR 이름과 버전 tag 부여

마지막 backend
→ Docker build context, Dockerfile이 사용할 수 있는 파일 범위
```

`-t`는 이미지를 업로드하지 않는다. 로컬 이미지에 push할 목적지와 tag가 포함된 이름을
붙일 뿐이다.

### 8.9 Backend 이미지 push

```powershell
docker push "${backendRepo}:sha-${sha}"
```

Docker는 전체 이름을 다음처럼 해석한다.

```text
Registry → 어느 ECR 서버로 보낼지
Repository → finrisk-backend
Tag → sha-<현재 Git SHA>
```

로컬 image layer와 manifest를 ECR에 업로드하고 SHA tag를 연결한다.

### 8.10 Frontend 이미지 빌드

```powershell
docker build `
    --provenance=false `
    -f frontend/Dockerfile `
    --build-arg "NEXT_PUBLIC_API_BASE_URL=" `
    --build-arg "NEXT_PUBLIC_OAUTH_BASE_URL=" `
    -t "${frontendRepo}:sha-${sha}" `
    frontend
```

`--build-arg 이름=값`은 Dockerfile의 `ARG 이름`에 build-time 값을 전달한다. 여기서는
`=` 뒤가 비어 있으므로 기존 Dockerfile 기본값을 빈 문자열로 덮어쓴다. 그 결과 운영
브라우저 요청이 `/backend-api/api/**`나 `localhost:8080`이 아니라 `/api/**`와
`/oauth2/**`가 된다.

마지막 `frontend`는 Frontend build context다. `.dockerignore`에 제외되지 않은 이 폴더의
파일이 Docker build에 전달된다.

### 8.11 Frontend 이미지 push

```powershell
docker push "${frontendRepo}:sha-${sha}"
```

Frontend 이미지 layer와 manifest를 `finrisk-frontend` Repository에 업로드하고 같은 Git
SHA tag를 연결한다.

### 8.12 ECR 이미지 조회

Backend:

```powershell
aws ecr describe-images `
    --region $region `
    --repository-name finrisk-backend `
    --image-ids "imageTag=sha-$sha"
```

Frontend:

```powershell
aws ecr describe-images `
    --region $region `
    --repository-name finrisk-frontend `
    --image-ids "imageTag=sha-$sha"
```

옵션 의미:

```text
aws ecr
→ AWS CLI에서 ECR 서비스 사용

describe-images
→ ECR 이미지 정보를 조회하는 읽기 전용 작업

--region $region
→ 서울 리전 조회

--repository-name finrisk-backend
→ Backend Repository 선택

--image-ids "imageTag=sha-$sha"
→ 현재 Git SHA tag를 가진 이미지 하나를 선택
```

`$sha`가 `abcdef123456`이면 마지막 옵션은 다음으로 변환된다.

```text
imageTag=sha-abcdef123456
```

결과에는 Repository 이름, image tag, image digest, 크기, push 시간 등이 표시된다. 이
명령은 이미지를 다운로드하거나 실행하거나 변경하지 않는다.

## 9. 최초 구축과 반복 배포 차이

### 최초 구축 또는 Terraform 인프라 변경

```text
terraform init
terraform validate
terraform plan
terraform apply
ECR 로그인
docker build
docker push
```

### ECR은 그대로이고 애플리케이션 코드만 변경

```text
테스트
변경사항을 정확한 Git commit으로 기록
새 Git SHA 확인
ECR 로그인
docker build
docker push
```

애플리케이션 코드만 변경할 때마다 Terraform을 다시 적용할 필요는 없다. Terraform 코드나
ECR 정책이 변경된 경우에 plan과 apply를 수행한다.

같은 SHA tag가 이미 ECR에 있으면 immutable 정책 때문에 다른 이미지로 덮어쓸 수 없다.
코드가 바뀌었다면 새 commit의 새 SHA tag를 사용한다. 이미 같은 이미지를 올린 경우에는
다시 push할 필요가 없다.

## 10. 선택적 자동화 스크립트

`infra/aws/scripts/push-ecr-images.ps1`은 필수가 아니다. 다음 수동 작업을 순서대로 묶고 오류
검사와 기존 immutable tag 검사를 추가한 편의 도구다.

```text
Git 상태와 SHA 확인
AWS 계정과 ECR Repository 확인
ECR 로그인
Backend/Frontend build
SHA tag push
선택적 milestone/latest push
결과 출력
```

원리를 학습할 때는 이 문서의 수동 명령을 사용하고, 반복 배포에서는 스크립트나 CI/CD가 같은
작업을 대신 실행할 수 있다.

## 11. 주요 장애 확인

### Docker Engine 연결 실패

```powershell
docker context show
docker info
```

Docker Desktop 실행 여부와 현재 Docker context를 확인한다.

### ECR 로그인 실패

```powershell
aws sts get-caller-identity
aws configure list
```

현재 AWS 인증 주체, region, `ecr:GetAuthorizationToken` 권한을 확인한다.

### Nginx 502

```powershell
docker compose --env-file .env.prod -f infra/docker/docker-compose.prod.yml ps
docker compose --env-file .env.prod -f infra/docker/docker-compose.prod.yml logs --tail 100 backend frontend nginx
```

Backend/Frontend health, 동일 network 연결, upstream 서비스 이름을 확인한다.

### `/backend-api/api` 또는 `//api`

Frontend production 이미지가 빈 public base URL build argument로 빌드됐는지 확인한다.

### Immutable tag 오류

이미 등록된 SHA tag를 다른 이미지로 덮어쓰지 않는다. 코드가 달라졌다면 새 commit의 SHA로
새 tag를 만든다.

## 12. Day 18 인계

Day 18 배포에서는 `latest`가 아니라 이번 push 결과의 정확한 SHA tag를 사용한다.

```text
<account-id>.dkr.ecr.ap-northeast-2.amazonaws.com/finrisk-backend:sha-<commit>
<account-id>.dkr.ecr.ap-northeast-2.amazonaws.com/finrisk-frontend:sha-<commit>
```

EC2의 ECR pull 권한, runtime Secret 주입, 실제 배포용 Compose 구성은 Day 18 범위에서 처리한다.
