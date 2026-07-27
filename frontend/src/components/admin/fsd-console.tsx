"use client";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { AdminPagination } from "./admin-pagination";
import { AdminQueryState } from "./admin-query-state";
import { AdminStatusBadge } from "./admin-status-badge";
import { AdminTable } from "./admin-table";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { getFsdEvent, getFsdEvents, reviewFsdEvent, type FsdEvent } from "@/lib/api/fsd";
export function FsdConsole() {
  const [status, setStatus] = useState("OPEN");
  const [severity, setSeverity] = useState("");
  const [search, setSearch] = useState("");
  const [page, setPage] = useState(0);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [note, setNote] = useState("");
  const client = useQueryClient();
  const list = useQuery({ queryKey: ["admin","fsd",status,severity,search,page], queryFn: () => getFsdEvents({ status, severity, search, page, size: 20 }) });
  const detail = useQuery({ queryKey: ["admin","fsd","detail",selectedId], queryFn: () => getFsdEvent(selectedId!), enabled: selectedId != null });
  const review = useMutation({ mutationFn: (next: FsdEvent["status"]) => reviewFsdEvent(selectedId!, next, note), onSuccess: (value) => { setNote(value.reviewNote ?? ""); void client.invalidateQueries({ queryKey: ["admin","fsd"] }); void client.invalidateQueries({ queryKey: ["admin","dashboard"] }); } });
  return <div><div className="mb-4 grid gap-3 rounded-xl bg-white p-4 shadow-sm sm:grid-cols-3"><select className="h-9 rounded-md border px-3 text-sm" value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }}><option value="">전체 상태</option>{["OPEN","REVIEWING","RESOLVED","FALSE_POSITIVE"].map((v) => <option key={v}>{v}</option>)}</select><select className="h-9 rounded-md border px-3 text-sm" value={severity} onChange={(e) => { setSeverity(e.target.value); setPage(0); }}><option value="">전체 심각도</option>{["LOW","MEDIUM","HIGH","CRITICAL"].map((v) => <option key={v}>{v}</option>)}</select><Input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="사용자/주문 ID" /></div>
    <AdminQueryState loading={list.isPending} error={list.isError} empty={list.data?.items.length === 0} retry={() => void list.refetch()} />
    {list.data ? <><AdminTable headers={["규칙","사용자","주문","금액","심각도","상태","탐지 시각",""]} rows={list.data.items.map((e) => [e.ruleCode, e.userEmail ?? e.userId, e.orderId ?? "-", e.amount == null ? "-" : `${e.amount.toLocaleString()} ${e.currency}`, <AdminStatusBadge key="sev" value={e.severity} />, <AdminStatusBadge key="s" value={e.status} />, new Date(e.detectedAt).toLocaleString(), <Button key="b" size="sm" variant="outline" onClick={() => { setSelectedId(e.id); setNote(e.reviewNote ?? ""); }}>검토</Button>])}/><div className="mt-4"><AdminPagination page={page} totalPages={list.data.totalPages} onChange={setPage} /></div></> : null}
    {selectedId != null ? <div role="dialog" aria-modal="true" className="fixed inset-0 z-50 grid place-items-center bg-slate-950/50 p-4"><div className="max-h-[90vh] w-full max-w-3xl overflow-auto rounded-xl bg-white p-6"><div className="flex justify-between"><h2 className="text-xl font-bold">FSD #{selectedId}</h2><Button variant="ghost" onClick={() => setSelectedId(null)}>닫기</Button></div><AdminQueryState loading={detail.isPending} error={detail.isError} retry={() => void detail.refetch()} />{detail.data ? <div className="mt-4 space-y-4"><div className="rounded-lg bg-slate-50 p-4 text-sm"><p><b>{detail.data.userName}</b> · {detail.data.userEmail}</p><p>{detail.data.orderId} · {detail.data.amount?.toLocaleString()} {detail.data.currency}</p><p className="mt-2">{detail.data.reason}</p></div><pre className="overflow-auto rounded-lg bg-slate-950 p-4 text-xs text-white">{JSON.stringify(detail.data.evidence, null, 2)}</pre><AdminTable headers={["유형","결과","오류","시각"]} rows={detail.data.attempts.map((a) => [a.attemptType, a.result, a.errorCode ? `${a.errorCode}: ${a.errorMessage ?? ""}` : "-", new Date(a.createdAt).toLocaleString()])}/><textarea className="min-h-24 w-full rounded-md border p-3 text-sm" maxLength={1000} value={note} onChange={(e) => setNote(e.target.value)} placeholder="검토 메모" /><div className="flex gap-2">{detail.data.status === "OPEN" ? <Button disabled={review.isPending} onClick={() => review.mutate("REVIEWING")}>검토 시작</Button> : null}{detail.data.status === "OPEN" || detail.data.status === "REVIEWING" ? <><Button disabled={review.isPending} onClick={() => review.mutate("RESOLVED")}>해결</Button><Button variant="outline" disabled={review.isPending} onClick={() => review.mutate("FALSE_POSITIVE")}>오탐</Button></> : null}</div>{review.isError ? <p className="text-sm text-red-600">이미 처리됐거나 상태 전이가 허용되지 않습니다.</p> : null}</div> : null}</div></div> : null}</div>;
}
