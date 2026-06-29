"use client";

import { ChevronLeft, ChevronRight, LayoutDashboard, Radar } from "lucide-react";
import Link from "next/link";

import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { useUiStore } from "@/store/ui-store";

export function Sidebar() {
  const { sidebarCollapsed, toggleSidebar } = useUiStore();

  return (
    <aside
      className={cn(
        "hidden min-h-screen shrink-0 border-r border-slate-800 bg-slate-950 text-white transition-[width] duration-200 md:flex md:flex-col",
        sidebarCollapsed ? "w-20" : "w-64",
      )}
    >
      <div className="flex h-16 items-center gap-3 border-b border-slate-800 px-5">
        <div className="flex size-9 shrink-0 items-center justify-center rounded-xl bg-blue-600">
          <Radar className="size-5" aria-hidden="true" />
        </div>
        {!sidebarCollapsed ? (
          <span className="text-lg font-bold tracking-tight">FinRisk Radar</span>
        ) : null}
      </div>

      <nav className="flex-1 p-3" aria-label="대시보드 메뉴">
        <Link
          href="/dashboard"
          className="flex h-11 items-center gap-3 rounded-xl bg-slate-800 px-3 text-sm font-semibold text-white"
        >
          <LayoutDashboard className="size-5 shrink-0" aria-hidden="true" />
          {!sidebarCollapsed ? <span>대시보드</span> : null}
        </Link>
      </nav>

      <div className="border-t border-slate-800 p-3">
        <Button
          type="button"
          variant="ghost"
          className="w-full text-slate-400 hover:bg-slate-800 hover:text-white"
          onClick={toggleSidebar}
          aria-label={sidebarCollapsed ? "사이드바 펼치기" : "사이드바 접기"}
        >
          {sidebarCollapsed ? (
            <ChevronRight className="size-5" />
          ) : (
            <>
              <ChevronLeft className="size-5" />
              <span>사이드바 접기</span>
            </>
          )}
        </Button>
      </div>
    </aside>
  );
}
