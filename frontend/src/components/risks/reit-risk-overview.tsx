"use client";

import { useEffect, useMemo, useState } from "react";
import { AlertTriangle, Building2, Play } from "lucide-react";
import { CommonChart } from "@/components/common/common-chart";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { apiErrorMessage } from "@/lib/api/error-message";
import {
  getLatestRisk,
  getRiskHistory,
  getRiskJob,
  getRiskScore,
  requestRiskCalculation,
  type RiskJob,
  type RiskScore,
  type TopRiskFactor,
} from "@/lib/api/risks";

const covenantSignals = new Set([
  "COVENANT_HEADROOM_LOW",
  "CASH_TRAP_COVENANT_BREACH",
  "DEFAULT_COVENANT_BREACH",
  "TRAPPED_CASH_DEPENDENCY",
]);

export function ReitRiskOverview({ assetId }: { assetId: number }) {
  const [job, setJob] = useState<RiskJob | null>(null);
  const [score, setScore] = useState<RiskScore | null>(null);
  const [history, setHistory] = useState<RiskScore[]>([]);
  const [error, setError] = useState<string | null>(null);
  const active =
    job?.status === "COLLECTING" ||
    job?.status === "REQUESTED" ||
    job?.status === "RUNNING";

  useEffect(() => {
    const refresh = () => {
      void Promise.all([getLatestRisk(assetId), getRiskHistory(assetId)])
        .then(([latest, previous]) => {
          setScore(latest);
          setHistory(previous);
        })
        .catch(() => undefined);
    };
    refresh();
    const timer = window.setInterval(refresh, 15_000);
    window.addEventListener("focus", refresh);
    return () => {
      window.clearInterval(timer);
      window.removeEventListener("focus", refresh);
    };
  }, [assetId]);

  useEffect(() => {
    if (!active || !job) return;
    const timer = window.setInterval(async () => {
      try {
        const next = await getRiskJob(job.jobId);
        setJob(next);
        if (next.status === "COMPLETED" && next.riskScoreId) {
          setScore(await getRiskScore(next.riskScoreId));
          setHistory(await getRiskHistory(assetId));
        }
        if (next.status === "FAILED")
          setError(next.failureMessage ?? "REIT risk calculation failed.");
      } catch {
        setError("REIT risk status could not be refreshed.");
      }
    }, 2000);
    return () => window.clearInterval(timer);
  }, [active, assetId, job]);

  async function calculate() {
    setError(null);
    try {
      setJob(await requestRiskCalculation(assetId));
    } catch (requestError) {
      setError(
        apiErrorMessage(
          requestError,
          "REIT risk calculation could not be requested.",
        ),
      );
    }
  }

  const factors = useMemo(() => score?.topRiskFactors ?? [], [score]);
  const covenant = factors.filter((factor) => covenantSignals.has(factor.signalType));
  const evidenceCards = useMemo(
    () => [
      evidenceCard("LTV", factors, ["LTV_SPIKE", "COVENANT_HEADROOM_LOW"], ["currentLtv", "ltv"]),
      evidenceCard("Asset value change", factors, ["ASSET_VALUE_DROP"], ["dropPercent"]),
      evidenceCard("Six-month maturity", factors, ["MATURITY_WALL"], ["concentrationPercent"]),
      evidenceCard("FX hedge burden", factors, ["FX_HEDGE_BURDEN"], ["burdenPercent"]),
    ],
    [factors],
  );

  return (
    <section className="mt-6 space-y-4">
      <div className="flex items-center justify-between gap-4">
        <div>
          <h2 className="text-xl font-bold text-slate-950">REIT risk early warning</h2>
          <p className="text-sm text-slate-500">Rule-based indicator using disclosed REIT metrics and debt maturities.</p>
        </div>
        <Button onClick={calculate} disabled={Boolean(active)}>
          <Play className="size-4" />
          {active ? job?.status : "Calculate"}
        </Button>
      </div>
      {error ? <p role="alert" className="rounded-lg bg-red-50 p-3 text-sm text-red-700">{error}</p> : null}
      {active ? (
        <p className="rounded-lg bg-blue-50 p-3 text-sm text-blue-700">
          {job?.status === "COLLECTING"
            ? "Collecting DART data and preparing REIT metrics."
            : job?.status === "REQUESTED"
              ? "REIT metrics are ready. Waiting for the risk calculator."
              : "Calculating the REIT risk score from the stored data."}
        </p>
      ) : null}
      {score ? (
        <>
          {score.dataQuality !== "COMPLETE" ? (
            <p className="rounded-lg bg-amber-50 p-3 text-sm text-amber-800">
              일부 리츠 전용 원천 데이터가 없어 부분 점수만 계산되었습니다. 0점은 안전을
              뜻하지 않습니다. 미확보 범주: {score.missingCategories || "상세 지표"}
            </p>
          ) : null}
          <div className="grid gap-3 sm:grid-cols-3">
            <Metric title="REIT risk score" value={`${score.totalScore}/100`} />
            <Metric title="Grade / status" value={`${score.riskGrade} / ${score.defaultStatus}`} />
            <Metric title="Confidence" value={`${score.confidence} (${score.requiredRuleSuccessRate}%)`} />
          </div>
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            {evidenceCards.map((item) => <Metric key={item.title} title={item.title} value={item.value} />)}
          </div>
          <Card>
            <CardHeader><CardTitle className="text-base">REIT category scores</CardTitle></CardHeader>
            <CardContent className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5">
              <Category label="Financial" value={score.financialScore} max={25} />
              <Category label="Liquidity & maturity" value={score.liquidityScore} max={35} />
              <Category label="Property valuation" value={score.marketScore} max={10} />
              <Category label="Credit events" value={score.creditEventScore} max={25} />
              <Category label="Foreign cash dependency" value={score.groupContagionScore} max={5} />
            </CardContent>
          </Card>
          {covenant.length ? (
            <Card className="border-red-200 bg-red-50/50">
              <CardHeader><CardTitle className="text-base text-red-900">Cash Trap & covenant warnings</CardTitle></CardHeader>
              <CardContent className="space-y-2">{covenant.map((factor) => <Factor key={factor.rank} factor={factor} />)}</CardContent>
            </Card>
          ) : null}
          <Card>
            <CardHeader><CardTitle className="text-base">Top REIT risk signals</CardTitle></CardHeader>
            <CardContent className="space-y-2">
              {factors.length ? factors.map((factor) => <Factor key={factor.rank} factor={factor} />) : <p className="text-sm text-slate-500">No scored REIT risk signals.</p>}
            </CardContent>
          </Card>
          {history.length ? (
            <CommonChart
              title="REIT risk trend"
              description="Historical total risk score"
              data={[...history].reverse().map((item) => ({ label: item.dataAsOfDate, value: item.totalScore }))}
            />
          ) : null}
          <p className="text-xs text-slate-500">Data as of {score.dataAsOfDate} · policy {score.ruleVersion} · quality {score.dataQuality}</p>
        </>
      ) : (
        <Card><CardContent className="flex items-center gap-3 py-6 text-slate-500"><Building2 className="size-5" />No completed REIT risk calculation.</CardContent></Card>
      )}
    </section>
  );
}

function parseEvidence(factor: TopRiskFactor): Record<string, unknown> {
  try { return JSON.parse(factor.evidence) as Record<string, unknown>; } catch { return {}; }
}

function evidenceCard(title: string, factors: TopRiskFactor[], signals: string[], keys: string[]) {
  const factor = factors.find((item) => signals.includes(item.signalType));
  if (!factor) return { title, value: "미확보 또는 점수화 신호 없음" };
  const evidence = parseEvidence(factor);
  const key = keys.find((candidate) => evidence[candidate] !== undefined);
  return { title, value: key ? String(evidence[key]) : "Risk detected" };
}

function Metric({ title, value }: { title: string; value: string }) {
  return <Card><CardContent className="p-4"><p className="text-xs font-semibold uppercase text-slate-500">{title}</p><p className="mt-2 text-xl font-bold">{value}</p></CardContent></Card>;
}

function Category({ label, value, max }: { label: string; value: number | null; max: number }) {
  return <div className="rounded-lg bg-slate-50 p-3"><p className="text-xs font-semibold uppercase text-slate-500">{label}</p><p className="mt-1 font-bold">{value === null ? "Not available" : `${value}/${max}`}</p></div>;
}

function Factor({ factor }: { factor: TopRiskFactor }) {
  return (
    <div className="flex gap-3 rounded-lg border bg-white p-3">
      <AlertTriangle className="mt-0.5 size-4 text-amber-600" />
      <div><p className="font-semibold">{factor.rank}. {factor.signalType}</p><p className="text-sm text-slate-700">{factor.summary}</p><p className="text-xs text-slate-500">{factor.category} · {factor.severity} · +{factor.score}</p></div>
    </div>
  );
}
