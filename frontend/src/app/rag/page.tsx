import { Suspense } from "react";

import { AuthGuard } from "@/components/auth/auth-guard";
import { AssetPageShell } from "@/components/assets/asset-page-shell";
import { RagSearchWorkbench } from "@/components/rag/rag-search-workbench";

export default function RagPage() {
  return (
    <AuthGuard>
      <AssetPageShell>
        <Suspense
          fallback={
            <p className="py-16 text-center text-slate-500">
              검색 화면을 준비하고 있습니다.
            </p>
          }
        >
          <RagSearchWorkbench />
        </Suspense>
      </AssetPageShell>
    </AuthGuard>
  );
}
