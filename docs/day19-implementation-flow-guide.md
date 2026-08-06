# Day 19 구현 흐름과 파일 호출 관계

이 문서는 Day 19 구현을 작은 실행 단위부터 따라가며 이해하기 위한 안내서다.
처음부터 전체 AWS 구조를 외우지 않고, 다음 순서로 범위를 넓힌다.

```text
컨테이너 한 묶음
→ EC2 한 대 배포
→ 배포 버전 정보
→ 새 EC2 최초 부팅
→ 애플리케이션 EC2 두 대 순차 배포
→ ALB와 ASG를 포함한 전체 인프라
```

---

## 1. 먼저 구분해야 하는 세 가지 저장소

Day 19 배포에서는 S3, ECR, SSM Parameter Store가 서로 다른 데이터를 보관한다.

| 저장소 | 저장하는 것 | 예시 |
| --- | --- | --- |
| ECR | 실제 실행 가능한 Docker 이미지 | Backend, Frontend 이미지 |
| S3 | 배포 방법을 적은 파일 | 배포 스크립트, Compose, Nginx, CloudWatch 설정 |
| SSM Parameter Store | Secret과 현재 승인된 배포 버전 정보 | DB 비밀번호, release manifest |

관계를 한 줄로 나타내면 다음과 같다.

```text
SSM release manifest가 버전을 선택
→ S3에서 그 버전의 배포 설정을 받음
→ ECR에서 그 버전의 Docker 이미지를 받음
→ Docker Compose로 실행
```

SSM에 Docker 이미지 자체가 저장되는 것은 아니다. 다음과 같은 ECR 이미지 주소만 저장된다.

```text
...amazonaws.com/finrisk-backend@sha256:...
```

---

## 2. 가장 작은 실행 단위: Docker Compose

Day 19는 기존 한 대의 EC2 역할을 `application`과 `runtime`으로 나눈다.

### 2.1 Application EC2

파일: `infra/aws/deploy/day19/docker-compose.app.yml`

```text
Application EC2
├─ nginx
├─ frontend
└─ backend
```

Application Backend는 HTTP API 요청만 처리한다.

```yaml
APP_WORKER_ENABLED: "false"
SPRING_KAFKA_LISTENER_AUTO_STARTUP: "false"
```

Redis와 Kafka는 Application EC2 안에서 실행하지 않는다. `RUNTIME_HOST`를 통해 Runtime
EC2에 접속한다.

```yaml
REDIS_HOST: ${RUNTIME_HOST}
KAFKA_BOOTSTRAP_SERVERS: ${RUNTIME_HOST}:9092
```

Application EC2는 같은 구성을 가진 두 대가 ASG에 포함된다. 한 대가 배포 또는 장애로 빠져도
다른 한 대가 요청을 처리한다.

### 2.2 Runtime EC2

파일: `infra/aws/deploy/day19/docker-compose.runtime.yml`

```text
Runtime EC2
├─ redis
├─ zookeeper
├─ kafka
└─ worker
```

Worker는 Backend와 같은 Docker 이미지를 사용하지만 백그라운드 역할을 활성화한다.

```yaml
APP_WORKER_ENABLED: "true"
SPRING_KAFKA_LISTENER_AUTO_STARTUP: "true"
```

즉, 이미지가 두 종류의 프로그램으로 나뉘는 것이 아니다. 같은 Backend 이미지를 서로 다른
환경변수로 실행한다.

```text
같은 Backend 이미지
├─ Application EC2: HTTP API 역할
└─ Runtime EC2: Kafka Consumer와 백그라운드 작업 역할
```

### 2.3 왜 Redis와 Kafka를 Application EC2마다 두지 않는가

Application EC2마다 Redis와 Kafka를 실행하면 서버별 상태가 분리된다.

```text
Application 1 → Redis 1, Kafka 1
Application 2 → Redis 2, Kafka 2
```

이 경우 로그인 토큰, 사용량 제한, 메시지 처리 상태가 서로 달라질 수 있다. Application EC2가
ASG에 의해 교체될 때 해당 데이터도 함께 사라질 수 있다. 따라서 Day 19에서는 두 Application
EC2가 한 Runtime EC2의 Redis와 Kafka를 공동으로 사용한다.

Runtime EC2 한 대는 단일 장애 지점이다. 현재 구조는 비용을 줄인 중간 단계이며, 이후에는
Redis를 ElastiCache로, Kafka를 MSK로, Worker를 별도 ASG나 ECS로 옮길 수 있다.

---

## 3. EC2 한 대를 실제로 배포하는 파일

파일: `infra/aws/scripts/deploy-day19-via-ssm.sh`

이 스크립트가 EC2 한 대의 실질적인 배포를 담당한다. 호출자는 두 종류다.

```text
새 Application EC2
→ bootstrap.sh가 호출

기존 Application/Runtime EC2
→ GitHub Actions가 SSM을 통해 호출
```

### 3.1 새 release 배포 호출

```bash
deploy-day19-via-ssm.sh \
  application \
  "$backend_image" \
  "$frontend_image" \
  "$source_ref"
```

인자의 의미는 다음과 같다.

| 인자 | 의미 |
| --- | --- |
| `application` 또는 `runtime` | 이 EC2에서 실행할 Compose 역할 |
| `backend_image` | Backend ECR digest 주소 |
| `frontend_image` | Frontend ECR digest 주소 |
| `source_ref` | 배포 파일을 만든 40자리 Git commit SHA |

### 3.2 재부팅 후 기존 설정 재사용 호출

```bash
deploy-day19-via-ssm.sh --reuse
```

`--reuse`는 새 release를 배포하는 기능이 아니다. EC2 재부팅 후 systemd가 기존에 검증된
로컬 파일을 사용하여 컨테이너를 다시 시작할 때 사용한다.

```text
/opt/finrisk/docker-compose.yml
/opt/finrisk/.env.prod
/opt/finrisk/release.env
```

### 3.3 스크립트 내부 실행 순서

```text
1. role, ECR 이미지 주소, Git SHA 형식 검증
2. IMDSv2로 EC2 instance ID와 region 조회
3. EC2 tag에서 공개 설정 조회
4. 새 release이면 S3에서 배포 파일 다운로드
5. SSM SecureString에서 Secret 조회
6. /opt/finrisk/.env.prod 생성
7. systemd unit 생성
8. CloudWatch Agent 시작
9. ECR 로그인
10. docker compose pull
11. docker compose up
12. 역할별 readiness 검사
13. 성공한 release를 release.env에 기록
```

### 3.4 EC2 tag에서 읽는 값

Terraform이 Launch Template 또는 Runtime EC2에 tag를 넣고, 배포 스크립트가 IMDSv2로
읽는다.

| EC2 tag | 배포 시 사용처 |
| --- | --- |
| `FinriskDbAddress` | RDS JDBC 주소 생성 |
| `FinriskRuntimeHost` | Application이 Redis/Kafka에 접속 |
| `FinriskApplicationBucket` | S3 배포 파일 경로 생성 |
| `FinriskGoogleClientId` | Google OAuth 공개 Client ID |
| `FinriskContainerLogGroup` | Docker awslogs 목적지 |
| `FinriskPublicBaseUrl` | OAuth와 결제의 공개 HTTPS URL |

### 3.5 SSM에서 읽는 Secret

배포 스크립트는 다음 값을 `--with-decryption`으로 조회한다.

```text
/finrisk/day18/postgres/password
/finrisk/day18/redis/password
/finrisk/day18/jwt/secret
/finrisk/day18/google/client-secret
/finrisk/day18/toss/widget-secret-key
/finrisk/day18/dart/api-key
/finrisk/day18/naver/client-secret
/finrisk/day18/openai/api-key
```

이 값은 Git, S3 배포 파일, EC2 tag에 저장하지 않는다. EC2 안에서는 권한을 `0600`으로 제한한
`/opt/finrisk/.env.prod`에 기록된다.

---

## 4. S3 배포 파일은 누가 만들고 누가 받는가

### 4.1 GitHub Actions가 업로드한다

파일: `.github/workflows/deploy-day19.yml`

`Publish immutable deployment assets` 단계가 다음 prefix를 조립한다.

```bash
artifact_base="s3://$APPLICATION_BUCKET/deploy/day19/$GITHUB_SHA"
```

S3에는 실제 디렉터리가 없다. 다음과 같은 object key prefix를 문자열로 만드는 것이다.

```text
s3://finrisk-raw-data/deploy/day19/<Git-SHA>/
```

그 아래에 다음 파일을 업로드한다.

```text
deploy-day19-via-ssm.sh
docker-compose.application.yml
docker-compose.runtime.yml
cloudwatch-agent.json
nginx.conf
```

원본 파일과 S3 이름의 대응은 다음과 같다.

| Git 저장소 원본 | S3 object 이름 |
| --- | --- |
| `infra/aws/scripts/deploy-day19-via-ssm.sh` | `deploy-day19-via-ssm.sh` |
| `infra/aws/deploy/day19/docker-compose.app.yml` | `docker-compose.application.yml` |
| `infra/aws/deploy/day19/docker-compose.runtime.yml` | `docker-compose.runtime.yml` |
| `infra/aws/deploy/day19/cloudwatch-agent.json` | `cloudwatch-agent.json` |
| `infra/nginx/nginx.conf` | `nginx.conf` |

현재 workflow trigger는 `workflow_dispatch`뿐이다. Git push만으로 S3 업로드와 배포가 자동
실행되지는 않는다.

```text
git push
→ GitHub 코드만 갱신

Deploy Day19 workflow 수동 실행
→ 테스트, S3 업로드, ECR push, 실제 배포 실행
```

### 4.2 배포 스크립트가 다운로드한다

`deploy-day19-via-ssm.sh`는 전달받은 `SOURCE_REF`로 같은 prefix를 다시 조립한다.

```bash
artifact_base="s3://$APPLICATION_BUCKET/deploy/day19/$SOURCE_REF"
```

`ROLE=application`이면 다음 파일을 받는다.

```text
docker-compose.application.yml
cloudwatch-agent.json
nginx.conf
```

`ROLE=runtime`이면 다음 파일을 받는다.

```text
docker-compose.runtime.yml
cloudwatch-agent.json
```

이 방식으로 Git commit A의 이미지에 Git commit A의 Compose와 Nginx 설정이 결합된다.

---

## 5. ECR 이미지는 언제 업로드하고 언제 다운로드하는가

### 5.1 GitHub Actions가 이미지 생성 및 업로드

```text
backend/Dockerfile
→ Backend 이미지 build
→ ECR finrisk-backend에 push

frontend/Dockerfile
→ Frontend 이미지 build
→ ECR finrisk-frontend에 push
```

이미지 tag는 다음 형식이다.

```text
sha-<GITHUB_SHA>
```

그다음 `aws ecr describe-images`로 immutable digest를 조회한다.

```text
tag:    sha-<Git-SHA>
digest: sha256:<이미지 내용 해시>
```

`describe-images`는 이미지를 다운로드하지 않는다. 이미지 주소를 확정하기 위해 metadata만
조회한다.

### 5.2 EC2가 실제 이미지 다운로드

EC2의 배포 스크립트가 다음 명령을 실행할 때 실제 이미지 layer가 ECR에서 EC2로 내려온다.

```bash
docker compose pull
```

따라서 역할은 다음과 같이 나뉜다.

```text
GitHub Actions
→ 이미지를 build하고 ECR에 push
→ 배포할 이미지 digest 주소를 EC2에 전달

EC2
→ 전달받은 주소를 .env.prod에 기록
→ docker compose pull로 ECR에서 실제 이미지 다운로드
```

---

## 6. Release manifest의 의미와 수명주기

### 6.1 Terraform이 Parameter를 먼저 생성

파일: `infra/aws/terraform/environments/day18/main.tf`

Terraform이 다음 두 Parameter를 만든다.

```text
/finrisk/day19/releases/current
/finrisk/day19/releases/previous
```

최초 값은 `UNSET`이다.

```hcl
value = "UNSET"

lifecycle {
  ignore_changes = [value]
}
```

`ignore_changes`가 있으므로 GitHub Actions가 값을 변경한 뒤 `terraform apply`를 실행해도
Terraform이 다시 `UNSET`으로 되돌리지 않는다.

### 6.2 GitHub Actions가 candidate manifest 생성

`Resolve immutable release` 단계가 다음 JSON을 만든다.

```json
{
  "release_id": "run-<run-id>-<attempt>",
  "source_ref": "<40자리 Git SHA>",
  "backend_image": "<ECR backend digest 주소>",
  "frontend_image": "<ECR frontend digest 주소>"
}
```

각 항목은 같은 배포 버전을 가리킨다.

| 필드 | 역할 |
| --- | --- |
| `release_id` | GitHub Actions 실행 식별 |
| `source_ref` | S3 배포 파일 prefix 선택 |
| `backend_image` | ECR Backend 이미지 선택 |
| `frontend_image` | ECR Frontend 이미지 선택 |

### 6.3 최초 release 초기화

ASG를 처음 켜기 전에는 배포할 승인 release가 필요하다. Day 19 workflow를
`release_only=true`로 실행하면 이미지와 배포 파일을 게시하고 `current` Parameter를
candidate manifest로 초기화한다.

```text
current = UNSET
→ release_only=true 실행
→ current = candidate JSON
→ 이후 ASG 활성화
→ 새 EC2 bootstrap이 current를 읽고 최초 배포
```

### 6.4 일반 순차 배포 성공 후 승인

일반 배포에서는 기존 `current`를 rollback 대상으로 유지한다. 두 Application EC2와 Runtime
EC2 배포, 공개 health 검사가 모두 성공한 뒤에만 다음과 같이 변경한다.

```text
previous = 기존 current
current  = 새 candidate
```

중간에 실패하면 `current`를 새 버전으로 변경하지 않는다.

---

## 7. 새 Application EC2가 처음 생성될 때

호출 흐름은 다음과 같다.

```text
Terraform application_fleet module
→ Launch Template 생성
→ bootstrap.sh.tftpl을 EC2 user_data로 포함
→ ASG가 EC2 생성
→ EC2가 user_data 실행
→ bootstrap이 current release 조회
→ S3에서 해당 release의 deploy-day19-via-ssm.sh 다운로드
→ 배포 스크립트를 application 역할로 실행
→ S3에서 Application Compose와 설정 다운로드
→ ECR에서 Backend/Frontend 이미지 pull
→ 컨테이너 실행
→ /readyz 성공
→ ALB Target이 healthy
```

관련 파일은 다음과 같다.

```text
infra/aws/terraform/modules/application_fleet/main.tf
→ Launch Template과 ASG 정의

infra/aws/terraform/modules/application_fleet/files/bootstrap.sh.tftpl
→ 새 EC2 최초 실행

infra/aws/scripts/deploy-day19-via-ssm.sh
→ 실제 컨테이너 배포
```

### 7.1 Bootstrap의 역할

Bootstrap은 애플리케이션 배포 로직 전체를 중복 구현하지 않는다.

```text
Docker, Compose, CloudWatch Agent 설치
→ current release 조회
→ 공통 배포 스크립트 다운로드
→ 공통 배포 스크립트 호출
```

신규 EC2와 기존 EC2 업데이트가 같은 배포 스크립트를 사용하므로 실행 방식이 달라지는 문제를
줄인다.

---

## 8. 기존 Application EC2 두 대를 순차 배포할 때

호출자는 GitHub Actions의 `Roll out through ASG Standby` 단계다.

### 8.1 한 인스턴스의 배포 순서

```text
InService/healthy
→ aws autoscaling enter-standby
→ ALB connection draining
→ Standby/unused
→ aws ssm send-command
→ EC2가 S3에서 배포 스크립트 다운로드
→ deploy-day19-via-ssm.sh application ... 실행
→ 로컬 /readyz 검증
→ aws autoscaling exit-standby
→ InService
→ ALB Target healthy 대기
```

Target Group을 직접 deregister/register하지 않는다. ASG Standby 전환이 Target Group 연결을
관리한다.

### 8.2 왜 한 대씩 처리하는가

```text
첫 번째 EC2 배포 중
→ 두 번째 EC2가 서비스 유지

첫 번째 EC2 healthy 복귀
→ 두 번째 EC2 배포 시작
```

Workflow는 배포 시작 전에 다음 조건을 확인한다.

```text
InService Application EC2 = 2대
healthy Target = 2개
```

### 8.3 실패 시 rollback

Application EC2 배포가 실패하면 해당 인스턴스에 기존 `current` manifest를 다시 배포한다.
이미 새 버전으로 배포된 앞선 인스턴스도 역순으로 기존 release를 재배포한다.

Runtime 배포가 실패해도 Runtime과 앞서 배포된 Application 인스턴스를 기존 release로
되돌린다.

---

## 9. Runtime EC2 배포 흐름

Application EC2 두 대가 모두 성공한 뒤 Runtime EC2를 배포한다.

```text
GitHub Actions
→ Runtime EC2 ID 조회
→ SSM send-command
→ deploy-day19-via-ssm.sh runtime ... 실행
→ S3에서 Runtime Compose 다운로드
→ ECR에서 Backend 이미지 pull
→ Redis, ZooKeeper, Kafka, Worker 실행
→ Worker /readyz 확인
→ Redis PING 확인
→ Kafka broker 연결 확인
```

Runtime에는 Frontend 컨테이너가 없지만 release manifest와 공통 배포 스크립트 형식을 단순하게
유지하기 위해 Frontend 이미지 주소도 함께 전달한다.

---

## 10. 사용자 HTTP 요청 흐름

### 10.1 일반 페이지와 API

```text
사용자
→ Route53 app.fin-risk.com
→ ALB HTTPS listener :443
→ Target Group
→ healthy Application EC2 한 대 선택
→ EC2 Nginx :80
```

Nginx 이후 경로는 다음과 같이 나뉜다.

```text
/                 → Frontend :3000
/api/**           → Backend :8080
/oauth2/**        → Backend :8080
/login/oauth2/**  → Backend :8080
/readyz           → Backend :8080/readyz
```

HTTP `:80`으로 들어온 외부 요청은 ALB가 HTTPS `:443`으로 301 redirect한다.

### 10.2 비동기 작업

```text
사용자 API 요청
→ Application Backend가 작업 접수
→ Kafka에 메시지 발행
→ Runtime Worker가 메시지 소비
→ 계산 또는 수집 작업 수행
→ RDS 등에 결과 저장
→ 사용자가 API로 결과 조회
```

무거운 백그라운드 처리가 사용자 API 프로세스의 응답성을 직접 떨어뜨리지 않도록 역할을
분리한다.

---

## 11. Readiness와 자동 복구 흐름

### 11.1 Backend readiness endpoint

파일: `backend/src/main/resources/application.yaml`

```yaml
management:
  endpoint:
    health:
      group:
        readiness:
          include: readinessState
          additional-path: server:/readyz
```

`/actuator/health` 전체가 아니라 트래픽 수신 준비 상태만 나타내는 `/readyz`를 ALB 전용으로
사용한다.

### 11.2 계층별 검사

```text
Docker Backend healthcheck
→ Backend 컨테이너의 127.0.0.1:8080/readyz

Docker Nginx healthcheck
→ Nginx 컨테이너의 127.0.0.1/readyz

배포 스크립트
→ EC2의 127.0.0.1/readyz

ALB Target Group
→ Application EC2 :80/readyz

ASG health_check_type = ELB
→ ALB가 장기간 unhealthy로 판단하면 ASG가 인스턴스 교체
```

### 11.3 주요 시간 설정

```text
Target health interval       = 15초
healthy threshold            = 2회
unhealthy threshold          = 3회
ASG health check grace       = 600초
Target deregistration delay  = 120초
Target slow start            = 30초
```

Grace period는 새 EC2가 Docker 이미지와 컨테이너를 준비하는 동안 ASG가 너무 일찍 장애로
판단하지 않도록 한다. Deregistration delay는 Standby 전환 중 기존 요청을 마무리할 시간을
준다.

---

## 12. Google OAuth 흐름과 ALB stickiness

```text
브라우저
→ /oauth2/authorization/google
→ Application Backend가 OAuth state를 HTTP session에 저장
→ Google 로그인
→ /login/oauth2/code/google 콜백
→ Backend가 state 검증
→ 임시 oauthCode 생성
→ Frontend /login?oauthCode=... redirect
→ Frontend가 /api/auth/oauth/exchange 호출
→ JWT 발급
```

Spring Security의 OAuth authorization request는 현재 인스턴스의 HTTP session에 저장된다.
시작 요청은 Application 1, callback은 Application 2로 전달되면 state를 찾을 수 없다.

Target Group은 이 왕복 동안 같은 브라우저를 같은 인스턴스로 보내기 위해 ALB cookie
stickiness를 600초 동안 사용한다.

```hcl
stickiness {
  type            = "lb_cookie"
  cookie_duration = 600
  enabled         = true
}
```

장기적으로 완전한 무상태 구성이 필요하면 Spring Session을 공용 Redis에 저장하는 방식도
검토할 수 있다.

---

## 13. 로그 흐름

### 13.1 EC2 파일 로그

Bootstrap과 배포 스크립트는 표준 출력과 표준 오류를 파일에 함께 기록한다.

```text
bootstrap.sh
→ /var/log/finrisk-bootstrap.log

deploy-day19-via-ssm.sh
→ /var/log/finrisk-deploy.log
```

파일: `infra/aws/deploy/day19/cloudwatch-agent.json`

CloudWatch Agent가 파일을 다음 위치로 전송한다.

```text
Log Group: /finrisk/day18/bootstrap

Log Stream:
bootstrap-{instance_id}
day19-deploy-{instance_id}
```

`{instance_id}`는 CloudWatch Agent가 실제 EC2 instance ID로 치환한다.

### 13.2 컨테이너 로그

Docker Compose는 `awslogs` logging driver를 사용한다.

```text
Log Group: /finrisk/day18/containers
```

주요 stream 이름은 다음과 같다.

```text
application-backend-{INSTANCE_ID}
application-frontend-{INSTANCE_ID}
application-nginx-{INSTANCE_ID}
runtime-redis-{INSTANCE_ID}
runtime-zookeeper-{INSTANCE_ID}
runtime-kafka-{INSTANCE_ID}
runtime-worker-{INSTANCE_ID}
```

Day 19에서도 기존 Day 18 CloudWatch Log Group을 재사용하기 때문에 group 이름에 `day18`이
남아 있다.

---

## 14. Terraform 모듈 연결 관계

환경 조립 파일은 `infra/aws/terraform/environments/day18/main.tf`이다. Day 18 인프라를
교체하지 않고 확장했기 때문에 환경 디렉터리 이름은 그대로 유지한다.

```text
network
├─ ALB security group
├─ Application security group
├─ Runtime EC2 security group
├─ public/private subnet
└─ RDS 접근 규칙

data
├─ RDS
└─ CloudWatch Log Group

compute
├─ Runtime EC2
├─ 공용 EC2 IAM role/profile
└─ Runtime용 tag

load_balancing
├─ ACM certificate
├─ Route53 validation/alias
├─ ALB
├─ HTTP/HTTPS listener
└─ Target Group

application_fleet
├─ Launch Template
├─ bootstrap user_data
├─ ASG
└─ ASG capacity alarm

cicd
└─ GitHub Actions OIDC role과 배포 권한
```

핵심 출력 연결은 다음과 같다.

```text
network.public_subnet_ids
→ ALB와 Application ASG가 사용

load_balancing.target_group_arn
→ application_fleet ASG가 연결

compute.private_dns
→ Application EC2의 FinriskRuntimeHost tag

data.db_address
→ Application/Runtime EC2의 FinriskDbAddress tag

data.container_log_group_name
→ Docker awslogs group 설정

release_current.name
→ Application bootstrap의 current release 조회 대상
```

### 14.1 `prevent_destroy`를 모든 EC2에 적용하지 않는 이유

Day 19의 두 EC2 종류는 교체 전략이 다르다.

| 대상 | 적용 | 이유 |
| --- | --- | --- |
| Runtime 단독 EC2 | `prevent_destroy = true` | Redis, Kafka, ZooKeeper를 같이 실행하므로 예상하지 못한 교체를 Terraform에서 막음 |
| Application Launch Template | `create_before_destroy = true` | 새 Launch Template을 먼저 만들어 ASG가 새 EC2를 생성할 수 있게 함 |
| Application ASG 인스턴스 | `prevent_destroy` 미적용 | 애플리케이션 장애 시 ELB health를 기준으로 ASG가 자동 교체해야 함 |

`infra/aws/terraform/modules/compute/main.tf`의 `prevent_destroy`는 Runtime EC2를 보호한다.
반면 Application EC2는 ASG가 소유하므로 개별 EC2에 이 설정을 붙이지 않는다. 이 구조에서
Application EC2 교체는 방지할 사고가 아니라 장애 복구 절차의 일부다.

RDS와 Terraform state bucket의 `prevent_destroy`는 EC2 배포 전략과는 별개로, 데이터와
상태 유실을 막기 위한 보호 장치다.

---

## 15. 파일별 의미와 호출 관계

### 15.1 배포 시작점

| 파일 | 의미 | 다음에 호출하거나 만드는 것 |
| --- | --- | --- |
| `.github/workflows/deploy-day19.yml` | 테스트, 게시, 순차 배포 전체 지휘 | S3, ECR, SSM, ASG, EC2 SSM command |
| `infra/aws/scripts/deploy-day19-via-ssm.sh` | EC2 한 대의 공통 배포 실행 | S3 다운로드, SSM Secret 조회, Docker Compose |

### 15.2 EC2 실행 구성

| 파일 | 의미 | 사용 주체 |
| --- | --- | --- |
| `infra/aws/deploy/day19/docker-compose.app.yml` | Application 컨테이너 정의 | Application EC2의 배포 스크립트 |
| `infra/aws/deploy/day19/docker-compose.runtime.yml` | Runtime 컨테이너 정의 | Runtime EC2의 배포 스크립트 |
| `infra/nginx/nginx.conf` | Frontend/Backend 경로 분배 | Application Nginx 컨테이너 |
| `infra/aws/deploy/day19/cloudwatch-agent.json` | EC2 파일 로그 수집 규칙 | Application/Runtime CloudWatch Agent |

### 15.3 Terraform

| 파일 | 의미 | 연결 대상 |
| --- | --- | --- |
| `infra/aws/terraform/environments/day18/main.tf` | 모든 모듈과 실제 값 조립 | 아래 Terraform 모듈 전체 |
| `modules/application_fleet/main.tf` | Launch Template과 ASG | bootstrap, Target Group |
| `modules/application_fleet/files/bootstrap.sh.tftpl` | 새 Application EC2 최초 실행 | SSM current release, S3 배포 스크립트 |
| `modules/load_balancing/main.tf` | ACM, Route53, ALB, Target Group | Application ASG |
| `modules/compute/main.tf` | Runtime EC2와 공용 IAM | Runtime Compose, Application 연결 대상 |
| `modules/network/main.tf` | subnet과 보안그룹 | ALB, Application, Runtime, RDS |
| `modules/data/main.tf` | RDS와 Log Group | Backend, CloudWatch |
| `modules/cicd/main.tf` | GitHub Actions AWS 권한 | S3/ECR/SSM/ASG/SSM command |

### 15.4 Backend와 Frontend

| 파일 | Day 19에서의 의미 |
| --- | --- |
| `backend/src/main/resources/application.yaml` | `/readyz`, OAuth redirect, Worker flag 기본값 |
| `backend/src/main/java/com/finrisk/radar/global/config/SecurityConfig.java` | readiness와 OAuth 경로 접근 허용 |
| `backend/src/main/java/com/finrisk/radar/document/DocumentSchedulingConfiguration.java` | Worker 역할에 따른 scheduler 활성화 제어 |
| `frontend/src/components/auth/login-form.tsx` | Google OAuth 시작과 code 교환 시작점 |

---

## 16. 가장 중요한 호출 흐름 네 개

### 16.1 최초 인프라 구축

```text
terraform apply
→ SSM release Parameter 생성(UNSET)
→ ALB, Target Group, Launch Template 등 생성

Day19 workflow release_only=true
→ S3 배포 파일 게시
→ ECR 이미지 게시
→ current release 초기화

terraform apply로 ASG 활성화
→ Application EC2 생성
→ bootstrap
→ current release 배포
```

### 16.2 일반 애플리케이션 배포

```text
Day19 workflow 실행
→ 테스트
→ S3 배포 파일 게시
→ ECR 이미지 게시
→ candidate manifest 생성
→ Application 1 Standby/배포/InService
→ Application 2 Standby/배포/InService
→ Runtime 배포
→ 공개 health 검사
→ previous/current release 갱신
```

### 16.3 새 EC2 자동 복구

```text
ALB가 Target unhealthy 판단
→ ASG가 unhealthy EC2 교체
→ 새 EC2 user_data bootstrap 실행
→ SSM current release 조회
→ S3와 ECR에서 승인된 버전 배포
→ /readyz 성공
→ Target healthy
```

### 16.4 EC2 재부팅

```text
EC2 재부팅
→ systemd finrisk-application.service 또는 finrisk-runtime.service
→ /opt/finrisk/deploy.sh --reuse
→ 기존 release.env와 Compose 사용
→ 컨테이너 재실행 및 readiness 검사
```

---

## 17. 공부할 때 실제로 따라갈 순서

1. `docker-compose.app.yml`에서 Application EC2의 세 컨테이너를 확인한다.
2. `docker-compose.runtime.yml`에서 Redis/Kafka/Worker 분리를 확인한다.
3. `deploy-day19-via-ssm.sh`에서 EC2 한 대가 배포되는 순서를 따라간다.
4. `deploy-day19.yml`의 S3 게시와 ECR 게시가 서로 다름을 확인한다.
5. release manifest가 `source_ref`와 두 이미지 digest를 묶는 방식을 확인한다.
6. `bootstrap.sh.tftpl`이 current release를 읽고 공통 배포 스크립트를 호출하는 부분을 본다.
7. `application_fleet/main.tf`에서 bootstrap이 Launch Template user data가 되는 과정을 본다.
8. `load_balancing/main.tf`에서 `/readyz`, ELB health, stickiness를 확인한다.
9. 마지막으로 환경 `main.tf`에서 모든 모듈의 입력과 출력을 연결한다.
10. 다시 `deploy-day19.yml`로 돌아와 Standby 순차 배포 전체를 읽는다.

이 순서를 지키면 가장 작은 실행 단위에서 시작해 전체 자동화까지 자연스럽게 연결된다.

---

## 18. 자주 혼동하는 내용

### Git push하면 바로 배포되는가

아니다. 현재 Day 19 workflow는 `workflow_dispatch`이므로 수동 실행해야 한다.

### `aws ecr describe-images`가 이미지를 EC2로 받는가

아니다. digest metadata만 조회한다. 실제 EC2 다운로드는 `docker compose pull`이다.

### `artifact_base`가 Terraform resource인가

아니다. S3 bucket과 Git SHA를 합친 Bash 문자열이다. `aws s3 cp`가 object를 올릴 때 해당
prefix가 생긴 것처럼 보인다.

### Bootstrap과 배포 스크립트는 같은가

아니다. Bootstrap은 새 EC2를 준비하고 공통 배포 스크립트를 호출한다. 실제 컨테이너 배포는
`deploy-day19-via-ssm.sh`가 담당한다.

### SSM Parameter Store에 이미지가 들어 있는가

아니다. ECR 이미지 digest 주소와 Git SHA가 포함된 작은 JSON manifest만 들어 있다.

### 왜 Day 19인데 resource 이름에 Day 18이 남는가

기존 Day 18 VPC, RDS, IAM, ECR, Log Group 등을 파괴하고 다시 만들지 않고 확장했기 때문이다.
Day 19는 기존 기반 위에 ALB, ASG, HTTPS, 순차 배포를 추가한 단계다.
