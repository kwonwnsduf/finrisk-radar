"use client";

import { useParams } from "next/navigation";

import { AssetDetail } from "@/components/assets/asset-detail";
import { AssetPageShell } from "@/components/assets/asset-page-shell";

export default function AssetDetailPage() {
  const params = useParams<{ assetId: string }>();
  const assetId = Number(params.assetId);

  return <AssetPageShell>{Number.isFinite(assetId) && assetId > 0
    ? <AssetDetail assetId={assetId} />
    : <p role="alert">올바르지 않은 자산 ID입니다.</p>}</AssetPageShell>;
}
