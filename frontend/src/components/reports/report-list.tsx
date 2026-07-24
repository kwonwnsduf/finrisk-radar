"use client";
import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";
import { Card, CardContent } from "@/components/ui/card";
import {
  getReports,
  type ReportStatus,
  type ReportType,
} from "@/lib/api/reports";
export function ReportList() {
  const [type, setType] = useState<ReportType | "">("");
  const [status, setStatus] = useState<ReportStatus | "">("");
  const [page, setPage] = useState(0);
  const q = useQuery({
    queryKey: ["reports", type, status, page],
    queryFn: () =>
      getReports({
        reportType: type || undefined,
        status: status || undefined,
        page,
      }),
  });
  return (
    <div className="mx-auto max-w-6xl space-y-5">
      <header>
        <p className="text-sm font-semibold text-blue-600">AI Reports</p>
        <h1 className="text-3xl font-bold">AI 리포트</h1>
      </header>
      <div className="flex gap-2">
        <select
          aria-label="리포트 유형"
          className={select}
          value={type}
          onChange={(e) => {
            setType(e.target.value as ReportType | "");
            setPage(0);
          }}
        >
          <option value="">전체 유형</option>
          <option value="RISK_ANALYSIS">위험 분석</option>
          <option value="BACKTEST_ANALYSIS">백테스트 해석</option>
          <option value="WATCHLIST_SUMMARY">관심목록</option>
        </select>
        <select
          aria-label="리포트 상태"
          className={select}
          value={status}
          onChange={(e) => {
            setStatus(e.target.value as ReportStatus | "");
            setPage(0);
          }}
        >
          <option value="">전체 상태</option>
          {["REQUESTED", "RUNNING", "COMPLETED", "FAILED"].map((x) => (
            <option key={x}>{x}</option>
          ))}
        </select>
      </div>
      {q.isLoading ? (
        <div className="h-40 animate-pulse rounded-xl bg-slate-200" />
      ) : q.data?.items.length ? (
        q.data.items.map((r) => (
          <Link key={r.id} href={`/reports/${r.id}`}>
            <Card className="mb-3 hover:border-blue-300">
              <CardContent className="flex items-center justify-between py-5">
                <div>
                  <p className="font-semibold">
                    {r.title ?? r.question ?? r.reportType}
                  </p>
                  <p className="mt-1 text-xs text-slate-500">
                    {r.reportType} ·{" "}
                    {new Date(r.requestedAt).toLocaleString("ko-KR")}
                  </p>
                </div>
                <span className="rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold">
                  {r.status}
                </span>
              </CardContent>
            </Card>
          </Link>
        ))
      ) : (
        <p className="rounded-xl border border-dashed p-10 text-center text-slate-500">
          생성된 리포트가 없습니다.
        </p>
      )}
      <div className="flex justify-end gap-2">
        <button disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
          이전
        </button>
        <button
          disabled={!q.data || page + 1 >= q.data.totalPages}
          onClick={() => setPage((p) => p + 1)}
        >
          다음
        </button>
      </div>
    </div>
  );
}
const select = "h-10 rounded-lg border border-slate-200 bg-white px-3 text-sm";
