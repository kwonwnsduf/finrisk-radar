"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Search, Star } from "lucide-react";
import Link from "next/link";
import { FormEvent, useState } from "react";

import { AssetTypeBadge } from "@/components/assets/asset-type-badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { apiErrorMessage } from "@/lib/api/error-message";
import { getAssets, searchAssets, type AssetType } from "@/lib/api/assets";
import { addWatchlist } from "@/lib/api/watchlists";

interface SearchState {
  keyword: string;
  assetType?: AssetType;
}

export function AssetCatalog() {
  const queryClient = useQueryClient();
  const [keyword, setKeyword] = useState("");
  const [assetType, setAssetType] = useState<AssetType | "">("");
  const [search, setSearch] = useState<SearchState>({ keyword: "" });
  const [notice, setNotice] = useState<string | null>(null);

  const assetsQuery = useQuery({
    queryKey: ["assets", search.keyword, search.assetType ?? "ALL"],
    queryFn: () => search.keyword
      ? searchAssets(search.keyword, search.assetType)
      : getAssets(),
  });

  const addMutation = useMutation({
    mutationFn: (assetId: number) => addWatchlist(assetId),
    onSuccess: (item) => {
      setNotice(`${item.name}을(를) 관심자산에 추가했습니다.`);
      void queryClient.invalidateQueries({ queryKey: ["watchlists"] });
    },
    onError: (error) => setNotice(apiErrorMessage(error, "관심자산을 추가하지 못했습니다.")),
  });

  function submitSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const normalizedKeyword = keyword.trim();
    if (!normalizedKeyword && assetType) {
      setNotice("자산 유형 검색에도 검색어가 필요합니다.");
      return;
    }
    setNotice(null);
    setSearch({ keyword: normalizedKeyword, assetType: assetType || undefined });
  }

  return (
    <div>
      <div className="mb-7">
        <p className="text-sm font-semibold text-blue-600">Asset Explorer</p>
        <h1 className="mt-1 text-3xl font-bold tracking-tight text-slate-950">자산 검색</h1>
        <p className="mt-2 text-sm text-slate-600">주식, 리츠, 채권 발행사를 검색하고 관심자산으로 관리하세요.</p>
      </div>

      <form onSubmit={submitSearch} className="mb-6 grid gap-3 rounded-2xl border border-slate-200 bg-white p-4 shadow-sm sm:grid-cols-[1fr_180px_auto]">
        <Input
          value={keyword}
          onChange={(event) => setKeyword(event.target.value)}
          placeholder="이름, 티커, 시장, 섹터 검색"
          aria-label="자산 검색어"
        />
        <select
          value={assetType}
          onChange={(event) => setAssetType(event.target.value as AssetType | "")}
          aria-label="자산 유형"
          className="h-9 rounded-md border border-slate-200 bg-white px-3 text-sm text-slate-800 outline-none focus:border-blue-500"
        >
          <option value="">전체 유형</option>
          <option value="STOCK">주식</option>
          <option value="REIT">리츠</option>
          <option value="BOND_ISSUER">채권 발행사</option>
        </select>
        <Button type="submit"><Search className="size-4" />검색</Button>
      </form>

      {notice ? <p role="status" className="mb-4 rounded-xl bg-slate-100 px-4 py-3 text-sm text-slate-700">{notice}</p> : null}
      {assetsQuery.isLoading ? <p className="py-12 text-center text-sm text-slate-500">자산을 불러오고 있습니다.</p> : null}
      {assetsQuery.isError ? <p role="alert" className="py-12 text-center text-sm text-red-600">자산 목록을 불러오지 못했습니다.</p> : null}
      {assetsQuery.data?.length === 0 ? <p className="py-12 text-center text-sm text-slate-500">조건에 맞는 자산이 없습니다.</p> : null}

      <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-3" aria-label="자산 목록">
        {assetsQuery.data?.map((asset) => (
          <Card key={asset.id} className="border-slate-200">
            <CardHeader className="gap-3">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <CardTitle className="text-lg">{asset.name}</CardTitle>
                  <p className="mt-1 font-mono text-sm text-slate-500">{asset.ticker}</p>
                </div>
                <AssetTypeBadge assetType={asset.assetType} />
              </div>
            </CardHeader>
            <CardContent>
              <dl className="mb-5 grid grid-cols-2 gap-3 text-sm">
                <div><dt className="text-slate-500">시장</dt><dd className="font-medium text-slate-900">{asset.market ?? "-"}</dd></div>
                <div><dt className="text-slate-500">섹터</dt><dd className="font-medium text-slate-900">{asset.sector ?? "-"}</dd></div>
              </dl>
              <div className="flex gap-2">
                <Button asChild variant="outline" className="flex-1"><Link href={`/assets/${asset.id}`}>상세 보기</Link></Button>
                <Button
                  type="button"
                  className="flex-1"
                  disabled={addMutation.isPending}
                  onClick={() => addMutation.mutate(asset.id)}
                >
                  <Star className="size-4" />관심 추가
                </Button>
              </div>
            </CardContent>
          </Card>
        ))}
      </section>
    </div>
  );
}
