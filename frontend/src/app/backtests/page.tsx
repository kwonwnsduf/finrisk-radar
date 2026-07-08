"use client";

import { AuthGuard } from "@/components/auth/auth-guard";
import { Header } from "@/components/layout/header";
import { Sidebar } from "@/components/layout/sidebar";
import { BacktestWorkbench } from "@/components/backtests/backtest-workbench";

export default function BacktestsPage() {
  return (
    <AuthGuard>
      <div className="flex min-h-screen bg-slate-50">
        <Sidebar />
        <div className="flex min-w-0 flex-1 flex-col">
          <Header />
          <main className="flex-1 p-5 md:p-8">
            <BacktestWorkbench />
          </main>
        </div>
      </div>
    </AuthGuard>
  );
}
