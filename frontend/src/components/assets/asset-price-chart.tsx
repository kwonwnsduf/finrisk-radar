"use client";

import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";

import type { Asset } from "@/lib/api/assets";
import {
  fetchMarketPrices,
  getCollectionJob,
  getMarketPrices,
  type CollectionStatus,
} from "@/lib/api/market-prices";
import { apiErrorMessage } from "@/lib/api/error-message";
import { useAuthStore } from "@/store/auth-store";
import { CommonChart } from "@/components/common/common-chart";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

type Period = "1M" | "6M" | "1Y" | "ALL";
const PERIODS: Period[] = ["1M", "6M", "1Y", "ALL"];

function isoDate(date: Date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function rangeFor(period: Period, collection = false) {
  const end = new Date();
  const start = new Date(end);
  if (period === "1M") start.setMonth(start.getMonth() - 1);
  if (period === "6M") start.setMonth(start.getMonth() - 6);
  if (period === "1Y") start.setFullYear(start.getFullYear() - 1);
  if (period === "ALL") {
    if (!collection) return {};
    start.setFullYear(start.getFullYear() - 5);
  }
  return { startDate: isoDate(start), endDate: isoDate(end) };
}

function marketTicker(asset: Asset) {
  if (asset.ticker.includes(".")) return asset.ticker.toUpperCase();
  if (asset.market === "KOSPI") return `${asset.ticker}.KS`;
  if (asset.market === "KOSDAQ") return `${asset.ticker}.KQ`;
  return asset.ticker.toUpperCase();
}

function terminal(status?: CollectionStatus) {
  return status === "COMPLETED" || status === "FAILED";
}

export function AssetPriceChart({ asset }: { asset: Asset }) {
  const [period, setPeriod] = useState<Period>("1Y");
  const [jobId, setJobId] = useState<string | null>(null);
  const queryClient = useQueryClient();
  const authStatus = useAuthStore((state) => state.status);
  const range = useMemo(() => rangeFor(period), [period]);

  const pricesQuery = useQuery({
    queryKey: ["market-prices", asset.id, range.startDate, range.endDate],
    queryFn: () => getMarketPrices(asset.id, range.startDate, range.endDate),
  });
  const jobQuery = useQuery({
    queryKey: ["market-price-job", jobId],
    queryFn: () => getCollectionJob(jobId!),
    enabled: jobId !== null,
    refetchInterval: (query) => terminal(query.state.data?.status) ? false : 2_000,
  });
  const fetchMutation = useMutation({
    mutationFn: () => {
      const collectionRange = rangeFor(period, true);
      return fetchMarketPrices({
        assetId: asset.id,
        ticker: marketTicker(asset),
        startDate: collectionRange.startDate!,
        endDate: collectionRange.endDate!,
      });
    },
    onSuccess: (job) => setJobId(job.jobId),
  });

  useEffect(() => {
    if (jobQuery.data?.status === "COMPLETED") {
      void queryClient.invalidateQueries({ queryKey: ["market-prices", asset.id] });
    }
  }, [asset.id, jobQuery.data?.status, queryClient]);

  if (asset.assetType === "BOND_ISSUER") return null;

  const chartData = (pricesQuery.data ?? []).map((price) => ({
    label: price.date,
    value: price.close,
  }));
  const status = jobQuery.data?.status ?? (fetchMutation.isPending ? "REQUESTED" : undefined);
  const error = fetchMutation.isError
    ? apiErrorMessage(fetchMutation.error, "가격 수집 요청에 실패했습니다.")
    : jobQuery.isError
      ? apiErrorMessage(jobQuery.error, "수집 상태를 확인하지 못했습니다.")
      : null;

  return (
    <div className="mt-6 space-y-4">
      <Card>
        <CardHeader>
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <CardTitle className="text-base">시장 가격</CardTitle>
              <p className="mt-1 text-sm text-slate-500">일별 종가 · {marketTicker(asset)}</p>
            </div>
            <div className="flex gap-1" aria-label="가격 조회 기간">
              {PERIODS.map((item) => (
                <Button key={item} size="sm" variant={period === item ? "default" : "outline"}
                  onClick={() => setPeriod(item)}>{item}</Button>
              ))}
            </div>
          </div>
        </CardHeader>
        <CardContent>
          {pricesQuery.isLoading ? <p className="py-16 text-center text-slate-500">가격을 불러오는 중입니다.</p> : null}
          {pricesQuery.isError ? <p role="alert" className="py-8 text-center text-red-600">가격을 불러오지 못했습니다.</p> : null}
          {!pricesQuery.isLoading && !pricesQuery.isError && chartData.length > 0
            ? <CommonChart title={`${period} 종가 추이`} data={chartData} />
            : null}
          {!pricesQuery.isLoading && !pricesQuery.isError && chartData.length === 0 ? (
            <div className="rounded-2xl bg-slate-50 px-5 py-10 text-center">
              <p className="text-sm text-slate-600">선택한 기간의 가격 데이터가 없습니다.</p>
              {authStatus === "authenticated" ? (
                <Button className="mt-4" onClick={() => fetchMutation.mutate()}
                  disabled={fetchMutation.isPending || status === "REQUESTED" || status === "RUNNING"}>
                  {status === "REQUESTED" || status === "RUNNING" ? "수집 중…" : "가격 데이터 수집"}
                </Button>
              ) : (
                <Button asChild className="mt-4"><Link href="/login">로그인 후 수집</Link></Button>
              )}
            </div>
          ) : null}
          {status ? <p role="status" className="mt-4 text-sm text-slate-600">
            수집 상태: {status}{jobQuery.data?.message ? ` · ${jobQuery.data.message}` : ""}
          </p> : null}
          {error ? <p role="alert" className="mt-3 text-sm text-red-600">{error}</p> : null}
        </CardContent>
      </Card>
    </div>
  );
}
