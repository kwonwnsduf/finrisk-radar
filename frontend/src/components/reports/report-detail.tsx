"use client";
import { useQuery } from "@tanstack/react-query";
import { ExternalLink } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { getReport } from "@/lib/api/reports";
import { ReportProgress } from "@/components/reports/report-progress";
export function ReportDetail({ id }: { id: string }) {
  const query = useQuery({
    queryKey: ["report", id],
    queryFn: () => getReport(id),
    refetchInterval: (q) => {
      const s = q.state.data?.status;
      return s === "REQUESTED" || s === "RUNNING" ? 2000 : false;
    },
  });
  if (query.isLoading)
    return <div className="h-64 animate-pulse rounded-2xl bg-slate-200" />;
  if (query.isError || !query.data)
    return (
      <p role="alert" className="text-red-600">
        리포트를 불러오지 못했습니다.
      </p>
    );
  const r = query.data;
  return (
    <div className="mx-auto max-w-5xl space-y-5">
      <header>
        <p className="text-sm font-semibold text-blue-600">{r.reportType}</p>
        <h1 className="mt-1 text-3xl font-bold">
          {r.title ?? "AI 리포트 생성 중"}
        </h1>
        <p className="mt-2 text-sm text-slate-500">
          {new Date(r.requestedAt).toLocaleString("ko-KR")} ·{" "}
          {r.model ?? "모델 대기"}
        </p>
      </header>
      <ReportProgress status={r.status} current={r.currentStep} />
      {r.status === "FAILED" ? (
        <p role="alert" className="rounded-xl bg-red-50 p-4 text-red-700">
          {r.failureMessage ?? "리포트 생성에 실패했습니다."}
        </p>
      ) : null}
      {r.structuredResult ? (
        <StructuredResult value={r.structuredResult} />
      ) : null}
    </div>
  );
}
function StructuredResult({ value }: { value: Record<string, unknown> }) {
  return (
    <div className="space-y-4">
      {Object.entries(value).map(([key, item]) => (
        <Card key={key}>
          <CardHeader>
            <CardTitle className="text-base">{label(key)}</CardTitle>
          </CardHeader>
          <CardContent>
            <Value value={item} />
          </CardContent>
        </Card>
      ))}
    </div>
  );
}
function Value({ value }: { value: unknown }) {
  if (Array.isArray(value))
    return (
      <div className="space-y-2">
        {value.length ? (
          value.map((item, i) => (
            <div key={i} className="rounded-lg bg-slate-50 p-3">
              <Value value={item} />
            </div>
          ))
        ) : (
          <p className="text-sm text-slate-400">없음</p>
        )}
      </div>
    );
  if (value && typeof value === "object") {
    const object = value as Record<string, unknown>;
    const url = typeof object.sourceUrl === "string" ? object.sourceUrl : null;
    return (
      <div className="space-y-1 text-sm">
        {Object.entries(object)
          .filter(([k]) => k !== "sourceUrl")
          .map(([k, v]) => (
            <div key={k}>
              <span className="font-semibold text-slate-600">{label(k)}: </span>
              <Value value={v} />
            </div>
          ))}
        {url ? (
          <a
            className="inline-flex items-center gap-1 text-blue-600"
            href={url}
            target="_blank"
            rel="noreferrer"
          >
            원문 보기
            <ExternalLink className="size-3" />
          </a>
        ) : null}
      </div>
    );
  }
  return (
    <span className="whitespace-pre-wrap text-sm leading-7 text-slate-700">
      {value == null ? "-" : String(value)}
    </span>
  );
}
function label(key: string) {
  return (
    (
      {
        summary: "요약",
        overallRiskLevel: "전체 위험 등급",
        keyRiskFactors: "주요 위험 요인",
        evidence: "근거 문서",
        checklist: "체크리스트",
        limitations: "한계",
        disclaimer: "면책",
        strengths: "강점",
        weaknesses: "약점",
        downsideRisks: "하락장 취약점",
        overfittingRisk: "과최적화 가능성",
        improvementIdeas: "개선 아이디어",
        cautions: "주의사항",
      } as Record<string, string>
    )[key] ?? key
  );
}
