import { OperationalIssueConsole } from "@/components/admin/operational-issue-console";
export default function AdminIssuesPage() { return <div><h1 className="text-2xl font-bold">시스템 문제</h1><p className="mb-6 mt-2 text-sm text-slate-500">실패 작업과 기존 recovery 기준을 넘긴 AI 리포트만 표시합니다.</p><OperationalIssueConsole /></div>; }
