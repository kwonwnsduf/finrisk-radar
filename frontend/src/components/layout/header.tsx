"use client";

import { Bell, LogOut, Menu } from "lucide-react";
import { useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useState } from "react";

import { Button } from "@/components/ui/button";
import { useUiStore } from "@/store/ui-store";
import { useAuthStore } from "@/store/auth-store";
import { logout } from "@/lib/api/auth";
import { clearAuthSession } from "@/lib/auth/session";

export function Header() {
  const toggleSidebar = useUiStore((state) => state.toggleSidebar);
  const user = useAuthStore((state) => state.user);
  const router = useRouter();
  const queryClient = useQueryClient();
  const [isLoggingOut, setIsLoggingOut] = useState(false);

  async function handleLogout() {
    setIsLoggingOut(true);
    try {
      await logout();
    } catch {
      // Local credentials are always removed even if server logout fails.
    } finally {
      clearAuthSession();
      queryClient.clear();
      router.replace("/login");
      setIsLoggingOut(false);
    }
  }

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
        <div className="hidden text-right sm:block">
          <p className="text-sm font-semibold text-slate-800">{user?.name}</p>
          <p className="text-xs text-slate-500">{user?.email}</p>
        </div>
        <Button type="button" size="sm" variant="ghost" onClick={handleLogout} disabled={isLoggingOut}>
          <LogOut className="size-4" aria-hidden="true" />
          {isLoggingOut ? "로그아웃 중" : "로그아웃"}
        </Button>
      </div>
    </header>
  );
}
