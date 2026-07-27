import type { ReactNode } from "react";
export function AdminTable({ headers, rows }: { headers: string[]; rows: ReactNode[][] }) {
  return <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white">
    <table className="min-w-full text-sm"><thead className="bg-slate-50 text-left text-slate-600"><tr>{headers.map((header) => <th key={header} className="whitespace-nowrap px-4 py-3 font-semibold">{header}</th>)}</tr></thead>
    <tbody className="divide-y divide-slate-100">{rows.map((row, index) => <tr key={index}>{row.map((cell, cellIndex) => <td key={cellIndex} className="whitespace-nowrap px-4 py-3 text-slate-700">{cell}</td>)}</tr>)}</tbody></table>
  </div>;
}
