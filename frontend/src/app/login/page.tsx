import { Suspense } from "react";

import { LoginForm } from "@/components/auth/login-form";

export default function LoginPage() {
  return (
    <Suspense
      fallback={
        <main className="flex min-h-screen items-center justify-center bg-slate-50 text-sm font-medium text-slate-500">
          로그인 화면을 준비하고 있습니다.
        </main>
      }
    >
      <LoginForm />
    </Suspense>
  );
}
