"use client";

import { useEffect, useState } from "react";
import { CircleAlert, X } from "lucide-react";

import { USAGE_LIMIT_EVENT } from "@/lib/api/limit-errors";

export function UsageLimitToast() {
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    const show = (event: Event) => {
      setMessage((event as CustomEvent<string>).detail);
    };
    window.addEventListener(USAGE_LIMIT_EVENT, show);
    return () => window.removeEventListener(USAGE_LIMIT_EVENT, show);
  }, []);

  if (!message) return null;

  return (
    <div
      role="alert"
      className="fixed bottom-6 right-6 z-50 flex max-w-sm items-start gap-3 rounded-2xl border border-amber-200 bg-white p-4 shadow-xl"
    >
      <CircleAlert className="mt-0.5 size-5 shrink-0 text-amber-600" />
      <p className="text-sm font-medium text-slate-800">{message}</p>
      <button
        type="button"
        aria-label="안내 닫기"
        className="text-slate-400 hover:text-slate-700"
        onClick={() => setMessage(null)}
      >
        <X className="size-4" />
      </button>
    </div>
  );
}
