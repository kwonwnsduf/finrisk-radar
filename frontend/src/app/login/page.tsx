import { LockKeyhole, Mail, MessageCircle, Radar } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

export default function LoginPage() {
  return (
    <main className="grid min-h-screen bg-slate-50 lg:grid-cols-[1.05fr_0.95fr]">
      <section className="relative hidden overflow-hidden bg-slate-950 p-12 text-white lg:flex lg:flex-col lg:justify-between">
        <div className="absolute -left-24 top-1/3 size-80 rounded-full bg-blue-600/20 blur-3xl" />
        <div className="absolute -right-20 bottom-10 size-72 rounded-full bg-cyan-400/10 blur-3xl" />

        <div className="relative flex items-center gap-3">
          <div className="flex size-10 items-center justify-center rounded-xl bg-blue-600">
            <Radar className="size-6" aria-hidden="true" />
          </div>
          <span className="text-xl font-bold tracking-tight">FinRisk Radar</span>
        </div>

        <div className="relative max-w-xl pb-16">
          <p className="mb-4 text-sm font-semibold uppercase tracking-[0.24em] text-blue-300">
            Financial Risk Intelligence
          </p>
          <h1 className="text-4xl font-bold leading-tight tracking-tight xl:text-5xl">
            더 빠르게 발견하고,
            <br />더 현명하게 대응하세요.
          </h1>
          <p className="mt-6 max-w-lg text-base leading-7 text-slate-300">
            흩어진 금융 데이터를 한곳에서 살펴보고 중요한 리스크 신호를 놓치지
            않도록 돕습니다.
          </p>
        </div>

        <p className="relative text-xs text-slate-500">
          © 2026 FinRisk Radar. Day 1 UI Preview.
        </p>
      </section>

      <section className="flex items-center justify-center px-5 py-12 sm:px-10">
        <div className="w-full max-w-md">
          <div className="mb-10 flex items-center gap-3 lg:hidden">
            <div className="flex size-10 items-center justify-center rounded-xl bg-blue-600 text-white">
              <Radar className="size-6" aria-hidden="true" />
            </div>
            <span className="text-xl font-bold tracking-tight text-slate-950">
              FinRisk Radar
            </span>
          </div>

          <div>
            <p className="text-sm font-semibold text-blue-600">다시 만나 반가워요</p>
            <h2 className="mt-2 text-3xl font-bold tracking-tight text-slate-950">
              로그인
            </h2>
            <p className="mt-3 text-sm leading-6 text-slate-500">
              계정 정보를 입력하고 대시보드를 확인하세요.
            </p>
          </div>

          <form className="mt-8 space-y-5">
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
                />
              </div>
            </div>

            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <label
                  htmlFor="password"
                  className="text-sm font-semibold text-slate-700"
                >
                  비밀번호
                </label>
                <span className="text-xs font-medium text-slate-400">
                  UI 데모
                </span>
              </div>
              <div className="relative">
                <LockKeyhole className="absolute left-3.5 top-1/2 size-4 -translate-y-1/2 text-slate-400" />
                <Input
                  id="password"
                  name="password"
                  type="password"
                  autoComplete="current-password"
                  placeholder="비밀번호를 입력하세요"
                  className="pl-10"
                />
              </div>
            </div>

            <Button type="button" className="w-full">
              로그인
            </Button>
          </form>

          <div className="my-7 flex items-center gap-4">
            <div className="h-px flex-1 bg-slate-200" />
            <span className="text-xs text-slate-400">또는 간편 로그인</span>
            <div className="h-px flex-1 bg-slate-200" />
          </div>

          <div className="grid gap-3 sm:grid-cols-2">
            <Button type="button" variant="outline">
              <span className="text-base font-bold text-blue-600">G</span>
              Google
            </Button>
            <Button type="button" variant="kakao">
              <MessageCircle className="size-4 fill-current" aria-hidden="true" />
              카카오
            </Button>
          </div>

          <p className="mt-8 text-center text-sm text-slate-500">
            아직 계정이 없으신가요?{" "}
            <a
              href="#signup"
              className="font-semibold text-blue-600 hover:text-blue-700"
            >
              회원가입
            </a>
          </p>
        </div>
      </section>
    </main>
  );
}
