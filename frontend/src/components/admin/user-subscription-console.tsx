"use client";
import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { AdminPagination } from "./admin-pagination";
import { AdminQueryState } from "./admin-query-state";
import { AdminStatusBadge } from "./admin-status-badge";
import { AdminTable } from "./admin-table";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { adminKeys, getAdminSubscriptions, getAdminUsers } from "@/lib/api/admin";
export function UserSubscriptionConsole() {
  const [tab, setTab] = useState<"users" | "subscriptions">("users");
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState("");
  const [plan, setPlan] = useState("");
  const params = { search, plan, page, size: 20 };
  const users = useQuery({ queryKey: adminKeys.users(params), queryFn: () => getAdminUsers(params), enabled: tab === "users" });
  const subscriptions = useQuery({ queryKey: adminKeys.subscriptions({ plan, page }), queryFn: () => getAdminSubscriptions({ plan, page, size: 20 }), enabled: tab === "subscriptions" });
  const query = tab === "users" ? users : subscriptions;
  return <div><div className="mb-5 flex gap-2"><Button variant={tab === "users" ? "default" : "outline"} onClick={() => { setTab("users"); setPage(0); }}>사용자</Button><Button variant={tab === "subscriptions" ? "default" : "outline"} onClick={() => { setTab("subscriptions"); setPage(0); }}>구독</Button></div>
    <div className="mb-4 grid gap-3 rounded-xl bg-white p-4 shadow-sm sm:grid-cols-[1fr_180px]">{tab === "users" ? <Input value={search} onChange={(e) => { setSearch(e.target.value); setPage(0); }} placeholder="이메일 또는 이름" /> : <div />}
      <select className="h-9 rounded-md border px-3 text-sm" value={plan} onChange={(e) => { setPlan(e.target.value); setPage(0); }}><option value="">전체 플랜</option><option>FREE</option><option>PREMIUM</option><option>ADMIN</option></select></div>
    <AdminQueryState loading={query.isPending} error={query.isError} empty={query.data?.items.length === 0} retry={() => void query.refetch()} />
    {tab === "users" && users.data ? <AdminTable headers={["사용자","권한","플랜","가입일","활성 구독","구독 종료","최근 결제"]} rows={users.data.items.map((u) => [[<div key={u.userId}><p className="font-semibold">{u.name}</p><p className="text-xs text-slate-500">{u.email}</p></div>], u.role, <AdminStatusBadge key="p" value={u.plan} />, new Date(u.joinedAt).toLocaleString(), u.activeSubscription ? "예" : "아니오", u.subscriptionEndAt ? new Date(u.subscriptionEndAt).toLocaleString() : "-", <AdminStatusBadge key="s" value={u.latestPaymentStatus} />])} /> : null}
    {tab === "subscriptions" && subscriptions.data ? <AdminTable headers={["사용자","플랜","상태","시작","종료","주문"]} rows={subscriptions.data.items.map((s) => [[<div key={s.subscriptionId}><p className="font-semibold">{s.name}</p><p className="text-xs text-slate-500">{s.email}</p></div>], s.plan, <AdminStatusBadge key="s" value={s.status} />, s.currentPeriodStart ? new Date(s.currentPeriodStart).toLocaleString() : "-", s.currentPeriodEnd ? new Date(s.currentPeriodEnd).toLocaleString() : "-", s.publicOrderId ?? "-"])}/> : null}
    {query.data ? <div className="mt-4"><AdminPagination page={page} totalPages={query.data.totalPages} onChange={setPage} /></div> : null}</div>;
}
