"use client";

import { Bell, Menu } from "lucide-react";

import { Button } from "@/components/ui/button";
import { useUiStore } from "@/store/ui-store";

export function Header() {
  const toggleSidebar = useUiStore((state) => state.toggleSidebar);

  return (
    <header className="flex h-16 items-center justify-between border-b border-slate-200 bg-white px-5 md:px-8">
      <div className="flex items-center gap-3">
        <Button
          type="button"
          size="icon"
          variant="ghost"
          onClick={toggleSidebar}
          aria-label="사이드바 전환"
          className="hidden md:inline-flex"
        >
          <Menu className="size-5" />
        </Button>
        <div>
          <h1 className="text-base font-bold text-slate-950">대시보드</h1>
          <p className="hidden text-xs text-slate-500 sm:block">
            금융 리스크 현황을 한눈에 확인하세요
          </p>
        </div>
      </div>

      <div className="flex items-center gap-2">
        <Button type="button" size="icon" variant="ghost" aria-label="알림">
          <Bell className="size-5" />
        </Button>
        <div className="flex size-9 items-center justify-center rounded-full bg-slate-900 text-xs font-bold text-white">
          FR
        </div>
      </div>
    </header>
  );
}
