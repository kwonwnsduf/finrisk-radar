"use client";

import { LockKeyhole, Mail, MessageCircle } from "lucide-react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useRef, useState, type FormEvent } from "react";

import { AuthShell } from "@/components/auth/auth-shell";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  exchangeOAuthCode,
  getAuthErrorMessage,
  login,
} from "@/lib/api/auth";
import { establishSession } from "@/lib/auth/session";
import { useAuthStore } from "@/store/auth-store";

const OAUTH_BASE_URL = (
  process.env.NEXT_PUBLIC_OAUTH_BASE_URL ?? "http://localhost:8080"
).replace(/\/+$/, "");

export function LoginForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const status = useAuthStore((state) => state.status);
  const processedCode = useRef<string | null>(null);
  const oauthCode = searchParams.get("oauthCode");
  const oauthError = searchParams.get("oauthError");
  const registered = searchParams.get("registered") === "1";
  const [error, setError] = useState<string | null>(() =>
    oauthError ? "Google 로그인에 실패했습니다. 다시 시도해 주세요." : null,
  );
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    if (status === "authenticated") {
      router.replace("/dashboard");
    }
  }, [router, status]);

  useEffect(() => {
    if (oauthError) {
      window.history.replaceState(null, "", "/login");
      return;
    }

    if (!oauthCode || processedCode.current === oauthCode) {
      return;
    }

    processedCode.current = oauthCode;
    void (async () => {
      await Promise.resolve();
      setIsSubmitting(true);
      setError(null);
      try {
        const response = await exchangeOAuthCode(oauthCode);
        window.history.replaceState(null, "", "/login");
        await establishSession(response);
        router.replace("/dashboard");
      } catch (exchangeError) {
        window.history.replaceState(null, "", "/login");
        setError(
          getAuthErrorMessage(
            exchangeError,
            "Google 로그인 정보를 확인할 수 없습니다.",
          ),
        );
      } finally {
        setIsSubmitting(false);
      }
    })();
  }, [oauthCode, oauthError, router]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formData = new FormData(event.currentTarget);
    setIsSubmitting(true);
    setError(null);

    try {
      const response = await login({
        email: String(formData.get("email") ?? ""),
        password: String(formData.get("password") ?? ""),
      });
      await establishSession(response);
      router.replace("/dashboard");
    } catch (loginError) {
      setError(
        getAuthErrorMessage(loginError, "로그인 중 오류가 발생했습니다."),
      );
    } finally {
      setIsSubmitting(false);
    }
  }

  function startGoogleLogin() {
    window.location.assign(`${OAUTH_BASE_URL}/oauth2/authorization/google`);
  }

  return (
    <AuthShell
      eyebrow="다시 만나 반가워요"
      title="로그인"
      description="계정 정보를 입력하고 대시보드를 확인하세요."
    >
      {registered && !error ? (
        <p className="mt-6 rounded-xl bg-emerald-50 px-4 py-3 text-sm font-medium text-emerald-700">
          회원가입이 완료되었습니다. 로그인해 주세요.
        </p>
      ) : null}
      {error ? (
        <p role="alert" className="mt-6 rounded-xl bg-red-50 px-4 py-3 text-sm font-medium text-red-700">
          {error}
        </p>
      ) : null}

      <form className="mt-8 space-y-5" onSubmit={handleSubmit}>
        <div className="space-y-2">
          <label htmlFor="email" className="text-sm font-semibold text-slate-700">
            이메일
          </label>
          <div className="relative">
            <Mail className="absolute left-3.5 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
            <Input
              id="email"
              name="email"
              type="email"
              autoComplete="email"
              placeholder="name@example.com"
              className="pl-10"
              maxLength={255}
              required
              disabled={isSubmitting}
            />
          </div>
        </div>

        <div className="space-y-2">
          <label htmlFor="password" className="text-sm font-semibold text-slate-700">
            비밀번호
          </label>
          <div className="relative">
            <LockKeyhole className="absolute left-3.5 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
            <Input
              id="password"
              name="password"
              type="password"
              autoComplete="current-password"
              placeholder="비밀번호를 입력하세요"
              className="pl-10"
              minLength={8}
              maxLength={64}
              required
              disabled={isSubmitting}
            />
          </div>
        </div>

        <Button type="submit" className="w-full" disabled={isSubmitting}>
          {isSubmitting ? "처리 중..." : "로그인"}
        </Button>
      </form>

      <div className="my-7 flex items-center gap-4">
        <div className="h-px flex-1 bg-slate-200" />
        <span className="text-xs text-slate-400">또는 간편 로그인</span>
        <div className="h-px flex-1 bg-slate-200" />
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        <Button type="button" variant="outline" onClick={startGoogleLogin} disabled={isSubmitting}>
          <span className="text-base font-bold text-blue-600">G</span>
          Google
        </Button>
        <Button type="button" variant="kakao" disabled title="추후 지원 예정">
          <MessageCircle className="size-4 fill-current" aria-hidden="true" />
          카카오
        </Button>
      </div>

      <p className="mt-8 text-center text-sm text-slate-500">
        아직 계정이 없으신가요?{" "}
        <Link href="/signup" className="font-semibold text-blue-600 hover:text-blue-700">
          회원가입
        </Link>
      </p>
    </AuthShell>
  );
}
