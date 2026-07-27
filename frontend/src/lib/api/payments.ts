import { apiClient } from "@/lib/api/client";
import type { ApiResponse } from "@/lib/auth/types";

export interface PaymentOrder {
  orderId: string;
  orderName: string;
  amount: number;
  currency: "KRW";
  customerKey: string;
  customerName: string;
  successUrl: string;
  failUrl: string;
}

export interface PaymentResult {
  orderId: string;
  orderStatus: string;
  amount: number;
  currency: string;
  paymentKey: string;
  paymentMethod?: string;
  approvedAt?: string;
  entitlementStart?: string;
  entitlementEnd?: string;
  subscriptionEnd?: string;
  currentPlan: string;
  replayed: boolean;
  recoveryRequired: boolean;
}

export interface PaymentHistoryItem {
  orderId: string;
  orderName: string;
  amount: number;
  currency: string;
  orderStatus: string;
  paymentMethod?: string;
  approvedAt?: string;
  canceledAt?: string;
  receiptUrl?: string;
  entitlementStart?: string;
  entitlementEnd?: string;
  entitlementRemainingSeconds: number;
  cancellable: boolean;
}

export interface PaymentPage<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export async function createPaymentOrder() {
  const storageKey = "payment-order-create";
  let idempotencyKey = sessionStorage.getItem(storageKey);
  if (!idempotencyKey) {
    idempotencyKey = crypto.randomUUID();
    sessionStorage.setItem(storageKey, idempotencyKey);
  }
  const response = await apiClient.post<ApiResponse<PaymentOrder>>(
    "/api/payments/orders",
    { productCode: "PREMIUM_MONTHLY" },
    {
      headers: {
        "X-Request-Id": crypto.randomUUID(),
        "Idempotency-Key": idempotencyKey,
      },
    },
  );
  return response.data.data;
}

export async function confirmPayment(input: {
  paymentKey: string;
  orderId: string;
  amount: number;
  idempotencyKey: string;
}) {
  const response = await apiClient.post<ApiResponse<PaymentResult>>(
    "/api/payments/confirm",
    input,
    { headers: { "X-Request-Id": crypto.randomUUID() } },
  );
  return response.data.data;
}

export async function getPayments(page = 0) {
  const response = await apiClient.get<
    ApiResponse<PaymentPage<PaymentHistoryItem>>
  >("/api/payments/me", { params: { page, size: 20 } });
  return response.data.data;
}

export async function getPaymentOrder(orderId: string) {
  const response = await apiClient.get<ApiResponse<PaymentHistoryItem>>(
    `/api/payments/orders/${orderId}`,
  );
  return response.data.data;
}

export async function cancelPayment(
  orderId: string,
  reason: string,
  cancelRequestId: string,
) {
  const response = await apiClient.post<ApiResponse<{
    orderId: string;
    orderStatus: string;
    amount: number;
    removedUnusedSeconds: number;
    subscriptionEnd?: string;
    currentPlan: string;
    replayed: boolean;
  }>>(`/api/payments/${orderId}/cancel`, { reason, cancelRequestId }, {
    headers: { "X-Request-Id": crypto.randomUUID() },
  });
  return response.data.data;
}
