# Day 12 RAG 검색 기반

Day 12는 Day 11에서 저장한 문서를 문장 기반 Chunk로 나누고 OpenAI
`text-embedding-3-small`로 1536차원 임베딩을 생성한 뒤 PostgreSQL pgvector에서
cosine 검색하는 단계다. LLM 보고서 생성은 포함하지 않는다.

## 문서 범위

- `FULL_TEXT`: 추출에 성공한 500자 이상의 DART 본문
- `PARTIAL`: 추출에 성공했지만 100~499자인 DART 본문
- `SNIPPET`: 네이버 뉴스 제목과 description
- `UNUSABLE`: 100자 미만이거나 제목과 사실상 같은 본문. 임베딩하지 않는다.

검색 응답의 `contentScope`를 확인해야 하며 `SNIPPET`을 기사 전문으로 취급하면 안 된다.

## 처리 흐름

```text
document-collected
→ rag-embedding-requested
→ 문장 분리와 500~800자 Chunk 생성
→ 32개 단위 OpenAI embedding 호출
→ document_chunks 저장
→ 새 document_embedding_jobs 세대 활성화
```

같은 문서 안의 반복 문구와 overlap은 같은 `content_hash`를 만들 수 있다. 따라서 Chunk의
유일성은 `(job_id, chunk_index)`만으로 보장한다.

모든 벡터 검색은 `document_embedding_jobs`를 JOIN하고 다음 조건을 적용한다.

```sql
j.active = TRUE
AND j.status = 'COMPLETED'
AND j.embedding_model = :currentModel
AND j.embedding_dimensions = 1536
```

Day 12의 vector 컬럼은 `vector(1536)`으로 고정된다. 차원이 다른 모델로 전환하려면
vector 컬럼과 데이터 재생성을 포함하는 별도 Flyway migration이 필요하다.

## API

- `POST /api/rag/search`: 인증 사용자 검색, FREE 월 100회
- `GET /api/documents/{documentId}/embedding-status`: 최신 Job 조회
- `POST /api/admin/documents/{documentId}/embedding`: 실패 Job 재요청
- `POST /api/admin/document-embeddings/rebuild`: 최대 100개 제한 backfill

rebuild는 기본적으로 `dryRun=true`이며 document ID, Asset, source, 날짜 중 하나 이상의
범위를 지정해야 한다.

## 로그와 운영

API Key, 문서 전체 본문, embedding vector는 로그에 남기지 않는다. Job/document ID,
Chunk 수, 입력 문자 수, 처리 시간, 안전한 오류 코드만 기록한다. 실제 OpenAI smoke test와
기존 PostgreSQL volume의 이미지 전환은 DB 백업 후 수동으로 수행한다.
