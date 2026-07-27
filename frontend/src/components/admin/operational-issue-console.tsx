"use client";
import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { AdminPagination } from "./admin-pagination";
import { AdminQueryState } from "./admin-query-state";
import { AdminStatusBadge } from "./admin-status-badge";
import { AdminTable } from "./admin-table";
import { Button } from "@/components/ui/button";
import { adminKeys, getAdminIssues } from "@/lib/api/admin";
const kinds = [
  ["backtests","백테스트"],["reports","AI 리포트"],["MARKET_DATA","시세 수집"],["DOCUMENT","문서 수집"],
] as const;
export function OperationalIssueConsole() {
  const [kind, setKind] = useState<(typeof kinds)[number][0]>("backtests");
  const [page, setPage] = useState(0);
  const query = useQuery({ queryKey: adminKeys.issues(kind, page), queryFn: () => getAdminIssues(kind, page) });
  return <div><div className="mb-5 flex flex-wrap gap-2">{kinds.map(([value,label]) => <Button key={value} variant={kind === value ? "default" : "outline"} onClick={() => { setKind(value); setPage(0); }}>{label}</Button>)}</div>
    <AdminQueryState loading={query.isPending} error={query.isError} empty={query.data?.items.length === 0} retry={() => void query.refetch()} />
    {query.data ? <><AdminTable headers={["문제","Job","사용자","자산","상태","오류","경과"]} rows={query.data.items.map((i) => [i.issueType, <span key={i.jobId} className="font-mono text-xs">{i.jobId}</span>, i.email ?? i.userId ?? "-", i.assetName ?? i.assetId ?? "-", <AdminStatusBadge key="s" value={i.status} />, i.failureCode ? `${i.failureCode}: ${i.failureMessage ?? ""}` : i.failureMessage ?? "-", `${Math.floor(i.ageSeconds / 60).toLocaleString()}분`])}/><div className="mt-4"><AdminPagination page={page} totalPages={query.data.totalPages} onChange={setPage} /></div></> : null}</div>;
}
