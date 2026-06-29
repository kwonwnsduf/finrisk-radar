"use client";

import { useQuery } from "@tanstack/react-query";
import { Activity } from "lucide-react";

import { DashboardCard } from "@/components/common/dashboard-card";
import { getBackendHealth } from "@/lib/api/health";

export function BackendHealthCard() {
  const { data, isPending, isError } = useQuery({
    queryKey: ["backend-health"],
    queryFn: getBackendHealth,
    refetchInterval: 15_000,
  });

  const isUp = data?.success === true && data.data === "UP";
  const value = isPending
    ? "확인 중"
    : isUp
      ? "정상"
      : isError
        ? "연결 확인 불가"
        : "점검 필요";

  return (
    <DashboardCard
      title="백엔드 상태"
      value={value}
      description="15초마다 상태를 확인합니다"
      icon={<Activity className="size-5" aria-hidden="true" />}
    />
  );
}
