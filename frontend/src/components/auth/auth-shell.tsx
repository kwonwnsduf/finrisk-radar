import { Radar } from "lucide-react";
import type { ReactNode } from "react";

interface AuthShellProps {
  eyebrow: string;
  title: string;
  description: string;
  children: ReactNode;
}

export function AuthShell({
  eyebrow,
  title,
  description,
  children,
}: AuthShellProps) {
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
          © 2026 FinRisk Radar.
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
            <p className="text-sm font-semibold text-blue-600">{eyebrow}</p>
            <h2 className="mt-2 text-3xl font-bold tracking-tight text-slate-950">
              {title}
            </h2>
            <p className="mt-3 text-sm leading-6 text-slate-500">
              {description}
            </p>
          </div>

          {children}
        </div>
      </section>
    </main>
  );
}
