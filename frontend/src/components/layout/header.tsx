"use client";

import { useQueryClient } from "@tanstack/react-query";
import { LogOut, Menu } from "lucide-react";
import { useRouter } from "next/navigation";
import { useState } from "react";

import { NotificationBell } from "@/components/notifications/notification-bell";
import { Button } from "@/components/ui/button";
import { logout } from "@/lib/api/auth";
import { clearAuthSession } from "@/lib/auth/session";
import { useAuthStore } from "@/store/auth-store";
import { useUiStore } from "@/store/ui-store";

export function Header() {
  const toggleSidebar = useUiStore((state) => state.toggleSidebar);
  const toggleMobileSidebar = useUiStore(
    (state) => state.toggleMobileSidebar,
  );
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
    <header className="flex h-16 min-w-0 items-center justify-between gap-2 border-b border-slate-200 bg-white px-3 sm:px-5 md:px-8">
      <div className="flex min-w-0 flex-1 items-center gap-2 sm:gap-3">
        <Button
          type="button"
          size="icon"
          variant="ghost"
          onClick={toggleMobileSidebar}
          aria-label="Open navigation"
          className="shrink-0 md:hidden"
        >
          <Menu className="size-5" />
        </Button>
        <Button
          type="button"
          size="icon"
          variant="ghost"
          onClick={toggleSidebar}
          aria-label="Toggle sidebar"
          className="hidden md:inline-flex"
        >
          <Menu className="size-5" />
        </Button>
        <div className="min-w-0">
          <h1 className="truncate text-base font-bold text-slate-950">
            Dashboard
          </h1>
          <p className="hidden text-xs text-slate-500 sm:block">
            Monitor your financial risk status at a glance.
          </p>
        </div>
      </div>

      <div className="flex shrink-0 items-center gap-1 sm:gap-2">
        <NotificationBell />
        <div className="hidden text-right sm:block">
          <p className="text-sm font-semibold text-slate-800">{user?.name}</p>
          <p className="max-w-48 truncate text-xs text-slate-500">
            {user?.email}
          </p>
        </div>
        <Button
          type="button"
          size="sm"
          variant="ghost"
          onClick={handleLogout}
          disabled={isLoggingOut}
          className="px-2 sm:px-3"
          aria-label={isLoggingOut ? "Logging out" : "Log out"}
        >
          <LogOut className="size-4" aria-hidden="true" />
          <span className="hidden sm:inline">
            {isLoggingOut ? "Logging out…" : "Log out"}
          </span>
        </Button>
      </div>
    </header>
  );
}
