"use client";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { AdminPagination } from "./admin-pagination";
import { AdminQueryState } from "./admin-query-state";
import { AdminStatusBadge } from "./admin-status-badge";
import { AdminTable } from "./admin-table";
import { Button } from "@/components/ui/button";
import { getCandidate, getCandidates, reviewCandidate, type CreditEventCandidate } from "@/lib/api/documents";
export function CreditEventCandidateList() {
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<CreditEventCandidate | null>(null);
  const [note, setNote] = useState("");
  const client = useQueryClient();
  const list = useQuery({ queryKey: ["admin","candidates","PENDING_REVIEW",page], queryFn: () => getCandidates("PENDING_REVIEW", page) });
  const detail = useQuery({ queryKey: ["admin","candidates","detail",selected?.id], queryFn: () => getCandidate(selected!.id), enabled: !!selected });
  const review = useMutation({ mutationFn: (action: "approve"|"reject") => reviewCandidate(selected!.id, action, note), onSuccess: () => { setSelected(null); setNote(""); void client.invalidateQueries({ queryKey: ["admin","candidates"] }); void client.invalidateQueries({ queryKey: ["admin","dashboard"] }); } });
  return <div><AdminQueryState loading={list.isPending} error={list.isError} empty={list.data?.items.length === 0} retry={() => void list.refetch()} />
    {list.data ? <><AdminTable headers={["후보","자산","사건","심각도","문서","생성 시각",""]} rows={list.data.items.map((c) => [`#${c.id}`, `${c.assetName} (${c.ticker})`, `${c.eventType} · ${c.eventDate}`, <AdminStatusBadge key="s" value={c.severity} />, c.documentTitle ?? "-", new Date(c.createdAt).toLocaleString(), <Button key="b" size="sm" variant="outline" onClick={() => setSelected(c)}>검토</Button>])}/><div className="mt-4"><AdminPagination page={page} totalPages={list.data.totalPages} onChange={setPage} /></div></> : null}
    {selected ? <div role="dialog" aria-modal="true" className="fixed inset-0 z-50 grid place-items-center bg-slate-950/50 p-4"><div className="max-h-[90vh] w-full max-w-4xl overflow-auto rounded-xl bg-white p-6"><div className="flex justify-between"><div><h2 className="text-xl font-bold">{selected.assetName} · {selected.eventType}</h2><p className="text-sm text-slate-500">후보 #{selected.id}</p></div><Button variant="ghost" onClick={() => setSelected(null)}>닫기</Button></div><AdminQueryState loading={detail.isPending} error={detail.isError} retry={() => void detail.refetch()} />{detail.data ? <div className="mt-5 space-y-4">{detail.data.matches.map((m) => <div key={m.id} className="rounded-lg border p-4"><div className="flex items-start justify-between gap-4"><div><p className="font-semibold">{m.documentTitle ?? `문서 #${m.documentId}`}</p><p className="text-xs text-slate-500">{m.sourceType} · {m.sourceName}</p></div><AdminStatusBadge value={m.assertionType} /></div><p className="mt-3 rounded bg-slate-50 p-3 text-sm">{m.sentenceText}</p>{m.sourceUrl ? <a className="mt-2 inline-block text-xs text-blue-600" href={m.sourceUrl} target="_blank" rel="noreferrer">원문 보기</a> : null}</div>)}{detail.data.nearbyCandidates.length ? <div className="rounded-lg bg-amber-50 p-4"><h3 className="font-semibold">같은 자산·사건 유형의 인접 후보</h3><p className="mt-1 text-sm">{detail.data.nearbyCandidates.map((n) => `#${n.id} ${n.eventDate} ${n.status}`).join(" · ")}</p></div> : null}<textarea maxLength={1000} className="min-h-24 w-full rounded-md border p-3 text-sm" value={note} onChange={(e) => setNote(e.target.value)} placeholder="검토 메모" /><div className="flex gap-2"><Button disabled={review.isPending} onClick={() => review.mutate("approve")}>승인</Button><Button variant="outline" disabled={review.isPending} onClick={() => review.mutate("reject")}>거절</Button></div>{review.isError ? <p className="text-sm text-red-600">이미 검토됐거나 처리할 수 없는 후보입니다.</p> : null}</div> : null}</div></div> : null}</div>;
}
