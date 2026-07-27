"use client";

import { useRouter } from "next/navigation";
import { useEffect, type ReactNode } from "react";
import { useAuthStore } from "@/store/auth-store";

export function AdminGuard({ children }: { children: ReactNode }) {
  const router = useRouter();
  const user = useAuthStore((state) => state.user);
  const status = useAuthStore((state) => state.status);

  useEffect(() => {
    if (status === "anonymous") router.replace("/login");
  }, [router, status]);

  if (status === "initializing") {
    return <p className="p-8 text-sm text-slate-500">관리자 권한을 확인하는 중입니다.</p>;
  }
  if (!user || user.role !== "ROLE_ADMIN") {
    return <div className="grid min-h-screen place-items-center bg-slate-50 p-6 text-center"><div><h1 className="text-xl font-bold">접근 권한이 없습니다</h1><p className="mt-2 text-sm text-slate-500">관리자 계정으로 로그인해야 합니다.</p></div></div>;
  }
  return children;
}
