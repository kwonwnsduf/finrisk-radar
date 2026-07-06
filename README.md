# FinRisk Radar

FinRisk Radar는 금융 리스크 분석 서비스를 위한 모노레포 프로젝트입니다.

## 기능별 문서

- [Day 4 자산·관심자산 실행 및 검증](docs/day4-assets-watchlist.md)
- [Day 5 시장 가격 데이터 파이프라인](docs/day5-market-data-pipeline.md)

## 디렉터리 구조

```text
backend/           Spring Boot 백엔드
frontend/          Next.js 프론트엔드
workers/           수집 및 비동기 작업 프로세스
infra/docker/      Docker 관련 설정
infra/prometheus/  Prometheus 설정
infra/grafana/     Grafana 설정
docs/              프로젝트 문서
```

## 현재 상태

Phase 1에서는 모노레포 디렉터리와 공통 루트 파일만 구성합니다.

- 백엔드 및 프론트엔드 코드는 아직 생성하지 않았습니다.
- Docker Compose와 모니터링 설정은 아직 생성하지 않았습니다.
- 로컬 환경변수는 `.env.local.example`을 복사해 이후 단계에서 사용합니다.
