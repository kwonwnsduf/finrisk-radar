"use client";

import { LockKeyhole, Mail, UserRound } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";

import { AuthShell } from "@/components/auth/auth-shell";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { getAuthErrorMessage, signup } from "@/lib/api/auth";

export function SignupForm() {
  const router = useRouter();
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formData = new FormData(event.currentTarget);
    setIsSubmitting(true);
    setError(null);

    try {
      await signup({
        email: String(formData.get("email") ?? ""),
        password: String(formData.get("password") ?? ""),
        name: String(formData.get("name") ?? ""),
      });
      router.replace("/login?registered=1");
    } catch (signupError) {
      setError(
        getAuthErrorMessage(signupError, "회원가입 중 오류가 발생했습니다."),
      );
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <AuthShell
      eyebrow="FinRisk Radar 시작하기"
      title="회원가입"
      description="기본 정보를 입력해 새로운 계정을 만드세요."
    >
      {error ? (
        <p role="alert" className="mt-6 rounded-xl bg-red-50 px-4 py-3 text-sm font-medium text-red-700">
          {error}
        </p>
      ) : null}

      <form className="mt-8 space-y-5" onSubmit={handleSubmit}>
        <div className="space-y-2">
          <label htmlFor="name" className="text-sm font-semibold text-slate-700">이름</label>
          <div className="relative">
            <UserRound className="absolute left-3.5 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
            <Input id="name" name="name" autoComplete="name" className="pl-10" minLength={1} maxLength={50} required disabled={isSubmitting} />
          </div>
        </div>
        <div className="space-y-2">
          <label htmlFor="email" className="text-sm font-semibold text-slate-700">이메일</label>
          <div className="relative">
            <Mail className="absolute left-3.5 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
            <Input id="email" name="email" type="email" autoComplete="email" placeholder="name@example.com" className="pl-10" maxLength={255} required disabled={isSubmitting} />
          </div>
        </div>
        <div className="space-y-2">
          <label htmlFor="password" className="text-sm font-semibold text-slate-700">비밀번호</label>
          <div className="relative">
            <LockKeyhole className="absolute left-3.5 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
            <Input id="password" name="password" type="password" autoComplete="new-password" placeholder="8~64자로 입력하세요" className="pl-10" minLength={8} maxLength={64} required disabled={isSubmitting} />
          </div>
        </div>
        <Button type="submit" className="w-full" disabled={isSubmitting}>
          {isSubmitting ? "처리 중..." : "회원가입"}
        </Button>
      </form>

      <p className="mt-8 text-center text-sm text-slate-500">
        이미 계정이 있으신가요?{" "}
        <Link href="/login" className="font-semibold text-blue-600 hover:text-blue-700">
          로그인
        </Link>
      </p>
    </AuthShell>
  );
}
