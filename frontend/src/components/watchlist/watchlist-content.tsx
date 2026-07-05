"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ExternalLink, Trash2 } from "lucide-react";
import Link from "next/link";
import { useState } from "react";

import { AssetTypeBadge } from "@/components/assets/asset-type-badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { apiErrorMessage } from "@/lib/api/error-message";
import { deleteWatchlist, getWatchlists } from "@/lib/api/watchlists";

export function WatchlistContent() {
  const queryClient = useQueryClient();
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const watchlistsQuery = useQuery({ queryKey: ["watchlists"], queryFn: getWatchlists });
  const deleteMutation = useMutation({
    mutationFn: (watchlistId: number) => deleteWatchlist(watchlistId),
    onSuccess: () => {
      setErrorMessage(null);
      void queryClient.invalidateQueries({ queryKey: ["watchlists"] });
    },
    onError: (error) => setErrorMessage(apiErrorMessage(error, "관심자산을 삭제하지 못했습니다.")),
  });

  return (
    <div>
      <div className="mb-7">
        <p className="text-sm font-semibold text-blue-600">My Watchlist</p>
        <h1 className="mt-1 text-3xl font-bold tracking-tight text-slate-950">내 관심자산</h1>
        <p className="mt-2 text-sm text-slate-600">계속 살펴볼 자산을 한곳에서 관리하세요.</p>
      </div>

      {errorMessage ? <p role="alert" className="mb-4 rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700">{errorMessage}</p> : null}
      {watchlistsQuery.isLoading ? <p className="py-12 text-center text-sm text-slate-500">관심자산을 불러오고 있습니다.</p> : null}
      {watchlistsQuery.isError ? <p role="alert" className="py-12 text-center text-sm text-red-600">관심자산을 불러오지 못했습니다.</p> : null}
      {watchlistsQuery.data?.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-slate-300 bg-white py-16 text-center">
          <p className="text-sm text-slate-600">아직 등록한 관심자산이 없습니다.</p>
          <Button asChild className="mt-4"><Link href="/assets">자산 둘러보기</Link></Button>
        </div>
      ) : null}

      <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-3" aria-label="내 관심자산 목록">
        {watchlistsQuery.data?.map((item) => (
          <Card key={item.watchlistId} className="border-slate-200">
            <CardHeader>
              <div className="flex items-start justify-between gap-3">
                <div><CardTitle className="text-lg">{item.name}</CardTitle><p className="mt-1 font-mono text-sm text-slate-500">{item.ticker}</p></div>
                <AssetTypeBadge assetType={item.assetType} />
              </div>
            </CardHeader>
            <CardContent>
              <p className="mb-5 text-sm text-slate-600">{item.market ?? "시장 미지정"} · {item.sector ?? "섹터 미지정"}</p>
              <div className="flex gap-2">
                <Button asChild variant="outline" className="flex-1"><Link href={`/assets/${item.assetId}`}><ExternalLink className="size-4" />상세</Link></Button>
                <Button
                  type="button"
                  variant="outline"
                  className="flex-1 text-red-600 hover:text-red-700"
                  disabled={deleteMutation.isPending}
                  onClick={() => deleteMutation.mutate(item.watchlistId)}
                >
                  <Trash2 className="size-4" />삭제
                </Button>
              </div>
            </CardContent>
          </Card>
        ))}
      </section>
    </div>
  );
}
