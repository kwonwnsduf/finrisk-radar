"use client";
import { AlertTriangle, CreditCard, LayoutDashboard, ShieldCheck, Users, FileWarning, ArrowLeft } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { cn } from "@/lib/utils";
const items = [
  { href: "/admin", label: "대시보드", icon: LayoutDashboard },
  { href: "/admin/users", label: "사용자·구독", icon: Users },
  { href: "/admin/payments", label: "결제", icon: CreditCard },
  { href: "/admin/fsd", label: "이상 결제 검토", icon: ShieldCheck },
  { href: "/admin/credit-event-candidates", label: "자산 위험 후보", icon: FileWarning },
  { href: "/admin/operational-issues", label: "시스템 문제", icon: AlertTriangle },
];
export function AdminSidebar() {
  const pathname = usePathname();
  return <aside className="hidden min-h-screen w-64 shrink-0 bg-slate-950 text-white md:flex md:flex-col">
    <div className="border-b border-slate-800 px-5 py-5"><p className="text-xs font-semibold text-blue-400">FINRISK RADAR</p><h1 className="mt-1 text-lg font-bold">운영 콘솔</h1></div>
    <nav className="flex-1 space-y-1 p-3" aria-label="관리자 메뉴">{items.map(({ href, label, icon: Icon }) => {
      const active = pathname === href || (href !== "/admin" && pathname.startsWith(href));
      return <Link key={href} href={href} className={cn("flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-semibold", active ? "bg-slate-800 text-white" : "text-slate-300 hover:bg-slate-900")}><Icon className="size-4" />{label}</Link>;
    })}</nav>
    <div className="border-t border-slate-800 p-3"><Link href="/dashboard" className="flex items-center gap-2 rounded-lg px-3 py-2 text-sm text-slate-300 hover:bg-slate-900"><ArrowLeft className="size-4" />일반 서비스로 돌아가기</Link></div>
  </aside>;
}

export function AdminMobileNav() {
  const pathname = usePathname();

  return (
    <nav
      className="flex min-w-max gap-2 border-b bg-white px-4 py-3 md:hidden"
      aria-label="관리자 모바일 메뉴"
    >
      {items.map(({ href, label, icon: Icon }) => {
        const active =
          pathname === href || (href !== "/admin" && pathname.startsWith(href));
        return (
          <Link
            key={href}
            href={href}
            className={cn(
              "flex items-center gap-2 rounded-full border px-3 py-2 text-xs font-semibold",
              active
                ? "border-slate-900 bg-slate-900 text-white"
                : "border-slate-200 text-slate-600",
            )}
          >
            <Icon className="size-3.5" />
            {label}
          </Link>
        );
      })}
    </nav>
  );
}
