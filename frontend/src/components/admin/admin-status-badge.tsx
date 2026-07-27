export function AdminStatusBadge({ value }: { value: string | null }) {
  if (!value) return <span className="text-slate-400">-</span>;
  const danger = value.includes("FAILED") || value === "RECOVERY_REQUIRED" || value === "OPEN";
  const success = value === "PAID" || value === "ACTIVE" || value === "RESOLVED" || value === "APPROVED";
  const tone = danger
    ? "bg-red-100 text-red-700"
    : success ? "bg-emerald-100 text-emerald-700" : "bg-amber-100 text-amber-700";
  return <span className={`inline-flex rounded-full px-2 py-1 text-xs font-semibold ${tone}`}>{value}</span>;
}
