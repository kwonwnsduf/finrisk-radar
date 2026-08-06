# Day 18 구조와 코드 흐름 가이드

이 문서는 Day18 코드를 처음 보는 사람이 다음 세 가지를 이해하기 위한 안내서다.

1. 전체 구조가 어떻게 생겼는가
2. 어떤 파일이 어떤 파일을 사용하거나 실행하는가
3. 최초 인프라 생성과 평소 배포가 어떤 순서로 진행되는가

세부 Bash 문법이나 Terraform 속성을 외우는 것이 목적은 아니다. 먼저 큰 흐름을 이해한 뒤,
필요한 파일만 찾아볼 수 있으면 된다.

## 1. Day18을 한 문장으로 설명하면

Terraform으로 AWS 인프라를 만들고, GitHub Actions가 ECR에 이미지를 올린 뒤, SSM을 통해
EC2의 Docker Compose를 갱신하는 구조다.

```text
Terraform
  → VPC, EC2, RDS, IAM, CloudWatch 생성

GitHub Actions
  → Docker 이미지 Build
  → ECR Push
  → SSM으로 EC2에 배포 명령 전달

EC2
  → SSM 비밀값 조회
  → ECR 이미지 Pull
  → Docker Compose 실행
```

## 2. 전체 실행 구조

```text
사용자
  │
  │ HTTP 80
  ▼
EC2 t3.small
  └─ Docker Compose
      ├─ Nginx
      ├─ Frontend
      ├─ Backend
      ├─ Redis
      ├─ Kafka
      └─ ZooKeeper
           │
           ├──────────────→ RDS PostgreSQL
           ├──────────────→ 기존 S3 finrisk-raw-data
           └──────────────→ 외부 API

Docker 및 EC2 로그
  └──────────────────────→ CloudWatch Logs
```

PostgreSQL은 EC2 컨테이너가 아니라 별도 RDS에서 실행한다. Redis, Kafka, ZooKeeper는 비용을
줄이기 위해 애플리케이션과 같은 EC2 한 대에서 실행한다.

## 3. 가장 중요한 구분: 생성과 배포

Day18에는 서로 다른 두 종류의 자동화가 있다.

| 구분 | 도구 | 하는 일 | 평소 실행 여부 |
| --- | --- | --- | --- |
| 인프라 생성 | Terraform | EC2, RDS, 네트워크, IAM 생성 | 인프라 변경 시 |
| 애플리케이션 배포 | GitHub Actions | 이미지 Build, ECR Push, EC2 배포 | 코드 Push 시 |

Terraform은 애플리케이션을 매번 배포하는 도구가 아니다. GitHub Actions도 VPC와 RDS를
매번 생성하지 않는다.

```text
Terraform apply
  → AWS 인프라를 생성하거나 변경

git push
  → 이미 존재하는 EC2에 새 애플리케이션 버전을 배포
```

## 4. 최초 구축 흐름

### 4.1 State 버킷을 먼저 생성

실행 대상:

```text
infra/aws/terraform/bootstrap/day18-state/
```

호출 흐름:

```text
terraform apply
  → bootstrap/day18-state/main.tf
  → Terraform State 전용 S3 버킷 생성
  → 버킷 이름 출력
```

이 단계는 애플리케이션용 AWS 자원을 만들지 않는다. Day18 state를 저장할 S3 버킷만
만든다.

```text
finrisk-radar-422060263480-tfstate
└─ finrisk-radar/day18/terraform.tfstate
```

State 버킷은 Day18 환경과 별도 state로 관리된다. 따라서 Day18 환경을 destroy해도 State
버킷은 삭제되지 않는다.

### 4.2 Day18 Terraform 초기화

실행 대상:

```text
infra/aws/terraform/environments/day18/
```

```powershell
terraform init -backend-config=backend.hcl
```

이 명령의 입력 관계는 다음과 같다.

```text
backend.tf
  → S3 backend를 사용한다고 선언

backend.hcl
  → 실제 State 버킷 이름 전달

Terraform
  → 이후 state를 S3에 읽고 저장
```

### 4.3 Terraform plan 및 apply

Terraform은 `environments/day18` 폴더의 모든 `.tf` 파일을 하나의 설정으로 자동으로 읽는다.
`main.tf`가 `providers.tf`를 순서대로 호출하는 방식은 아니다.

```text
versions.tf   ─┐
providers.tf  ─┤
variables.tf  ─┼→ Terraform이 모두 함께 읽음
main.tf       ─┤
outputs.tf    ─┤
backend.tf    ─┘

day18.auto.tfvars
  → variables.tf에 선언된 변수의 실제 값 제공
```

그다음 `main.tf`가 네 모듈을 조립한다.

```text
environments/day18/main.tf
  ├─ module.network
  ├─ module.data
  ├─ module.compute
  └─ module.cicd
```

모듈 연결 순서는 다음과 같다.

```text
network
  │ Private Subnet, Security Group
  ▼
data
  │ RDS 주소, CloudWatch Log Group
  ▼
compute
  │ EC2 Instance ID
  ▼
cicd
  └─ 해당 EC2에 배포할 수 있는 GitHub IAM 권한 생성
```

### 4.4 EC2 최초 부팅

`compute/main.tf`는 `bootstrap.sh`를 EC2 User Data로 전달한다.

```hcl
user_data_base64 = filebase64("${path.module}/files/bootstrap.sh")
```

실행 흐름:

```text
Terraform이 EC2 생성
  → Amazon Linux가 최초 부팅
  → EC2 User Data 실행
  → bootstrap.sh 실행
  → Docker 설치
  → Docker Compose 설치
  → 비상용 swap 설정
  → CloudWatch Agent 설치
```

`bootstrap.sh`는 애플리케이션을 배포하지 않는다. 새 EC2를 Docker를 실행할 수 있는 상태로
준비하는 파일이다.

## 5. 평소 CI/CD 배포 흐름

평소에는 Terraform을 실행하지 않는다. `main` 브랜치 Push 또는 수동 Workflow 실행으로
GitHub Actions가 시작된다.

```text
git push
  ▼
.github/workflows/deploy-day18.yml
  │
  ├─ 1. 소스 Checkout
  ├─ 2. GitHub OIDC로 임시 AWS 권한 획득
  ├─ 3. Backend와 Frontend Docker 이미지 Build
  ├─ 4. ECR에 이미지 Push
  ├─ 5. 이미지 digest와 EC2 Instance ID 조회
  ├─ 6. SSM SendCommand 실행
  ▼
EC2
  └─ deploy-day18-via-ssm.sh 실행
      ├─ 배포 설정 파일 다운로드
      ├─ EC2 태그에서 공개 설정 조회
      ├─ SSM Parameter Store에서 비밀값 조회
      ├─ .env.prod 생성
      ├─ ECR 로그인
      ├─ Docker 이미지 Pull
      ├─ docker compose up
      └─ 컨테이너 상태 출력
```

현재 `deploy-day18.yml`에는 Gradle 또는 Frontend 테스트 명령이 없다. 이 Workflow는 이미지
Build와 배포를 담당한다. 따라서 이 문서에서 말하는 Day18 CI/CD의 실제 자동화 범위는
`Build → Push → Deploy → 공개 Health Check`다.

### 5.1 `cicd/main.tf`가 하는 일

`modules/cicd/main.tf`는 배포를 직접 실행하지 않는다. 다음 권한을 가진 GitHub Actions용
IAM Role을 만든다.

```text
ECR 이미지 Push 권한
SSM SendCommand 권한
EC2 및 SSM 배포 결과 조회 권한
```

예를 들어 다음 코드는 SSM 명령을 실행하는 코드가 아니다.

```hcl
actions = ["ssm:SendCommand"]
```

GitHub Actions가 나중에 SSM 명령을 보낼 수 있도록 허용한다는 뜻이다.

### 5.2 실제로 배포를 시작하는 파일

실제 CI/CD 시작점은 다음 파일이다.

```text
.github/workflows/deploy-day18.yml
```

역할을 비유하면 다음과 같다.

```text
modules/cicd/main.tf
  = GitHub 작업자에게 AWS 출입증 발급

deploy-day18.yml
  = 배포 작업 지시 및 전체 순서 제어

deploy-day18-via-ssm.sh
  = EC2 안에서 수행할 실제 작업 방법
```

### 5.3 배포 스크립트가 다운로드하는 파일

`deploy-day18-via-ssm.sh`는 배포 대상 Git commit에서 다음 파일을 EC2로 다운로드한다.

```text
infra/aws/deploy/day18/docker-compose.yml
infra/aws/deploy/day18/cloudwatch-agent.json
infra/nginx/nginx.conf
```

그다음 EC2의 `/opt/finrisk`에 배치한다.

```text
/opt/finrisk/
├─ docker-compose.yml
├─ nginx.conf
├─ cloudwatch-agent.json
├─ .env.prod
├─ release.env
└─ deploy.sh
```

현재 Workflow의 자동 Push 경로에는 Backend, Frontend, 배포 스크립트, Workflow 파일이
포함된다. Compose, Nginx 또는 CloudWatch 설정만 변경한 경우에는 GitHub Actions의
`workflow_dispatch`로 수동 실행해야 해당 변경이 EC2에 반영된다.

## 6. 애플리케이션 요청 흐름

배포가 완료된 뒤 사용자의 HTTP 요청은 다음 순서로 흐른다.

```text
브라우저
  │ HTTP 80
  ▼
EC2 Security Group
  ▼
Nginx 컨테이너
  ├─ /api/*, /oauth2/*, /login/oauth2/*
  │    └─ Backend:8080
  │
  ├─ /actuator/health
  │    └─ Backend:8080
  │
  └─ 그 외 경로
       └─ Frontend:3000
```

Backend의 내부 연결은 다음과 같다.

```text
Backend
  ├─ Redis:6379
  ├─ Kafka:9092
  ├─ RDS PostgreSQL:5432
  ├─ S3 finrisk-raw-data
  └─ Google, Toss, DART, Naver, OpenAI API
```

Redis, Kafka, ZooKeeper, Backend, Frontend 포트는 Security Group에서 외부에 공개하지 않는다.
외부에는 Nginx의 80번 포트만 공개한다.

## 7. 설정값과 비밀값 흐름

### 7.1 공개 설정값

공개값은 Git에서 제외된 `day18.auto.tfvars`에 저장한다.

```text
day18.auto.tfvars
  → variables.tf
  → environments/day18/main.tf
  → compute 모듈
  → EC2 태그
  → deploy-day18-via-ssm.sh가 태그 조회
  → .env.prod
```

대표적인 공개값:

- Google Client ID
- Toss Widget Client Key
- Naver Client ID
- OpenAI 모델 이름

Toss Widget Client Key는 Frontend 이미지 Build에도 필요하므로 GitHub Repository Variable에도
같은 공개값을 등록한다.

### 7.2 비밀값

비밀값 자체는 Terraform 코드나 tfvars에 저장하지 않는다.

```text
SSM Parameter Store SecureString
  → EC2 IAM Role로 조회
  → deploy-day18-via-ssm.sh
  → /opt/finrisk/.env.prod
  → Backend 및 Redis 컨테이너
```

대표적인 비밀값:

- PostgreSQL Password
- Redis Password
- JWT Secret
- OAuth Client Secret
- Toss Secret Key
- 외부 API Key

`register-day18-secret.ps1`은 이 값을 SSM에 등록하거나 교체할 때 사용하는 선택적 도구다.
이미 SSM에 값이 존재하면 평소 배포에서는 실행하지 않는다.

RDS 비밀번호만 Terraform apply 중 ephemeral SSM 조회를 거쳐 RDS의 write-only password 입력에
전달된다. 평문 값을 Terraform state에 저장하지 않기 위한 구성이다.

## 8. 로그 흐름

로그 전송 방법은 두 가지다.

### 8.1 Docker 컨테이너 로그

`docker-compose.yml`의 `awslogs` 드라이버가 직접 전송한다.

```text
Backend, Frontend, Nginx, Redis, Kafka, ZooKeeper 로그
  → Docker awslogs
  → /finrisk/day18/containers
```

### 8.2 EC2 파일 로그

CloudWatch Agent가 `cloudwatch-agent.json` 설정을 읽어 전송한다.

```text
/var/log/finrisk-bootstrap.log
/var/log/finrisk-deploy.log
  → CloudWatch Agent
  → /finrisk/day18/bootstrap
```

`cloudwatch-agent.json`은 실행 스크립트가 아니다. CloudWatch Agent에 어떤 파일을 어떤 Log
Group으로 보낼지 알려주는 설정 파일이다.

## 9. Terraform State 분리 구조

```text
Day17 ECR state
  └─ finrisk-backend, finrisk-frontend 관리

Day18 bootstrap state
  └─ Terraform State용 S3 버킷 관리

Day18 environment state
  └─ VPC, EC2, RDS, IAM, CloudWatch 관리
```

Day18은 기존 자원을 다음 data source로 조회만 한다.

```text
data.aws_ecr_repository.backend
data.aws_ecr_repository.frontend
data.aws_s3_bucket.application
```

따라서 Day18 destroy는 기존 ECR Repository와 `finrisk-raw-data` 버킷을 삭제하지 않는다.

## 10. 폴더 구조와 의미

```text
infra/aws/
├─ terraform/
│  ├─ bootstrap/day18-state/       State 버킷 전용 Terraform
│  ├─ environments/day18/          Day18 Terraform 실행 시작점
│  └─ modules/
│     ├─ network/                  VPC, Subnet, Route, Security Group
│     ├─ data/                     RDS와 CloudWatch Log Group
│     ├─ compute/                  EC2, EC2 IAM, 최초 부팅
│     └─ cicd/                     GitHub OIDC와 배포 IAM 권한
│
├─ deploy/day18/
│  ├─ docker-compose.yml           EC2에서 실행할 컨테이너 구성
│  └─ cloudwatch-agent.json        EC2 파일 로그 수집 설정
│
└─ scripts/
   ├─ deploy-day18-via-ssm.sh      EC2 내부 실제 배포 작업
   ├─ register-day18-secret.ps1    선택적 SSM 비밀값 등록 도구
   └─ push-ecr-images.ps1          선택적 수동 ECR Push 도구

.github/workflows/
└─ deploy-day18.yml                평소 CI/CD 시작점

infra/nginx/
└─ nginx.conf                      HTTP 요청 라우팅 설정
```

## 11. 파일별 의미

### 11.1 Bootstrap Stack

| 파일 | 의미 | 누가 읽는가 |
| --- | --- | --- |
| `bootstrap/day18-state/main.tf` | State용 S3 버킷 생성 | Terraform |
| `providers.tf` | AWS Provider와 리전 설정 | Terraform |
| `variables.tf` | 리전과 선택적 버킷 이름 선언 | Terraform |
| `outputs.tf` | 생성된 버킷 이름 출력 | Terraform |
| `versions.tf` | Terraform 및 Provider 버전 제한 | Terraform |
| `terraform.tfvars.example` | Bootstrap 입력 예제 | 사람 |

### 11.2 Day18 Environment

| 파일 | 의미 | 누가 읽는가 |
| --- | --- | --- |
| `backend.tf` | S3 backend 사용 선언과 state key | Terraform |
| `backend.hcl` | 실제 State 버킷 이름 | `terraform init` |
| `backend.hcl.example` | `backend.hcl` 복사용 예제 | 사람 |
| `versions.tf` | Terraform과 AWS Provider 버전 제한 | Terraform |
| `providers.tf` | AWS Provider, 기존 ECR 및 S3 조회 | Terraform |
| `variables.tf` | Day18에서 받을 입력 선언 | Terraform |
| `day18.auto.tfvars` | 공개 입력의 실제 로컬 값 | Terraform 자동 로드 |
| `day18.auto.tfvars.example` | 로컬 입력 파일의 복사용 예제 | 사람 |
| `main.tf` | 네 모듈 조립과 값 연결 | Terraform |
| `outputs.tf` | EC2 URL, RDS 주소, IAM Role 출력 | Terraform |

`backend.hcl`과 `day18.auto.tfvars`는 Git에 올리지 않는다. 비밀값을 넣는 파일도 아니다.

### 11.3 각 Module 공통 파일

| 파일 | 의미 |
| --- | --- |
| `variables.tf` | 모듈이 외부에서 받을 입력 |
| `main.tf` | 입력으로 생성할 AWS 자원 |
| `outputs.tf` | 다른 모듈에 전달할 생성 결과 |

모듈 공부 순서:

```text
network → data → compute → cicd
```

### 11.4 배포 파일

| 파일 | 필수 여부 | 의미 |
| --- | --- | --- |
| `.github/workflows/deploy-day18.yml` | 필수 | CI/CD 전체 순서 |
| `deploy-day18-via-ssm.sh` | 현재 구조에서 필수 | EC2 내부 배포 명령 |
| `docker-compose.yml` | 필수 | 실행할 컨테이너와 제한 설정 |
| `nginx.conf` | 필수 | Frontend와 Backend 라우팅 |
| `cloudwatch-agent.json` | 로그 수집에 필요 | EC2 파일 로그 수집 목록 |
| `bootstrap.sh` | 새 EC2 생성 시 필요 | Docker와 Agent 최초 설치 |
| `register-day18-secret.ps1` | 선택 | SSM 비밀값 등록 편의 도구 |
| `push-ecr-images.ps1` | 선택 | CI/CD 없이 수동 ECR Push할 때 사용 |

## 12. 자동 생성 파일과 공부하지 않아도 되는 파일

### `.terraform/`

`terraform init`이 만드는 로컬 캐시다.

```text
.terraform/modules/modules.json
.terraform/providers/...
```

직접 수정하지 않고 Git에도 올리지 않는다. 삭제해도 `terraform init`으로 다시 생성된다.

### `.terraform.lock.hcl`

Terraform이 선택한 Provider의 정확한 버전과 checksum을 기록한다. 자동 생성되지만 팀원과 같은
Provider를 사용하기 위해 Git에는 올린다. 직접 수정하지 않는다.

### `terraform.tfstate`

Terraform이 실제 AWS 자원과 Terraform 주소를 연결하는 기록이다. 자동 생성되며 직접 수정하지
않는다. Day18 environment state는 로컬이 아니라 State 전용 S3 버킷에 저장된다.

### `*.tfplan`

`terraform plan -out`이 생성하는 실행 계획 바이너리다. 사람이 편집하거나 Git에 올리는 파일이
아니다.

## 13. 언제 어떤 파일이 사용되는가

| 상황 | 시작 파일 또는 명령 | 주요 연결 파일 |
| --- | --- | --- |
| State 버킷 최초 생성 | Bootstrap `terraform apply` | Bootstrap `.tf` 파일 |
| Day18 인프라 생성 | Environment `terraform apply` | Environment와 Modules |
| EC2 최초 부팅 | EC2 User Data | `bootstrap.sh` |
| 일반 코드 배포 | `deploy-day18.yml` | ECR, SSM, 배포 스크립트 |
| EC2 내부 배포 | `deploy-day18-via-ssm.sh` | Compose, Nginx, CloudWatch 설정 |
| 비밀값 최초 등록·교체 | 선택적 등록 스크립트 또는 AWS CLI | SSM Parameter Store |
| 수동 ECR Push | 선택적 Push 스크립트 또는 Docker/AWS CLI | ECR |
| 사용자 HTTP 요청 | `nginx.conf` | Frontend 또는 Backend |

## 14. 추천 공부 순서

모든 파일을 순서대로 읽지 않는다. 다음 순서가 가장 덜 헷갈린다.

```text
1. 이 문서의 전체 흐름
2. environments/day18/main.tf
3. modules/network
4. modules/data
5. modules/compute
6. modules/cicd
7. docker-compose.yml
8. deploy-day18.yml
9. deploy-day18-via-ssm.sh의 큰 단계만 확인
10. bootstrap과 CloudWatch 설정은 필요할 때 확인
```

각 Terraform 모듈 안에서는 다음 순서로 읽는다.

```text
variables.tf → main.tf → outputs.tf
```

당장 외워야 할 핵심 흐름은 하나다.

```text
Terraform이 서버를 만들고
→ GitHub Actions가 이미지를 ECR에 올리고
→ SSM이 EC2에 명령을 전달하고
→ EC2의 Docker Compose가 새 컨테이너를 실행한다.
```
