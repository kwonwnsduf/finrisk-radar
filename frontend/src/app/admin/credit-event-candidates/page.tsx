import { CreditEventCandidateList } from "@/components/admin/credit-event-candidate-list";
export default function CreditEventCandidatesPage() { return <div><h1 className="text-2xl font-bold">자산 위험 후보 검토</h1><p className="mb-6 mt-2 text-sm text-slate-500">문서 기반 신용 사건 후보의 근거를 확인하고 승인하거나 거절합니다.</p><CreditEventCandidateList /></div>; }
