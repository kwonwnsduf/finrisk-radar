import { AssetPageShell } from "@/components/assets/asset-page-shell";
import { AuthGuard } from "@/components/auth/auth-guard";
import { WatchlistContent } from "@/components/watchlist/watchlist-content";

export default function WatchlistPage() {
  return (
    <AuthGuard>
      <AssetPageShell><WatchlistContent /></AssetPageShell>
    </AuthGuard>
  );
}
