import Link from "next/link";
import { Crown, ShieldCheck, Sparkles } from "lucide-react";
import { AppShell } from "@/components/layout/app-shell";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

export default function PricingPage() {
  return <AppShell><div className="mx-auto max-w-4xl">
    <div className="mb-8 text-center"><h1 className="text-3xl font-bold text-slate-950">FinRisk Radar PREMIUM</h1><p className="mt-2 text-slate-600">자동 갱신 없이 결제일로부터 30일 동안 이용합니다.</p></div>
    <Card className="mx-auto max-w-xl border-blue-200 shadow-lg"><CardHeader><Crown className="size-10 text-blue-600" /><CardTitle className="text-2xl">PREMIUM 30일 이용권</CardTitle></CardHeader><CardContent>
      <p className="text-4xl font-bold">₩5,900</p><p className="mt-1 text-sm text-slate-500">일회성 테스트 결제 · 자동 갱신 없음</p>
      <ul className="my-6 space-y-3 text-sm text-slate-700"><li className="flex gap-2"><Sparkles className="size-5 text-blue-600" />백테스트·위험 보고서·AI Agent·RAG Search 월 사용량 제한 해제</li><li className="flex gap-2"><ShieldCheck className="size-5 text-blue-600" />서버 금액 검증과 이상 결제 탐지 적용</li></ul>
      <Button asChild className="w-full"><Link href="/payment">결제하기</Link></Button>
    </CardContent></Card>
  </div></AppShell>;
}
