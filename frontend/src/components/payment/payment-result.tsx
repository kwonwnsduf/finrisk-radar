"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useEffect, useRef } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { apiErrorMessage } from "@/lib/api/error-message";
import { confirmPayment } from "@/lib/api/payments";

export function PaymentResult({
  paymentKey,
  orderId,
  amount,
}: {
  paymentKey?: string;
  orderId?: string;
  amount?: string;
}) {
  const queryClient = useQueryClient();
  const started = useRef(false);
  const confirmation = useMutation({
    mutationFn: confirmPayment,
    onSuccess: () => {
      sessionStorage.removeItem("payment-order-create");
      for (const key of [["me"], ["subscription-usage"], ["subscription"], ["payments"], ["usage"]]) {
        void queryClient.invalidateQueries({ queryKey: key });
      }
    },
  });

  useEffect(() => {
    if (started.current || !paymentKey || !orderId || !amount) return;
    started.current = true;
    const storageKey = `payment-confirm:${orderId}`;
    let idempotencyKey = sessionStorage.getItem(storageKey);
    if (!idempotencyKey) {
      idempotencyKey = crypto.randomUUID();
      sessionStorage.setItem(storageKey, idempotencyKey);
    }
    confirmation.mutate({ paymentKey, orderId, amount: Number(amount), idempotencyKey });
  }, [amount, confirmation, orderId, paymentKey]);

  if (!paymentKey || !orderId || !amount) {
    return <ResultCard title="잘못된 결제 결과" message="필수 결제 정보가 없습니다." />;
  }
  if (confirmation.isPending || confirmation.isIdle) {
    return <ResultCard title="결제 승인 처리 중" message="창을 닫거나 새로고침하지 않아도 안전하게 처리됩니다." />;
  }
  if (confirmation.isError) {
    return <ResultCard title="결제 상태 확인 필요" message={apiErrorMessage(confirmation.error, "결제 승인 상태를 확인하지 못했습니다.")} />;
  }
  const result = confirmation.data;
  return (
    <Card className="mx-auto max-w-xl">
      <CardHeader><CardTitle>{result.replayed ? "이미 승인된 결제입니다" : "결제가 완료되었습니다"}</CardTitle></CardHeader>
      <CardContent className="space-y-3">
        <p>결제 금액 <strong>₩{result.amount.toLocaleString()}</strong></p>
        <p className="text-sm text-slate-600">주문번호 {result.orderId}</p>
        <p className="text-sm text-slate-600">PREMIUM 종료일 {format(result.subscriptionEnd)}</p>
        <div className="flex gap-2 pt-3">
          <Button asChild><Link href="/payments">결제 내역</Link></Button>
          <Button asChild variant="outline"><Link href="/settings/subscription">구독 설정</Link></Button>
        </div>
      </CardContent>
    </Card>
  );
}

function ResultCard({ title, message }: { title: string; message: string }) {
  return <Card className="mx-auto max-w-xl"><CardHeader><CardTitle>{title}</CardTitle></CardHeader><CardContent><p className="text-sm text-slate-600">{message}</p></CardContent></Card>;
}

function format(value?: string) {
  return value ? new Date(value).toLocaleString("ko-KR") : "-";
}
