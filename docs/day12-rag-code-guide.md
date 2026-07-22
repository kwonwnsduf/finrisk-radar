# FinRisk Radar Day 12 RAG 코드 가이드

## 1. Day 12가 하려는 일

Day 12의 목표는 수집된 뉴스와 공시를 단순 키워드가 아니라 **문장의 의미**로 검색할 수 있게 만드는 것이다.

예를 들어 사용자가 다음과 같이 검색한다고 가정한다.

```text
단기 차입금 증가로 유동성 위험이 커진 문서를 찾아줘
```

문서에 정확히 `유동성 위험`이라는 단어가 없어도 `현금 부족`, `차입금 만기`, `상환 부담`처럼 의미가 비슷한 내용을 찾는 것이 목표다.

이를 위해 Day 12는 두 가지 흐름을 구현한다.

```text
[임베딩 생성 흐름]
수집 문서 → Chunk 분할 → OpenAI 임베딩 → pgvector 저장

[검색 흐름]
사용자 질문 → OpenAI 임베딩 → pgvector 유사도 검색 → 문서/Asset/Risk 정보 반환
```

Day 12는 답변을 생성하는 LLM 기능까지 구현하지 않는다. 현재 범위는 향후 RAG 답변 생성에 사용할 **근거 문서를 검색하는 단계**다.

---

## 2. 먼저 알아야 할 용어

### 2.1 임베딩

임베딩은 텍스트의 의미를 숫자 배열로 변환하는 작업이다.

```text
"단기 부채가 증가했다"
→ [0.018, -0.042, 0.107, ...]
```

Day 12에서는 숫자 1,536개로 구성된 벡터를 사용한다.

의미가 비슷한 텍스트는 벡터 공간에서도 가까워진다.

```text
"단기 차입금이 증가했다"
"부채 상환 부담이 커졌다"
→ 비교적 가까운 벡터

"신제품 판매량이 증가했다"
→ 비교적 먼 벡터
```

### 2.2 Chunk

긴 문서 전체를 한 번에 검색 단위로 사용하지 않고 여러 조각으로 나눈 것이다.

```text
공시 문서 8,000자
├─ Chunk 0: 720자
├─ Chunk 1: 650자
├─ Chunk 2: 790자
└─ ...
```

문서 전체가 아니라 관련 문단에 가까운 Chunk를 반환하므로 검색 결과의 근거가 더 구체적이다.

### 2.3 Job

한 문서의 특정 버전을 특정 임베딩 모델로 처리하는 작업 단위다.

```text
문서 ID 10
+ contentVersion 2
+ text-embedding-3-small
+ 1536차원
= 하나의 DocumentEmbeddingJob
```

Job은 처리 상태, 실패 원인, 시도 횟수, 생성 Chunk 수와 활성 세대 여부를 기록한다.

### 2.4 active 세대

동일 문서를 다시 수집하거나 임베딩 모델을 바꾸면 새 Job과 새 Chunk를 만든다. 이전 Chunk를 즉시 덮어쓰지 않는다.

```text
문서 10
├─ Job A: contentVersion 1, COMPLETED, active=false
└─ Job B: contentVersion 2, COMPLETED, active=true
```

검색은 `active=true`인 Job의 Chunk만 사용한다. 이 방식 덕분에 새 임베딩이 실패해도 이전 성공 세대를 유지할 수 있다.

---

## 3. 전체 패키지 구조

```text
com.finrisk.radar.rag
├─ DocumentEmbeddingJob             Job JPA 엔티티
├─ EmbeddingJobStatus               Job 상태 enum
├─ DocumentChunk                    Chunk 값 객체
├─ DocumentEmbeddingJobRepository   Job JPA Repository
├─ DocumentChunkRepository          Chunk JDBC batch 저장
├─ RagVectorSearchRepository        pgvector 검색 SQL
├─ RagSearchCriteria                검색 조건
├─ RagSearchHit                     검색 SQL 결과
├─ chunk
│  └─ DocumentChunker               문서 분할과 임베딩 입력 생성
├─ embedding
│  ├─ EmbeddingClient               provider-neutral 인터페이스
│  ├─ OpenAiEmbeddingClient         OpenAI 실제 구현
│  ├─ OpenAiEmbeddingProperties     모델/차원/batch/timeout 설정
│  └─ EmbeddingClientException      외부 API 오류와 재시도 여부
├─ event
│  └─ EmbeddingRequestedEvent       임베딩 작업 요청 이벤트
├─ kafka
│  ├─ DocumentEmbeddingTrigger      document-collected 소비
│  ├─ RagEventPublisher             rag 요청 이벤트 발행
│  ├─ EmbeddingRequestedConsumer    worker 이벤트 소비
│  ├─ RagKafkaConfiguration         Topic 설정
│  ├─ RagKafkaErrorConfiguration    재시도/최종 실패 처리
│  └─ RagTopics                     Topic 이름
├─ service
│  ├─ EmbeddingRequestService       Job 생성과 Kafka 요청
│  ├─ EmbeddingJobService           Job 상태 트랜잭션
│  ├─ DocumentEmbeddingService      worker 오케스트레이션
│  ├─ EmbeddingPersistenceService   성공 결과 원자적 저장
│  ├─ RagSearchService              검색과 응답 보강
│  └─ NonRetryableEmbeddingException
└─ api
   ├─ RagController                 검색 API
   ├─ DocumentEmbeddingController   상태 조회 API
   ├─ RagAdminController            재처리/rebuild API
   └─ 요청/응답 record
```

---

## 4. DB 구조와 클래스의 관계

```text
documents
    1
    │
    N
document_embedding_jobs
    1
    │
    N
document_chunks
```

### 4.1 `documents`

원본 문서다. Day 12에서는 다음 값이 특히 중요하다.

- `content`: Chunk로 나눌 원문
- `content_hash`: 처리 중 문서 변경 확인
- `content_version`: 문서 세대
- `content_scope`: 본문 품질과 임베딩 가능 여부

`content_scope`는 다음과 같다.

| 값 | 의미 | 임베딩 |
|---|---|---:|
| `FULL_TEXT` | DART 본문 500자 이상 | O |
| `PARTIAL` | DART 본문 100~499자 | O |
| `SNIPPET` | 네이버 제목 + description | O |
| `UNUSABLE` | 100자 미만 또는 제목 수준 | X |

### 4.2 `document_embedding_jobs`

`DocumentEmbeddingJob` JPA 엔티티와 연결된다.

핵심 유일키는 다음과 같다.

```text
(document_id, content_version, embedding_model, embedding_dimensions)
```

같은 이벤트가 여러 번 도착해도 같은 세대의 Job을 하나만 만들기 위한 멱등성 키다.

문서별 `active=true` Job도 하나만 허용한다.

### 4.3 `document_chunks`

본문 조각과 `vector(1536)`을 저장한다.

```text
UNIQUE(job_id, chunk_index)
```

만 유지한다. overlap이나 반복 문구 때문에 같은 `content_hash`가 여러 Chunk에 존재할 수 있으므로 hash는 유일키가 아니다.

이 테이블은 JPA Entity 대신 `DocumentChunk` record와 JDBC Repository로 다룬다. pgvector cast, batch insert, native vector 검색이 핵심이고 Chunk 자체에는 복잡한 상태 전환이 없기 때문이다.

---

## 5. 자동 임베딩 생성 흐름

전체 호출 순서는 다음과 같다.

```text
문서 수집 완료
  ↓
document-collected
  ↓
DocumentEmbeddingTrigger
  ↓
EmbeddingRequestService.request()
  ↓
DocumentEmbeddingJob 생성 또는 기존 Job 재사용
  ↓
rag-embedding-requested
  ↓
EmbeddingRequestedConsumer
  ↓
DocumentEmbeddingService.process()
  ├─ Job PROCESSING
  ├─ 문서 버전 확인
  ├─ DocumentChunker.chunk()
  ├─ OpenAiEmbeddingClient.embedAll()
  └─ EmbeddingPersistenceService.complete()
       ├─ 문서 버전 재확인
       ├─ Chunk batch 저장
       ├─ 이전 Job active=false
       └─ 현재 Job COMPLETED, active=true
```

### 5.1 문서 수집 완료 이벤트

기존 문서 수집 기능은 문서 저장과 Asset 매핑을 완료한 뒤 `document-collected` 이벤트를 발행한다.

Day 12는 기존 이벤트를 별도의 consumer group으로 구독한다. 따라서 기존 위험 분석 consumer와 RAG consumer가 서로 이벤트를 빼앗지 않고 각각 같은 이벤트를 처리한다.

### 5.2 `DocumentEmbeddingTrigger`

```java
@KafkaListener(
    topics = DocumentTopics.COLLECTED,
    groupId = "finrisk-rag-embedding-trigger")
public void consume(DocumentCollectedEvent event) {
    requests.request(event.documentId(), event.correlationId(), false);
}
```

이 클래스는 연결 역할만 한다.

- 문서 내용을 직접 읽지 않는다.
- Chunk를 만들지 않는다.
- OpenAI를 호출하지 않는다.
- `EmbeddingRequestService`에 Job 요청을 위임한다.

### 5.3 `EmbeddingRequestService`

이 서비스는 **임베딩 작업을 새로 만들어야 하는가**를 결정한다.

처리 순서는 다음과 같다.

```text
1. documentId로 Document 조회
2. 현재 contentVersion + 모델 + 차원의 Job 조회
3. 없으면 Job 생성
4. UNUSABLE이면 SKIPPED Job 생성
5. 처리 대상이면 rag-embedding-requested 발행
```

기존 Job이 있으면 상태에 따라 다르게 처리한다.

| 기존 상태 | 자동 이벤트 | 관리자 retry |
|---|---|---|
| `COMPLETED` | 그대로 반환 | 그대로 반환 |
| `SKIPPED` | 그대로 반환 | 그대로 반환 |
| `REQUESTED/PROCESSING` | 같은 Job 이벤트 발행 가능 | 같은 Job 사용 |
| `FAILED` | 보통 그대로 반환 | `REQUESTED`로 바꾸고 재발행 |
| Kafka 발행 실패 | 자동 재발행 가능 | 재발행 가능 |

동시에 같은 문서 이벤트가 들어오면 두 요청이 모두 INSERT를 시도할 수 있다. DB unique 제약 위반이 발생하면 이미 생성된 Job을 다시 조회해 반환한다.

```java
try {
    return jobs.saveAndFlush(created);
} catch (DataIntegrityViolationException exception) {
    return jobs.findByDocumentIdAndContentVersionAndEmbeddingModelAndEmbeddingDimensions(...)
        .orElseThrow(() -> exception);
}
```

이것이 중복 Kafka 이벤트에 대한 멱등 처리다.

### 5.4 `EmbeddingRequestedEvent`

Job 실행에 필요한 식별 정보를 전달한다.

```text
eventVersion
correlationId
jobId
documentId
contentVersion
embeddingModel
embeddingDimensions
occurredAt
```

이벤트에 문서 본문이나 API Key, vector를 넣지 않는다. worker는 `jobId`를 기준으로 DB에서 최신 정보를 다시 읽는다.

### 5.5 `RagEventPublisher`

`rag-embedding-requested` Topic에 이벤트를 발행한다.

```java
kafka.send(topic, documentId.toString(), event)
    .get(5, TimeUnit.SECONDS);
```

문서 ID를 Kafka key로 사용하므로 같은 문서의 이벤트는 동일 partition에 배치되는 방향을 갖는다. 발행 결과를 최대 5초 기다리며 실패하면 Job에 `RAG_EVENT_PUBLISH_FAILED`를 기록할 수 있게 예외를 전달한다.

### 5.6 `EmbeddingRequestedConsumer`

실제 임베딩 worker의 입구다.

```java
public void consume(EmbeddingRequestedEvent event) {
    embeddings.process(event.jobId());
}
```

consumer는 로직을 갖지 않고 `DocumentEmbeddingService`에 위임한다.

### 5.7 `DocumentEmbeddingService`

임베딩 생성 흐름의 중심 오케스트레이터다.

#### 1단계: Job 시작

```java
var job = jobs.start(jobId);
```

Job을 `PROCESSING`으로 바꾸고 `attemptCount`를 증가시킨다. 이미 `COMPLETED`나 `SKIPPED`면 즉시 끝낸다.

#### 2단계: 설정 일치 확인

```java
if (!job.model().equals(embeddings.modelName())
    || job.dimensions() != 1536) {
    throw new NonRetryableEmbeddingException(...);
}
```

이벤트가 대기하는 사이 설정이 바뀌었거나 다른 차원의 Job이면 처리하지 않는다.

#### 3단계: stale 문서 확인

```java
if (document.getContentVersion() != job.contentVersion()
    || !document.getContentHash().equals(job.sourceContentHash())) {
    throw new NonRetryableEmbeddingException(...);
}
```

Job 생성 이후 문서 본문이 바뀌었다면 과거 본문을 임베딩하지 않는다. 새 contentVersion용 Job이 처리해야 한다.

#### 4단계: Chunk 생성

```java
List<DocumentChunk> chunks = chunker.chunk(document.getContent());
```

빈 Chunk 결과는 재시도해도 해결되지 않으므로 비재시도 오류다.

#### 5단계: OpenAI batch 호출

```java
for (int start = 0; start < chunks.size(); start += batchSize) {
    List<String> inputs = ...;
    vectors.addAll(embeddings.embedAll(inputs));
}
```

기본 batch size는 32다. 모든 batch 결과를 메모리에 모은 뒤 저장한다. 중간 batch까지만 성공했다고 일부 Chunk를 DB에 저장하지 않는다.

#### 6단계: 최종 저장

```java
persistence.complete(jobId, chunks, vectors);
```

외부 API 호출이 모두 끝난 후에만 DB 저장 트랜잭션을 시작한다.

### 5.8 왜 OpenAI 호출 중 DB 트랜잭션을 잡지 않는가

외부 API는 수 초 이상 걸리거나 timeout이 발생할 수 있다. 이 시간 동안 DB 트랜잭션을 유지하면 다음 문제가 생긴다.

- DB connection 장기 점유
- lock 유지 시간 증가
- 장애 시 rollback 범위 증가
- OpenAI 지연이 DB 처리량에 직접 영향

그래서 흐름을 분리했다.

```text
짧은 트랜잭션: Job PROCESSING 전환
트랜잭션 없음: Chunk 생성 + OpenAI 호출
짧은 트랜잭션: Chunk 저장 + active 세대 교체
```

### 5.9 `EmbeddingPersistenceService`

모든 벡터가 준비된 뒤 최종 결과를 하나의 트랜잭션으로 반영한다.

```text
1. Job과 Document 다시 조회
2. contentVersion/hash 재확인
3. 현재 Job Chunk 저장
4. 문서의 기존 active Job 모두 비활성화
5. 현재 Job COMPLETED + active=true
```

저장 직전 문서를 다시 확인하는 이유는 OpenAI 호출 중 문서가 변경될 수 있기 때문이다.

이 메서드 전체가 `@Transactional`이므로 Chunk 저장 또는 active 전환 중 하나라도 실패하면 전부 rollback된다. 따라서 다음과 같은 절반 성공 상태를 만들지 않는다.

```text
Chunk 일부만 저장됨
이전 Job은 inactive
새 Job은 아직 실패
```

### 5.10 `DocumentChunkRepository`

Chunk는 JPA가 아니라 `NamedParameterJdbcTemplate`로 저장한다.

```java
CAST(:embedding AS vector)
```

`float[]`을 PostgreSQL vector 문자열로 변환하고 batch insert한다.

```text
[0.12,-0.03,0.55,...]
```

재시도 시 같은 Job에 남은 데이터가 없도록 먼저 해당 Job의 Chunk를 삭제한 뒤 다시 넣는다. DB의 실제 유일키는 `(job_id, chunk_index)`다.

---

## 6. `DocumentChunker` 작동 방식

`DocumentChunker`는 기존 `KoreanSentenceSplitter`와 `DocumentContentNormalizer`를 재사용한다.

상수는 다음 의미다.

```java
MIN_TARGET = 500;
TARGET = 800;
MAX = 1000;
```

- 가능하면 500자 이상 묶는다.
- 약 800자를 목표로 한다.
- 어떤 Chunk도 1,000자를 넘기지 않는다.

### 6.1 처리 단계

```text
Document.content
→ 한국어 문장 분리
→ 1,000자 초과 문장 추가 분할
→ 문장을 500~800자 정도로 묶기
→ 이전 Chunk 마지막 문장 overlap
→ chunkIndex/contentHash 생성
```

### 6.2 overlap

문장 경계에서 맥락이 끊기는 것을 줄이기 위해 이전 Chunk의 마지막 문장을 다음 Chunk 앞에 한 번 더 포함한다.

```text
Chunk 0: 문장 0, 1, 2
Chunk 1: 문장 2, 3, 4
                  ↑ overlap
```

다만 overlap을 넣었을 때 1,000자를 넘으면 생략한다.

### 6.3 긴 단일 문장

한 문장이 1,000자를 넘으면 공백을 우선 찾아 800자 부근에서 나눈다. 적당한 공백이 없으면 800자에서 강제로 나눈다.

### 6.4 저장 본문과 임베딩 입력

DB에는 순수 Chunk 본문만 저장한다.

```text
삼성전자는 단기 차입금 만기를 관리하고 있다...
```

OpenAI에는 제목 문맥을 추가한다.

```text
제목: 삼성전자 사업보고서

본문:
삼성전자는 단기 차입금 만기를 관리하고 있다...
```

제목을 임베딩에 포함하므로 제목만 바뀌어도 `Document.contentVersion`이 증가한다.

### 6.5 동일 hash Chunk를 제거하지 않는 이유

반복 공시 문구나 overlap으로 동일한 내용이 여러 위치에 나타날 수 있다. 따라서 `contentHash`는 진단용이고 식별자가 아니다.

```text
식별/순서: jobId + chunkIndex
비교/진단: contentHash
```

---

## 7. `EmbeddingClient`와 OpenAI 호출

### 7.1 `EmbeddingClient`

```java
public interface EmbeddingClient {
    float[] embed(String text);
    List<float[]> embedAll(List<String> texts);
    String modelName();
    int dimensions();
}
```

서비스는 OpenAI 구체 클래스를 직접 의존하지 않고 이 인터페이스를 사용한다. 테스트에서는 Fake 구현을 넣을 수 있고, 향후 같은 계약을 지원하는 다른 provider로 교체할 수 있다.

### 7.2 `OpenAiEmbeddingProperties`

다음을 관리한다.

- base URL
- API Key
- 모델명
- dimensions
- batch size
- connect/read timeout

Day 12에서는 `dimensions != 1536`이면 애플리케이션 시작 단계에서 실패한다. 다른 차원은 DB vector 컬럼과 인덱스 migration이 필요하기 때문이다.

### 7.3 `OpenAiEmbeddingClient`

OpenAI의 `/v1/embeddings`를 `RestClient`로 호출한다.

요청의 핵심 형태는 다음과 같다.

```json
{
  "input": ["첫 번째 Chunk", "두 번째 Chunk"],
  "model": "text-embedding-3-small",
  "dimensions": 1536,
  "encoding_format": "float"
}
```

응답을 그대로 믿지 않고 검증한다.

- 응답 개수와 입력 개수가 같은가
- 각 응답 index가 유효하고 중복되지 않는가
- 누락된 index가 없는가
- 모든 vector가 정확히 1,536차원인가

응답 순서가 달라도 `index`를 기준으로 원래 입력 순서로 재배열한다.

### 7.4 외부 API 오류 분류

| 오류 | 재시도 | 이유 |
|---|---:|---|
| HTTP 429 | O | 일시적 rate limit 가능 |
| HTTP 5xx | O | provider 일시 장애 가능 |
| timeout/network | O | 일시적 네트워크 문제 가능 |
| HTTP 400/401/403 | X | 요청/인증을 바꾸지 않으면 동일 실패 |
| 응답 count/index 오류 | X | 응답 계약 위반 |
| 1536차원 불일치 | X | 저장 불가능 |
| 빈 입력 | X | 호출 전 validation 문제 |

API Key, 입력 본문, vector는 로그에 출력하지 않는다.

---

## 8. Kafka 재시도와 실패 처리

`RagKafkaErrorConfiguration`은 worker 전용 listener factory를 만든다.

```text
최초 처리
→ 실패
→ 1초 후 재시도 1
→ 실패
→ 2초 후 재시도 2
→ 최종 recover
```

`NonRetryableEmbeddingException`, `IllegalArgumentException`, `DataIntegrityViolationException`은 즉시 recover한다.

최종 recover에서는 다음을 수행한다.

```text
Job status = FAILED
active = false
failureCode 기록
failureMessage 기록
job/document/topic/partition/offset 로그
```

실패한 새 Job은 이전 active Job을 비활성화하지 않는다. 이전 검색 세대는 성공한 새 세대가 완성될 때까지 유지된다.

---

## 9. Job 상태 흐름

```text
                 ┌─────────────┐
                 │  REQUESTED  │
                 └──────┬──────┘
                        │ worker 시작
                        ▼
                 ┌─────────────┐
                 │ PROCESSING  │
                 └───┬─────┬───┘
                     │     │
              성공   │     │ 실패
                     ▼     ▼
             ┌───────────┐ ┌────────┐
             │ COMPLETED │ │ FAILED │
             │ active=true│ └────┬───┘
             └───────────┘       │ 관리자 재처리
                                 └──→ REQUESTED

UNUSABLE 문서
REQUESTED 생성 없이 → SKIPPED
```

`COMPLETED`와 `SKIPPED`는 terminal 상태다. `FAILED`는 관리자 재처리를 통해 다시 `REQUESTED`로 전환할 수 있다.

`attemptCount`는 worker가 Job을 시작할 때마다 증가한다. Kafka 재시도도 새로운 처리 시도이므로 증가한다.

---

## 10. 검색 흐름

전체 호출 순서는 다음과 같다.

```text
POST /api/rag/search
  ↓
Security 인증
  ↓
@UsageLimit(RAG_SEARCH)
  ↓
RagController
  ↓
RagSearchService
  ├─ Asset 필터 유효성 확인
  ├─ 질문 OpenAI 임베딩
  ├─ RagVectorSearchRepository.search()
  ├─ Asset 일괄 조회
  ├─ Risk Match 일괄 조회
  └─ RagSearchResponse 조립
```

### 10.1 `RagController`

Controller는 요청 validation과 서비스 호출만 담당한다.

```java
@PostMapping("/search")
@UsageLimit(UsageType.RAG_SEARCH)
public ApiResponse<List<RagSearchResponse>> search(
    @Valid @RequestBody RagSearchRequest request) {
    return ApiResponse.success(search.search(request));
}
```

질문은 URL query parameter가 아니라 request body로 받는다.

### 10.2 `RagSearchRequest`

지원하는 조건은 다음과 같다.

- `query`: 필수, 최대 500자
- `assetId`
- `documentType`
- `sourceType`
- `publishedFrom`, `publishedTo`
- `limit`: 기본 5, 최대 20
- `minimumSimilarity`: 0~1

### 10.3 `RagSearchService`

먼저 질문 자체를 동일한 OpenAI 모델로 임베딩한다.

```java
query = embeddings.embed(request.query().trim());
```

문서 vector와 질문 vector가 같은 모델과 차원을 사용해야 의미 있는 거리 비교가 가능하다.

그 후 `RagVectorSearchRepository`에서 Chunk를 찾고, 검색된 문서 ID들을 모아 Asset과 Risk Match를 일괄 조회한다. 문서별 개별 조회를 피하기 위한 구조다.

Risk Match는 검색 Chunk의 `sentenceStartIndex ~ sentenceEndIndex` 범위에 해당하는 것만 응답에 포함한다.

### 10.4 `RagVectorSearchRepository`

검색의 가장 중요한 공통 SQL은 다음이다.

```sql
FROM document_chunks c
JOIN document_embedding_jobs j ON j.job_id = c.job_id
JOIN documents d ON d.id = c.document_id
WHERE j.active = TRUE
  AND j.status = 'COMPLETED'
  AND j.embedding_model = :currentModel
  AND j.embedding_dimensions = 1536
```

`document_chunks`만 단독 검색하지 않는 이유는 이전 contentVersion, 이전 모델, 실패하거나 처리 중인 Job의 Chunk를 제외하기 위해서다.

cosine distance 계산은 pgvector 연산자를 사용한다.

```sql
c.embedding <=> CAST(:embedding AS vector)
```

거리가 작을수록 의미가 가깝다. 응답의 similarity는 다음처럼 계산한다.

```sql
1 - cosine_distance
```

정렬은 다음 우선순위다.

```text
1. vector 거리 오름차순
2. 발행일 최신순
3. Chunk ID 최신순
```

Asset 필터는 `EXISTS`, 날짜/type/source/minimum similarity는 SQL에서 직접 적용한다. Top K를 뽑은 다음 Java에서 필터링하면 정확한 Top K가 아니기 때문이다.

### 10.5 검색 응답

한 결과에는 다음 정보가 포함된다.

```text
chunkId / chunkIndex
documentId / title
chunkContent
similarity
documentType / sourceType / contentScope
sourceName / sourceUrl / publishedAt
연결 Asset 목록
해당 Chunk 범위의 Risk Match 목록
```

`contentScope`를 함께 반환하므로 사용자는 결과가 DART 전문인지, 부분 본문인지, 뉴스 snippet인지 구분할 수 있다.

---

## 11. 상태 조회와 관리자 재처리

### 11.1 문서 임베딩 상태 조회

```http
GET /api/documents/{documentId}/embedding-status
```

`DocumentEmbeddingController`가 해당 문서의 가장 최근 요청 Job을 반환한다.

### 11.2 단일 문서 재처리

```http
POST /api/admin/documents/{documentId}/embedding
```

관리자 전용이다. 실패 Job이면 다시 `REQUESTED`로 전환하고 이벤트를 발행한다. 이미 완료된 동일 세대는 중복 생성하지 않는다.

### 11.3 범위 rebuild

```http
POST /api/admin/document-embeddings/rebuild
```

다음 범위 중 하나 이상을 사용한다.

- document IDs
- Asset
- source
- 날짜

기본값은 `dryRun=true`이며 최대 100개 문서만 조회한다. dry-run에서는 후보 문서 ID만 보여주고 Job을 만들지 않는다.

---

## 12. 클래스별 역할 요약

| 클래스 | 한 줄 책임 |
|---|---|
| `DocumentEmbeddingJob` | 임베딩 작업의 상태와 세대를 표현하는 JPA Entity |
| `EmbeddingJobStatus` | REQUESTED/PROCESSING/COMPLETED/FAILED/SKIPPED 상태 |
| `DocumentChunk` | Chunker가 생성한 불변 값 객체 |
| `DocumentChunker` | 문서를 겹침이 있는 500~800자 Chunk로 분리 |
| `DocumentEmbeddingJobRepository` | 동일 세대 Job 조회와 이전 active Job 비활성화 |
| `DocumentChunkRepository` | vector cast와 Chunk batch 저장 |
| `EmbeddingClient` | 임베딩 provider 추상화 |
| `OpenAiEmbeddingClient` | OpenAI 호출과 응답 검증 |
| `OpenAiEmbeddingProperties` | 모델, 1536차원, batch, timeout 설정 검증 |
| `EmbeddingClientException` | 외부 API 실패 코드와 retryable 여부 전달 |
| `EmbeddingRequestedEvent` | worker 실행을 요청하는 Kafka 메시지 |
| `DocumentEmbeddingTrigger` | 기존 문서 수집 완료 이벤트를 RAG 요청으로 연결 |
| `RagEventPublisher` | rag-embedding-requested 이벤트 발행 |
| `EmbeddingRequestedConsumer` | 이벤트를 받아 worker 서비스 실행 |
| `RagKafkaErrorConfiguration` | 1초/2초 재시도와 최종 FAILED 처리 |
| `EmbeddingRequestService` | Job 생성, 멱등성, SKIPPED, 이벤트 발행 결정 |
| `EmbeddingJobService` | Job 상태 변경을 짧은 트랜잭션으로 관리 |
| `DocumentEmbeddingService` | Chunk 생성부터 OpenAI 호출까지 전체 작업 조정 |
| `EmbeddingPersistenceService` | 성공 결과와 active 세대 전환을 원자적으로 저장 |
| `RagVectorSearchRepository` | active/current-model/1536 Chunk의 cosine 검색 |
| `RagSearchService` | 질문 임베딩, 검색, Asset/Risk 응답 보강 |
| `RagController` | 인증 사용자의 RAG 검색 API |
| `DocumentEmbeddingController` | 최근 임베딩 Job 상태 API |
| `RagAdminController` | 관리자 단일 재처리와 범위 rebuild API |

---

## 13. 실패 시나리오별 작동 방식

### OpenAI 일부 batch 실패

```text
batch 1 성공
batch 2 실패
→ DB Chunk 저장 안 함
→ 이전 active Job 유지
→ Kafka 재시도 또는 FAILED
```

### OpenAI 성공 후 DB 저장 실패

```text
모든 vector 생성 성공
→ Chunk 저장 중 DB 실패
→ 최종 트랜잭션 rollback
→ active 세대 변경 없음
→ Kafka 재시도 가능
```

### 처리 중 문서 변경

```text
Job: contentVersion 1
현재 문서: contentVersion 2
→ EMBEDDING_DOCUMENT_STALE
→ 비재시도 실패
→ version 2용 새 Job이 처리
```

### 모델명 변경

```text
기존 Job: model A, active=true
현재 설정: model B
→ model B의 새 Job 생성
→ 성공 전까지 기존 Job은 보존
→ 성공 시 기존 Job inactive, 새 Job active
```

검색 SQL은 현재 모델만 허용하므로 모델 B로 설정을 바꾼 직후부터 새 Job 완료 전까지 해당 문서가 검색되지 않을 수 있다.

### `UNUSABLE` 문서

```text
Document는 보존
→ Job SKIPPED
→ OpenAI 호출 없음
→ Chunk 없음
→ 벡터 검색 노출 없음
```

---

## 14. 메트릭과 로그

임베딩:

- `rag.embedding.completed`
- `rag.embedding.failed`
- `rag.embedding.api.calls`
- `rag.embedding.chunks.created`
- `rag.embedding.duration`

검색:

- `rag.search.requests`
- `rag.search.duration`

로그에는 Job/문서 ID, Chunk 수, batch 수, 실패 코드처럼 운영에 필요한 정보만 남긴다. API Key, 문서 본문, vector는 남기지 않는다.

---

## 15. 테스트가 보장하는 것

| 테스트 | 검증 내용 |
|---|---|
| `DocumentChunkerTest` | 길이, 순서, overlap, 긴 문장, 빈 본문 |
| `DocumentEmbeddingJobTest` | Job 상태 전환과 attempt count |
| `EmbeddingRequestServiceTest` | 중복 Job, 실패 재요청, Kafka 발행 실패 |
| `DocumentEmbeddingServiceTest` | Chunk → embedding → 저장 오케스트레이션 |
| `OpenAiEmbeddingClientTest` | count/index/1536차원/오류 분류 |
| `RagFlywayMigrationIntegrationTest` | pgvector extension, vector(1536), DB 제약 |
| `RagVectorSearchRepositoryIntegrationTest` | inactive/이전 모델 제외와 각종 필터 |
| `RagControllerTest` | API 요청 validation과 응답 연결 |

자동 테스트에서는 실제 과금을 피하기 위해 Fake embedding 또는 모의 HTTP 응답을 사용한다. 실제 키를 사용하는 smoke test는 별도로 수행한다.

---

## 16. 코드를 읽는 추천 순서

처음에는 패키지 순서보다 실행 흐름 순서로 읽는 것이 좋다.

### 임베딩 생성 흐름

```text
1. DocumentContentScope
2. DocumentEmbeddingJob / EmbeddingJobStatus
3. DocumentChunk
4. DocumentChunker
5. EmbeddingClient
6. OpenAiEmbeddingProperties
7. OpenAiEmbeddingClient
8. DocumentEmbeddingTrigger
9. EmbeddingRequestService
10. RagEventPublisher / EmbeddingRequestedEvent
11. EmbeddingRequestedConsumer
12. EmbeddingJobService
13. DocumentEmbeddingService
14. EmbeddingPersistenceService
15. DocumentChunkRepository
16. RagKafkaErrorConfiguration
```

### 검색 흐름

```text
1. RagSearchRequest / RagSearchResponse
2. RagController
3. RagSearchService
4. RagSearchCriteria / RagSearchHit
5. RagVectorSearchRepository
6. DocumentEmbeddingController
7. RagAdminController
```

각 클래스를 읽을 때 다음 질문을 던지면 구조를 이해하기 쉽다.

```text
이 클래스는 입력을 어디서 받는가?
무엇을 검증하는가?
어떤 상태를 바꾸는가?
다음에 어느 클래스를 호출하는가?
실패하면 누가 재시도하거나 상태를 기록하는가?
트랜잭션은 어디에서 시작하고 끝나는가?
```

---

## 17. 핵심 설계 결론

Day 12의 핵심은 단순히 OpenAI API를 한 번 호출하는 것이 아니다.

```text
1. 문서 품질을 scope로 구분한다.
2. 긴 문서를 검색 가능한 Chunk로 나눈다.
3. Chunk의 의미를 1536차원 vector로 저장한다.
4. Job 세대로 문서 변경과 모델 변경을 추적한다.
5. 새 세대가 완전히 성공했을 때만 active를 교체한다.
6. 검색은 active + COMPLETED + 현재 모델 Chunk만 사용한다.
7. Kafka 재시도 중에도 이전 성공 검색 결과를 보호한다.
```

따라서 전체 시스템을 한 문장으로 요약하면 다음과 같다.

> 수집된 문서를 안전하게 버전별 임베딩하고, 현재 유효한 세대의 근거 Chunk만 의미 기반으로 검색하는 시스템이다.
