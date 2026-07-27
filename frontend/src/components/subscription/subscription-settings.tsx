"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { getMySubscription } from "@/lib/api/subscriptions";

export function SubscriptionSettings() {
  const query = useQuery({ queryKey: ["subscription"], queryFn: getMySubscription });
  if (query.isPending) return <p>구독 정보를 불러오는 중입니다.</p>;
  if (query.isError || !query.data) return <p role="alert">구독 정보를 불러오지 못했습니다.</p>;
  const data = query.data;
  return <div className="space-y-5">
    <Card><CardHeader><CardTitle>현재 구독</CardTitle></CardHeader><CardContent className="grid gap-3 sm:grid-cols-2">
      <Info label="플랜" value={data.currentPlan} /><Info label="상태" value={data.subscriptionStatus} />
      <Info label="시작일" value={format(data.currentPeriodStart)} /><Info label="종료일" value={format(data.currentPeriodEnd)} />
      <Info label="남은 기간" value={`${data.remainingDays}일`} /><Info label="자동 갱신" value="사용 안 함" />
      {data.currentPlan === "FREE" ? <Button asChild><Link href="/pricing">PREMIUM 보기</Link></Button> : null}
    </CardContent></Card>
    <Card><CardHeader><CardTitle>결제별 구독 기여분</CardTitle></CardHeader><CardContent className="space-y-3">
      {data.entitlements.length === 0 ? <p className="text-sm text-slate-500">구독 기여 내역이 없습니다.</p> : data.entitlements.map((item) =>
        <div key={item.orderId} className="rounded-xl border border-slate-200 p-4">
          <strong>{item.status}</strong>
          <p className="text-sm text-slate-500">{format(item.periodStart)} → {format(item.periodEnd)}</p>
          {item.status === "CANCELED" ? (
            <p className="text-xs text-slate-500">
              실제 사용 종료 {format(item.usedUntil)} · 제거된 미사용 기간 {duration(item.removedUnusedSeconds)}
            </p>
          ) : (
            <p className="text-xs text-slate-500">남은 기여 기간 {duration(item.remainingSeconds)}</p>
          )}
          <p className="text-xs text-slate-400">주문 {item.orderId}</p>
        </div>)}
    </CardContent></Card>
  </div>;
}
function Info({ label, value }: { label: string; value: string }) { return <div className="rounded-xl bg-slate-50 p-4"><p className="text-xs text-slate-500">{label}</p><strong>{value}</strong></div>; }
function format(value?: string) { return value ? new Date(value).toLocaleString("ko-KR") : "-"; }
function duration(seconds: number) {
  const days = Math.floor(seconds / 86_400);
  const hours = Math.floor((seconds % 86_400) / 3_600);
  return `${days}일 ${hours}시간`;
}
