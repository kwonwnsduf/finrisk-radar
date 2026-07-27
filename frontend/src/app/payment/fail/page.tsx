import { AppShell } from "@/components/layout/app-shell";
import { PaymentFailure } from "@/components/payment/payment-failure";

export default async function PaymentFailPage({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const values = await searchParams;
  const first = (value: string | string[] | undefined) =>
    Array.isArray(value) ? value[0] : value;
  return (
    <AppShell>
      <PaymentFailure
        code={first(values.code)}
        orderId={first(values.orderId)}
      />
    </AppShell>
  );
}
