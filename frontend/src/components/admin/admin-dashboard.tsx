"use client";
import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { AlertTriangle, CreditCard, ShieldCheck, Users } from "lucide-react";
import { DashboardCard } from "@/components/common/dashboard-card";
import { AdminQueryState } from "@/components/admin/admin-query-state";
import { adminKeys, getAdminDashboard, type MoneySummary } from "@/lib/api/admin";
const money = (values: MoneySummary[]) => values.map((v) => `${v.amount.toLocaleString()} ${v.currency}`).join(", ") || "0";
export function AdminDashboard() {
  const query = useQuery({ queryKey: adminKeys.dashboard, queryFn: getAdminDashboard });
  const d = query.data;
  return <div><div className="mb-7"><h1 className="text-3xl font-bold">운영 대시보드</h1><p className="mt-2 text-sm text-slate-500">정확히 저장된 운영 데이터만 집계합니다.</p></div>
    <AdminQueryState loading={query.isPending} error={query.isError} retry={() => void query.refetch()} />
    {d ? <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
      <Link href="/admin/users"><DashboardCard title="전체 사용자" value={d.users.total.toLocaleString()} description={`FREE ${d.users.free} · PREMIUM ${d.users.premium}`} icon={<Users className="size-5" />} /></Link>
      <Link href="/admin/users?tab=subscriptions"><DashboardCard title="활성 구독" value={d.users.activeSubscriptions.toLocaleString()} description={`7일 신규 구독 레코드 ${d.users.newSubscriptionRecordsLast7Days}`} icon={<Users className="size-5" />} /></Link>
      <Link href="/admin/payments"><DashboardCard title="24시간 결제 승인" value={money(d.payments.approvedLast24Hours)} description={`실패 시도 ${d.payments.failedAttemptsLast24Hours}`} icon={<CreditCard className="size-5" />} /></Link>
      <Link href="/admin/payments?recoveryRequired=true"><DashboardCard title="복구 필요 결제" value={d.payments.recoveryRequired.toLocaleString()} description={`미처리 FSD ${d.payments.unresolvedFsd}`} icon={<AlertTriangle className="size-5" />} /></Link>
      <Link href="/admin/fsd"><DashboardCard title="FSD 검토" value={(d.reviews.openFsd + d.reviews.reviewingFsd).toLocaleString()} description={`OPEN ${d.reviews.openFsd} · REVIEWING ${d.reviews.reviewingFsd}`} icon={<ShieldCheck className="size-5" />} /></Link>
      <Link href="/admin/credit-event-candidates"><DashboardCard title="위험 후보" value={d.reviews.pendingCandidates.toLocaleString()} description={`검토 대상 자산 ${d.reviews.pendingCandidateAssets}`} icon={<ShieldCheck className="size-5" />} /></Link>
      <Link href="/admin/operational-issues"><DashboardCard title="최근 실패 작업" value={(d.jobs.failedBacktestsLast24Hours + d.jobs.failedReportsLast24Hours + d.jobs.failedMarketCollectionsLast24Hours + d.jobs.failedDocumentCollectionsLast24Hours).toLocaleString()} description={`stale AI 리포트 ${d.jobs.staleReports}`} icon={<AlertTriangle className="size-5" />} /></Link>
      <Link href="/admin/operational-issues"><DashboardCard title="실행 대기·진행" value={(d.jobs.activeBacktests + d.jobs.activeReports).toLocaleString()} description={`백테스트 ${d.jobs.activeBacktests} · 리포트 ${d.jobs.activeReports}`} icon={<AlertTriangle className="size-5" />} /></Link>
    </div> : null}</div>;
}
