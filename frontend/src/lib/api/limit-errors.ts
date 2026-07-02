export const USAGE_LIMIT_EVENT = "finrisk:usage-limit";

const LIMIT_MESSAGES: Record<string, string> = {
  USAGE_001: "이번 달 백테스트 사용 한도를 모두 사용했습니다.",
  USAGE_002: "이번 달 위험 리포트 사용 한도를 모두 사용했습니다.",
  USAGE_003: "이번 달 AI Agent 질문 한도를 모두 사용했습니다.",
  USAGE_004: "FREE 플랜은 관심 자산을 최대 5개까지 등록할 수 있습니다.",
};

export function usageLimitMessage(code?: string) {
  return code ? LIMIT_MESSAGES[code] : undefined;
}

export function announceUsageLimit(code?: string) {
  const message = usageLimitMessage(code);
  if (message && typeof window !== "undefined") {
    window.dispatchEvent(
      new CustomEvent<string>(USAGE_LIMIT_EVENT, { detail: message }),
    );
  }
}
