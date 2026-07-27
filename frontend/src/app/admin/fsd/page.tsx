import { FsdConsole } from "@/components/admin/fsd-console";
export default function AdminFsdPage() {
  return <div><h1 className="mb-2 text-2xl font-bold">이상 결제 검토</h1><p className="mb-6 text-sm text-slate-500">FSD 탐지 근거와 결제 시도를 확인하고 상태를 처리합니다.</p><FsdConsole /></div>;
}
