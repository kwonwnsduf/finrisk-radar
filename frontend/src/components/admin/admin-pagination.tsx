"use client";
import { Button } from "@/components/ui/button";
export function AdminPagination({ page, totalPages, onChange }: { page: number; totalPages: number; onChange: (page: number) => void }) {
  return <div className="flex items-center justify-end gap-2">
    <span className="text-sm text-slate-500">{totalPages ? page + 1 : 0} / {totalPages}</span>
    <Button variant="outline" disabled={page === 0} onClick={() => onChange(page - 1)}>이전</Button>
    <Button variant="outline" disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)}>다음</Button>
  </div>;
}
