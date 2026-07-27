import { AdminGuard } from "@/components/auth/admin-guard";
import { FsdConsole } from "@/components/admin/fsd-console";
import { AppShell } from "@/components/layout/app-shell";

export default function AdminFsdPage() {
  return <AppShell><AdminGuard><div><h1 className="mb-6 text-2xl font-bold">FSD 이상 결제 검토</h1><FsdConsole /></div></AdminGuard></AppShell>;
}
