import type { ReportStatus, ReportStep } from "@/lib/api/reports";
const steps: [ReportStep, string][] = [
  ["ASSET_RESOLUTION", "자산 확인"],
  ["RISK_DATA", "위험 데이터 조회"],
  ["DOCUMENT_SEARCH", "관련 문서 검색"],
  ["AI_ANALYSIS", "AI 분석"],
  ["REPORT_SAVE", "리포트 저장"],
  ["COMPLETED", "완료"],
];
export function ReportProgress({
  status,
  current,
}: {
  status: ReportStatus;
  current: ReportStep | null;
}) {
  const index = current ? steps.findIndex(([s]) => s === current) : -1;
  return (
    <ol className="grid gap-2 sm:grid-cols-3">
      {steps.map(([step, label], i) => (
        <li key={step} className={stepClassName(status, i, index)}>
          {label}
        </li>
      ))}
    </ol>
  );
}

function stepClassName(
  status: ReportStatus,
  stepIndex: number,
  currentIndex: number,
) {
  const baseClassName = "rounded-lg border px-3 py-2 text-sm";

  if (status === "FAILED" && stepIndex === currentIndex) {
    return `${baseClassName} border-red-300 bg-red-50 text-red-700`;
  }

  if (stepIndex <= currentIndex) {
    return `${baseClassName} border-blue-200 bg-blue-50 font-semibold text-blue-700`;
  }

  return `${baseClassName} border-slate-200 text-slate-400`;
}
