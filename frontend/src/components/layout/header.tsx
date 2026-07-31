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
          aria-label="Toggle sidebar"
          className="hidden md:inline-flex"
        >
          <Menu className="size-5" />
        </Button>
        <div>
          <h1 className="text-base font-bold text-slate-950">Dashboard</h1>
          <p className="hidden text-xs text-slate-500 sm:block">
            Monitor your financial risk status at a glance.
          </p>
        </div>
      </div>

      <div className="flex items-center gap-2">
        <NotificationBell />
        <div className="hidden text-right sm:block">
          <p className="text-sm font-semibold text-slate-800">{user?.name}</p>
          <p className="text-xs text-slate-500">{user?.email}</p>
        </div>
        <Button
          type="button"
          size="sm"
          variant="ghost"
          onClick={handleLogout}
          disabled={isLoggingOut}
        >
          <LogOut className="size-4" aria-hidden="true" />
          {isLoggingOut ? "Logging out…" : "Log out"}
        </Button>
      </div>
    </header>
  );
}
