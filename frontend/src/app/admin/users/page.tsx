import { UserSubscriptionConsole } from "@/components/admin/user-subscription-console";
export default function AdminUsersPage() { return <div><h1 className="text-2xl font-bold">사용자·구독</h1><p className="mb-6 mt-2 text-sm text-slate-500">가입자와 현재 구독 상태를 확인합니다.</p><UserSubscriptionConsole /></div>; }
