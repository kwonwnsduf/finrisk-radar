"use client";

import { useQuery } from "@tanstack/react-query";
import { Bot, ChartNoAxesCombined, Crown, Landmark, Star } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { getMySubscription } from "@/lib/api/subscriptions";
import { getMyUsage, type UsageItem } from "@/lib/api/usage";

function usageText(item: UsageItem) {
  return item.limit === null ? `${item.used}회 / 무제한` : `${item.used} / ${item.limit}`;
}

export function SubscriptionUsageSection() {
  const { data, isPending, isError } = useQuery({
    queryKey: ["subscription-usage"],
    queryFn: async () => {
      const [subscription, usage] = await Promise.all([
        getMySubscription(),
        getMyUsage(),
      ]);
      return { subscription, usage };
    },
  });

  if (isPending) {
    return <p className="mt-6 text-sm text-slate-500">구독 정보를 불러오는 중입니다.</p>;
  }
  if (isError || !data) {
    return <p className="mt-6 text-sm text-red-600">구독 정보를 불러오지 못했습니다.</p>;
  }

  const items = [
    ["백테스트", data.usage.backtest, ChartNoAxesCombined],
    ["위험 리포트", data.usage.riskReport, Landmark],
    ["AI Agent", data.usage.aiAgent, Bot],
    ["관심 자산", data.usage.watchlist, Star],
  ] as const;

  return (
    <section className="mt-6" aria-labelledby="subscription-title">
      <div className="grid gap-4 lg:grid-cols-[280px_1fr]">
        <Card>
          <CardHeader>
            <CardTitle id="subscription-title">현재 플랜</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="flex items-center gap-3">
              <Crown className="size-8 text-blue-600" />
              <strong className="text-2xl text-slate-950">{data.subscription.plan}</strong>
            </div>
            <Button className="mt-6 w-full" disabled title="Day14에서 제공 예정">
              프리미엄 전환
            </Button>
            <p className="mt-2 text-center text-xs text-slate-400">결제 기능은 준비 중입니다.</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader><CardTitle>이번 달 사용량</CardTitle></CardHeader>
          <CardContent className="grid gap-3 sm:grid-cols-2">
            {items.map(([label, item, Icon]) => (
              <div key={label} className="flex items-center justify-between rounded-xl bg-slate-50 p-4">
                <div className="flex items-center gap-2 text-sm font-medium text-slate-700">
                  <Icon className="size-4 text-blue-600" />{label}
                </div>
                <strong className="text-sm text-slate-950">{usageText(item)}</strong>
              </div>
            ))}
          </CardContent>
        </Card>
      </div>
    </section>
  );
}
