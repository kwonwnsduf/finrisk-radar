"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { getPaymentOrder } from "@/lib/api/payments";

export function PaymentFailure({
  code,
  orderId,
}: {
  code?: string;
  orderId?: string;
}) {
  const order = useQuery({
    queryKey: ["payment-order", orderId],
    queryFn: () => getPaymentOrder(orderId!),
    enabled: Boolean(orderId),
    retry: 1,
  });

  return (
    <Card className="mx-auto max-w-xl">
      <CardHeader><CardTitle>결제가 완료되지 않았습니다</CardTitle></CardHeader>
      <CardContent className="space-y-4">
        <p className="text-sm text-slate-600">
          결제가 취소되었거나 결제수단에서 요청을 거절했습니다. 이 화면만으로
          서버 주문을 실패 처리하지 않습니다.
        </p>
        {order.data ? (
          <p className="rounded-lg bg-slate-50 p-3 text-sm">
            서버 주문 상태: <strong>{order.data.orderStatus}</strong>
          </p>
        ) : null}
        {order.isError ? (
          <p className="text-sm text-amber-700">
            주문 상태를 조회하지 못했습니다. 결제 내역에서 다시 확인해 주세요.
          </p>
        ) : null}
        {code ? <p className="text-xs text-slate-500">오류 코드: {code}</p> : null}
        <div className="flex gap-2">
          <Button asChild><Link href="/payments">결제 내역 확인</Link></Button>
          <Button asChild variant="outline"><Link href="/payment">다시 결제하기</Link></Button>
        </div>
      </CardContent>
    </Card>
  );
}
