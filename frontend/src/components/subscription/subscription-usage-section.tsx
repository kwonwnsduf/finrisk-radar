"use client";

import { useQuery } from "@tanstack/react-query";
import { Bot, ChartNoAxesCombined, Crown, FileSearch, Landmark, Star } from "lucide-react";
import Link from "next/link";
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
      const [subscription, usage] = await Promise.all([getMySubscription(), getMyUsage()]);
      return { subscription, usage };
    },
  });
  if (isPending) return <p className="mt-6 text-sm text-slate-500">구독 정보를 불러오는 중입니다.</p>;
  if (isError || !data) return <p className="mt-6 text-sm text-red-600">구독 정보를 불러오지 못했습니다.</p>;
  const items = [
    ["백테스트", data.usage.backtest, ChartNoAxesCombined],
    ["위험 보고서", data.usage.riskReport, Landmark],
    ["AI Agent", data.usage.aiAgent, Bot],
    ["관심 자산", data.usage.watchlist, Star],
    ["RAG Search", data.usage.ragSearch, FileSearch],
  ] as const;
  return <section className="mt-6" aria-labelledby="subscription-title"><div className="grid gap-4 lg:grid-cols-[280px_1fr]">
    <Card><CardHeader><CardTitle id="subscription-title">현재 플랜</CardTitle></CardHeader><CardContent><div className="flex items-center gap-3"><Crown className="size-8 text-blue-600" /><strong className="text-2xl">{data.subscription.currentPlan}</strong></div><Button asChild className="mt-6 w-full"><Link href={data.subscription.currentPlan === "FREE" ? "/pricing" : "/settings/subscription"}>{data.subscription.currentPlan === "FREE" ? "PREMIUM 보기" : "구독 관리"}</Link></Button></CardContent></Card>
    <Card><CardHeader><CardTitle>이번 달 사용량</CardTitle></CardHeader><CardContent className="grid gap-3 sm:grid-cols-2">{items.map(([label, item, Icon]) => <div key={label} className="flex items-center justify-between rounded-xl bg-slate-50 p-4"><div className="flex items-center gap-2 text-sm font-medium"><Icon className="size-4 text-blue-600" />{label}</div><strong className="text-sm">{usageText(item)}</strong></div>)}</CardContent></Card>
  </div></section>;
}
