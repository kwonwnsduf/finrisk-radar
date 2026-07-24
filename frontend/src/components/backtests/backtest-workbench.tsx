"use client";

import { useEffect, useMemo, useState } from "react";
import { Activity, Plus, Play, Trash2 } from "lucide-react";
import {
  Bar,
  BarChart,
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";

import {
  createBacktest,
  getBacktestJob,
  type BacktestJob,
  type CustomConditionType,
  type StrategyCondition,
  type StrategyType,
} from "@/lib/api/backtests";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";
import { createBacktestReport } from "@/lib/api/reports";
import { useRouter } from "next/navigation";

const BASIC_STRATEGIES: { type: StrategyType; label: string }[] = [
  { type: "BUY_AND_HOLD", label: "Buy & Hold" },
  { type: "MOVING_AVERAGE", label: "Moving Average" },
  { type: "RSI", label: "RSI" },
  { type: "BOLLINGER_BAND", label: "Bollinger Band" },
  { type: "MACD", label: "MACD" },
  { type: "VOLATILITY_BREAKOUT", label: "Volatility Breakout" },
  { type: "DCA", label: "DCA" },
  { type: "MA_DEVIATION", label: "MA Deviation" },
  { type: "DONCHIAN_CHANNEL", label: "Donchian Channel" },
  { type: "MOMENTUM", label: "Momentum" },
];

const BUY_CONDITIONS: CustomConditionType[] = [
  "RSI_LESS_THAN",
  "PRICE_ABOVE_MA",
  "MA_CROSS_UP",
  "BOLLINGER_LOWER_TOUCH",
  "MACD_GOLDEN_CROSS",
  "VOLUME_SPIKE",
  "BREAKOUT",
  "MA_DISCOUNT",
  "DONCHIAN_HIGH_BREAKOUT",
  "MOMENTUM_POSITIVE",
];

const SELL_CONDITIONS: CustomConditionType[] = [
  "RSI_GREATER_THAN",
  "PRICE_BELOW_MA",
  "MA_CROSS_DOWN",
  "BOLLINGER_UPPER_TOUCH",
  "MACD_DEAD_CROSS",
  "STOP_LOSS",
  "TAKE_PROFIT",
  "TRAILING_STOP",
  "MA_PREMIUM",
  "DONCHIAN_LOW_BREAKDOWN",
  "MOMENTUM_NEGATIVE",
];

const PERIOD_CONDITIONS = new Set<CustomConditionType>([
  "PRICE_ABOVE_MA",
  "MA_CROSS_UP",
  "BOLLINGER_LOWER_TOUCH",
  "VOLUME_SPIKE",
  "BREAKOUT",
  "MA_DISCOUNT",
  "DONCHIAN_HIGH_BREAKOUT",
  "MOMENTUM_POSITIVE",
  "PRICE_BELOW_MA",
  "MA_CROSS_DOWN",
  "BOLLINGER_UPPER_TOUCH",
  "MA_PREMIUM",
  "DONCHIAN_LOW_BREAKDOWN",
  "MOMENTUM_NEGATIVE",
]);

const VALUE_CONDITIONS = new Set<CustomConditionType>([
  "RSI_LESS_THAN",
  "RSI_GREATER_THAN",
  "STOP_LOSS",
  "TAKE_PROFIT",
  "TRAILING_STOP",
  "VOLUME_SPIKE",
  "MA_DISCOUNT",
  "MA_PREMIUM",
]);

export function BacktestWorkbench() {
  const router = useRouter();
  const [mode, setMode] = useState<"BASIC" | "CUSTOM">("BASIC");
  const [assetId, setAssetId] = useState("1");
  const [startDate, setStartDate] = useState("2020-01-01");
  const [endDate, setEndDate] = useState("2025-12-31");
  const [initialCash, setInitialCash] = useState("10000000");
  const [strategyType, setStrategyType] =
    useState<StrategyType>("BUY_AND_HOLD");
  const [buyConditions, setBuyConditions] = useState<StrategyCondition[]>([
    { type: "RSI_LESS_THAN", value: 30 },
  ]);
  const [sellConditions, setSellConditions] = useState<StrategyCondition[]>([
    { type: "RSI_GREATER_THAN", value: 70 },
    { type: "STOP_LOSS", value: -5 },
  ]);
  const [job, setJob] = useState<BacktestJob | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const activeJobId =
    job?.status === "REQUESTED" || job?.status === "RUNNING" ? job.jobId : null;

  useEffect(() => {
    if (!activeJobId) return;
    const timer = window.setInterval(async () => {
      try {
        setJob(await getBacktestJob(activeJobId));
      } catch {
        setError("Failed to refresh backtest status.");
      }
    }, 2000);
    return () => window.clearInterval(timer);
  }, [activeJobId]);

  const portfolioChart = useMemo(
    () =>
      job?.result?.dailyPortfolioValues.map((point) => ({
        label: point.date,
        value: Number(point.portfolioValue),
      })) ?? [],
    [job],
  );

  const monthlyChart = useMemo(
    () =>
      job?.result?.monthlyReturns.map((point) => ({
        label: point.month,
        value: Number(point.returnRate),
      })) ?? [],
    [job],
  );

  async function runBacktest() {
    setLoading(true);
    setError(null);
    try {
      const selectedStrategy = mode === "CUSTOM" ? "CUSTOM" : strategyType;
      const created = await createBacktest({
        assetId: Number(assetId),
        startDate,
        endDate,
        initialCash: Number(initialCash),
        strategyType: selectedStrategy,
        buyConditions:
          selectedStrategy === "CUSTOM" ? buyConditions : undefined,
        sellConditions:
          selectedStrategy === "CUSTOM" ? sellConditions : undefined,
      });
      setJob(await getBacktestJob(created.jobId));
    } catch {
      setError("Backtest request failed.");
    } finally {
      setLoading(false);
    }
  }

  async function analyzeBacktest() {
    if (!job?.jobId) return;
    setLoading(true);
    try {
      const report = await createBacktestReport(job.jobId);
      router.push(`/reports/${report.reportId}`);
    } catch {
      setError("AI analysis request failed.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="space-y-5">
      <section className="flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
        <div>
          <h1 className="text-2xl font-bold text-slate-950">Backtest</h1>
          <p className="mt-1 text-sm text-slate-500">
            Strategy engine and custom builder
          </p>
        </div>
        <Button
          type="button"
          onClick={runBacktest}
          disabled={loading || Boolean(activeJobId)}
        >
          <Play className="size-4" />
          Run
        </Button>
      </section>

      <section className="grid gap-5 xl:grid-cols-[420px_minmax(0,1fr)]">
        <Card className="rounded-lg">
          <CardHeader>
            <CardTitle className="text-base text-slate-950">Request</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="grid grid-cols-2 gap-3">
              <Field label="Asset ID">
                <Input
                  value={assetId}
                  onChange={(event) => setAssetId(event.target.value)}
                  inputMode="numeric"
                />
              </Field>
              <Field label="Initial cash">
                <Input
                  value={initialCash}
                  onChange={(event) => setInitialCash(event.target.value)}
                  inputMode="numeric"
                />
              </Field>
              <Field label="Start">
                <Input
                  type="date"
                  value={startDate}
                  onChange={(event) => setStartDate(event.target.value)}
                />
              </Field>
              <Field label="End">
                <Input
                  type="date"
                  value={endDate}
                  onChange={(event) => setEndDate(event.target.value)}
                />
              </Field>
            </div>

            <div className="grid grid-cols-2 gap-2 rounded-lg bg-slate-100 p-1">
              {(["BASIC", "CUSTOM"] as const).map((item) => (
                <button
                  key={item}
                  type="button"
                  onClick={() => setMode(item)}
                  className={cn(
                    "h-10 rounded-md text-sm font-semibold text-slate-600",
                    mode === item && "bg-white text-slate-950 shadow-sm",
                  )}
                >
                  {item === "BASIC" ? "Basic" : "Custom"}
                </button>
              ))}
            </div>

            {mode === "BASIC" ? (
              <div className="grid grid-cols-2 gap-2">
                {BASIC_STRATEGIES.map((strategy) => (
                  <button
                    key={strategy.type}
                    type="button"
                    onClick={() => setStrategyType(strategy.type)}
                    className={cn(
                      "min-h-11 rounded-md border px-3 py-2 text-left text-sm font-semibold",
                      strategyType === strategy.type
                        ? "border-blue-600 bg-blue-50 text-blue-700"
                        : "border-slate-200 bg-white text-slate-700 hover:bg-slate-50",
                    )}
                  >
                    {strategy.label}
                  </button>
                ))}
              </div>
            ) : (
              <CustomBuilder
                buyConditions={buyConditions}
                sellConditions={sellConditions}
                setBuyConditions={setBuyConditions}
                setSellConditions={setSellConditions}
              />
            )}

            {error ? (
              <p role="alert" className="text-sm font-semibold text-red-600">
                {error}
              </p>
            ) : null}
          </CardContent>
        </Card>

        <div className="space-y-5">
          <StatusCard job={job} loading={loading} />
          {job?.result ? (
            <>
              <Button
                type="button"
                variant="outline"
                onClick={analyzeBacktest}
                disabled={loading}
              >
                AI 해석 리포트 생성
              </Button>
              <ResultCards result={job.result} />
              <ChartCard
                title="Portfolio value"
                data={portfolioChart}
                kind="line"
              />
              <ChartCard
                title="Monthly returns"
                data={monthlyChart}
                kind="bar"
              />
              <TradeTable job={job} />
            </>
          ) : null}
        </div>
      </section>
    </div>
  );
}

function Field({
  label,
  children,
}: {
  label: string;
  children: React.ReactNode;
}) {
  return (
    <label className="space-y-1.5">
      <span className="text-xs font-semibold uppercase text-slate-500">
        {label}
      </span>
      {children}
    </label>
  );
}

function CustomBuilder({
  buyConditions,
  sellConditions,
  setBuyConditions,
  setSellConditions,
}: {
  buyConditions: StrategyCondition[];
  sellConditions: StrategyCondition[];
  setBuyConditions: (conditions: StrategyCondition[]) => void;
  setSellConditions: (conditions: StrategyCondition[]) => void;
}) {
  return (
    <div className="space-y-4">
      <ConditionList
        title="Buy conditions"
        options={BUY_CONDITIONS}
        conditions={buyConditions}
        onChange={setBuyConditions}
      />
      <ConditionList
        title="Sell conditions"
        options={SELL_CONDITIONS}
        conditions={sellConditions}
        onChange={setSellConditions}
      />
    </div>
  );
}

function ConditionList({
  title,
  options,
  conditions,
  onChange,
}: {
  title: string;
  options: CustomConditionType[];
  conditions: StrategyCondition[];
  onChange: (conditions: StrategyCondition[]) => void;
}) {
  function addCondition() {
    const type = options[0];
    onChange([...conditions, defaultCondition(type)]);
  }

  function updateCondition(index: number, condition: StrategyCondition) {
    onChange(
      conditions.map((item, itemIndex) =>
        itemIndex === index ? condition : item,
      ),
    );
  }

  function removeCondition(index: number) {
    onChange(conditions.filter((_, itemIndex) => itemIndex !== index));
  }

  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-semibold text-slate-950">{title}</h3>
        <Button
          type="button"
          size="sm"
          variant="outline"
          onClick={addCondition}
        >
          <Plus className="size-4" />
          Add
        </Button>
      </div>
      <div className="space-y-2">
        {conditions.map((condition, index) => (
          <ConditionRow
            key={`${condition.type}-${index}`}
            condition={condition}
            options={options}
            onChange={(next) => updateCondition(index, next)}
            onRemove={() => removeCondition(index)}
          />
        ))}
      </div>
    </div>
  );
}

function ConditionRow({
  condition,
  options,
  onChange,
  onRemove,
}: {
  condition: StrategyCondition;
  options: CustomConditionType[];
  onChange: (condition: StrategyCondition) => void;
  onRemove: () => void;
}) {
  return (
    <div className="grid gap-2 rounded-lg border border-slate-200 bg-slate-50 p-2">
      <div className="flex gap-2">
        <select
          className="h-10 min-w-0 flex-1 rounded-md border border-slate-200 bg-white px-2 text-sm font-semibold text-slate-800"
          value={condition.type}
          onChange={(event) =>
            onChange(
              defaultCondition(event.target.value as CustomConditionType),
            )
          }
        >
          {options.map((option) => (
            <option key={option} value={option}>
              {option}
            </option>
          ))}
        </select>
        <Button
          type="button"
          variant="ghost"
          size="icon"
          onClick={onRemove}
          aria-label="Remove condition"
        >
          <Trash2 className="size-4" />
        </Button>
      </div>
      <div className="grid grid-cols-2 gap-2">
        {PERIOD_CONDITIONS.has(condition.type) ? (
          <Input
            aria-label="Period"
            value={condition.period ?? ""}
            inputMode="numeric"
            placeholder="Period"
            onChange={(event) =>
              onChange({
                ...condition,
                period: numberOrUndefined(event.target.value),
              })
            }
          />
        ) : null}
        {VALUE_CONDITIONS.has(condition.type) ? (
          <Input
            aria-label="Value"
            value={condition.value ?? ""}
            inputMode="decimal"
            placeholder="Value"
            onChange={(event) =>
              onChange({
                ...condition,
                value: numberOrUndefined(event.target.value),
              })
            }
          />
        ) : null}
      </div>
    </div>
  );
}

function StatusCard({
  job,
  loading,
}: {
  job: BacktestJob | null;
  loading: boolean;
}) {
  return (
    <Card className="rounded-lg">
      <CardContent className="flex items-center justify-between py-5">
        <div className="flex items-center gap-3">
          <span className="flex size-10 items-center justify-center rounded-lg bg-blue-50 text-blue-700">
            <Activity className="size-5" />
          </span>
          <div>
            <p className="text-sm font-semibold text-slate-950">
              {job?.status ?? (loading ? "REQUESTING" : "READY")}
            </p>
            <p className="text-sm text-slate-500">
              {job?.message ?? "No active job"}
            </p>
          </div>
        </div>
        {job?.jobId ? (
          <span className="text-xs font-mono text-slate-400">
            {job.jobId.slice(0, 8)}
          </span>
        ) : null}
      </CardContent>
    </Card>
  );
}

function ResultCards({
  result,
}: {
  result: NonNullable<BacktestJob["result"]>;
}) {
  const items = [
    ["Total return", result.totalReturnRate],
    ["CAGR", result.cagr],
    ["MDD", result.mdd],
    ["Win rate", result.winRate],
    ["Trades", result.tradeCount],
    ["Sharpe", result.sharpeRatio],
    ["Benchmark", result.benchmarkReturnRate],
  ] as const;
  return (
    <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
      {items.map(([label, value]) => (
        <Card key={label} className="rounded-lg">
          <CardContent className="p-4">
            <p className="text-xs font-semibold uppercase text-slate-500">
              {label}
            </p>
            <p className="mt-2 text-xl font-bold text-slate-950">
              {label === "Trades" ? value : `${Number(value).toFixed(2)}%`}
            </p>
          </CardContent>
        </Card>
      ))}
    </div>
  );
}

function ChartCard({
  title,
  data,
  kind,
}: {
  title: string;
  data: { label: string; value: number }[];
  kind: "line" | "bar";
}) {
  return (
    <Card className="rounded-lg">
      <CardHeader>
        <CardTitle className="text-base text-slate-950">{title}</CardTitle>
      </CardHeader>
      <CardContent>
        <div className="h-72">
          <ResponsiveContainer width="100%" height="100%">
            {kind === "line" ? (
              <LineChart
                data={data}
                margin={{ top: 8, right: 8, left: -12, bottom: 0 }}
              >
                <CartesianGrid
                  stroke="#e2e8f0"
                  strokeDasharray="4 4"
                  vertical={false}
                />
                <XAxis
                  dataKey="label"
                  tick={{ fill: "#64748b", fontSize: 12 }}
                  tickLine={false}
                  axisLine={false}
                />
                <YAxis
                  tick={{ fill: "#64748b", fontSize: 12 }}
                  tickLine={false}
                  axisLine={false}
                />
                <Tooltip />
                <Line
                  dataKey="value"
                  dot={false}
                  stroke="#2563eb"
                  strokeWidth={2.5}
                  type="monotone"
                />
              </LineChart>
            ) : (
              <BarChart
                data={data}
                margin={{ top: 8, right: 8, left: -12, bottom: 0 }}
              >
                <CartesianGrid
                  stroke="#e2e8f0"
                  strokeDasharray="4 4"
                  vertical={false}
                />
                <XAxis
                  dataKey="label"
                  tick={{ fill: "#64748b", fontSize: 12 }}
                  tickLine={false}
                  axisLine={false}
                />
                <YAxis
                  tick={{ fill: "#64748b", fontSize: 12 }}
                  tickLine={false}
                  axisLine={false}
                />
                <Tooltip />
                <Bar dataKey="value" fill="#0f766e" radius={[4, 4, 0, 0]} />
              </BarChart>
            )}
          </ResponsiveContainer>
        </div>
      </CardContent>
    </Card>
  );
}

function TradeTable({ job }: { job: BacktestJob }) {
  const trades = job.result?.trades ?? [];
  return (
    <Card className="rounded-lg">
      <CardHeader>
        <CardTitle className="text-base text-slate-950">Trades</CardTitle>
      </CardHeader>
      <CardContent>
        <div className="overflow-x-auto">
          <table className="w-full min-w-[720px] text-left text-sm">
            <thead className="border-b border-slate-200 text-xs uppercase text-slate-500">
              <tr>
                <th className="py-2 pr-3">Date</th>
                <th className="py-2 pr-3">Side</th>
                <th className="py-2 pr-3">Price</th>
                <th className="py-2 pr-3">Quantity</th>
                <th className="py-2 pr-3">Cash</th>
                <th className="py-2 pr-3">Value</th>
                <th className="py-2">Reason</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 text-slate-700">
              {trades.map((trade, index) => (
                <tr key={`${trade.date}-${trade.side}-${index}`}>
                  <td className="py-2 pr-3">{trade.date}</td>
                  <td className="py-2 pr-3 font-semibold">{trade.side}</td>
                  <td className="py-2 pr-3">{formatNumber(trade.price)}</td>
                  <td className="py-2 pr-3">{formatNumber(trade.quantity)}</td>
                  <td className="py-2 pr-3">{formatNumber(trade.cashAfter)}</td>
                  <td className="py-2 pr-3">
                    {formatNumber(trade.portfolioValueAfter)}
                  </td>
                  <td className="py-2">{trade.reason}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </CardContent>
    </Card>
  );
}

function defaultCondition(type: CustomConditionType): StrategyCondition {
  return {
    type,
    period: PERIOD_CONDITIONS.has(type) ? 20 : undefined,
    value: defaultValue(type),
  };
}

function defaultValue(type: CustomConditionType) {
  if (type === "RSI_LESS_THAN") return 30;
  if (type === "RSI_GREATER_THAN") return 70;
  if (type === "STOP_LOSS") return -5;
  if (type === "TAKE_PROFIT") return 10;
  if (type === "TRAILING_STOP") return 5;
  if (type === "VOLUME_SPIKE") return 2;
  if (type === "MA_DISCOUNT") return -5;
  if (type === "MA_PREMIUM") return 5;
  return undefined;
}

function numberOrUndefined(value: string) {
  if (value.trim() === "") return undefined;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : undefined;
}

function formatNumber(value: number) {
  return new Intl.NumberFormat("en-US", { maximumFractionDigits: 4 }).format(
    Number(value),
  );
}
