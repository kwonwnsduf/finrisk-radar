# Day 2 백엔드 인증 시스템 복습 문서

> 범위: Day 2 Phase 1 ~ Phase 6A 백엔드 구현  
> 제외: Phase 6B 프론트엔드 연결  
> 목적: 나중에 실제 코드를 다시 읽을 때, **왜 이 구조를 선택했고 요청이 어떤 메서드를 거쳐 실행되는지** 한 문서에서 복원하기 위함

이 문서는 현재 저장소의 실제 코드를 기준으로 작성했다. 요청서에서 사용한 `OAuthLoginCodeStore`라는 이름은 현재 코드에서는 `OAuthCodeStore`이고, `OAuthCodeExchangeService`라는 별도 클래스는 없다. OAuth code 교환은 현재 `AuthService.exchangeOAuthCode()`가 담당한다. 이후 설명에서는 혼동을 막기 위해 실제 클래스와 메서드 이름을 사용한다.

---

# 1. 전체 인증 흐름

## 1.1 한눈에 보는 전체 순서

```text
[이메일 회원가입]
SignupRequest 검증
↓
이메일 trim + 소문자 정규화
↓
중복 이메일 확인
↓
BCrypt 비밀번호 해시
↓
User(provider=LOCAL, role=ROLE_USER) 저장
↓
SignupResponse 반환

[이메일 로그인]
LoginRequest 검증
↓
이메일로 User 조회
↓
provider=LOCAL 확인
↓
BCrypt matches()로 비밀번호 검증
↓
AuthTokenIssuer 호출
↓
JwtProvider가 Access/Refresh Token 생성
↓
RefreshTokenStore가 Refresh Token을 Redis에 저장
  key = refresh:user:{userId}
  TTL = 14일
↓
AuthResponse 반환

[인증 요청]
Authorization: Bearer {Access Token}
↓
JwtAuthenticationFilter
↓
JwtProvider.parseAccessToken()
  서명 + issuer + 만료 + tokenType=ACCESS 검증
↓
Redis blacklist:access:{jti} 확인
↓
CustomUserPrincipal 생성
↓
SecurityContext에 Authentication 등록
↓
Controller의 @AuthenticationPrincipal로 전달

[Access Token 재발급]
POST /api/auth/refresh + Refresh Token
↓
tokenType=REFRESH JWT 검증
↓
Redis refresh:user:{userId} 조회
↓
요청 토큰과 Redis 토큰을 상수 시간 비교
↓
DB에서 현재 User 조회
↓
현재 email/role로 새 Access Token만 발급
↓
TokenResponse 반환

[로그아웃]
인증된 Access Token으로 POST /api/auth/logout
↓
Principal에서 userId, jti, exp 추출
↓
Access Token 남은 수명 계산
↓
Redis Lua Script 원자 실행
  refresh:user:{userId} 삭제
  blacklist:access:{jti} = "1" 저장
  TTL = Access Token 남은 수명
↓
이후 같은 Access Token은 Filter에서 차단
↓
삭제된 Refresh Token으로 재발급도 실패

[Google OAuth]
브라우저가 /oauth2/authorization/google 이동
↓
Spring Security가 Google Authorization Endpoint로 redirect
↓
Google 인증 및 사용자 동의
↓
Google이 /login/oauth2/code/google로 authorization code 전달
↓
Spring Security가 Google token/userinfo endpoint와 통신
↓
CustomOAuth2UserService
  Google sub/email/email_verified/name 검증
↓
(provider=GOOGLE, providerId=sub) 사용자 조회 또는 생성
↓
OAuthSuccessHandler
  32바이트 난수 기반 1회용 oauthCode 생성
↓
Redis oauth:code:{code} → userId, TTL 180초 저장
↓
OAuth 임시 HTTP Session 폐기
↓
프론트 /login?oauthCode=... 로 redirect
↓
POST /api/auth/oauth/exchange
↓
Redis GETDEL로 code를 한 번만 소비
↓
userId로 User 조회
↓
AuthTokenIssuer 호출
↓
서비스 Access/Refresh Token 발급 + Refresh Token Redis 저장
↓
AuthResponse 반환
```

핵심은 인증 수단이 달라도 마지막 토큰 발급 과정은 같다는 점이다. 이메일 로그인은 비밀번호 검증 후, Google 로그인은 Google 신원 검증과 1회용 code 교환 후 모두 `AuthTokenIssuer`로 합류한다.

## 1.2 저장소별 책임

| 저장소 | 저장하는 것 | 저장하지 않는 것 | 이유 |
|---|---|---|---|
| PostgreSQL | 사용자, BCrypt 비밀번호, role, provider, providerId | JWT, Google Access Token | 영속적인 계정 정보의 기준 |
| Redis | Refresh Token, Access blacklist, 1회용 OAuth code | 사용자 프로필 | TTL과 빠른 조회·삭제가 필요한 단기 인증 상태 |
| JWT | userId, tokenType, 만료시간 등 | 비밀번호, 이름, Google Token | 요청마다 DB를 조회하지 않고 인증 정보를 전달 |
| 임시 HTTP Session | Google OAuth authorization request/state 처리에 필요한 임시 상태 | 서비스 로그인 세션 | OAuth callback이 끝나면 즉시 폐기 |

---

# 2. Phase별 설명

## Phase 1 — 이메일 회원가입과 로그인 기반

### 왜 만들었는가

모든 인증 방식의 출발점은 사용자 모델과 비밀번호 검증이다. JWT를 먼저 붙이면 사용자 식별, 중복 이메일, 비밀번호 해시, 공통 오류 형식이 불명확해진다. 그래서 Phase 1에서는 토큰 없이도 회원가입과 로그인이 올바르게 동작하는 도메인 기반을 먼저 만들었다.

### 주요 클래스

- `User`, `Role`, `UserRepository`: 사용자 영속 모델과 조회
- `SignupRequest`, `LoginRequest`: 입력 검증
- `SignupResponse`, 당시의 사용자 응답 DTO: 비밀번호 없는 출력
- `AuthController`: HTTP 계약
- `AuthService`: 정규화, 중복 검사, BCrypt 검증
- `PasswordEncoderConfig`: `BCryptPasswordEncoder` Bean
- `ApiResponse`, `ErrorCode`, `BusinessException`, `GlobalExceptionHandler`: 공통 성공·오류 응답
- `V1__create_app_users.sql`: `app_users` 테이블 생성

### 회원가입 실행 순서

```text
POST /api/auth/signup
↓
AuthController.signup(@Valid SignupRequest)
↓
Bean Validation
  email: 필수, 이메일 형식, 최대 255자
  password: 필수, 8~64자
  name: 필수, 최대 50자
↓
AuthService.signup()
↓
email trim + lowercase / name trim
↓
UserRepository.existsByEmail()
↓
PasswordEncoder.encode(rawPassword)
↓
User.create(...)
  provider=LOCAL
  role=ROLE_USER
↓
UserRepository.saveAndFlush()
↓
PostgreSQL app_users INSERT
↓
SignupResponse.from(user)
↓
201 ApiResponse<SignupResponse>
```

서비스에서 먼저 중복을 확인하지만 DB에도 이메일 unique 제약을 둔다. 두 요청이 동시에 들어오면 둘 다 사전 조회를 통과할 수 있으므로, `saveAndFlush()`에서 발생하는 `DataIntegrityViolationException`도 `DUPLICATE_EMAIL`로 변환한다. 애플리케이션 검사와 DB 제약을 함께 쓰는 이유다.

### 로그인 실행 순서

```text
POST /api/auth/login
↓
AuthController.login()
↓
AuthService.login()
↓
이메일 정규화
↓
UserRepository.findByEmail()
↓
provider == LOCAL 확인
↓
PasswordEncoder.matches(raw, encoded)
↓
성공: 토큰 발급 단계로 이동
실패: AUTH_002 (이메일 미존재와 비밀번호 불일치를 동일하게 응답)
```

미등록 이메일과 비밀번호 불일치에 같은 응답을 사용하는 이유는 공격자에게 “가입된 이메일인지”를 알려 주지 않기 위해서다. Google 사용자는 DB에 BCrypt 문자열이 있어도 `provider != LOCAL`이므로 이메일 로그인이 거부된다.

## Phase 2 — JWT Access/Refresh Token 발급

### JWT 발급 과정

Phase 2에서는 로그인 성공 결과를 단순 사용자 정보에서 서비스 JWT가 포함된 `AuthResponse`로 확장했다. JJWT 0.13.0과 HS256을 사용한다.

```text
AuthService.login()
↓
AuthTokenIssuer.issue(user)  // Phase 6A에서 공통화된 현재 구조
↓
JwtProvider.generateTokens(user)
↓
같은 issuedAt 기준으로
  generateAccessToken(user, issuedAt)
  generateRefreshToken(user, issuedAt)
↓
TokenPair(accessToken, refreshToken)
```

### Access Token Claim

| Claim | 값 | 이유 |
|---|---|---|
| `iss` | `finrisk-radar` | 다른 발급자가 만든 토큰 거부 |
| `sub` | userId 문자열 | 서비스 내부 사용자 식별 |
| `jti` | 매번 새로운 UUID | 개별 Access Token blacklist 식별자 |
| `iat` | 발급 시각 | 토큰 발급 시점 추적 |
| `exp` | 발급 후 30분 | 탈취 시 피해 시간 제한 |
| `tokenType` | `ACCESS` | Refresh Token의 API 인증 오용 방지 |
| `email` | 사용자 이메일 | Principal 구성 |
| `role` | `ROLE_USER` 또는 `ROLE_ADMIN` | Spring Security 권한 구성 |

### Refresh Token Claim

Refresh Token에는 `iss`, `sub`, `jti`, `iat`, `exp`, `tokenType=REFRESH`만 넣는다. email과 role은 넣지 않는다. Refresh 시 DB에서 사용자를 다시 조회해 현재 email과 role로 새 Access Token을 만들기 때문이다. 역할이 변경되었을 때 14일 전 정보를 계속 복제하지 않는 효과가 있다.

### Access와 Refresh를 분리한 이유

- Access Token은 짧게 유지해 탈취 피해를 제한한다.
- Refresh Token은 긴 로그인 지속성을 제공하지만 일반 API 인증에는 사용할 수 없다.
- Parser 자체가 `tokenType`을 요구하므로 두 토큰이 같은 secret을 쓰더라도 용도를 바꿔 쓸 수 없다.
- Refresh Token은 Redis 상태와 함께 검증하므로 서버에서 사실상 폐기할 수 있다.

### Secret 처리

`JWT_SECRET`은 Base64 문자열이어야 한다. `JwtProvider` 생성 시 decode한 결과가 32바이트(256비트) 미만이면 애플리케이션 시작을 실패시킨다. 약한 secret으로 조용히 서비스가 시작되는 것보다 빠르게 실패하는 편이 안전하다.

## Phase 3 — JWT 인증 Filter와 `/api/users/me`

### 인증이 이루어지는 순서

```text
GET /api/users/me
Authorization: Bearer {Access Token}
↓
JwtAuthenticationFilter.doFilterInternal()
↓
Bearer Token 추출
↓
JwtProvider.parseAccessToken()
  HS256, 서명, issuer, exp, tokenType=ACCESS
  sub, email, role, jti, exp 필수값 검증
↓
TokenRevocationStore.isBlacklisted(jti)
↓
CustomUserPrincipal.from(claims)
↓
UsernamePasswordAuthenticationToken.authenticated(...)
↓
SecurityContextHolder에 저장
↓
SecurityConfig의 authenticated 조건 통과
↓
UserController.me(@AuthenticationPrincipal principal)
↓
UserService.getMe(principal.userId())
↓
UserRepository.findById()
↓
DB의 최신 사용자 정보로 MeResponse 반환
```

### SecurityContext와 Principal

`SecurityContext`는 현재 요청에서 “누가 인증되었는가”를 Spring Security가 공유하는 저장소다. 여기에 JPA `User` Entity나 비밀번호를 넣지 않고 `CustomUserPrincipal`만 넣는다.

Principal은 다음을 가진다.

- `userId`: DB 재조회 키
- `email`: Principal 이름
- `role`: `SimpleGrantedAuthority` 변환
- `tokenId`: logout blacklist용 jti
- `expiresAt`: logout blacklist TTL 계산용 exp

필터는 매 요청마다 User DB를 조회하지 않는다. `/me`처럼 최신 계정 정보가 필요한 지점에서만 ID로 DB를 조회한다. 따라서 일반 인증 비용을 줄이면서 `/me` 응답은 최신 상태를 보여 준다.

### 401 처리

필터에서 발생한 예외는 Controller가 실행되기 전이므로 `GlobalExceptionHandler`가 처리할 수 없다. 잘못된 토큰은 인증을 등록하지 않고 chain을 계속 진행하고, 보호 경로에서 `JwtAuthenticationEntryPoint`가 `AUTH_003` JSON을 쓴다. Redis blacklist 조회 장애는 인증 성공으로 간주하면 안 되므로 필터가 `SecurityErrorResponseWriter`로 즉시 `AUTH_005` 503을 쓰고 chain을 중단한다.

## Phase 4 — Redis Refresh Token 저장과 Access 재발급

### 왜 Redis인가

JWT만 사용하면 Refresh Token은 서명과 만료시간이 맞는 동안 서버가 강제로 끊기 어렵다. Redis에 “현재 허용된 Refresh Token”을 저장하면 다음이 가능하다.

- 사용자가 다시 로그인할 때 기존 Refresh Token 덮어쓰기
- 로그아웃 시 즉시 삭제
- TTL을 JWT 만료시간과 맞춰 자동 정리
- 빠른 key/value 조회

### 로그인 시 저장

```text
AuthTokenIssuer.issue(user)
↓
JwtProvider.generateTokens(user)
↓
RefreshTokenStore.save(userId, refreshToken)
↓
SET refresh:user:{userId} {token} EX 14일
```

사용자당 key 하나이므로 새 로그인은 이전 로그인에서 발급된 Refresh Token을 무효화한다. 이는 단순한 “사용자당 하나의 활성 Refresh Token” 정책이다.

### Refresh API 실행 순서

```text
POST /api/auth/refresh
↓
AuthController.refresh()
↓
AuthService.refresh()
↓
JwtProvider.parseRefreshToken()
  tokenType=REFRESH 포함 전체 JWT 검증
↓
RefreshTokenStore.find(userId)
↓
Redis 저장 토큰과 요청 토큰 MessageDigest.isEqual 비교
↓
UserRepository.findById(userId)
↓
JwtProvider.generateAccessToken(user)
↓
TokenResponse(accessToken)
```

Access Token만 새로 발급하는 것은 Refresh Token Rotation을 아직 도입하지 않았기 때문이다. 기존 Refresh Token의 Redis TTL도 연장하지 않는다. 같은 Refresh Token은 원래 만료 시점까지 반복 사용할 수 있다. 단순하지만 탈취된 Refresh Token의 재사용 탐지는 불가능하므로 향후 Rotation이 보안 강화 지점이다.

## Phase 5 — Logout과 Access Token Blacklist

### JWT인데 blacklist가 필요한 이유

서명된 Access Token은 서버 메모리와 무관하게 만료까지 유효하다. 로그아웃에서 Refresh Token만 삭제하면 새 Access Token 발급은 막을 수 있지만, 이미 발급된 Access Token은 최대 30분 동안 계속 사용할 수 있다. 이 틈을 막기 위해 jti를 blacklist에 저장한다.

### Logout 실행 순서

```text
POST /api/auth/logout + Bearer Access Token
↓
JwtAuthenticationFilter 인증
↓
AuthController.logout(principal)
↓
AuthService.logout()
↓
Duration.between(now, principal.expiresAt())
↓
TokenRevocationStore.revoke(userId, jti, remainingTtl)
↓
Redis Lua Script 원자 실행
  DEL refresh:user:{userId}
  PSETEX blacklist:access:{jti} remainingMillis "1"
↓
200 ApiResponse<Void>
```

### Lua Script를 사용한 이유

Refresh 삭제와 blacklist 저장을 별도 명령으로 실행하면 중간 장애 시 반쪽 로그아웃이 된다. 예를 들어 Refresh는 삭제됐지만 blacklist 저장이 실패할 수 있다. Lua Script는 Redis에서 두 명령을 하나의 원자적 작업으로 실행한다.

Blacklist TTL은 고정 30분이 아니라 Access Token의 `exp - now`다. 토큰이 만료되면 JWT Parser가 어차피 거부하므로 blacklist도 더 이상 필요 없다. 남은 수명만 저장하면 Redis 공간을 낭비하지 않는다.

### Filter의 blacklist 검사 위치

1. 먼저 JWT 서명·만료·`tokenType=ACCESS`를 검증한다.
2. 유효한 토큰에서 jti를 꺼낸다.
3. 그때 Redis blacklist를 조회한다.
4. blacklist가 아니어야 SecurityContext에 등록한다.

Malformed JWT마다 Redis를 조회하지 않으므로 불필요한 Redis 부하를 줄인다.

## Phase 6A — Google OAuth2 Backend

### Spring Security OAuth2 전체 흐름

```text
브라우저 GET /oauth2/authorization/google
↓
Spring Security OAuth2AuthorizationRequestRedirectFilter
↓
Google Authorization Endpoint로 302
↓
사용자 Google 로그인/동의
↓
Google callback: /login/oauth2/code/google?code=...&state=...
↓
Spring Security가 임시 Session의 authorization request/state 확인
↓
Google authorization code를 Google Access Token으로 교환
↓
Google userinfo 호출
↓
CustomOAuth2UserService.loadUser()
↓
Google 사용자 조회 또는 생성
↓
CustomOAuth2User 반환
↓
OAuthSuccessHandler
↓
서비스 1회용 oauthCode 생성 및 Redis 저장
↓
OAuth 임시 Session 폐기
↓
프론트 /login?oauthCode=... redirect
↓
POST /api/auth/oauth/exchange
↓
OAuthCodeStore.consume() → Redis GETDEL
↓
AuthService.exchangeOAuthCode()
↓
UserRepository.findById()
↓
AuthTokenIssuer.issue(user)
↓
서비스 JWT 발급 및 Refresh Token 저장
```

### OAuth 계정 매핑

Google의 변경되지 않는 사용자 식별자인 `sub`를 `providerId`로 저장한다. 조회 키는 `(provider=GOOGLE, providerId=sub)`다. 이메일은 표시와 서비스 연락처 의미가 있지만 Google 계정의 영구 식별자로 사용하지 않는다.

신규 Google 사용자는 다음 조건을 만족해야 한다.

- registrationId가 `google`
- `sub`, `email`, `name` 존재
- `email_verified=true`
- email 최대 255자
- 기존 동일 email 사용자가 없어야 함

동일 이메일의 LOCAL 계정과 자동 연결하지 않는다. 이메일만 같다는 이유로 서로 다른 인증 수단의 계정을 연결하면 계정 탈취나 예상하지 못한 로그인 경로가 생길 수 있기 때문이다. DB의 전역 email unique 제약과 서비스 검사가 이를 막는다.

### Google 사용자의 random BCrypt password

`password` 컬럼의 NOT NULL 제약을 유지한다. Google 사용자는 비밀번호 로그인을 하지 않지만, 생성 시 `SecureRandom` 32바이트를 Base64URL 문자열로 바꾸고 BCrypt 해시만 저장한다. 원문 난수는 폐기한다. 동시에 `AuthService.login()`이 `provider=LOCAL`을 요구하므로 우연히 비밀번호가 맞을 가능성과 관계없이 이메일 로그인이 불가능하다.

### 왜 JWT를 redirect URL에 넣지 않는가

JWT를 query string에 넣으면 다음 위치에 남을 수 있다.

- 브라우저 방문 기록
- 프록시와 서버 access log
- 분석 도구
- Referer header
- 화면 캡처와 공유 URL

대신 180초짜리 1회용 code만 URL로 전달한다. code가 유출되어도 짧은 시간 안에 한 번만 사용할 수 있고, 실제 JWT는 프론트가 서버에 POST하여 응답 body로 받는다.

### OAuthCodeStore와 GETDEL

`oauth:code:{code}`의 value는 userId이고 TTL은 180초다. `getAndDelete()`는 Redis GETDEL 의미로 조회와 삭제를 원자적으로 처리한다. 조회 후 별도 삭제를 하면 두 요청이 동시에 같은 code를 사용할 수 있지만 GETDEL은 하나만 성공한다.

### AuthTokenIssuer의 의미

이메일 로그인과 OAuth 로그인에서 각각 JWT 발급 코드를 작성하면 만료시간, Claim, Redis 저장, 오류 변환이 서로 달라질 수 있다. 두 흐름 모두 검증이 끝난 `User`를 `AuthTokenIssuer.issue()`에 넘김으로써 토큰 정책을 한곳에 둔다.

### OAuth Session은 왜 잠깐만 쓰는가

OAuth redirect와 callback은 서로 다른 HTTP 요청이다. 그 사이 authorization request와 `state`를 연결하려면 임시 저장이 필요해 `SessionCreationPolicy.IF_REQUIRED`를 사용한다. 하지만 서비스 인증은 JWT 기반이므로 성공·실패 Handler에서 SecurityContext를 비우고 Session을 invalidate한다. 즉 Session은 Google handshake에만 사용되고 서비스 로그인 상태로 남지 않는다.

---

# 3. 클래스별 설명

## 3.1 `auth` 패키지

### AuthController

- 역할: 인증 HTTP endpoint와 상태 코드를 정의한다.
- 주요 메서드: `signup`, `login`, `refresh`, `exchangeOAuthCode`, `logout`.
- 호출자: HTTP client.
- 호출 대상: `AuthService`.
- 반환: 모두 `ApiResponse<T>`로 감싼 DTO. signup만 201, 나머지 성공은 200.

### AuthService

- 역할: 이메일 인증, Refresh 검증, logout, OAuth code 교환의 업무 흐름을 조합한다.
- 주요 메서드:
  - `signup()`: 정규화, 중복 검사, BCrypt 저장.
  - `login()`: LOCAL provider와 BCrypt 검증 후 `AuthTokenIssuer` 호출.
  - `refresh()`: Refresh JWT + Redis 값 + DB 사용자 검증 후 Access 재발급.
  - `logout()`: 남은 TTL 계산 후 `TokenRevocationStore` 호출.
  - `exchangeOAuthCode()`: GETDEL로 code 소비 후 사용자 조회와 토큰 발급.
- 반환: `SignupResponse`, `AuthResponse`, `TokenResponse` 또는 void.

### AuthTokenIssuer

- 역할: 모든 로그인 수단의 서비스 JWT 발급을 공통화한다.
- 주요 메서드: `issue(User)`.
- 호출자: `AuthService.login()`, `AuthService.exchangeOAuthCode()`.
- 호출 대상: `JwtProvider.generateTokens()`, `RefreshTokenStore.save()`.
- 반환: 사용자 정보와 두 토큰을 가진 `AuthResponse`.
- Redis 장애: `AUTH_005`로 변환.

### RefreshTokenStore

- 역할: Redis Refresh Token 저장·조회.
- `save(userId, token)`: `refresh:user:{userId}`에 `JwtProperties.refreshTokenExpiration` TTL로 저장.
- `find(userId)`: 현재 허용된 Refresh Token 조회.
- 사용자당 key 하나이므로 새 로그인은 이전 값을 덮어쓴다.

### TokenRevocationStore

- 역할: logout 원자 처리와 blacklist 조회.
- `revoke()`: Lua Script로 Refresh 삭제 + Access jti blacklist 저장.
- `isBlacklisted()`: `blacklist:access:{jti}` 존재 여부 조회.
- value는 의미 없는 고정값 `"1"`; 토큰 원문은 blacklist에 저장하지 않는다.

## 3.2 `auth.dto` 패키지

### SignupRequest

회원가입 email/password/name 입력과 Bean Validation 규칙을 가진다. compact constructor에서 email과 name을 trim한다.

### LoginRequest

로그인 email/password 입력을 가진다. email을 trim하고 email 형식 및 password 길이를 검증한다.

### RefreshRequest

blank가 아닌 Refresh Token 하나를 받는다.

### OAuthCodeExchangeRequest

blank가 아니고 최대 200자인 1회용 code를 받는다.

### SignupResponse

`id`, `email`, `name`, 문자열 `role`만 반환한다. signup에서 토큰과 비밀번호를 노출하지 않는다.

### AuthResponse

사용자 정보와 `accessToken`, `refreshToken`을 반환한다. 이메일 로그인과 OAuth code 교환이 공유한다.

### TokenResponse

Refresh API가 새 `accessToken`만 반환할 때 사용한다.

## 3.3 `auth.jwt` 패키지

### JwtProperties

`jwt.secret`, Access 만료시간, Refresh 만료시간을 Spring 설정에서 바인딩한다.

### JwtProvider

- 역할: JWT 생성과 typed Claim 파싱·검증.
- `generateTokens(User)`: Access/Refresh 쌍 생성.
- `generateAccessToken(User)`: Refresh 시 Access만 생성.
- `parseAccessToken(String)`: Access 전용 Parser와 필수 Claim 검증 후 `AccessTokenClaims` 반환.
- `parseRefreshToken(String)`: Refresh 전용 Parser 검증 후 `RefreshTokenClaims` 반환.
- 생성 시 secret과 만료 설정을 검증하므로 잘못된 설정은 startup 단계에서 실패한다.

### TokenPair

발급된 Access Token과 Refresh Token을 함께 전달하는 내부 record다.

### AccessTokenClaims

파싱이 끝난 `userId`, `email`, `role`, `tokenId(jti)`, `expiresAt`을 타입 안전하게 전달한다.

### RefreshTokenClaims

Refresh Token에서 필요한 `userId`만 전달한다.

### CustomUserPrincipal

Spring Security의 `Principal` 구현이다. Access Claim을 담고 role을 `SimpleGrantedAuthority`로 변환한다. password와 Entity는 보관하지 않는다.

### JwtAuthenticationFilter

매 요청에서 Bearer Token을 찾는 `OncePerRequestFilter`다. Access Token 검증 → blacklist 확인 → Principal 생성 → SecurityContext 등록 순서로 동작한다.

### JwtAuthenticationEntryPoint

인증되지 않은 사용자가 보호 경로에 접근했을 때 `AUTH_003` 401을 작성하도록 `SecurityErrorResponseWriter`에 위임한다.

### SecurityErrorResponseWriter

Controller 밖의 Security Filter 계층에서도 공통 `ApiResponse` JSON을 쓸 수 있게 status/content-type/encoding/body를 작성한다.

## 3.4 `auth.oauth` 패키지

### CustomOAuth2UserService

- 역할: Google userinfo를 서비스 `User`로 매핑.
- 호출자: Spring Security `oauth2Login().userInfoEndpoint()`.
- `loadUser()`: 기본 Google userinfo 조회 후 sub/email/email_verified/name 검증.
- 기존 `(GOOGLE, sub)` 사용자를 반환하거나 신규 사용자를 저장한다.
- LOCAL 이메일 충돌은 `oauth_email_conflict`로 거부한다.

### CustomOAuth2User

OAuth 인증 handshake 동안 사용하는 `OAuth2User` 구현이다. 서비스 userId, Google attributes, role authority를 가진다. 성공 Handler가 userId를 얻는 Principal이다.

### OAuthProperties

프론트 redirect URI와 code TTL을 `oauth.*` 설정에서 바인딩한다. URI는 absolute, TTL은 양수여야 한다.

### OAuthSuccessHandler

- Google 인증 성공 후 실행된다.
- `CustomOAuth2User`에서 userId를 얻는다.
- 32바이트 난수 code를 생성하고 `OAuthCodeStore`에 저장한다.
- 임시 Session과 SecurityContext를 지운다.
- JWT가 아닌 oauthCode만 프론트 query에 붙여 redirect한다.

### OAuthFailureHandler

OAuth 실패 시 임시 Session과 SecurityContext를 지우고 프론트에 `oauthError=authentication_failed`만 전달한다. 내부 Google 오류를 URL에 노출하지 않는다.

### OAuthCodeStore

요청서의 `OAuthLoginCodeStore`에 해당하는 실제 클래스다. `oauth:code:{code}` 저장과 `getAndDelete()` 소비를 담당한다.

### NoOpOAuth2AuthorizedClientRepository

Spring Security가 Google Access Token을 기본 저장소에 남기지 않도록 load는 null, save/remove는 no-op으로 구현한다. 서비스는 Google Token을 재사용하지 않으므로 callback 처리 후 보관할 이유가 없다.

## 3.5 `user` 패키지

### User

`app_users`에 매핑되는 Entity다. id, email, BCrypt password, name, provider, providerId, role과 생성·수정 시간을 가진다. `create()`는 LOCAL, `createGoogle()`은 GOOGLE 사용자를 만든다.

### AuthProvider

`LOCAL`, `GOOGLE` 인증 출처를 구분한다. 동일 User 테이블을 쓰면서 허용 로그인 방식을 명시한다.

### Role

`ROLE_USER`, `ROLE_ADMIN`을 정의한다. Spring Security authority 이름과 직접 호환된다.

### UserRepository

`existsByEmail`, `findByEmail`, `findByProviderAndProviderId`와 기본 `findById`를 제공한다.

### UserService

인증된 userId로 현재 DB 사용자를 조회해 `MeResponse`로 변환한다. 삭제된 사용자는 `AUTH_003`이다.

### UserController

`GET /api/users/me`에서 `@AuthenticationPrincipal CustomUserPrincipal`을 받고 UserService를 호출한다.

### MeResponse

현재 사용자의 id/email/name/문자열 role을 반환한다.

## 3.6 공통 클래스

### SecurityConfig

공개/보호 경로, JWT Filter 위치, OAuth2 Login Handler, EntryPoint, Session 정책을 조합한다.

### PasswordEncoderConfig

`BCryptPasswordEncoder`를 `PasswordEncoder` Bean으로 제공한다. OAuth UserService도 같은 Bean을 재사용한다.

### ErrorCode / BusinessException / GlobalExceptionHandler

업무 오류를 HTTP status와 안정적인 code로 매핑한다. `AUTH_001`부터 `AUTH_006`까지 중복 이메일, 자격 증명, 인증, Refresh, Redis 장애, OAuth code 오류를 구분한다.

### ApiResponse

성공과 오류 응답을 `success`, `code`, `message`, `data` 구조로 통일한다.

### BaseTimeEntity

User의 `createdAt`, `updatedAt`을 JPA lifecycle callback으로 관리한다.

---

# 4. 코드 실행 순서

## 4.1 회원가입

```text
AuthController.signup(request)
↓
AuthService.signup(request)
↓
normalizeEmail()
↓
UserRepository.existsByEmail(email)
↓
PasswordEncoder.encode(password)
↓
User.create(email, encodedPassword, name)
↓
UserRepository.saveAndFlush(user)
↓
SignupResponse.from(savedUser)
↓
ApiResponse.success(response), HTTP 201
```

## 4.2 이메일 로그인

```text
AuthController.login(request)
↓
AuthService.login(request)
↓
UserRepository.findByEmail(email)
↓
user.getProvider() == LOCAL
↓
PasswordEncoder.matches(raw, hash)
↓
AuthTokenIssuer.issue(user)
↓
JwtProvider.generateTokens(user)
↓
RefreshTokenStore.save(userId, refreshToken)
↓
AuthResponse.from(user, tokenPair)
↓
ApiResponse<AuthResponse>
```

## 4.3 Access Token으로 `/me`

```text
JwtAuthenticationFilter.resolveBearerToken(request)
↓
JwtProvider.parseAccessToken(token)
↓
TokenRevocationStore.isBlacklisted(jti)
↓
CustomUserPrincipal.from(claims)
↓
UsernamePasswordAuthenticationToken.authenticated(...)
↓
SecurityContextHolder.setAuthentication()
↓
UserController.me(principal)
↓
UserService.getMe(principal.userId())
↓
UserRepository.findById()
↓
MeResponse.from(user)
```

## 4.4 Refresh

```text
AuthController.refresh(request)
↓
AuthService.refresh(request)
↓
JwtProvider.parseRefreshToken(token)
↓
RefreshTokenStore.find(userId)
↓
MessageDigest.isEqual(stored, requested)
↓
UserRepository.findById(userId)
↓
JwtProvider.generateAccessToken(user)
↓
TokenResponse(newAccessToken)
```

## 4.5 Logout

```text
JwtAuthenticationFilter 인증
↓
AuthController.logout(principal)
↓
AuthService.logout(principal)
↓
Duration.between(now, principal.expiresAt())
↓
TokenRevocationStore.revoke(userId, jti, remainingTtl)
↓
Redis Lua: DEL refresh key + PSETEX blacklist key
```

## 4.6 Google OAuth 로그인과 code 발급

```text
SecurityConfig.oauth2Login()
↓
Google redirect / callback / token / userinfo (Spring Security 담당)
↓
CustomOAuth2UserService.loadUser(userRequest)
↓
DefaultOAuth2UserService.loadUser()
↓
UserRepository.findByProviderAndProviderId(GOOGLE, sub)
↓ (없으면)
UserRepository.existsByEmail(email)
↓
SecureRandom → BCrypt
↓
User.createGoogle()
↓
UserRepository.saveAndFlush()
↓
CustomOAuth2User.from(user, attributes)
↓
OAuthSuccessHandler.onAuthenticationSuccess()
↓
OAuthCodeStore.save(code, userId)
↓
Session invalidate
↓
frontendRedirectUri?oauthCode=...
```

## 4.7 OAuth code 교환

```text
AuthController.exchangeOAuthCode(request)
↓
AuthService.exchangeOAuthCode(request)
↓
OAuthCodeStore.consume(code)
  Redis GETDEL
↓
parseOAuthUserId(value)
↓
UserRepository.findById(userId)
↓
AuthTokenIssuer.issue(user)
↓
JwtProvider.generateTokens(user)
↓
RefreshTokenStore.save(userId, refreshToken)
↓
AuthResponse
```

현재 별도의 `OAuthCodeExchangeService`는 없다. 이 orchestration은 `AuthService.exchangeOAuthCode()` 안에 있다.

---

# 5. 왜 이렇게 설계했는가

## 왜 JWT인가

Bearer Token 하나로 여러 서버 인스턴스가 동일한 secret을 사용해 서명을 검증할 수 있고, 매 요청마다 서버 Session을 조회하지 않아도 된다. 다만 “완전 무상태”만 고집하면 logout과 Refresh 폐기가 어려워서, 이 프로젝트는 Access 인증은 JWT로 처리하고 폐기 상태는 Redis로 보완하는 혼합 방식을 사용한다.

## 왜 Refresh Token이 필요한가

Access Token을 길게 만들면 편하지만 탈취 피해가 커진다. Access는 30분으로 짧게, Refresh는 14일로 길게 분리해 사용성 및 피해 시간을 절충한다. Refresh는 일반 API에 사용할 수 없고 Redis 현재값과 일치해야 한다.

## 왜 Redis인가

Refresh Token, blacklist, OAuth code는 모두 만료가 있고 빠른 조회·삭제가 중요하다. Redis의 TTL과 원자 명령은 이 특성에 적합하다. PostgreSQL에 저장하면 만료 cleanup과 고빈도 blacklist 조회 부담이 커진다.

## 왜 Blacklist인가

Access JWT는 발급 후 자체 완결적이라 logout만으로 무효화되지 않는다. jti blacklist는 남은 만료시간 동안만 해당 토큰을 거부해 즉시 logout 의미를 만든다.

## 왜 BCrypt인가

BCrypt는 salt를 포함하고 계산 비용을 높여 DB 유출 시 대입 공격 비용을 증가시킨다. `matches()`가 raw password와 저장 hash를 비교하며 원문 비밀번호는 저장하지 않는다.

## 왜 OAuth Code Exchange인가

서비스 JWT를 redirect URL에 노출하지 않기 위해서다. 짧고 1회용인 code를 URL로 보내고, JWT는 POST body 응답에서 전달한다. Redis GETDEL로 재사용을 막는다.

## 왜 OAuth에서만 Session을 잠깐 쓰는가

Google로 나갔다가 돌아오는 두 요청 사이 `state`와 authorization request를 연결하려면 임시 상태가 필요하다. 서비스 API 인증은 JWT로 유지하고 callback 직후 Session을 폐기해 두 모델의 경계를 분명히 한다.

## 왜 Google 사용자에게 random BCrypt password를 저장하는가

DB `password NOT NULL` 규칙을 깨지 않고 Entity 불변식을 단순하게 유지하기 위해서다. 추측할 수 없는 난수를 해시하고 원문을 폐기한다. 동시에 LOCAL provider만 비밀번호 로그인을 허용해 OAuth 계정에 비밀번호 경로가 열리지 않는다.

## 왜 AuthTokenIssuer를 따로 만들었는가

이메일과 OAuth가 서로 다른 인증 절차를 가지더라도 서비스 토큰 정책은 같아야 한다. Access/Refresh 생성, Refresh Redis 저장, Redis 장애 변환, 응답 조립을 한 컴포넌트에 두면 정책 drift와 중복을 막는다.

## 왜 LOCAL과 GOOGLE 이메일을 자동 연결하지 않는가

이메일 일치만으로 인증 주체를 연결하는 것은 위험하다. provider와 providerId를 별도 식별자로 사용하고 충돌을 명시적으로 거부하면 계정 병합은 추후 별도 본인 확인 절차로 설계할 수 있다.

## 왜 Google Token을 저장하지 않는가

현재 서비스는 Google API를 후속 호출하지 않는다. 필요 없는 provider token을 저장하면 유출 표면만 커진다. `NoOpOAuth2AuthorizedClientRepository`로 Spring 기본 저장도 하지 않는다.

---

# 6. 프로젝트 구조

```text
backend/src/main/java/com/finrisk/radar
├─ auth
│  ├─ AuthController.java          # 인증 HTTP API
│  ├─ AuthService.java             # 인증 업무 흐름 orchestration
│  ├─ AuthTokenIssuer.java         # 공통 JWT 발급 + Refresh 저장
│  ├─ RefreshTokenStore.java       # Redis Refresh Token 저장소
│  ├─ TokenRevocationStore.java    # logout Lua + Access blacklist
│  ├─ dto
│  │  ├─ SignupRequest.java
│  │  ├─ LoginRequest.java
│  │  ├─ RefreshRequest.java
│  │  ├─ OAuthCodeExchangeRequest.java
│  │  ├─ SignupResponse.java
│  │  ├─ AuthResponse.java
│  │  └─ TokenResponse.java
│  ├─ jwt
│  │  ├─ JwtProperties.java        # JWT 설정 바인딩
│  │  ├─ JwtProvider.java          # JWT 생성·검증
│  │  ├─ TokenPair.java
│  │  ├─ AccessTokenClaims.java
│  │  ├─ RefreshTokenClaims.java
│  │  ├─ CustomUserPrincipal.java
│  │  ├─ JwtAuthenticationFilter.java
│  │  ├─ JwtAuthenticationEntryPoint.java
│  │  └─ SecurityErrorResponseWriter.java
│  └─ oauth
│     ├─ OAuthProperties.java      # redirect URI, code TTL
│     ├─ CustomOAuth2UserService.java
│     ├─ CustomOAuth2User.java
│     ├─ OAuthSuccessHandler.java
│     ├─ OAuthFailureHandler.java
│     ├─ OAuthCodeStore.java       # oauth:code 저장 및 GETDEL
│     └─ NoOpOAuth2AuthorizedClientRepository.java
├─ user
│  ├─ User.java
│  ├─ UserRepository.java
│  ├─ UserService.java
│  ├─ UserController.java
│  ├─ MeResponse.java
│  ├─ Role.java
│  └─ AuthProvider.java
└─ global
   ├─ config
   │  ├─ SecurityConfig.java
   │  ├─ PasswordEncoderConfig.java
   │  └─ SwaggerConfig.java
   ├─ error
   │  ├─ ErrorCode.java
   │  ├─ BusinessException.java
   │  └─ GlobalExceptionHandler.java
   ├─ response
   │  └─ ApiResponse.java
   └─ entity
      └─ BaseTimeEntity.java

backend/src/main/resources
├─ application.yaml                # JWT, Google OAuth, OAuth code 설정
├─ application-docker.yaml         # PostgreSQL, Redis, Kafka 연결
└─ db/migration
   ├─ V1__create_app_users.sql
   └─ V2__add_oauth_provider_to_app_users.sql
```

`auth`는 인증 use case, `auth.jwt`는 서비스 JWT, `auth.oauth`는 외부 Google handshake, `user`는 사용자 도메인과 `/me`, `global`은 보안 설정과 공통 오류를 담당한다.

---

# 7. 면접 대비

## Q1. JWT를 쓰는데 왜 Redis도 사용했나요?

Access 인증은 JWT 서명으로 처리해 Session 조회를 피하지만, Refresh Token 폐기와 즉시 logout은 상태가 필요합니다. Redis에 현재 Refresh Token과 Access jti blacklist를 TTL로 저장해 JWT의 무상태 장점과 서버 제어 가능성을 절충했습니다.

## Q2. Access Token과 Refresh Token은 어떻게 구분하나요?

둘 다 HS256을 쓰지만 `tokenType` Claim이 다릅니다. Access Parser는 `ACCESS`, Refresh Parser는 `REFRESH`를 필수로 요구하므로 Refresh Token을 Bearer 인증에 사용할 수 없습니다.

## Q3. Refresh Token 탈취에 대한 현재 한계는 무엇인가요?

Rotation을 하지 않아 유효한 Refresh Token은 만료 또는 새 로그인 전까지 반복 사용할 수 있습니다. 사용자당 Redis key 하나라 새 로그인과 logout으로 폐기할 수 있지만, 재사용 탐지는 없습니다. 후속으로 Rotation과 token family 재사용 탐지를 도입할 수 있습니다.

## Q4. JWT logout은 어떻게 구현했나요?

Refresh Token을 Redis에서 삭제하고 현재 Access Token의 jti를 blacklist에 저장합니다. blacklist TTL은 Access Token 남은 수명입니다. 두 작업은 Redis Lua Script로 원자 처리합니다.

## Q5. blacklist key에 왜 토큰 전체가 아니라 jti를 사용했나요?

토큰 원문 노출을 줄이고 key 길이를 작게 유지하기 위해서입니다. Access Token마다 UUID jti가 있어 개별 토큰을 정확히 식별할 수 있습니다.

## Q6. Filter에서 DB User를 매번 조회하지 않는 이유는 무엇인가요?

JWT의 userId/email/role로 Principal을 구성해 인증 요청 비용을 줄입니다. 최신 정보가 필요한 `/me`에서만 userId로 DB를 조회합니다. 대신 토큰 안 role은 Access 만료까지 과거 값일 수 있다는 trade-off가 있습니다.

## Q7. 잘못된 JWT 예외를 왜 GlobalExceptionHandler가 처리하지 않나요?

Filter는 Controller 이전 계층이라 `@RestControllerAdvice` 범위 밖입니다. EntryPoint와 `SecurityErrorResponseWriter`가 같은 `ApiResponse` 형식의 JSON을 직접 작성합니다.

## Q8. Redis 장애 시 인증 정책은 무엇인가요?

Fail-closed입니다. blacklist 확인이 불가능하면 유효 JWT라도 인증을 허용하지 않고 503 `AUTH_005`를 반환합니다. 로그인 중 Refresh 저장 실패도 토큰을 반환하지 않고 503으로 실패합니다.

## Q9. 왜 이메일 로그인은 Google 사용자에게 허용하지 않나요?

Google 사용자 password는 DB 불변식을 위한 random hash이지 사용자가 아는 비밀번호가 아닙니다. `provider=LOCAL`을 명시적으로 요구해 인증 경로를 구분합니다.

## Q10. Google 계정을 이메일로 찾지 않고 sub로 찾는 이유는 무엇인가요?

`sub`는 Google 발급자 내 안정적인 사용자 식별자이고 이메일은 변경될 수 있습니다. `(provider, providerId)`를 외부 계정 식별 키로 사용합니다.

## Q11. 같은 이메일의 LOCAL과 GOOGLE 계정을 왜 자동 연결하지 않았나요?

이메일 일치만으로 계정 소유가 동일하다고 단정하지 않기 위해서입니다. 자동 연결 대신 충돌을 거부하고, 향후 기존 계정 인증을 요구하는 명시적 연결 기능으로 확장하는 편이 안전합니다.

## Q12. OAuth 성공 후 JWT를 바로 redirect하지 않은 이유는 무엇인가요?

URL query는 browser history, log, Referer에 남을 수 있습니다. 대신 TTL 180초의 1회용 code를 보내고 GETDEL 교환 후 response body로 JWT를 전달합니다.

## Q13. OAuth code를 조회 후 삭제가 아니라 GETDEL로 처리한 이유는 무엇인가요?

조회와 삭제 사이 동시 요청 race를 없애기 위해서입니다. GETDEL은 한 요청만 값을 얻도록 원자적으로 처리합니다.

## Q14. OAuth에서 SessionCreationPolicy가 IF_REQUIRED인 이유는 무엇인가요?

Google redirect와 callback 사이 authorization request/state를 보관할 Session이 필요하기 때문입니다. 일반 JWT API는 Session을 만들 필요가 없고, OAuth Handler에서 callback 후 Session을 폐기합니다.

## Q15. 왜 Google Access Token 저장을 막았나요?

후속 Google API를 호출하지 않아 필요하지 않습니다. 필요 없는 장기 보관은 공격 표면이므로 no-op authorized-client repository로 폐기합니다.

## Q16. BCrypt 중복 가입 race는 어떻게 처리했나요?

서비스의 `existsByEmail`은 빠른 사용자 피드백용이고, 최종 일관성은 DB unique 제약이 보장합니다. `saveAndFlush`의 unique 위반도 동일한 `AUTH_001`로 변환합니다.

## Q17. Refresh Token 비교에 MessageDigest.isEqual을 사용한 이유는 무엇인가요?

단순 문자열 비교보다 비교 시간 차이를 줄이는 상수 시간 비교 성질을 사용하려는 목적입니다. Redis 미존재는 먼저 거부합니다.

## Q18. 현재 설계에서 개선할 부분은 무엇인가요?

Refresh Rotation과 reuse detection, Redis에 Refresh 원문 대신 hash 저장, key namespace 버전 관리, 역할 변경 즉시 반영 전략, 다중 기기 세션 모델, OAuth 계정 명시적 연결, 비대칭키 JWT/키 회전 등을 개선할 수 있습니다.

---

# 8. 중요 코드 설명

아래 코드는 현재 프로젝트에서 핵심 부분만 발췌한 것이다. secret, JWT, OAuth code, Redis value의 실제 원문은 포함하지 않는다.

## 8.1 `build.gradle` — 인증 관련 의존성

```groovy
implementation 'org.springframework.boot:spring-boot-starter-data-redis'
implementation 'org.springframework.boot:spring-boot-starter-oauth2-client'
implementation 'org.springframework.boot:spring-boot-starter-security'

implementation 'org.flywaydb:flyway-core'
implementation 'org.flywaydb:flyway-database-postgresql'

implementation 'io.jsonwebtoken:jjwt-api:0.13.0'
runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.13.0'
runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.13.0'
```

- `starter-data-redis`: `StringRedisTemplate`, Redis connection, Spring Data Redis 지원.
- `starter-oauth2-client`: Google authorization redirect, callback, token 교환, userinfo 처리.
- `starter-security`: FilterChain, SecurityContext, PasswordEncoder, OAuth/JWT 보안 기반.
- `flyway-core`: migration 실행 엔진.
- `flyway-database-postgresql`: 현재 Flyway 버전에서 PostgreSQL 지원 모듈.
- `jjwt-api`: 컴파일 시 사용하는 builder/parser API.
- `jjwt-impl`: 실제 JWT 구현체이므로 runtime dependency.
- `jjwt-jackson`: Claim JSON 직렬화·역직렬화.

## 8.2 `application.yaml` / `application-docker.yaml`

### JWT와 Google OAuth

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID}
            client-secret: ${GOOGLE_CLIENT_SECRET}
            scope:
              - email
              - profile
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"

oauth:
  frontend-redirect-uri: ${OAUTH_FRONTEND_REDIRECT_URI:http://localhost:3000/login}
  code-ttl: ${OAUTH_CODE_TTL:180s}

jwt:
  secret: ${JWT_SECRET}
  access-token-expiration: ${JWT_ACCESS_TOKEN_EXPIRATION:30m}
  refresh-token-expiration: ${JWT_REFRESH_TOKEN_EXPIRATION:14d}
```

- Google Client ID/Secret은 파일에 직접 쓰지 않고 환경변수에서 바인딩한다.
- callback URI는 Spring의 base URL과 registrationId로 계산된다.
- OAuth code는 기본 180초다.
- Access 30분, Refresh 14일 기본값을 사용한다.
- `${JWT_SECRET}`에 기본값이 없으므로 누락 시 startup이 실패한다.

### Docker profile의 PostgreSQL과 Redis

```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://postgres:5432/finrisk}
    username: ${POSTGRES_USER:finrisk}
    password: ${POSTGRES_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: none
  data:
    redis:
      host: ${REDIS_HOST:redis}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD}
```

- 실제 Redis 설정은 기본 `application.yaml`이 아니라 `application-docker.yaml`에 있다.
- `ddl-auto: none`이므로 Hibernate가 schema를 만들지 않고 Flyway migration이 책임진다.
- 현재 `spring.flyway.*` 명시 설정은 없다. Flyway 의존성, DataSource, 기본 `db/migration` 위치 때문에 Spring Boot 자동 구성이 V1/V2를 실행한다.

## 8.3 `.env.local.example` — 값이 아닌 형식만 제시

```dotenv
JWT_SECRET=replace-with-base64-encoded-32-byte-secret
GOOGLE_CLIENT_ID=replace-with-google-client-id
GOOGLE_CLIENT_SECRET=replace-with-google-client-secret
OAUTH_FRONTEND_REDIRECT_URI=http://localhost:3000/login
OAUTH_CODE_TTL=180s
```

실제 `.env.local` 값은 문서·로그·Git에 포함하지 않는다.

## 8.4 `docker-compose.local.yml` — backend 환경변수 전달

```yaml
backend:
  environment:
    SPRING_PROFILES_ACTIVE: docker
    SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/${POSTGRES_DB:-finrisk}
    POSTGRES_USER: ${POSTGRES_USER:-finrisk}
    POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:?POSTGRES_PASSWORD is required}
    REDIS_HOST: redis
    REDIS_PORT: 6379
    REDIS_PASSWORD: ${REDIS_PASSWORD:?REDIS_PASSWORD is required}
    JWT_SECRET: ${JWT_SECRET:?JWT_SECRET is required}
    GOOGLE_CLIENT_ID: ${GOOGLE_CLIENT_ID:?GOOGLE_CLIENT_ID is required}
    GOOGLE_CLIENT_SECRET: ${GOOGLE_CLIENT_SECRET:?GOOGLE_CLIENT_SECRET is required}
    OAUTH_FRONTEND_REDIRECT_URI: ${OAUTH_FRONTEND_REDIRECT_URI:-http://localhost:3000/login}
    OAUTH_CODE_TTL: ${OAUTH_CODE_TTL:-180s}
```

- `SPRING_PROFILES_ACTIVE=docker`: `application-docker.yaml`을 활성화한다.
- PostgreSQL/Redis hostname은 Compose service name을 사용한다.
- `:?` 문법은 필수 secret 누락 시 Compose 단계에서 실패시킨다.
- 실제 값은 `.env.local`에서 읽어 container runtime 환경으로 전달한다.

## 8.5 `V1__create_app_users.sql`

```sql
CREATE TABLE app_users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    name VARCHAR(50) NOT NULL,
    role VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT uk_app_users_email UNIQUE (email)
);
```

- `app_users`: `users` 같은 일반 이름과 DB 충돌 가능성을 피한다.
- `password NOT NULL`: LOCAL과 GOOGLE 모두 BCrypt 문자열을 저장한다.
- email unique: 중복 가입과 LOCAL/GOOGLE 자동 중복 계정을 막는다.
- 시간 컬럼: `BaseTimeEntity`와 매핑된다.

## 8.6 `V2__add_oauth_provider_to_app_users.sql`

```sql
ALTER TABLE app_users
    ADD COLUMN provider VARCHAR(20) NOT NULL DEFAULT 'LOCAL',
    ADD COLUMN provider_id VARCHAR(255);

ALTER TABLE app_users
    ADD CONSTRAINT uk_app_users_provider_provider_id
        UNIQUE (provider, provider_id);
```

- 기존 Phase 1 사용자는 default로 `LOCAL`이 된다.
- LOCAL은 providerId가 null, GOOGLE은 Google `sub`를 저장한다.
- `(provider, provider_id)` unique로 같은 Google 계정의 중복 생성을 막는다.

## 8.7 `SecurityConfig`

```java
return http
        .csrf(AbstractHttpConfigurer::disable)
        .exceptionHandling(exception -> exception
                .authenticationEntryPoint(jwtAuthenticationEntryPoint))
        .requestCache(cache -> cache.requestCache(new NullRequestCache()))
        .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
        .oauth2Login(oauth -> oauth
                .authorizedClientRepository(authorizedClientRepository)
                .userInfoEndpoint(userInfo -> userInfo
                        .userService(customOAuth2UserService))
                .successHandler(oauthSuccessHandler)
                .failureHandler(oauthFailureHandler))
        .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(
                        "/api/auth/signup",
                        "/api/auth/login",
                        "/api/auth/refresh",
                        "/api/auth/oauth/exchange",
                        "/oauth2/authorization/google",
                        "/login/oauth2/code/google",
                        "/api/health",
                        "/actuator/health",
                        "/actuator/prometheus",
                        "/swagger-ui/**",
                        "/v3/api-docs/**")
                .permitAll()
                .requestMatchers("/api/users/me").authenticated()
                .requestMatchers("/api/auth/logout").authenticated()
                .anyRequest().authenticated())
        .addFilterBefore(jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class)
        .build();
```

- CSRF 비활성화: 브라우저 cookie session이 아니라 Authorization Bearer 기반 API이기 때문이다.
- `NullRequestCache`: 미인증 API 요청을 Session에 저장하고 로그인 후 복원하는 form-login 동작이 필요 없다.
- `IF_REQUIRED`: OAuth handshake에서만 Session을 만들 수 있게 한다.
- `oauth2Login`: custom user service와 성공·실패 Handler를 연결한다.
- auth 시작/교환, health, Swagger는 공개한다.
- `/me`, `/logout`은 인증이 필수다.
- JWT Filter는 username/password filter 전에 실행해 SecurityContext를 먼저 구성한다.

## 8.8 `JwtProvider` — Access/Refresh 생성

```java
private String generateAccessToken(User user, Instant issuedAt) {
    return Jwts.builder()
            .issuer(ISSUER)
            .subject(String.valueOf(user.getId()))
            .id(UUID.randomUUID().toString())
            .issuedAt(Date.from(issuedAt))
            .expiration(Date.from(issuedAt.plus(accessTokenExpiration)))
            .claim(TOKEN_TYPE_CLAIM, "ACCESS")
            .claim("email", user.getEmail())
            .claim("role", user.getRole().name())
            .signWith(signingKey, Jwts.SIG.HS256)
            .compact();
}
```

- `issuer`: 우리 서비스 토큰인지 구분한다.
- `subject`: userId를 표준 Claim에 둔다.
- `id`: blacklist용 고유 jti다.
- `expiration`: Access 수명을 제한한다.
- `tokenType`: Refresh 오용을 막는다.
- email/role: Principal과 authority를 만든다.
- HS256: 동일 secret으로 서명·검증한다.

```java
private String generateRefreshToken(User user, Instant issuedAt) {
    return Jwts.builder()
            .issuer(ISSUER)
            .subject(String.valueOf(user.getId()))
            .id(UUID.randomUUID().toString())
            .issuedAt(Date.from(issuedAt))
            .expiration(Date.from(issuedAt.plus(refreshTokenExpiration)))
            .claim(TOKEN_TYPE_CLAIM, "REFRESH")
            .signWith(signingKey, Jwts.SIG.HS256)
            .compact();
}
```

- Refresh에는 email/role을 넣지 않는다.
- Refresh 시 DB 최신 정보를 읽어 Access Claim을 다시 만든다.

## 8.9 `JwtProvider` — Access/Refresh 파싱

```java
this.accessTokenParser = Jwts.parser()
        .verifyWith(signingKey)
        .requireIssuer(ISSUER)
        .require(TOKEN_TYPE_CLAIM, "ACCESS")
        .build();

this.refreshTokenParser = Jwts.parser()
        .verifyWith(signingKey)
        .requireIssuer(ISSUER)
        .require(TOKEN_TYPE_CLAIM, "REFRESH")
        .build();
```

- Parser 단계에서 signature, issuer, expiration과 tokenType을 검증한다.
- Access와 Refresh Parser를 분리해 호출자가 용도를 실수하기 어렵게 한다.

```java
public AccessTokenClaims parseAccessToken(String token) {
    Jws<Claims> parsed = accessTokenParser.parseSignedClaims(token);
    if (!"HS256".equals(parsed.getHeader().getAlgorithm())) {
        throw new UnsupportedJwtException("Only HS256 access tokens are supported.");
    }

    Claims claims = parsed.getPayload();
    Long userId = parseUserId(claims.getSubject());
    String email = claims.get("email", String.class);
    Role role = Role.valueOf(claims.get("role", String.class));
    String tokenId = claims.getId();
    Date expiration = claims.getExpiration();

    return new AccessTokenClaims(
            userId, email, role, tokenId, expiration.toInstant());
}
```

- Parser 성공 후에도 알고리즘과 애플리케이션 필수 Claim을 명시적으로 확인한다.
- raw `Claims`를 Filter에 넘기지 않고 typed record로 바꾼다.

```java
public RefreshTokenClaims parseRefreshToken(String token) {
    Jws<Claims> parsed = refreshTokenParser.parseSignedClaims(token);
    if (!"HS256".equals(parsed.getHeader().getAlgorithm())) {
        throw new UnsupportedJwtException("Only HS256 refresh tokens are supported.");
    }
    return new RefreshTokenClaims(
            parseUserId(parsed.getPayload().getSubject()));
}
```

- Refresh에서 서비스가 필요로 하는 값은 userId뿐이다.

## 8.10 `JwtAuthenticationFilter`

```java
String token = resolveBearerToken(request);
if (token != null
        && SecurityContextHolder.getContext().getAuthentication() == null) {
    try {
        AccessTokenClaims claims = jwtProvider.parseAccessToken(token);

        if (tokenRevocationStore.isBlacklisted(claims.tokenId())) {
            SecurityContextHolder.clearContext();
            filterChain.doFilter(request, response);
            return;
        }

        CustomUserPrincipal principal = CustomUserPrincipal.from(claims);
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, principal.authorities());

        SecurityContextHolder.getContext().setAuthentication(authentication);
    } catch (DataAccessException exception) {
        SecurityContextHolder.clearContext();
        errorResponseWriter.write(response,
                ErrorCode.AUTH_SERVICE_UNAVAILABLE);
        return;
    } catch (JwtException | IllegalArgumentException exception) {
        SecurityContextHolder.clearContext();
    }
}
filterChain.doFilter(request, response);
```

- Bearer가 없으면 익명 요청으로 진행한다.
- `parseAccessToken()`이 `tokenType=ACCESS`까지 검증한다.
- JWT 검증 후 jti blacklist를 확인한다.
- 정상일 때만 authenticated token을 SecurityContext에 넣는다.
- Redis 장애는 503으로 즉시 중단한다.
- 잘못된 JWT는 익명으로 계속 진행하고 보호 경로에서 EntryPoint가 401을 만든다.

## 8.11 `JwtAuthenticationEntryPoint`

```java
@Override
public void commence(
        HttpServletRequest request,
        HttpServletResponse response,
        AuthenticationException authException
) throws IOException, ServletException {
    errorResponseWriter.write(response, ErrorCode.UNAUTHORIZED);
}
```

- 보호 endpoint에 인증 없이 도달하면 실행된다.
- 모든 누락/위조/만료/blacklist 인증 실패를 외부에는 `AUTH_003`으로 통일한다.
- 내부 JWT 오류와 토큰 원문을 응답에 노출하지 않는다.

## 8.12 `AuthService`

### signup

```java
String email = normalizeEmail(request.email());
if (userRepository.existsByEmail(email)) {
    throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
}

User user = User.create(
        email,
        passwordEncoder.encode(request.password()),
        request.name().trim()
);

try {
    return SignupResponse.from(userRepository.saveAndFlush(user));
} catch (DataIntegrityViolationException exception) {
    throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
}
```

- 입력 정규화 후 BCrypt hash만 Entity에 전달한다.
- 사전 중복 확인과 DB unique race 처리를 모두 한다.

### login

```java
User user = userRepository.findByEmail(email)
        .orElseThrow(() ->
                new BusinessException(ErrorCode.INVALID_CREDENTIALS));

if (user.getProvider() != AuthProvider.LOCAL
        || !passwordEncoder.matches(request.password(), user.getPassword())) {
    throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
}

return authTokenIssuer.issue(user);
```

- Google 계정은 random password hash와 무관하게 LOCAL 로그인이 차단된다.
- 검증 후 공통 issuer로 넘긴다.

### refresh

```java
RefreshTokenClaims claims = jwtProvider.parseRefreshToken(request.refreshToken());
String storedToken = refreshTokenStore.find(claims.userId());

if (!tokensMatch(storedToken, request.refreshToken())) {
    throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
}

User user = userRepository.findById(claims.userId())
        .orElseThrow(() ->
                new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

return new TokenResponse(jwtProvider.generateAccessToken(user));
```

- JWT 하나만 믿지 않고 Redis current token까지 검증한다.
- 현재 DB role/email로 Access만 새로 만든다.

### logout

```java
Duration remainingTtl = Duration.between(
        Instant.now(), principal.expiresAt());
if (remainingTtl.toMillis() <= 0) {
    throw new BusinessException(ErrorCode.UNAUTHORIZED);
}

tokenRevocationStore.revoke(
        principal.userId(), principal.tokenId(), remainingTtl);
```

- Principal에 보관한 exp와 jti가 logout 처리에 사용된다.
- blacklist는 토큰의 실제 남은 시간만 유지된다.

## 8.13 `AuthTokenIssuer`

```java
public AuthResponse issue(User user) {
    TokenPair tokens = jwtProvider.generateTokens(user);
    try {
        refreshTokenStore.save(user.getId(), tokens.refreshToken());
    } catch (DataAccessException exception) {
        throw new BusinessException(ErrorCode.AUTH_SERVICE_UNAVAILABLE);
    }
    return AuthResponse.from(user, tokens);
}
```

- Access/Refresh를 한 번에 생성한다.
- Refresh 저장이 실패하면 토큰 응답 자체를 실패시킨다.
- 이메일과 OAuth가 동일한 토큰 정책을 사용한다.

## 8.14 `RefreshTokenStore`

```java
private static final String KEY_PREFIX = "refresh:user:";

public void save(Long userId, String refreshToken) {
    redisTemplate.opsForValue().set(
            key(userId), refreshToken, refreshTokenExpiration);
}

public String find(Long userId) {
    return redisTemplate.opsForValue().get(key(userId));
}
```

- key는 `refresh:user:{userId}`다.
- value는 현재 구현에서 Refresh Token 원문이다. 문서에는 실제 값을 포함하지 않는다.
- TTL은 JWT Refresh 만료 설정과 같은 14일이다.

## 8.15 `TokenRevocationStore`

```java
private static final DefaultRedisScript<Long> LOGOUT_SCRIPT =
        new DefaultRedisScript<>(
                "redis.call('DEL', KEYS[1]); "
                + "redis.call('PSETEX', KEYS[2], ARGV[2], ARGV[1]); "
                + "return 1;",
                Long.class
        );
```

- `KEYS[1]`: `refresh:user:{userId}`.
- `KEYS[2]`: `blacklist:access:{jti}`.
- `ARGV[1]`: blacklist 고정 value `"1"`.
- `ARGV[2]`: Access Token 남은 TTL millisecond.
- `DEL`과 `PSETEX`를 원자 실행한다.

```java
public boolean isBlacklisted(String tokenId) {
    return Boolean.TRUE.equals(
            redisTemplate.hasKey(blacklistKey(tokenId)));
}
```

- Filter가 JWT 파싱 후 호출한다.
- blacklist에는 JWT 원문을 넣지 않고 jti만 key에 사용한다.

## 8.16 `User` Entity

```java
@Column(nullable = false, length = 255)
private String password;

@Enumerated(EnumType.STRING)
@Column(nullable = false, length = 20)
private AuthProvider provider;

@Column(name = "provider_id", length = 255)
private String providerId;

@Enumerated(EnumType.STRING)
@Column(nullable = false, length = 20)
private Role role;
```

- password는 LOCAL 실제 비밀번호 또는 GOOGLE random secret의 BCrypt hash다.
- provider는 허용 로그인 경로를 결정한다.
- providerId는 Google `sub`다.
- role 문자열은 Spring authority와 호환된다.

```java
public static User create(String email, String password, String name) {
    return new User(email, password, name,
            Role.ROLE_USER, AuthProvider.LOCAL, null);
}

public static User createGoogle(
        String email, String password, String name, String providerId) {
    return new User(email, password, name,
            Role.ROLE_USER, AuthProvider.GOOGLE, providerId);
}
```

- 생성 메서드가 provider와 기본 role 조합을 강제한다.

## 8.17 `CustomOAuth2UserService`

```java
OAuth2User oauthUser = super.loadUser(userRequest);
Map<String, Object> attributes = oauthUser.getAttributes();

String providerId = requiredString(attributes, "sub",
        "missing_google_subject");
String email = normalizeEmail(requiredString(attributes, "email",
        "missing_google_email"));
if (!isVerified(attributes.get("email_verified"))) {
    throw authenticationException("unverified_google_email");
}
String name = normalizeName(requiredString(attributes, "name",
        "missing_google_name"));

User user = userRepository
        .findByProviderAndProviderId(AuthProvider.GOOGLE, providerId)
        .orElseGet(() -> createGoogleUser(email, name, providerId));
```

- Spring 기본 서비스가 Google userinfo를 가져온 뒤 서비스 규칙을 적용한다.
- email이 아닌 Google sub로 기존 계정을 찾는다.
- 검증된 email만 허용한다.

```java
if (userRepository.existsByEmail(email)) {
    throw authenticationException("oauth_email_conflict");
}

byte[] randomBytes = new byte[32];
secureRandom.nextBytes(randomBytes);
String randomPassword = Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(randomBytes);

User user = User.createGoogle(
        email,
        passwordEncoder.encode(randomPassword),
        name,
        providerId
);
return userRepository.saveAndFlush(user);
```

- LOCAL email 충돌은 자동 연결하지 않는다.
- 32바이트 random 원문은 저장하지 않고 BCrypt 결과만 저장한다.

## 8.18 `OAuthSuccessHandler`

```java
String code = generateCode();
codeStore.save(code, oauthUser.getUserId());

clearSession(request);

String redirectUri = UriComponentsBuilder
        .fromUri(properties.getFrontendRedirectUri())
        .queryParam("oauthCode", code)
        .build()
        .encode()
        .toUriString();
response.sendRedirect(redirectUri);
```

- 서비스 JWT를 만들거나 URL에 넣지 않는다.
- Redis에는 code → userId 매핑을 저장한다.
- OAuth용 임시 Session을 지운 후 프론트로 이동한다.

```java
private String generateCode() {
    byte[] bytes = new byte[32];
    secureRandom.nextBytes(bytes);
    return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(bytes);
}
```

- 예측하기 어려운 256비트 난수를 URL-safe 문자열로 만든다.

## 8.19 `OAuthFailureHandler`

```java
SecurityContextHolder.clearContext();
HttpSession session = request.getSession(false);
if (session != null) {
    session.invalidate();
}

String redirectUri = UriComponentsBuilder
        .fromUri(properties.getFrontendRedirectUri())
        .queryParam("oauthError", "authentication_failed")
        .build()
        .encode()
        .toUriString();
response.sendRedirect(redirectUri);
```

- 실패해도 임시 Session을 남기지 않는다.
- 내부 예외나 Google Token을 프론트 URL에 노출하지 않는다.

## 8.20 `OAuthCodeStore` — 요청서의 OAuthLoginCodeStore

```java
private static final String KEY_PREFIX = "oauth:code:";

public void save(String code, Long userId) {
    redisTemplate.opsForValue().set(
            key(code), userId.toString(), properties.getCodeTtl());
}

public String consume(String code) {
    return redisTemplate.opsForValue().getAndDelete(key(code));
}
```

- key는 `oauth:code:{code}`다.
- 기본 TTL은 180초다.
- `getAndDelete()`로 한 요청만 성공한다.
- Redis value는 userId이며 실제 code/value는 로그에 남기지 않는다.

## 8.21 OAuth Code Exchange — 현재는 `AuthService`

```java
public AuthResponse exchangeOAuthCode(OAuthCodeExchangeRequest request) {
    String userIdValue;
    try {
        userIdValue = oauthCodeStore.consume(request.code());
    } catch (DataAccessException exception) {
        throw new BusinessException(ErrorCode.AUTH_SERVICE_UNAVAILABLE);
    }

    Long userId = parseOAuthUserId(userIdValue);
    User user = userRepository.findById(userId)
            .orElseThrow(() ->
                    new BusinessException(ErrorCode.INVALID_OAUTH_CODE));

    return authTokenIssuer.issue(user);
}
```

- 별도 `OAuthCodeExchangeService` 클래스는 현재 없다.
- code를 GETDEL로 먼저 소비한다.
- 없거나 잘못된 userId면 `AUTH_006`이다.
- DB User를 조회한 뒤 공통 `AuthTokenIssuer`로 JWT와 Refresh 저장을 처리한다.
- code 소비 후 Refresh 저장에서 Redis 장애가 나면 code는 이미 소진된다는 현재 trade-off가 있다. 클라이언트는 OAuth를 다시 시작해야 한다.

---

## 마지막으로 기억할 핵심

```text
비밀번호 신뢰 확인은 BCrypt
외부 Google 신뢰 확인은 Spring OAuth2 Client
서비스 내부 인증 증명은 Access JWT
로그인 지속은 Refresh JWT + Redis current value
즉시 로그아웃은 jti blacklist
브라우저 OAuth 전달은 180초 1회용 code
모든 로그인 수단의 최종 JWT 발급은 AuthTokenIssuer
```

코드를 다시 읽을 때는 “무엇을 검증한 뒤 다음 컴포넌트로 넘기는가”를 따라가면 된다. `AuthService`와 `CustomOAuth2UserService`가 신원을 검증하고, `AuthTokenIssuer`가 서비스 토큰을 발급하며, `JwtAuthenticationFilter`가 이후 요청에서 그 토큰을 서비스 사용자 Principal로 복원한다.
