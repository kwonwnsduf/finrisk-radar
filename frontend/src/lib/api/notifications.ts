import { apiClient } from "@/lib/api/client";
import type { ApiResponse } from "@/lib/auth/types";

export type NotificationType =
  | "BACKTEST_COMPLETED"
  | "BACKTEST_FAILED"
  | "REPORT_COMPLETED"
  | "REPORT_FAILED"
  | "HIGH_RISK_SIGNAL_DETECTED"
  | "PAYMENT_COMPLETED"
  | "PAYMENT_CANCELED"
  | "PAYMENT_FAILED"
  | "FSD_REVIEW_REQUIRED"
  | "PAYMENT_RECOVERY_REQUIRED";

export type NotificationReferenceType =
  | "BACKTEST"
  | "AI_REPORT"
  | "ASSET"
  | "PAYMENT_ORDER"
  | "FSD_EVENT";

export interface NotificationItem {
  id: number;
  type: NotificationType;
  title: string;
  message: string;
  referenceType: NotificationReferenceType;
  referenceId: string;
  targetUrl: string | null;
  isRead: boolean;
  createdAt: string;
  readAt: string | null;
}

export interface NotificationPage {
  items: NotificationItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface NotificationFilters {
  read?: boolean;
  type?: NotificationType;
  page?: number;
  size?: number;
}

export const notificationKeys = {
  all: ["notifications"] as const,
  list: (filters: NotificationFilters) => ["notifications", filters] as const,
  unreadCount: ["notification-unread-count"] as const,
};

export async function getNotifications(filters: NotificationFilters = {}) {
  const response = await apiClient.get<ApiResponse<NotificationPage>>(
    "/api/notifications",
    { params: filters },
  );
  return response.data.data;
}

export async function getUnreadNotificationCount() {
  const response = await apiClient.get<ApiResponse<{ count: number }>>(
    "/api/notifications/unread-count",
  );
  return response.data.data;
}

export async function markNotificationRead(notificationId: number) {
  const response = await apiClient.patch<ApiResponse<NotificationItem>>(
    `/api/notifications/${notificationId}/read`,
  );
  return response.data.data;
}

export async function markAllNotificationsRead() {
  const response = await apiClient.patch<ApiResponse<{ updatedCount: number }>>(
    "/api/notifications/read-all",
  );
  return response.data.data;
}
