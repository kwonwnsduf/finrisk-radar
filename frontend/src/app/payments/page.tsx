import { AppShell } from "@/components/layout/app-shell";
import { PaymentHistory } from "@/components/payment/payment-history";

export default function PaymentsPage() {
  return <AppShell><div><h1 className="mb-6 text-2xl font-bold">결제 내역</h1><PaymentHistory /></div></AppShell>;
}
