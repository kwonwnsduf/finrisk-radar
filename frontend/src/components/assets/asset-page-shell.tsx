"use client";

import { Radar, Search, Star, LayoutDashboard, LogIn } from "lucide-react";
import Link from "next/link";

import { Button } from "@/components/ui/button";
import { useAuthStore } from "@/store/auth-store";

export function AssetPageShell({ children }: { children: React.ReactNode }) {
  const status = useAuthStore((state) => state.status);

  return (
    <div className="min-h-screen bg-slate-50">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex h-16 max-w-7xl items-center justify-between px-5">
          <Link href="/assets" className="flex items-center gap-3 font-bold text-slate-950">
            <span className="flex size-9 items-center justify-center rounded-xl bg-blue-600 text-white">
              <Radar className="size-5" aria-hidden="true" />
            </span>
            FinRisk Radar
          </Link>
          <nav className="flex items-center gap-1" aria-label="자산 메뉴">
            <Button asChild variant="ghost" size="sm"><Link href="/assets"><Search className="size-4" />자산</Link></Button>
            <Button asChild variant="ghost" size="sm"><Link href="/watchlist"><Star className="size-4" />관심자산</Link></Button>
            {status === "authenticated" ? (
              <Button asChild variant="ghost" size="sm"><Link href="/dashboard"><LayoutDashboard className="size-4" />대시보드</Link></Button>
            ) : (
              <Button asChild variant="ghost" size="sm"><Link href="/login"><LogIn className="size-4" />로그인</Link></Button>
            )}
          </nav>
        </div>
      </header>
      <main className="mx-auto max-w-7xl px-5 py-8">{children}</main>
    </div>
  );
}
