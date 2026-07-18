# Day11 뉴스·공시 기반 문서 위험 탐지

Day11은 Naver News Search API의 제목·description과 OpenDART 공시 원문을 수집하고, 문장 단위 위험 근거를 `DocumentRiskMatch`로 저장한 뒤 검토 가능한 `CreditEventCandidate`를 만든다.

관리자가 후보를 승인하기 전에는 기존 `RiskScore`에 영향을 주지 않는다. 승인하면 기존 `CreditEvent`를 생성하고 Day9/Day10의 `risk-score-requested` 흐름을 재사용한다.

전체 코드 흐름, 분석 규칙, Kafka·Entity·API·프론트 클래스별 역할과 현재 구현 제한은 [Day11 실제 코드 흐름 가이드](./day11-code-flow-guide.md)를 참고한다.

## 현재 수집 범위

- Naver News: Search API의 제목과 description만 분석하며 언론사 본문은 크롤링하지 않는다.
- OpenDART: 공시 목록과 공시 원본 ZIP/XML/HTML을 공식 API로 수집한다.
- 추가 source는 `DocumentSourceCollector` 구현체로 확장한다.

## 핵심 설정

- `NAVER_NEWS_BASE_URL`
- `NAVER_CLIENT_ID`
- `NAVER_CLIENT_SECRET`
- `DART_API_KEY`
- AWS S3 설정
- `DOCUMENT_COLLECTION_SCHEDULER_ENABLED`
- `DOCUMENT_COLLECTION_SCHEDULER_CRON`
- `DOCUMENT_RECALCULATION_RETRY_DELAY`
