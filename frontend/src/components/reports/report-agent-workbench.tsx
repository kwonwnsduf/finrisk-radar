"use client";

import { useMutation, useQuery } from "@tanstack/react-query";
import { Bot, Play } from "lucide-react";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { getAssets } from "@/lib/api/assets";
import {
  createNaturalBacktest,
  getBacktestJob,
  getCompletedBacktests,
} from "@/lib/api/backtests";
import { apiErrorMessage } from "@/lib/api/error-message";
import {
  createBacktestReport,
  createRiskReport,
  createWatchlistSummary,
} from "@/lib/api/reports";
import { getMyUsage } from "@/lib/api/usage";

type Mode = "RISK" | "BACKTEST_REPORT" | "NATURAL_BACKTEST" | "WATCHLIST";

export function ReportAgentWorkbench() {
  const router = useRouter();
  const [mode, setMode] = useState<Mode>("RISK");
  const [question, setQuestion] = useState(
    "이 자산의 주요 위험을 근거와 함께 분석해줘",
  );
  const [assetId, setAssetId] = useState("");
  const [backtestId, setBacktestId] = useState("");
  const [jobId, setJobId] = useState<string | null>(null);
  const [clarification, setClarification] = useState<string[]>([]);

  const assets = useQuery({ queryKey: ["assets"], queryFn: getAssets });
  const backtests = useQuery({
    queryKey: ["completed-backtests"],
    queryFn: getCompletedBacktests,
  });
  const usage = useQuery({ queryKey: ["usage"], queryFn: getMyUsage });
  const job = useQuery({
    queryKey: ["backtest-job", jobId],
    queryFn: () => getBacktestJob(jobId!),
    enabled: !!jobId,
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      return status === "REQUESTED" || status === "RUNNING" ? 2000 : false;
    },
  });

  const submit = useMutation({
    mutationFn: async () => {
      setClarification([]);
      if (mode === "RISK") {
        return createRiskReport(
          assetId ? Number(assetId) : undefined,
          question,
        );
      }
      if (mode === "BACKTEST_REPORT")
        return createBacktestReport(backtestId, question);
      if (mode === "WATCHLIST") return createWatchlistSummary(question);

      const result = await createNaturalBacktest(
        question,
        assetId ? Number(assetId) : undefined,
      );
      if (result.outcome === "NEEDS_CLARIFICATION") {
        setClarification(result.missingFields);
        return null;
      }
      setJobId(result.jobId);
      return null;
    },
    onSuccess: (result) => {
      if (result?.reportId) router.push(`/reports/${result.reportId}`);
    },
  });

  const modes: [Mode, string][] = [
    ["RISK", "위험 분석"],
    ["BACKTEST_REPORT", "백테스트 해석"],
    ["NATURAL_BACKTEST", "자연어 백테스트"],
    ["WATCHLIST", "관심목록 요약"],
  ];

  return (
    <div className="mx-auto max-w-5xl space-y-6">
      <header>
        <p className="text-sm font-semibold text-blue-600">
          Deterministic AI Agent
        </p>
        <h1 className="mt-1 text-3xl font-bold">AI 분석 에이전트</h1>
        <p className="mt-2 text-sm text-slate-500">
          서버가 검증된 데이터 조회 순서를 통제하며 내부 추론 과정은 노출하지
          않습니다.
        </p>
      </header>

      <div className="grid gap-2 sm:grid-cols-4">
        {modes.map(([value, label]) => (
          <button
            key={value}
            onClick={() => setMode(value)}
            className={`rounded-xl border p-3 text-sm font-semibold ${
              mode === value
                ? "border-blue-600 bg-blue-50 text-blue-700"
                : "bg-white"
            }`}
          >
            {label}
          </button>
        ))}
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <Bot className="size-5" />
            질문
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <textarea
            aria-label="AI 질문"
            className="min-h-32 w-full rounded-xl border border-slate-200 p-3 text-sm"
            maxLength={500}
            value={question}
            onChange={(event) => setQuestion(event.target.value)}
          />
          {mode === "RISK" || mode === "NATURAL_BACKTEST" ? (
            <select
              aria-label="분석 자산"
              className={selectClass}
              value={assetId}
              onChange={(event) => setAssetId(event.target.value)}
            >
              <option value="">질문에서 자동 식별</option>
              {assets.data?.map((asset) => (
                <option key={asset.id} value={asset.id}>
                  {asset.name}
                </option>
              ))}
            </select>
          ) : null}
          {mode === "BACKTEST_REPORT" ? (
            <select
              aria-label="백테스트 선택"
              className={selectClass}
              value={backtestId}
              onChange={(event) => setBacktestId(event.target.value)}
            >
              <option value="">완료 백테스트 선택</option>
              {backtests.data?.items.map((backtest) => (
                <option key={backtest.jobId} value={backtest.jobId}>
                  {backtest.strategyType} · {backtest.startDate}~
                  {backtest.endDate}
                </option>
              ))}
            </select>
          ) : null}
          <div className="flex flex-wrap items-center justify-between gap-3">
            <p className="text-xs text-slate-500">
              남은 횟수 · 위험 {remaining(usage.data?.riskReport)} · AI{" "}
              {remaining(usage.data?.aiAgent)} · 백테스트{" "}
              {remaining(usage.data?.backtest)}
            </p>
            <Button
              onClick={() => submit.mutate()}
              disabled={
                submit.isPending ||
                !question.trim() ||
                (mode === "BACKTEST_REPORT" && !backtestId)
              }
            >
              <Play className="size-4" />
              {submit.isPending ? "처리 중" : "요청"}
            </Button>
          </div>
          {submit.isError ? (
            <p role="alert" className="text-sm text-red-600">
              {apiErrorMessage(submit.error, "요청을 처리하지 못했습니다.")}
            </p>
          ) : null}
          {clarification.length ? (
            <p
              role="status"
              className="rounded-lg bg-amber-50 p-3 text-sm text-amber-800"
            >
              추가 정보가 필요합니다: {clarification.join(", ")}
            </p>
          ) : null}
        </CardContent>
      </Card>

      {job.data ? (
        <Card>
          <CardContent className="py-5">
            <p className="font-semibold">백테스트 {job.data.status}</p>
            <p className="text-sm text-slate-500">{job.data.message}</p>
            {job.data.result ? (
              <p className="mt-3 text-sm">
                총수익률 {Number(job.data.result.totalReturnRate).toFixed(2)}% ·
                MDD {Number(job.data.result.mdd).toFixed(2)}%
              </p>
            ) : null}
          </CardContent>
        </Card>
      ) : null}
    </div>
  );
}

const selectClass =
  "h-11 w-full rounded-xl border border-slate-200 bg-white px-3 text-sm";

function remaining(item?: { used: number; limit: number | null }) {
  if (!item) return "-";
  return item.limit === null ? "무제한" : Math.max(0, item.limit - item.used);
}
