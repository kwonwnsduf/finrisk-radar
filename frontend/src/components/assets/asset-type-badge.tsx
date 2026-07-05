import type { AssetType } from "@/lib/api/assets";

const LABELS: Record<AssetType, string> = {
  STOCK: "주식",
  REIT: "리츠",
  BOND_ISSUER: "채권 발행사",
};

export function AssetTypeBadge({ assetType }: { assetType: AssetType }) {
  return (
    <span className="rounded-full bg-blue-50 px-2.5 py-1 text-xs font-semibold text-blue-700">
      {LABELS[assetType]}
    </span>
  );
}
