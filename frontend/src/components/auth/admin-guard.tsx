"use client";

import { useRouter } from "next/navigation";
import { useEffect, type ReactNode } from "react";
import { useAuthStore } from "@/store/auth-store";

export function AdminGuard({ children }: { children: ReactNode }) {
  const router = useRouter();
  const user = useAuthStore((state) => state.user);

  useEffect(() => {
    if (user && user.role !== "ROLE_ADMIN") router.replace("/dashboard");
  }, [router, user]);

  if (!user || user.role !== "ROLE_ADMIN") {
    return <p className="p-8 text-sm text-slate-500">관리자 권한을 확인하고 있습니다.</p>;
  }
  return children;
}
