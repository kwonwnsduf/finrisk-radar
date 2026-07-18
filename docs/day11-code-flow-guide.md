# Day11 뉴스·공시 문서 위험 탐지: 실제 코드 흐름 가이드

이 문서는 Day11의 계획이 아니라 현재 저장소에 구현된 코드를 기준으로 작성한 실행 가이드다. 문서 수집 요청이 어떤 클래스와 Kafka Topic을 지나 DB에 저장되고, 어떤 규칙으로 위험 문장을 판정하며, 관리자 승인 후 Day9/Day10 Risk Engine으로 어떻게 연결되는지를 설명한다.

## 1. Day11의 책임과 범위

Day11의 책임은 다음 세 가지다.

1. Naver News Search API와 OpenDART API에서 문서를 수집한다.
2. 문서에서 위험 문장, 위험 표현, assertion, 금액, 통화와 신뢰도를 추출한다.
3. 동일 사건을 `CreditEventCandidate`로 묶고, 관리자 승인 후 기존 `CreditEvent → RiskSignal → RiskScore` 흐름을 실행한다.

Day11은 Day9/Day10 Rule Engine을 대체하지 않는다. 문서 분석 결과가 바로 점수를 바꾸지 않으며, 승인된 `CreditEvent`만 기존 엔진의 입력이 된다.

현재 수집 범위는 다음과 같다.

- Naver News: 검색 API의 `title`과 `description`만 분석한다. 언론사 본문은 크롤링하지 않는다.
- OpenDART: 공시 목록을 조회하고 공시 원본 ZIP을 다운로드한 뒤 가장 긴 XML/HTML 항목의 텍스트를 분석한다.

## 2. 전체 실행 흐름

```text
관리자 API 또는 Scheduler
        │
        │ DocumentCollectionRequestService.request()
        ▼
DocumentCollectionJob(REQUESTED)
        │
        │ document-fetch-requested
        ▼
DocumentCollectorWorker
        │
        │ DocumentCollectionExecutionService.execute()
        ├─ DocumentCollectorRegistry
        │   ├─ NaverNewsSearchCollector
        │   └─ OpenDartDisclosureCollector
        ├─ DocumentPersistenceService
        │   ├─ DocumentContentNormalizer
        │   └─ DocumentRawStorage(S3 또는 unavailable)
        └─ DocumentAssetMappingService
        │
        ▼
Document + DocumentAssetMapping
        │
        │ document-collected (문서별 1건)
        ▼
DocumentRiskAnalysisWorker
        │
        │ DocumentRiskAnalysisService.analyze()
        ├─ KoreanSentenceSplitter
        ├─ DocumentRiskRuleRegistry
        ├─ KoreanAssertionClassifier
        ├─ DocumentAmountExtractor
        └─ SourceReliabilityPolicy
        │
        ▼
DocumentRiskMatch
        │
        │ confidence >= 0.60 && assertion != NEGATED
        ▼
CreditEventCandidateService
        │
        ▼
CreditEventCandidate(PENDING_REVIEW)
        │
        ├─ 관리자 반려 → REJECTED, 종료
        │
        └─ 관리자 승인
              │ CreditEventReviewService.approve()
              ▼
           CreditEvent
              │ CandidateApprovedNotification(AFTER_COMMIT)
              ▼
DocumentRiskRecalculationCoordinator
              │ RiskCalculationRequestService.request()
              ▼
        risk-score-requested
              │
              ▼
        기존 Day9/Day10 Rule Engine
              │
              ▼
        RiskSignal + RiskScore
```

## 3. 수집 요청 단계

### 3.1 관리자 API

`DocumentAdminController.collect()`이 다음 요청을 받는다.

```http
POST /api/admin/document-collections
Content-Type: application/json

{
  "assetIds": [8],
  "sourceTypes": ["NAVER_NEWS", "OPEN_DART"],
  "fromDate": "2026-07-17",
  "toDate": "2026-07-18"
}
```

`DocumentCollectionRequest`가 빈 Asset/source 목록과 null 날짜를 Bean Validation으로 막는다. 날짜 역전은 `DocumentCollectionRequestService`와 DB `CHECK(to_date >= from_date)`가 추가로 막는다.

`DocumentCollectionRequestService.request()`의 실제 처리 순서는 다음과 같다.

1. 날짜 범위를 검사한다.
2. Asset을 조회한다.
3. `BOND_ISSUER`, `REIT`만 허용한다. 다른 AssetType은 예외가 아니라 건너뛴다.
4. Asset × source 조합마다 `DocumentCollectionJob`을 생성한다.
5. `DocumentFetchRequestedEvent`를 `document-fetch-requested`에 발행한다.
6. Kafka 발행 실패 시 Job을 `FAILED`로 바꾼다.

활성 Job 중복은 DB partial unique index가 막는다.

```sql
UNIQUE(asset_id, source_type)
WHERE status IN ('REQUESTED', 'RUNNING')
```

### 3.2 Scheduler

`DocumentCollectionScheduler`는 `DOCUMENT_COLLECTION_SCHEDULER_ENABLED=true`일 때만 생성된다.

- 기본 cron: 매시 정각 `0 0 * * * *`
- 대상: 모든 `BOND_ISSUER`, `REIT`
- source: `NAVER_NEWS`, `OPEN_DART`
- 기간: 어제부터 오늘
- 요청자: `null`인 시스템 요청

현재 Scheduler는 Asset별 예외를 빈 catch로 무시하므로 실패 원인을 Scheduler 로그에서 직접 확인하기 어렵다.

## 4. Kafka 흐름

| Topic | Key | Producer | Consumer | 의미 |
|---|---|---|---|---|
| `document-fetch-requested` | `assetId` | `DocumentEventPublisher.requested()` | `DocumentCollectorWorker` | source별 수집 실행 요청 |
| `document-collected` | `documentId` | `DocumentCollectorWorker` | `DocumentRiskAnalysisWorker` | 문서 저장·Asset 매핑 완료 |
| `document-collection-failed` | `assetId` | `DocumentCollectorWorker` | 현재 전용 Consumer 없음 | 수집 실패 통지 |
| `document-risk-analyzed` | `documentId` | `DocumentRiskAnalysisWorker` | 현재 전용 Consumer 없음 | match/candidate 생성 수 통지 |

모든 Topic은 현재 partition 1, replica 1로 생성된다. 이벤트에는 `eventVersion=1`, `correlationId`, 발생 시간이 포함된다.

`document-fetch-requested` 발행만 최대 5초 기다리는 동기 확인 방식이다. 나머지 이벤트는 `KafkaTemplate.send()` 결과를 기다리지 않는다.

`DocumentRiskAnalyzedEvent.candidateCount`는 새로 생성된 고유 후보 수가 아니라 후보 생성 조건을 통과해 `attach()`를 호출한 Match 수다. 여러 Match가 하나의 기존 후보로 병합되어도 각각 count가 증가한다.

## 5. source별 수집 규칙

### 5.1 공통 확장 계약

`DocumentSourceCollector`는 두 메서드만 요구한다.

```java
boolean supports(DocumentSourceType sourceType);
List<CollectedDocument> collect(DocumentCollectionContext context);
```

`DocumentCollectorRegistry`는 Spring이 주입한 모든 구현체 중 `supports()`가 true인 Collector를 선택한다. 따라서 새 source는 다음 작업으로 추가할 수 있다.

1. `DocumentSourceType` 추가
2. `DocumentSourceCollector` 구현체 추가
3. 별도 Worker 분기 없이 Spring Bean 등록

`CollectedDocument`는 source별 DTO를 수집 파이프라인 밖으로 노출하지 않는 공통 결과다. 분석용 본문과 원본 byte payload를 함께 운반한다.

### 5.2 NaverNewsSearchCollector

요청 계약은 다음과 같다.

```text
Base URL: https://naverapihub.apigw.ntruss.com
Path:     /search/v1/news
Header:   X-NCP-APIGW-API-KEY-ID
Header:   X-NCP-APIGW-API-KEY
Query:    {asset.name} {asset.ticker}
display:  100
start:    1
sort:     date
format:   json
```

각 검색 결과는 다음처럼 변환된다.

- `title`: HTML 제거한 제목
- `content`: `title + ". " + description`
- `summary`: description
- `sourceUrl`: `originallink`, 없으면 Naver `link`
- `sourceName`: URL host
- `rawPayload`: Naver item JSON

수집 기간 필터는 API 응답의 `pubDate`를 서울 시간으로 바꾼 뒤 애플리케이션에서 수행한다. 언론사 URL로 추가 HTTP 요청을 보내지 않는다.

현재 구현 제약:

- 첫 페이지 최대 100건만 조회하고 다음 페이지를 순회하지 않는다.
- Naver API가 제공하는 title/description 외 본문은 없다.

### 5.3 OpenDartDisclosureCollector

`DocumentCollectionExecutionService`가 먼저 `DartCorpCodeService.findCorpCodeByStockCode(asset.ticker)`로 corpCode를 구한다.

Collector는 다음 API를 순서대로 호출한다.

1. `/api/list.json`: corpCode와 기간으로 공시 목록 조회
2. `/api/document.xml`: 각 `rcept_no`의 공시 ZIP 다운로드
3. ZIP의 XML/HTML/HTM 항목을 순회
4. Jsoup으로 텍스트를 추출
5. 텍스트가 가장 긴 항목을 분석용 본문으로 선택

DART status `000`은 정상, `013`은 조회 결과 없음으로 취급한다. 그 외 status는 수집 실패다.

현재 구현 제약:

- 공시 목록 첫 페이지 100건만 조회한다.
- ZIP 내부 문서를 UTF-8로 가정한다.
- 가장 긴 XML/HTML 파일을 본문으로 고르므로 DART의 논리적 main document와 항상 같다는 보장은 없다.
- corpCode를 stockCode/ticker로만 찾기 때문에 JTBC 같은 비상장 발행사는 현재 구조로 자동 매핑하기 어렵다.

## 6. 원본 저장과 Document 중복 제거

### 6.1 S3 저장

`S3DocumentRawStorage`의 key 형식은 다음과 같다.

```text
documents/raw/{source}/{yyyy}/{MM}/{dd}/{jobId}/{externalId}.{extension}
```

예:

```text
documents/raw/naver-news/2026/07/18/{jobId}/{urlHash}.json
documents/raw/open-dart/2026/07/18/{jobId}/{rceptNo}.zip
documents/raw/open-dart/2026/07/18/{jobId}/{rceptNo}-main.xml
```

S3 설정이 없으면 `UnavailableDocumentRawStorage`가 선택된다. 이 경우 수집 자체는 계속되고 `raw_s3_path`가 null이 된다.

### 6.2 정규화

`DocumentContentNormalizer`는 다음을 수행한다.

- text: HTML 제거, 연속 공백 하나로 축약, trim
- URL: fragment(`#...`) 제거, 나머지 scheme/authority/path/query 유지
- hash: SHA-256을 64자리 hex 문자열로 변환

### 6.3 중복 제거와 버전

DB의 기본 문서 identity는 다음 unique key다.

```text
(source_type, external_id)
```

- Naver: 정규화 URL의 SHA-256을 `external_id`로 사용
- DART: `rcept_no`를 `external_id`로 사용

`content_hash`는 정규화 본문 SHA-256이다. 같은 external ID를 다시 수집했을 때 hash가 바뀌면 본문을 갱신하고 `content_version`을 증가시킨다. `content_hash`와 `canonical_url_hash`는 인덱스만 있고 unique가 아니므로 서로 다른 external ID의 동일 본문을 자동 병합하지는 않는다.

## 7. Asset 자동 매핑

`DocumentAssetMappingService.map()`은 문서 하나를 여러 Asset에 연결한다.

| 방법 | confidence | 현재 사용 조건 |
|---|---:|---|
| `CORP_CODE` | 1.00 | OpenDART 수집 요청 대상 Asset |
| `TICKER` | 1.00 | 길이 4 이상 ticker가 제목+본문에 포함 |
| `COMPANY_NAME` | 0.95 | 길이 3 이상 회사명이 제목+본문에 포함 |
| `ALIAS` | 0.90 | `AssetAlias.normalizedAlias`가 본문에 포함 |
| `MANUAL` | 1.00 | 관리자 매핑 API |
| `STOCK_CODE` | 정책상 존재 | 현재 자동 매핑 코드에서는 직접 사용하지 않음 |

수집 요청 대상 Asset은 항상 먼저 연결되고 `is_primary=true`가 된다. 추가로 발견된 Asset도 문서에 연결되며, `(document_id, asset_id)` unique로 중복을 막는다.

`matched_value`는 실제 매핑에 사용한 corpCode, ticker, 회사명 또는 alias다. `AssetAlias` 유형은 `LEGAL_NAME`, `BRAND`, `ABBREVIATION`, `FORMER_NAME`, `MANUAL`이다.

## 8. 문장 기반 위험 분석

### 8.1 분석 반복 구조

`DocumentRiskAnalysisService.analyze(documentId)`는 다음 3중 반복을 수행한다.

```text
문서에 매핑된 각 Asset
  × 문서의 각 문장
    × 등록된 각 위험 Rule
```

Rule 정규식이 한 문장에서 여러 번 발견되면 각 위치마다 `DocumentRiskMatch`를 만든다.

중복 Match key는 다음과 같다.

```text
documentId + assetId + keywordCode + sentenceIndex + matchStartOffset
```

### 8.2 KoreanSentenceSplitter

현재 정규식은 줄바꿈, `.`, `!`, `?`를 문장 경계로 사용한다.

```regex
[^\n.!?]+(?:[.!?]+|$)
```

각 문장은 다음 값을 가진다.

- `index`: 0부터 시작하는 문장 번호
- `text`: trim한 문장
- `documentStart`, `documentEnd`: 문서 전체에서의 위치

`DocumentRiskMatch.matchStartOffset/endOffset`은 문서 전체가 아니라 `sentenceText` 내부 위치다. 문서 전체 위치는 evidence의 `sentenceDocumentStart`를 더해 계산한다. `matchEndOffset`은 Java Matcher의 exclusive end다.

### 8.3 위험 Rule

현재 `DocumentRiskRuleRegistry`의 business mapping은 다음과 같다.

| keywordCode | 탐지 의미 | CreditEventType | severity | rule reliability |
|---|---|---|---|---:|
| `REHABILITATION` | 회생절차/법정관리 | `REHABILITATION_FILED` | CRITICAL | 1.00 |
| `ACCELERATION` | 기한이익상실 | `ACCELERATION_EVENT` | CRITICAL | 1.00 |
| `REFINANCING_FAILURE` | 차환/리파이낸싱 실패 | `REFINANCING_FAILURE` | HIGH | 0.95 |
| `FUNDING_FAILURE` | 자금조달/CP 발행 실패 | `FUNDING_FAILURE` | HIGH | 0.90 |
| `BOND_ISSUANCE_FAILURE` | 회사채 발행 실패·철회 | `BOND_ISSUANCE_FAILURE` | HIGH | 0.95 |
| `RIGHTS_OFFERING_FAILURE` | 유상증자 실패·철회 | `RIGHTS_OFFERING_FAILURE` | HIGH | 0.90 |
| `CREDIT_DOWNGRADE` | 신용등급 하락·강등 | `CREDIT_RATING_DOWNGRADE` | HIGH | 0.95 |
| `NEGATIVE_OUTLOOK` | 등급전망 부정적 | `NEGATIVE_OUTLOOK` | MEDIUM | 0.90 |
| `LIQUIDITY_CRISIS` | 유동성 위기 | `LIQUIDITY_CRISIS` | HIGH | 0.85 |
| `FX_HEDGE_STRESS` | 환헤지 부담/마진콜 | `FX_HEDGE_STRESS` | MEDIUM | 0.80 |
| `CASH_TRAP` | Cash Trap 발동/위반 | `CASH_TRAP_TRIGGERED` | HIGH | 0.90 |
| `DIVIDEND_REDUCTION` | 배당 축소/중단 | `DIVIDEND_REDUCTION` | MEDIUM | 0.80 |
| `LTV_BREACH` | LTV 한도 초과/약정 위반 | `LTV_COVENANT_BREACH` | HIGH | 0.90 |

`keywordCode`는 발견된 문자열이 아니라 탐지 Rule의 안정적인 ID다. 실제 원문 표현은 `matchedText`, 기존 Risk Engine이 이해하는 금융 사건 의미는 `eventType`에 저장한다.

### 8.4 assertion 판정

`KoreanAssertionClassifier`가 문장 전체를 다음 중 하나로 분류한다.

- `AFFIRMED`: 발생 사실로 판단, assertion score 1.00
- `UNCERTAIN`: 우려/가능성/전망, assertion score 0.65
- `NEGATED`: 부정 표현, assertion score 0.00

판정 순서는 부정 표현을 먼저 검사하고, 다음으로 불확실 표현을 검사하며, 둘 다 아니면 확정으로 본다. `NEGATED`도 Match는 저장하지만 후보를 만들지 않는다.

### 8.5 출처 신뢰도

`SourceReliabilityPolicy`의 현재 값은 다음과 같다.

| source | reliability |
|---|---:|
| OpenDART | 1.00 |
| 주요 언론 host | 0.75 |
| 기타 Naver 검색 결과 | 0.60 |

주요 언론 host 목록은 `yna.co.kr`, `reuters.com`, `bloomberg.com`, `hankyung.com`, `mk.co.kr`이다.

### 8.6 최종 Match confidence

최종 신뢰도는 네 요소의 가중합이다.

```text
confidence =
    rule reliability        × 0.35
  + source reliability      × 0.25
  + assertion score         × 0.20
  + asset match confidence  × 0.20
```

소수점 넷째 자리까지 `HALF_UP`으로 반올림한다.

예를 들어 OpenDART에서 확정된 차환 실패가 corpCode 1.00으로 매핑되면:

```text
0.95×0.35 + 1.00×0.25 + 1.00×0.20 + 1.00×0.20 = 0.9825
```

후보 생성 조건은 다음 두 가지를 모두 만족해야 한다.

```text
assertion != NEGATED
confidence >= 0.60
```

### 8.7 금액과 통화 추출

`DocumentAmountExtractor`는 다음 형식을 의도한다.

- 한국어: 원, 만원, 억원, 조원
- 외화: `USD 100M`, `100M USD`
- 통화: KRW, USD, EUR, JPY
- 단위: K, M, B, million, billion

정규화 예:

```text
300억원   → 30000000000.00 KRW
5,000억원 → 500000000000.00 KRW
USD 100M  → 100000000.00 USD
```

한 문장에 금액이 여러 개면 다음 순서로 대표 금액을 선택한다.

1. 위험 키워드 뒤 40자 이내의 가장 가까운 금액
2. 없으면 키워드와 절대 거리가 가장 가까운 금액

대표 금액은 `extractedAmount`, `extractedCurrency`, `amountOriginalText`에 저장하고, 전체 금액 후보는 `evidence.amounts`에 저장한다. 환율 변환은 하지 않는다.

### 8.8 DocumentRiskMatch evidence

Match는 다음 정보를 JSONB evidence로 보존한다.

```json
{
  "keywordCode": "REFINANCING_FAILURE",
  "sentenceDocumentStart": 120,
  "assertion": "UNCERTAIN",
  "amounts": [],
  "sourceType": "NAVER_NEWS",
  "assetMatchMethod": "COMPANY_NAME",
  "analyzerVersion": "document-risk-v1"
}
```

## 9. 동일 사건 후보 병합

`CreditEventCandidateService.attach()`는 먼저 다음 범위의 후보를 찾는다.

```text
같은 assetId
+ 같은 eventType
+ eventDate ±7일
```

그 후 동일 사건 여부를 다음처럼 판단한다.

1. 기존 Match와 신규 Match에 금액이 모두 있으면 통화와 금액이 정확히 같을 때 동일 사건
2. 둘 중 금액이 없으면 문장 token Jaccard 유사도 0.65 이상일 때 동일 사건

동일 후보가 없으면 새 `CreditEventCandidate(PENDING_REVIEW)`를 만든다.

`incidentKey`는 다음 basis를 SHA-256한 뒤 앞 40자만 사용한다.

```text
assetId | eventType | ISO year-week | (currency+amount 또는 sentenceText)
```

각 Match는 `candidate_id`로 후보에 연결된다. 후보의 `confidence`는 연결된 Match 중 최댓값으로 올라간다.

## 10. 관리자 승인과 반려

관리자 화면과 API는 후보를 자동 점수 반영하기 전 사람이 검토하게 한다.

```http
GET  /api/admin/credit-event-candidates?status=PENDING_REVIEW
GET  /api/admin/credit-event-candidates/{id}
POST /api/admin/credit-event-candidates/{id}/approve
POST /api/admin/credit-event-candidates/{id}/reject
```

### 승인

`CreditEventReviewService.approve()`는 다음을 수행한다.

1. 상태가 `PENDING_REVIEW`인지 검사한다.
2. confidence 내림차순으로 Match를 읽는다.
3. 최고 confidence 문장과 원문 금액을 CreditEvent description으로 만든다.
4. 기존 `RiskAdminService.createEvent()`를 호출한다.
5. `externalEventKey`를 `DOCUMENT_CANDIDATE:{candidateId}`로 지정한다.
6. 후보를 `APPROVED`로 바꾸고 `approved_credit_event_id`를 저장한다.
7. 같은 트랜잭션에서 `CandidateApprovedNotification`을 발행한다.

### 반려

`CreditEventReviewService.reject()`는 후보를 `REJECTED`로 바꾸고 검토자, 검토 시간, 메모를 저장한다. `CreditEvent`와 Risk 계산 Job은 생성하지 않는다.

모든 `/api/admin/**` API는 `SecurityConfig`에서 `ROLE_ADMIN`만 허용한다.

현재 프론트는 승인/반려 버튼은 제공하지만 review note 입력 UI는 없고 빈 문자열을 보낸다.

## 11. Day9/Day10 재계산 연결

승인 트랜잭션이 commit되면 `DocumentRiskRecalculationCoordinator.approved()`가 실행된다. `AFTER_COMMIT`을 사용하므로 CreditEvent가 DB에 확정되기 전에 Risk 계산이 시작되는 문제를 피한다.

`RiskCalculationRequestService.request(reviewerUserId, assetId)`는 AssetType에 따라 기존 rule version을 고른다.

- `BOND_ISSUER`: `corporate-risk-v1`
- `REIT`: `reit-risk-v1`

그 후 기존 `risk-score-requested` Kafka 흐름을 실행한다.

### 채권 발행사

`StandardRiskRuleConfiguration.creditEventRiskRule()`은 Asset의 CreditEvent 중 점수가 가장 큰 사건을 선택한다. 대표 점수 예시는 다음과 같다.

| CreditEventType | score / 25 |
|---|---:|
| `NEGATIVE_OUTLOOK` | 2 |
| `CREDIT_RATING_DOWNGRADE` | 4 |
| `SPECULATIVE_GRADE_ENTRY` | 7 |
| `FUNDING_FAILURE`, `RIGHTS_OFFERING_FAILURE`, `BOND_ISSUANCE_FAILURE` | 8 |
| `REFINANCING_FAILURE` | 12 |
| `ACCELERATION_EVENT` | 18 |
| `REHABILITATION_FILED` | 25 |
| 그 밖의 Day11 event | 기본 5 |

### REIT

기존 REIT 사건 Rule이 다음 Day11 event를 사용한다.

- `REFINANCING_FAILURE`: 발생 후 90/180/365일에 따라 6/4/2점
- `RIGHTS_OFFERING_FAILURE`: 4점
- `BOND_ISSUANCE_FAILURE`: 5점
- `CREDIT_RATING_DOWNGRADE`: 2점
- `REHABILITATION_FILED`: 11점
- `LIQUIDITY_CRISIS`, `CASH_TRAP_TRIGGERED`, `LTV_COVENANT_BREACH`: 8점
- `FX_HEDGE_STRESS`: 5점
- `DIVIDEND_REDUCTION`: 3점

Risk 계산 결과에서 `status=CALCULATED`이고 `score>0`인 Rule만 `RiskSignal`로 저장된다.

## 12. 조회 API와 프론트

### 문서 API

```http
GET /api/documents
GET /api/documents/{id}
GET /api/documents/{id}/risk-matches
```

목록 필터:

- `assetId`
- `sourceType`
- `documentType`
- `riskOnly`
- `from`, `to`
- `cursor`, `size` (`size`는 1~100으로 보정)

현재 `DocumentQueryService`는 DB paging query가 아니라 전체 문서를 읽은 후 Java stream으로 필터링한다.

### 관리자 API

```http
POST   /api/admin/document-collections
GET    /api/admin/document-collections/{jobId}
POST   /api/admin/documents/{documentId}/asset-mappings?assetId={assetId}
DELETE /api/admin/documents/{documentId}/asset-mappings/{assetId}
GET    /api/admin/credit-event-candidates
POST   /api/admin/credit-event-candidates/{id}/approve
POST   /api/admin/credit-event-candidates/{id}/reject
```

### 프론트

- `DocumentRiskSection`: Asset 상세에서 최근 문서와 Match를 조회한다.
- `RiskHighlight`: sentence offset으로 matched text를 `<mark>` 처리한다.
- `CreditEventCandidateList`: 관리자 후보 목록, 근거 문장, 금액, 승인/반려 버튼을 표시한다.
- `AssetDetail`: `BOND_ISSUER`, `REIT` 상세 화면에 `DocumentRiskSection`을 붙인다.
- `Sidebar`: 관리자에게 `/admin/credit-event-candidates` 메뉴를 노출한다.

## 13. Entity 관계와 상태

```text
DocumentCollectionJob
  - REQUESTED → RUNNING → COMPLETED
  - REQUESTED/RUNNING → FAILED

Document
  1 ── N DocumentAssetMapping N ── 1 Asset
  1 ── N DocumentRiskMatch

Asset
  1 ── N AssetAlias

CreditEventCandidate
  1 ── N DocumentRiskMatch(candidate_id)
  └─ representative_match_id → DocumentRiskMatch
  - PENDING_REVIEW → APPROVED
  - PENDING_REVIEW → REJECTED

CreditEventCandidate(APPROVED)
  └─ approved_credit_event_id → CreditEvent
       └─ RiskSignal → RiskScore
```

`credit_event_candidates`와 `document_risk_matches`는 서로 참조하므로 Migration은 후보 테이블을 먼저 만들고 Match 테이블 생성 후 `ALTER TABLE`로 representative Match FK를 추가한다.

## 14. 클래스별 역할 사전

### Domain Entity와 Enum

| 클래스 | 역할 |
|---|---|
| `Document` | 정규화한 문서 본문과 source identity, S3 경로, hash/version 저장 |
| `DocumentCollectionJob` | Asset/source/기간 단위 비동기 수집 Job 상태 관리 |
| `AssetAlias` | 회사명 외 브랜드·약칭·과거명 등 자동 매핑용 별칭 |
| `DocumentAssetMapping` | Document와 Asset의 N:M 연결, 매핑 방법과 confidence 저장 |
| `DocumentRiskMatch` | 위험 문장, keyword, offset, assertion, 금액, evidence 저장 |
| `CreditEventCandidate` | 여러 Match를 동일 사건으로 묶은 승인 전 검토 단위 |
| `DocumentType` | `NEWS`, `DISCLOSURE` |
| `DocumentSourceType` | `NAVER_NEWS`, `OPEN_DART` |
| `DocumentAssertionType` | `AFFIRMED`, `UNCERTAIN`, `NEGATED` |
| `AssetMatchMethod` | `CORP_CODE`, `STOCK_CODE`, `TICKER`, `COMPANY_NAME`, `ALIAS`, `MANUAL` |
| `AssetAliasType` | 별칭의 의미 구분 |
| `DocumentCollectionStatus` | 수집 Job 상태 |
| `CreditEventCandidateStatus` | 후보 검토 상태 |
| `RecalculationStatus` | 승인 후 Risk 재계산 추적 상태 |

### Repository

| 클래스 | 역할 |
|---|---|
| `DocumentRepository` | source/external ID dedup 조회와 최신 문서 조회 |
| `DocumentCollectionJobRepository` | 수집 Job 영속화 |
| `AssetAliasRepository` | alias/Asset 기준 별칭 조회 |
| `DocumentAssetMappingRepository` | 문서↔Asset 조회와 수동 삭제 |
| `DocumentRiskMatchRepository` | 문서/후보별 Match 조회와 Match 멱등성 확인 |
| `CreditEventCandidateRepository` | 검토 큐, 재계산 재시도, ±7일 후보 조회 |

### Collector

| 클래스 | 역할 |
|---|---|
| `DocumentSourceCollector` | source 확장 인터페이스 |
| `DocumentCollectorRegistry` | source enum에 맞는 Collector 선택 |
| `DocumentCollectionContext` | Job, Asset, corpCode, 기간 전달 |
| `CollectedDocument` | source 구현체가 공통 파이프라인에 반환하는 DTO |
| `NaverNewsProperties` | Naver base URL/client credential 설정 |
| `NaverNewsSearchCollector` | Naver title/description 수집 |
| `OpenDartDisclosureCollector` | 공시 목록·ZIP 다운로드·XML/HTML 텍스트 추출 |

### Storage와 정규화

| 클래스 | 역할 |
|---|---|
| `DocumentRawStorage` | 원본 저장 추상화 |
| `S3DocumentRawStorage` | S3 key 생성과 putObject 수행 |
| `UnavailableDocumentRawStorage` | S3 미설정 환경의 null-path fallback |
| `DocumentStorageConfiguration` | S3 설정 유무에 따라 구현체 선택 |
| `DocumentContentNormalizer` | HTML/공백/URL 정규화와 SHA-256 생성 |
| `DocumentPersistenceService` | S3 저장, document dedup, 본문 갱신/version 증가 |

### 수집 Service와 Worker

| 클래스 | 역할 |
|---|---|
| `DocumentCollectionRequestService` | Asset/source별 Job 생성 및 요청 이벤트 발행 |
| `DocumentCollectionJobService` | Job 상태 전이와 실패 메시지 제한 |
| `DocumentCollectionExecutionService` | Asset/corpCode/Collector/저장/매핑을 조율 |
| `DocumentCollectionScheduler` | 활성화 시 전체 채권/REIT 정기 수집 |
| `DocumentSchedulingConfiguration` | Spring scheduling 활성화 |
| `DocumentCollectorWorker` | fetch 요청 Consumer, 문서별 collected 이벤트 발행 |

### 분석 Service

| 클래스 | 역할 |
|---|---|
| `KoreanSentenceSplitter` | 본문을 문장과 offset으로 분리 |
| `DocumentRiskRuleRegistry` | keyword regex → event type/severity/reliability 정책 |
| `KoreanAssertionClassifier` | 확정/불확실/부정 판정 |
| `DocumentAmountExtractor` | 금액·통화 정규화와 대표 금액 선택 |
| `SourceReliabilityPolicy` | DART/주요언론/일반뉴스 출처 점수 |
| `DocumentRiskAnalysisService` | 모든 분석기를 조합해 Match와 후보 생성 |
| `DocumentRiskAnalysisWorker` | collected 이벤트 Consumer, analyzed 이벤트 발행 |

### 후보 승인과 Risk 연결

| 클래스 | 역할 |
|---|---|
| `CreditEventCandidateService` | ±7일/금액/Jaccard 기준 사건 병합 |
| `CreditEventReviewService` | 승인 시 기존 CreditEvent 생성, 반려 상태 저장 |
| `CandidateApprovedNotification` | 승인 commit 후 재계산을 시작하는 내부 이벤트 |
| `DocumentRiskRecalculationCoordinator` | 기존 Risk 계산 요청과 DEFERRED 재시도 |

### Kafka

| 클래스 | 역할 |
|---|---|
| `DocumentTopics` | 네 Topic 이름 상수 |
| `DocumentKafkaConfiguration` | Topic 생성 Bean |
| `DocumentEventPublisher` | Kafka key 선택과 이벤트 발행 |
| `DocumentFetchRequestedEvent` | 수집 요청 계약 |
| `DocumentCollectedEvent` | 문서 저장/매핑 완료 계약 |
| `DocumentCollectionFailedEvent` | 수집 실패 계약 |
| `DocumentRiskAnalyzedEvent` | 분석 건수 결과 계약 |

### API/DTO

| 클래스 | 역할 |
|---|---|
| `DocumentAdminController` | 수집 Job 및 수동 Asset 매핑 API |
| `DocumentController` | 문서/Match 조회 API |
| `CreditEventCandidateAdminController` | 후보 조회·승인·반려 API |
| `DocumentCollectionRequest` | 수집 요청 body |
| `DocumentCollectionJobResponse` | Job 상태 응답 |
| `DocumentResponse` | 문서와 연결 Asset/Match 수 응답 |
| `DocumentRiskMatchResponse` | 문장, offset, assertion, 금액, evidence 응답 |
| `CandidateResponse` | 후보와 모든 Match 응답 |
| `CandidateReviewRequest` | 최대 1000자 review note |
| `DocumentQueryService` | 조회 필터와 response 조립 |

### 프론트

| 파일/컴포넌트 | 역할 |
|---|---|
| `frontend/src/lib/api/documents.ts` | 문서·Match·후보 API 타입과 호출 함수 |
| `DocumentRiskSection` | Asset별 최근 문서와 선택 문서의 위험 Match 표시 |
| `RiskHighlight` | sentence offset을 이용한 위험 표현 highlight |
| `CreditEventCandidateList` | 관리자 검토 큐와 승인/반려 action |
| `CreditEventCandidatesPage` | 관리자 검토 페이지 entry point |
| `AssetDetail` | 채권/REIT 상세 화면에 문서 위험 섹션 연결 |
| `Sidebar` | 관리자에게 Event Review 메뉴 노출 |

### 테스트

| 클래스 | 검증 범위 |
|---|---|
| `DocumentAmountExtractorTest` | 한국어/외화 금액 정규화와 대표 금액 선택 |
| `DocumentCollectorRegistryTest` | source별 Collector 선택과 미지원 source 예외 |
| `KoreanAssertionClassifierTest` | 확정·불확실·부정 표현 분류 |
| `NaverNewsSearchCollectorTest` | API HUB URL/header와 title+description만 사용하는 계약 |

현재 테스트는 Collector 전체 Kafka 통합, OpenDART ZIP의 다양한 인코딩, 후보 동시 병합, 승인 후 실제 RiskScore 완료 상태까지는 다루지 않는다.

## 15. DB Migration의 핵심 제약

`V12__create_document_risk_detection.sql`은 다음 무결성을 보장한다.

- 문서: `(source_type, external_id)` unique
- 활성 수집 Job: `(asset_id, source_type)` partial unique
- Asset alias: `(asset_id, normalized_alias)` unique
- 문서 Asset 매핑: `(document_id, asset_id)` unique
- confidence: 0~1 CHECK
- 후보: `(asset_id, event_type, incident_key)` unique
- Match: `(document_id, asset_id, keyword_code, sentence_index, match_start_offset)` unique
- Match offset: `start >= 0`, `end > start` CHECK
- 문서 삭제 시 mapping/match는 `ON DELETE CASCADE`

## 16. 현재 구현에서 반드시 알아야 할 제한과 미완결 지점

다음 항목은 설계가 아니라 현재 코드에서 확인되는 실제 제한이다.

1. **대표 Match 선택 로직**  
   `CreditEventCandidate.representative()`는 신규 Match가 붙을 때마다 `representative_match_id`를 교체한다. 후보 confidence는 최댓값이지만 representative ID는 반드시 최고 confidence Match가 아니다.

2. **재계산 완료 상태 미연결**  
   `RecalculationStatus`에는 `COMPLETED`, `FAILED`가 있지만 현재 Coordinator는 `REQUESTED`, `DEFERRED`까지만 기록한다. Risk Job 완료/실패 이벤트를 후보에 되돌려 기록하는 Consumer가 없다.

3. **기초 지표가 없으면 승인 사건도 점수화되지 않음**  
   현재 Risk context는 채권 발행사의 `financial_metrics`, REIT의 `reit_metrics`가 0건이면 계산 전체를 실패시킨다. 따라서 Day11 CreditEvent가 승인돼도 JTBC/제이알글로벌리츠처럼 기초 데이터가 없으면 RiskScore가 생성되지 않는다.

4. **Scheduler 오류 은폐**  
   Scheduler가 RuntimeException을 무시한다. 운영에서는 구조화 로그와 실패 metric이 필요하다.

5. **Kafka 후속 처리 부족**  
   `document-collection-failed`, `document-risk-analyzed` 전용 Consumer/DLT가 없다. 분석 Worker도 예외를 잡아 failed event로 전환하지 않는다.

6. **문서 내용 변경 시 stale Match 가능성**  
   content version이 올라가도 기존 Match를 삭제하거나 version별로 분리하지 않는다. 같은 위치 key는 건너뛰고, 사라진 과거 Match는 남을 수 있다.

7. **API pagination의 메모리 처리**  
   문서 목록은 DB cursor query가 아니라 `findAll` 후 Java filter/limit 방식이다. 데이터 증가 전에 projection과 keyset paging으로 바꿔야 한다.

8. **수집 pagination 없음**  
   Naver와 DART 모두 첫 페이지 100건까지만 처리한다.

9. **S3 extracted path 미보존**  
    DART ZIP의 `raw_s3_path`는 저장하지만 별도로 저장한 main XML/HTML path는 Document에 기록하지 않는다.

10. **관리자 review note UI 없음**  
    API와 DB 필드는 있지만 프론트 버튼은 빈 note를 보낸다.

## 17. 정상 결과의 예

예를 들어 OpenDART 공시에서 다음 문장이 정상 탐지된다고 가정한다.

```text
회사는 5,000억원 규모의 회사채 발행을 철회했다.
```

결과 흐름:

```text
Document
  sourceType=OPEN_DART
  contentVersion=1

DocumentAssetMapping
  assetId=대상 회사
  matchMethod=CORP_CODE
  confidence=1.00

DocumentRiskMatch
  keywordCode=BOND_ISSUANCE_FAILURE
  eventType=BOND_ISSUANCE_FAILURE
  assertionType=AFFIRMED
  extractedAmount=500000000000.00
  extractedCurrency=KRW
  amountOriginalText=5,000억원
  confidence≈0.9825

CreditEventCandidate
  status=PENDING_REVIEW
  incidentKey={동일 사건 그룹 key}

관리자 승인
  ↓
CreditEvent
  externalEventKey=DOCUMENT_CANDIDATE:{candidateId}
  ↓
기존 Day9/Day10 계산
  ↓
RiskSignal + RiskScore
```

## 18. Day12/Day13 확장 지점

Day12는 `Document.id`, `content`, `contentHash`, `contentVersion`을 입력으로 Chunk와 Embedding을 만들 수 있다. 내용 버전이 바뀌면 기존 Chunk를 폐기하고 새 버전으로 재생성해야 한다.

Day13 LLM/Agent는 다음 Day11 evidence를 검증 가능한 입력으로 사용할 수 있다.

- 원본 Document/source URL/S3 path
- sentence text와 document/sentence offset
- matched text와 keyword code
- assertion type
- 전체 금액 후보와 대표 정규화 금액
- source reliability와 Asset mapping confidence
- analyzer version
- 관리자 승인된 CreditEvent

LLM 결과는 기존 Rule 결과를 덮어쓰기보다 별도 analyzer version과 evidence를 가진 보조 Match로 저장하는 방향이 안전하다.
