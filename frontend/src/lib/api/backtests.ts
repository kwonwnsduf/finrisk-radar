import { apiClient } from "@/lib/api/client";
import type { ApiResponse } from "@/lib/auth/types";

export type StrategyType =
  | "BUY_AND_HOLD"
  | "MOVING_AVERAGE"
  | "RSI"
  | "BOLLINGER_BAND"
  | "MACD"
  | "VOLATILITY_BREAKOUT"
  | "DCA"
  | "MA_DEVIATION"
  | "DONCHIAN_CHANNEL"
  | "MOMENTUM"
  | "CUSTOM";

export type CustomConditionType =
  | "RSI_LESS_THAN"
  | "PRICE_ABOVE_MA"
  | "MA_CROSS_UP"
  | "BOLLINGER_LOWER_TOUCH"
  | "MACD_GOLDEN_CROSS"
  | "VOLUME_SPIKE"
  | "BREAKOUT"
  | "MA_DISCOUNT"
  | "DONCHIAN_HIGH_BREAKOUT"
  | "MOMENTUM_POSITIVE"
  | "RSI_GREATER_THAN"
  | "PRICE_BELOW_MA"
  | "MA_CROSS_DOWN"
  | "BOLLINGER_UPPER_TOUCH"
  | "MACD_DEAD_CROSS"
  | "STOP_LOSS"
  | "TAKE_PROFIT"
  | "TRAILING_STOP"
  | "MA_PREMIUM"
  | "DONCHIAN_LOW_BREAKDOWN"
  | "MOMENTUM_NEGATIVE";

export interface StrategyCondition {
  type: CustomConditionType;
  period?: number;
  shortPeriod?: number;
  longPeriod?: number;
  signalPeriod?: number;
  value?: number;
}

export interface BacktestCreateRequest {
  assetId: number;
  strategyType: StrategyType;
  startDate: string;
  endDate: string;
  initialCash: number;
  buyConditions?: StrategyCondition[];
  sellConditions?: StrategyCondition[];
}

export interface BacktestCreateResponse {
  jobId: string;
  status: BacktestStatus;
}

export type BacktestStatus = "REQUESTED" | "RUNNING" | "COMPLETED" | "FAILED";

export interface DailyPortfolioValue {
  date: string;
  cash: number;
  positionQuantity: number;
  closePrice: number;
  portfolioValue: number;
}

export interface MonthlyReturn {
  month: string;
  returnRate: number;
}

export interface Trade {
  date: string;
  side: "BUY" | "SELL";
  price: number;
  quantity: number;
  cashAfter: number;
  portfolioValueAfter: number;
  reason: string;
}

export interface BacktestResult {
  firstPriceDate: string;
  lastPriceDate: string;
  initialClose: number;
  finalClose: number;
  totalReturnRate: number;
  cagr: number;
  mdd: number;
  winRate: number;
  tradeCount: number;
  sharpeRatio: number;
  benchmarkReturnRate: number;
  monthlyReturns: MonthlyReturn[];
  dailyPortfolioValues: DailyPortfolioValue[];
  trades: Trade[];
}

export interface BacktestJob {
  jobId: string;
  assetId: number;
  strategyType: StrategyType;
  startDate: string;
  endDate: string;
  initialCash: number;
  status: BacktestStatus;
  message: string;
  startedAt: string | null;
  completedAt: string | null;
  result: BacktestResult | null;
}

export async function createBacktest(request: BacktestCreateRequest) {
  const response = await apiClient.post<ApiResponse<BacktestCreateResponse>>(
    "/api/backtests",
    request,
  );
  return response.data.data;
}

export async function getBacktestJob(jobId: string) {
  const response = await apiClient.get<ApiResponse<BacktestJob>>(
    `/api/backtests/${jobId}`,
  );
  return response.data.data;
}

export interface BacktestPage {
  items: BacktestJob[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
export interface NaturalBacktestResponse {
  outcome: "ACCEPTED" | "NEEDS_CLARIFICATION";
  jobId: string | null;
  status: BacktestStatus | null;
  parsedRequest: BacktestCreateRequest | null;
  missingFields: string[];
  assetCandidates: { id: number; name: string; ticker: string }[];
}
export async function getCompletedBacktests() {
  const r = await apiClient.get<ApiResponse<BacktestPage>>("/api/backtests", {
    params: { status: "COMPLETED", size: 50 },
  });
  return r.data.data;
}
export async function createNaturalBacktest(
  question: string,
  assetId?: number,
) {
  const r = await apiClient.post<ApiResponse<NaturalBacktestResponse>>(
    "/api/backtests/natural-language",
    { question, assetId },
  );
  return r.data.data;
}
