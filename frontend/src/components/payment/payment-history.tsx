"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { apiErrorMessage } from "@/lib/api/error-message";
import { cancelPayment, getPayments } from "@/lib/api/payments";

export function PaymentHistory() {
  const [page, setPage] = useState(0);
  const [canceling, setCanceling] = useState<string | null>(null);
  const [error, setError] = useState("");
  const queryClient = useQueryClient();
  const query = useQuery({ queryKey: ["payments", page], queryFn: () => getPayments(page) });
  const cancel = useMutation({
    mutationFn: ({ orderId, requestId }: { orderId: string; requestId: string }) =>
      cancelPayment(orderId, "사용자 요청", requestId),
    onSuccess: () => {
      setCanceling(null);
      void queryClient.invalidateQueries({ queryKey: ["payments"] });
      void queryClient.invalidateQueries({ queryKey: ["subscription"] });
      void queryClient.invalidateQueries({ queryKey: ["subscription-usage"] });
      void queryClient.invalidateQueries({ queryKey: ["me"] });
    },
    onError: (value) => setError(apiErrorMessage(value, "결제를 취소하지 못했습니다.")),
  });

  function cancellationRequestId(orderId: string) {
    const key = `payment-cancel:${orderId}`;
    const existing = sessionStorage.getItem(key);
    if (existing) return existing;
    const created = crypto.randomUUID();
    sessionStorage.setItem(key, created);
    return created;
  }

  if (query.isPending) return <p>결제 내역을 불러오는 중입니다.</p>;
  if (query.isError || !query.data) return <p role="alert">결제 내역을 불러오지 못했습니다.</p>;

  return (
    <div className="space-y-4">
      {error ? <p role="alert" className="rounded-xl bg-red-50 p-3 text-sm text-red-700">{error}</p> : null}
      {query.data.items.length === 0 ? <Card><CardContent className="p-8 text-center text-slate-500">아직 결제 내역이 없습니다.</CardContent></Card> : null}
      {query.data.items.map((item) => (
        <Card key={item.orderId}><CardContent className="flex flex-col gap-4 p-5 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <div className="flex items-center gap-2"><strong>{item.orderName}</strong><Status value={item.orderStatus} /></div>
            <p className="mt-1 text-sm text-slate-500">{item.orderId} · ₩{item.amount.toLocaleString()} · {item.paymentMethod ?? "-"}</p>
            <p className="text-xs text-slate-400">승인 {format(item.approvedAt)} {item.canceledAt ? `· 취소 ${format(item.canceledAt)}` : ""}</p>
            {item.entitlementStart ? <p className="text-xs text-slate-500">기여 기간 {format(item.entitlementStart)} → {format(item.entitlementEnd)} · 미사용 {duration(item.entitlementRemainingSeconds)}</p> : null}
          </div>
          <div className="flex gap-2">
            {item.receiptUrl ? <Button asChild variant="outline"><a href={item.receiptUrl} target="_blank" rel="noreferrer">영수증</a></Button> : null}
            {item.cancellable ? <Button variant="destructive" onClick={() => setCanceling(item.orderId)}>취소</Button> : null}
          </div>
          {canceling === item.orderId ? (
            <div role="dialog" aria-modal="true" className="fixed inset-0 z-50 grid place-items-center bg-slate-950/40 p-4">
              <Card className="max-w-md"><CardContent className="space-y-4 p-6">
                <strong>결제를 전액 취소할까요?</strong>
                <p className="text-sm text-slate-600">이미 사용한 구독 기간은 이력으로 유지되고, 이 결제가 기여한 미사용 기간만 제거됩니다.</p>
                <div className="flex justify-end gap-2"><Button variant="outline" onClick={() => setCanceling(null)}>닫기</Button>
                  <Button variant="destructive" disabled={cancel.isPending} onClick={() => cancel.mutate({ orderId: item.orderId, requestId: cancellationRequestId(item.orderId) })}>전액 취소</Button>
                </div>
              </CardContent></Card>
            </div>
          ) : null}
        </CardContent></Card>
      ))}
      <div className="flex justify-end gap-2"><Button variant="outline" disabled={page === 0} onClick={() => setPage((v) => v - 1)}>이전</Button><Button variant="outline" disabled={page + 1 >= query.data.totalPages} onClick={() => setPage((v) => v + 1)}>다음</Button></div>
    </div>
  );
}

function Status({ value }: { value: string }) {
  const tone = value === "PAID" ? "bg-emerald-100 text-emerald-700" : value === "CANCELED" ? "bg-slate-200 text-slate-700" : "bg-amber-100 text-amber-700";
  return <span className={`rounded-full px-2 py-1 text-xs font-semibold ${tone}`}>{value}</span>;
}
function format(value?: string) { return value ? new Date(value).toLocaleString("ko-KR") : "-"; }
function duration(seconds: number) {
  const days = Math.floor(seconds / 86_400);
  const hours = Math.floor((seconds % 86_400) / 3_600);
  return `${days}일 ${hours}시간`;
}
