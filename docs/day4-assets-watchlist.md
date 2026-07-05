# Day 4 자산·관심자산 실행 및 검증

## 실행

루트의 `.env.local.example`을 `.env.local`로 복사하고 필수 값을 채운 뒤 실행한다.

```bash
docker compose --env-file .env.local -f infra/docker/docker-compose.local.yml up -d --build
docker compose --env-file .env.local -f infra/docker/docker-compose.local.yml ps
```

- 프론트: <http://localhost:3000/assets>
- Swagger UI: <http://localhost:8080/swagger-ui.html>
- OpenAPI JSON: <http://localhost:8080/v3/api-docs>

Docker 프로필에서는 자산 시드가 활성화되며, `(ticker, market)` 기준으로 중복 없이 다음 6개가 준비된다: 삼성전자, NAVER, 제이알글로벌리츠, 맥쿼리인프라, JTBC, 콘텐트리중앙.

## 공개 자산 API

```bash
curl http://localhost:8080/api/assets
curl "http://localhost:8080/api/assets/search?keyword=삼성"
curl "http://localhost:8080/api/assets/search?keyword=리츠&assetType=REIT"
curl http://localhost:8080/api/assets/1
```

자산 생성은 `ROLE_ADMIN` 액세스 토큰만 허용한다.

```bash
curl -X POST http://localhost:8080/api/assets \
  -H "Authorization: Bearer $ADMIN_ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"테스트 자산","ticker":"TEST01","market":"PRIVATE","sector":"Test","country":"KR","currency":"KRW","assetType":"BOND_ISSUER"}'
```

## 인증 및 관심자산 API

테스트 사용자를 만들고 로그인한다. 이미 가입된 이메일이면 signup은 생략한다.

```bash
curl -X POST http://localhost:8080/api/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"email":"day4@example.com","password":"password123!","name":"Day4 User"}'

curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"day4@example.com","password":"password123!"}'
```

로그인 응답의 `data.accessToken`을 환경변수에 넣고, `GET /api/assets` 응답에서 실제 자산 ID를 확인한다.

```bash
export ACCESS_TOKEN="<access-token>"

curl -X POST http://localhost:8080/api/watchlists \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"assetId":1}'

curl http://localhost:8080/api/watchlists \
  -H "Authorization: Bearer $ACCESS_TOKEN"

curl -X DELETE http://localhost:8080/api/watchlists/1 \
  -H "Authorization: Bearer $ACCESS_TOKEN"
```

검증할 오류 흐름:

1. 같은 `assetId`를 두 번 등록하면 HTTP 409 / `WATCHLIST_001`이 반환된다.
2. FREE 사용자가 서로 다른 자산 5개를 등록한 뒤 6번째를 등록하면 HTTP 429 / `USAGE_004`가 반환된다.
3. 존재하지 않는 `assetId`는 HTTP 404 / `ASSET_001`이 반환된다.
4. 다른 사용자의 `watchlistId` 삭제는 HTTP 404 / `WATCHLIST_002`가 반환된다.
5. 인증 없이 관심자산 API를 호출하면 HTTP 401 / `AUTH_003`이 반환된다.

## 로컬 테스트

```bash
cd backend
./gradlew test

cd ../frontend
corepack pnpm test
corepack pnpm typecheck
corepack pnpm lint
corepack pnpm build
```

## 데이터 이전 정책

Flyway V4는 기존 Day 3 `watchlist_items.asset_code` 값을 정규화해 `market=LEGACY`인 자산으로 만든 후 `asset_id` 외래키로 연결한다. 같은 사용자의 대소문자만 다른 중복 코드는 가장 오래된 행 하나를 남긴다. Docker 시드와 ticker가 일치하는 LEGACY 자산은 정식 샘플 정보로 갱신되므로 기존 관심자산 연결이 유지된다.
