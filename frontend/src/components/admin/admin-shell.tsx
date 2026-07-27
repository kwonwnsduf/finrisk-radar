"use client";
import type { ReactNode } from "react";
import { AdminGuard } from "@/components/auth/admin-guard";
import {
  AdminMobileNav,
  AdminSidebar,
} from "@/components/admin/admin-sidebar";
import { useAuthStore } from "@/store/auth-store";
export function AdminShell({ children }: { children: ReactNode }) {
  const user = useAuthStore((state) => state.user);
  return <AdminGuard><div className="flex min-h-screen bg-slate-50"><AdminSidebar /><div className="min-w-0 flex-1"><header className="flex h-16 items-center justify-between border-b bg-white px-5 md:px-8"><div><p className="font-bold">서비스 운영 콘솔</p><p className="text-xs text-slate-500">사업 현황, 결제 문제, 사람 검토가 필요한 항목</p></div><div className="text-right"><p className="text-sm font-semibold">{user?.name}</p><p className="text-xs text-slate-500">{user?.email}</p></div></header><div className="overflow-x-auto"><AdminMobileNav /></div><main className="p-5 md:p-8">{children}</main></div></div></AdminGuard>;
}
