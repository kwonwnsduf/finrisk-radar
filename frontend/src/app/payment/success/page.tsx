import { AppShell } from "@/components/layout/app-shell";
import { PaymentResult } from "@/components/payment/payment-result";

export default async function PaymentSuccessPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const values = await searchParams;
  const one = (value: string | string[] | undefined) => Array.isArray(value) ? value[0] : value;
  return <AppShell><PaymentResult paymentKey={one(values.paymentKey)} orderId={one(values.orderId)} amount={one(values.amount)} /></AppShell>;
}
