"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { getFsdEvents, reviewFsdEvent, type FsdEvent } from "@/lib/api/fsd";

export function FsdConsole() {
  const [status, setStatus] = useState("");
  const [severity, setSeverity] = useState("");
  const [search, setSearch] = useState("");
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<FsdEvent | null>(null);
  const [note, setNote] = useState("");
  const queryClient = useQueryClient();
  const query = useQuery({ queryKey: ["fsd-events", status, severity, search, page], queryFn: () => getFsdEvents({ status, severity, search, page, size: 20 }) });
  const review = useMutation({
    mutationFn: (next: FsdEvent["status"]) => reviewFsdEvent(selected!.id, next, note),
    onSuccess: (value) => { setSelected(value); void queryClient.invalidateQueries({ queryKey: ["fsd-events"] }); },
  });
  return <div className="space-y-4">
    <div className="grid gap-3 rounded-xl bg-white p-4 shadow-sm sm:grid-cols-3">
      <select className="h-10 rounded-md border px-3" value={status} onChange={(e) => setStatus(e.target.value)}><option value="">모든 상태</option><option>OPEN</option><option>REVIEWING</option><option>RESOLVED</option><option>FALSE_POSITIVE</option></select>
      <select className="h-10 rounded-md border px-3" value={severity} onChange={(e) => setSeverity(e.target.value)}><option value="">모든 심각도</option><option>LOW</option><option>MEDIUM</option><option>HIGH</option><option>CRITICAL</option></select>
      <Input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="사용자 또는 주문 DB ID" />
    </div>
    {query.isPending ? <p>FSD 이벤트를 불러오는 중입니다.</p> : null}
    {query.data?.items.map((item) => <button key={item.id} className="block w-full rounded-xl bg-white p-4 text-left shadow-sm" onClick={() => { setSelected(item); setNote(item.reviewNote ?? ""); }}>
      <div className="flex flex-wrap gap-2"><Badge value={item.severity} /><Badge value={item.decision} /><Badge value={item.status} /><strong>{item.ruleCode}</strong></div>
      <p className="mt-2 text-sm text-slate-600">{item.reason}</p>
    </button>)}
    {query.data ? <div className="flex justify-end gap-2">
      <Button variant="outline" disabled={page === 0} onClick={() => setPage((value) => value - 1)}>이전</Button>
      <Button variant="outline" disabled={page + 1 >= query.data.totalPages} onClick={() => setPage((value) => value + 1)}>다음</Button>
    </div> : null}
    {selected ? <div role="dialog" aria-modal="true" className="fixed inset-0 z-50 grid place-items-center bg-slate-950/40 p-4"><div className="max-h-[85vh] w-full max-w-2xl overflow-auto rounded-xl bg-white p-6">
      <div className="flex justify-between"><h2 className="text-xl font-bold">{selected.ruleCode}</h2><Button variant="ghost" onClick={() => setSelected(null)}>닫기</Button></div>
      <p className="mt-3 text-sm">{selected.reason}</p><pre className="mt-4 overflow-auto rounded-lg bg-slate-950 p-4 text-xs text-slate-100">{JSON.stringify(selected.evidence, null, 2)}</pre>
      <textarea className="mt-4 min-h-24 w-full rounded-md border p-3" value={note} onChange={(e) => setNote(e.target.value)} placeholder="검토 메모" />
      <div className="mt-3 flex flex-wrap gap-2"><Button onClick={() => review.mutate("REVIEWING")}>검토 중</Button><Button onClick={() => review.mutate("RESOLVED")}>해결</Button><Button variant="outline" onClick={() => review.mutate("FALSE_POSITIVE")}>오탐</Button></div>
    </div></div> : null}
  </div>;
}
function Badge({ value }: { value: string }) { return <span className="rounded-full bg-slate-100 px-2 py-1 text-xs font-semibold text-slate-700">{value}</span>; }
