import { PaymentConsole } from "@/components/admin/payment-console";
export default function AdminPaymentsPage() { return <div><h1 className="text-2xl font-bold">결제 운영</h1><p className="mb-6 mt-2 text-sm text-slate-500">결제 실패, FSD, 복구 필요 주문을 조사합니다.</p><PaymentConsole /></div>; }
