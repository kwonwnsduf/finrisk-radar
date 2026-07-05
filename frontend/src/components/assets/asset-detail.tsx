"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, Star } from "lucide-react";
import Link from "next/link";
import { useState } from "react";

import { AssetTypeBadge } from "@/components/assets/asset-type-badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { apiErrorMessage } from "@/lib/api/error-message";
import { getAsset } from "@/lib/api/assets";
import { addWatchlist } from "@/lib/api/watchlists";

export function AssetDetail({ assetId }: { assetId: number }) {
  const queryClient = useQueryClient();
  const [notice, setNotice] = useState<string | null>(null);
  const assetQuery = useQuery({ queryKey: ["asset", assetId], queryFn: () => getAsset(assetId) });
  const addMutation = useMutation({
    mutationFn: () => addWatchlist(assetId),
    onSuccess: (item) => {
      setNotice(`${item.name}을(를) 관심자산에 추가했습니다.`);
      void queryClient.invalidateQueries({ queryKey: ["watchlists"] });
    },
    onError: (error) => setNotice(apiErrorMessage(error, "관심자산을 추가하지 못했습니다.")),
  });

  if (assetQuery.isLoading) return <p className="py-16 text-center text-slate-500">자산 정보를 불러오고 있습니다.</p>;
  if (assetQuery.isError || !assetQuery.data) return <p role="alert" className="py-16 text-center text-red-600">자산 정보를 찾을 수 없습니다.</p>;

  const asset = assetQuery.data;
  const details = [
    ["티커", asset.ticker], ["시장", asset.market], ["섹터", asset.sector],
    ["국가", asset.country], ["통화", asset.currency],
  ];

  return (
    <div className="mx-auto max-w-3xl">
      <Button asChild variant="ghost" className="mb-4"><Link href="/assets"><ArrowLeft className="size-4" />자산 목록</Link></Button>
      <Card className="border-slate-200">
        <CardHeader>
          <div className="flex items-start justify-between gap-4">
            <div><p className="mb-2 text-sm font-semibold text-blue-600">Asset Detail</p><CardTitle className="text-3xl">{asset.name}</CardTitle></div>
            <AssetTypeBadge assetType={asset.assetType} />
          </div>
        </CardHeader>
        <CardContent>
          <dl className="grid gap-5 rounded-2xl bg-slate-50 p-5 sm:grid-cols-2">
            {details.map(([label, value]) => <div key={label}><dt className="text-sm text-slate-500">{label}</dt><dd className="mt-1 font-semibold text-slate-900">{value ?? "-"}</dd></div>)}
          </dl>
          {notice ? <p role="status" className="mt-4 rounded-xl bg-slate-100 px-4 py-3 text-sm text-slate-700">{notice}</p> : null}
          <Button className="mt-5 w-full" onClick={() => addMutation.mutate()} disabled={addMutation.isPending}>
            <Star className="size-4" />관심자산에 추가
          </Button>
        </CardContent>
      </Card>
    </div>
  );
}
