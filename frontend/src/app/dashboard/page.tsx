import { ShieldCheck, WalletCards } from "lucide-react";

import { CommonChart, type ChartPoint } from "@/components/common/common-chart";
import { DashboardCard } from "@/components/common/dashboard-card";
import { BackendHealthCard } from "@/components/dashboard/backend-health-card";
import { SubscriptionUsageSection } from "@/components/subscription/subscription-usage-section";

const sampleRiskData: ChartPoint[] = [
  { label: "월", value: 42 },
  { label: "화", value: 38 },
  { label: "수", value: 51 },
  { label: "목", value: 47 },
  { label: "금", value: 56 },
  { label: "토", value: 49 },
  { label: "일", value: 44 },
];

export default function DashboardPage() {
  return (
    <div className="mx-auto max-w-7xl">
      <div className="mb-6">
        <p className="text-sm font-semibold text-blue-600">오늘의 요약</p>
        <h2 className="mt-1 text-2xl font-bold tracking-tight text-slate-950">
          리스크 모니터링
        </h2>
      </div>

      <section className="grid gap-4 md:grid-cols-3" aria-label="주요 지표">
        <BackendHealthCard />
        <DashboardCard
          title="총 자산"
          value="준비 중"
          description="데이터 연동 전 샘플 카드"
          icon={<WalletCards className="size-5" aria-hidden="true" />}
        />
        <DashboardCard
          title="리스크 점수"
          value="준비 중"
          description="분석 기능은 이후 단계에서 제공됩니다"
          icon={<ShieldCheck className="size-5" aria-hidden="true" />}
        />
      </section>

      <SubscriptionUsageSection />

      <section className="mt-6">
        <CommonChart
          title="주간 리스크 추이"
          description="화면 구성을 위한 샘플 데이터입니다"
          data={sampleRiskData}
        />
      </section>
    </div>
  );
}
