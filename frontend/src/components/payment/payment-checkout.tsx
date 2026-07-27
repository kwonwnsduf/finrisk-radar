"use client";

import { loadTossPayments, type TossPaymentsWidgets } from "@tosspayments/tosspayments-sdk";
import { useMutation } from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { apiErrorMessage } from "@/lib/api/error-message";
import { createPaymentOrder, type PaymentOrder } from "@/lib/api/payments";

export function PaymentCheckout() {
  const clientKey = process.env.NEXT_PUBLIC_TOSS_CLIENT_KEY;
  const hasWidgetClientKey = Boolean(clientKey?.includes("_gck_"));
  const [order, setOrder] = useState<PaymentOrder | null>(null);
  const [widgets, setWidgets] = useState<TossPaymentsWidgets | null>(null);
  const [message, setMessage] = useState("");
  const initialized = useRef(false);
  const createOrder = useMutation({
    mutationFn: createPaymentOrder,
    onSuccess: setOrder,
    onError: (error) => setMessage(apiErrorMessage(error, "주문을 만들지 못했습니다.")),
  });

  useEffect(() => {
    if (!order || initialized.current) return;
    initialized.current = true;
    if (!clientKey || !hasWidgetClientKey) return;
    void loadTossPayments(clientKey)
      .then(async (toss) => {
        const next = toss.widgets({ customerKey: order.customerKey });
        await next.setAmount({ currency: "KRW", value: order.amount });
        await Promise.all([
          next.renderPaymentMethods({ selector: "#payment-methods", variantKey: "DEFAULT" }),
          next.renderAgreement({ selector: "#agreement", variantKey: "AGREEMENT" }),
        ]);
        setWidgets(next);
      })
      .catch((error: unknown) => {
        setMessage(error instanceof Error ? error.message : "결제위젯을 불러오지 못했습니다.");
      });
  }, [clientKey, hasWidgetClientKey, order]);

  async function requestPayment() {
    if (!order || !widgets) return;
    setMessage("");
    try {
      await widgets.requestPayment({
        orderId: order.orderId,
        orderName: order.orderName,
        customerName: order.customerName,
        successUrl: order.successUrl,
        failUrl: order.failUrl,
      });
    } catch (error) {
      setMessage(error instanceof Error ? error.message : "결제창을 열지 못했습니다.");
    }
  }

  return (
    <div className="mx-auto max-w-3xl space-y-5">
      <Card>
        <CardHeader><CardTitle>FinRisk Radar PREMIUM 결제</CardTitle></CardHeader>
        <CardContent>
          <div className="flex items-center justify-between rounded-xl bg-slate-50 p-4">
            <div><strong>30일 이용권</strong><p className="text-sm text-slate-500">자동 갱신 없음</p></div>
            <strong className="text-xl">₩5,900</strong>
          </div>
          {!order ? (
            <Button className="mt-5 w-full" disabled={createOrder.isPending} onClick={() => createOrder.mutate()}>
              {createOrder.isPending ? "서버 주문 생성 중…" : "결제 준비하기"}
            </Button>
          ) : null}
        </CardContent>
      </Card>
      {order ? <Card><CardContent className="pt-6"><div id="payment-methods" /><div id="agreement" />
        <Button className="mt-5 w-full" disabled={!widgets} onClick={() => void requestPayment()}>
          {widgets ? "₩5,900 결제하기" : "결제위젯 로딩 중…"}
        </Button>
      </CardContent></Card> : null}
      {message || (order && !hasWidgetClientKey) ? <p role="alert" className="rounded-xl bg-red-50 p-4 text-sm text-red-700">{message || "결제위젯 연동 클라이언트 키(gck)가 필요합니다. API 개별 연동 키(ck/sk)는 사용할 수 없습니다."}</p> : null}
    </div>
  );
}
