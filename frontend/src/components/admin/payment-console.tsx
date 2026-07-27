"use client";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { AdminPagination } from "./admin-pagination";
import { AdminQueryState } from "./admin-query-state";
import { AdminStatusBadge } from "./admin-status-badge";
import { AdminTable } from "./admin-table";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { adminKeys, getAdminPayment, getAdminPayments, reconcileAdminPayment, type AdminPayment } from "@/lib/api/admin";
export function PaymentConsole() {
  const [page, setPage] = useState(0);
  const [orderId, setOrderId] = useState("");
  const [status, setStatus] = useState("");
  const [selected, setSelected] = useState<AdminPayment | null>(null);
  const params = { orderId, status, page, size: 20 };
  const query = useQuery({ queryKey: adminKeys.payments(params), queryFn: () => getAdminPayments(params) });
  const detail = useQuery({ queryKey: selected ? adminKeys.payment(selected.orderId) : ["admin","payment","none"], queryFn: () => getAdminPayment(selected!.orderId), enabled: !!selected });
  const client = useQueryClient();
  const reconcile = useMutation({ mutationFn: reconcileAdminPayment, onSuccess: () => { void client.invalidateQueries({ queryKey: ["admin","payments"] }); void client.invalidateQueries({ queryKey: adminKeys.dashboard }); } });
  return <div><div className="mb-4 grid gap-3 rounded-xl bg-white p-4 shadow-sm sm:grid-cols-[1fr_220px]"><Input value={orderId} onChange={(e) => { setOrderId(e.target.value); setPage(0); }} placeholder="주문 ID" /><select className="h-9 rounded-md border px-3 text-sm" value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }}><option value="">전체 상태</option>{["READY","CONFIRMING","PAID","CANCELING","CANCELED","FAILED","RECOVERY_REQUIRED"].map((s) => <option key={s}>{s}</option>)}</select></div>
    <AdminQueryState loading={query.isPending} error={query.isError} empty={query.data?.items.length === 0} retry={() => void query.refetch()} />
    {query.data ? <><AdminTable headers={["주문","사용자","금액","상태","FSD","승인/취소",""]} rows={query.data.items.map((p) => [p.orderId, <div key={p.orderId}><p>{p.name}</p><p className="text-xs text-slate-500">{p.email}</p></div>, `${p.amount.toLocaleString()} ${p.currency}`, <AdminStatusBadge key="status" value={p.status} />, <AdminStatusBadge key="fsd" value={p.fsdStatus} />, p.paidAt ? new Date(p.paidAt).toLocaleString() : p.canceledAt ? new Date(p.canceledAt).toLocaleString() : "-", <Button key="detail" size="sm" variant="outline" onClick={() => setSelected(p)}>조사</Button>])}/><div className="mt-4"><AdminPagination page={page} totalPages={query.data.totalPages} onChange={setPage} /></div></> : null}
    {selected ? <div role="dialog" aria-modal="true" className="fixed inset-0 z-50 grid place-items-center bg-slate-950/50 p-4"><div className="max-h-[90vh] w-full max-w-3xl overflow-auto rounded-xl bg-white p-6"><div className="flex items-start justify-between"><div><h2 className="text-xl font-bold">{selected.orderId}</h2><p className="text-sm text-slate-500">{selected.email}</p></div><Button variant="ghost" onClick={() => setSelected(null)}>닫기</Button></div>
      <AdminQueryState loading={detail.isPending} error={detail.isError} retry={() => void detail.refetch()} />
      {detail.data ? <div className="mt-5 space-y-5"><div className="grid gap-3 rounded-lg bg-slate-50 p-4 sm:grid-cols-3"><div><p className="text-xs text-slate-500">상태</p><AdminStatusBadge value={detail.data.payment.status} /></div><div><p className="text-xs text-slate-500">금액</p><p>{detail.data.payment.amount.toLocaleString()} {detail.data.payment.currency}</p></div><div><p className="text-xs text-slate-500">실패 코드</p><p>{detail.data.payment.latestFailureCode ?? "-"}</p></div></div>
        <div><h3 className="mb-2 font-bold">결제 시도</h3><AdminTable headers={["유형","결과","오류","시각"]} rows={detail.data.attempts.map((a) => [a.attemptType, <AdminStatusBadge key="r" value={a.result} />, a.errorCode ? `${a.errorCode}: ${a.errorMessage ?? ""}` : "-", new Date(a.createdAt).toLocaleString()])} /></div>
        {detail.data.cancellation ? <div className="rounded-lg border p-4"><h3 className="font-bold">취소</h3><p className="mt-2 text-sm">{detail.data.cancellation.cancelReason} · {detail.data.cancellation.amount.toLocaleString()} · {detail.data.cancellation.status}</p></div> : null}
        {detail.data.payment.recoveryRequired ? <Button disabled={reconcile.isPending} onClick={() => reconcile.mutate(detail.data!.payment.orderId)}>결제 상태 대사</Button> : null}
        {reconcile.isError ? <p className="text-sm text-red-600">대사 요청에 실패했습니다.</p> : null}
      </div> : null}</div></div> : null}</div>;
}
