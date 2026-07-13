"use client";
import { useEffect, useState } from "react";
import { Activity, AlertTriangle, Play } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  getLatestRisk,
  getRiskJob,
  getRiskScore,
  requestRiskCalculation,
  type RiskJob,
  type RiskScore,
} from "@/lib/api/risks";

export function RiskOverview({ assetId }: { assetId: number }) {
  const [job, setJob] = useState<RiskJob | null>(null);
  const [score, setScore] = useState<RiskScore | null>(null);
  const [error, setError] = useState<string | null>(null);
  const active = job?.status === "REQUESTED" || job?.status === "RUNNING";
  useEffect(() => {
    getLatestRisk(assetId)
      .then(setScore)
      .catch(() => undefined);
  }, [assetId]);
  useEffect(() => {
    if (!active || !job) return;
    const timer = window.setInterval(async () => {
      try {
        const next = await getRiskJob(job.jobId);
        setJob(next);
        if (next.status === "COMPLETED" && next.riskScoreId)
          setScore(await getRiskScore(next.riskScoreId));
        if (next.status === "FAILED")
          setError(next.failureMessage ?? "Risk calculation failed.");
      } catch {
        setError("Risk status could not be refreshed.");
      }
    }, 2000);
    return () => window.clearInterval(timer);
  }, [active, job]);
  async function calculate() {
    setError(null);
    try {
      setJob(await requestRiskCalculation(assetId));
    } catch {
      setError("Risk calculation could not be requested.");
    }
  }
  const categories = score
    ? [
        ["Financial", score.financialScore, 25],
        ["Liquidity & maturity", score.liquidityScore, 35],
        ["Market", score.marketScore, 10],
        ["Credit event", score.creditEventScore, 25],
        ["Group contagion", score.groupContagionScore, 5],
      ]
    : [];
  return (
    <section className="mt-6 space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-xl font-bold text-slate-950">
            Corporate risk early warning
          </h2>
          <p className="text-sm text-slate-500">
            Explainable rule-based indicator, not an official credit rating.
          </p>
        </div>
        <Button onClick={calculate} disabled={Boolean(active)}>
          <Play className="size-4" />
          {active ? job?.status : "Calculate"}
        </Button>
      </div>
      {error ? (
        <p
          role="alert"
          className="rounded-lg bg-red-50 p-3 text-sm text-red-700"
        >
          {error}
        </p>
      ) : null}
      {score ? (
        <>
          <div className="grid gap-3 sm:grid-cols-3">
            <Metric title="Risk score" value={`${score.totalScore}/100`} />
            <Metric
              title="Grade / status"
              value={`${score.riskGrade} / ${score.defaultStatus}`}
            />
            <Metric
              title="Confidence"
              value={`${score.confidence} (${score.requiredRuleSuccessRate}%)`}
            />
          </div>
          <Card>
            <CardHeader>
              <CardTitle className="text-base">Category scores</CardTitle>
            </CardHeader>
            <CardContent className="grid gap-3 sm:grid-cols-2">
              {categories.map(([name, value, max]) => (
                <div key={String(name)} className="rounded-lg bg-slate-50 p-3">
                  <p className="text-xs font-semibold uppercase text-slate-500">
                    {name}
                  </p>
                  <p className="mt-1 font-bold">
                    {value === null ? "Not available" : `${value}/${max}`}
                  </p>
                </div>
              ))}
            </CardContent>
          </Card>
          <Card>
            <CardHeader>
              <CardTitle className="text-base">Top risk factors</CardTitle>
            </CardHeader>
            <CardContent className="space-y-2">
              {score.topRiskFactors.length ? (
                score.topRiskFactors.map((f) => (
                  <div
                    key={f.rank}
                    className="flex gap-3 rounded-lg border p-3"
                  >
                    <AlertTriangle className="mt-0.5 size-4 text-amber-600" />
                    <div>
                      <p className="font-semibold">
                        {f.rank}. {f.summary}
                      </p>
                      <p className="text-xs text-slate-500">
                        {f.category} · {f.severity} · +{f.score}
                      </p>
                    </div>
                  </div>
                ))
              ) : (
                <p className="text-sm text-slate-500">
                  No scored risk factors.
                </p>
              )}
            </CardContent>
          </Card>
          <p className="text-xs text-slate-500">
            Data as of {score.dataAsOfDate} · policy {score.ruleVersion} ·
            quality {score.dataQuality}
          </p>
        </>
      ) : (
        <Card>
          <CardContent className="flex items-center gap-3 py-6 text-slate-500">
            <Activity className="size-5" />
            No completed risk calculation.
          </CardContent>
        </Card>
      )}
    </section>
  );
}
function Metric({ title, value }: { title: string; value: string }) {
  return (
    <Card>
      <CardContent className="p-4">
        <p className="text-xs font-semibold uppercase text-slate-500">
          {title}
        </p>
        <p className="mt-2 text-xl font-bold">{value}</p>
      </CardContent>
    </Card>
  );
}
