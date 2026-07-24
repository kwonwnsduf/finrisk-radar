"use client";

import {
  ChartNoAxesCombined,
  FileSearch,
  Bot,
  Files,
  ChevronLeft,
  ChevronRight,
  LayoutDashboard,
  Radar,
  ShieldCheck,
  Search,
  Star,
} from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";

import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { useUiStore } from "@/store/ui-store";
import { useAuthStore } from "@/store/auth-store";

const navItems = [
  { href: "/dashboard", label: "Dashboard", icon: LayoutDashboard },
  { href: "/assets", label: "Assets", icon: Search },
  { href: "/watchlist", label: "Watchlist", icon: Star },
  { href: "/backtests", label: "Backtests", icon: ChartNoAxesCombined },
  { href: "/rag", label: "RAG Search", icon: FileSearch },
  { href: "/ai", label: "AI Agent", icon: Bot },
  { href: "/reports", label: "AI Reports", icon: Files },
];

export function Sidebar() {
  const { sidebarCollapsed, toggleSidebar } = useUiStore();
  const user = useAuthStore((state) => state.user);
  const pathname = usePathname();
  const visibleItems =
    user?.role === "ROLE_ADMIN"
      ? [
          ...navItems,
          {
            href: "/admin/credit-event-candidates",
            label: "Event Review",
            icon: ShieldCheck,
          },
        ]
      : navItems;

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
          <span className="text-lg font-bold tracking-tight">
            FinRisk Radar
          </span>
        ) : null}
      </div>

      <nav className="flex-1 p-3" aria-label="Dashboard menu">
        {visibleItems.map((item) => {
          const Icon = item.icon;
          const active =
            pathname === item.href ||
            (item.href !== "/dashboard" && pathname.startsWith(item.href));
          return (
            <Link
              key={item.href}
              href={item.href}
              className={cn(
                "flex h-11 items-center gap-3 rounded-xl px-3 text-sm font-semibold",
                active
                  ? "bg-slate-800 text-white"
                  : "mt-1 text-slate-300 hover:bg-slate-800 hover:text-white",
              )}
            >
              <Icon className="size-5 shrink-0" aria-hidden="true" />
              {!sidebarCollapsed ? <span>{item.label}</span> : null}
            </Link>
          );
        })}
      </nav>

      <div className="border-t border-slate-800 p-3">
        <Button
          type="button"
          variant="ghost"
          className="w-full text-slate-400 hover:bg-slate-800 hover:text-white"
          onClick={toggleSidebar}
          aria-label={sidebarCollapsed ? "Expand sidebar" : "Collapse sidebar"}
        >
          {sidebarCollapsed ? (
            <ChevronRight className="size-5" />
          ) : (
            <>
              <ChevronLeft className="size-5" />
              <span>Collapse</span>
            </>
          )}
        </Button>
      </div>
    </aside>
  );
}
