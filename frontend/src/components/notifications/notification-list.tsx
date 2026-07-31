"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CheckCheck } from "lucide-react";
import { useRouter } from "next/navigation";
import { useState } from "react";

import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import {
  getNotifications,
  markAllNotificationsRead,
  markNotificationRead,
  notificationKeys,
  type NotificationItem,
  type NotificationType,
} from "@/lib/api/notifications";

const TYPES: NotificationType[] = [
  "BACKTEST_COMPLETED",
  "BACKTEST_FAILED",
  "REPORT_COMPLETED",
  "REPORT_FAILED",
  "HIGH_RISK_SIGNAL_DETECTED",
  "PAYMENT_COMPLETED",
  "PAYMENT_CANCELED",
  "PAYMENT_FAILED",
  "FSD_REVIEW_REQUIRED",
  "PAYMENT_RECOVERY_REQUIRED",
];

export function NotificationList() {
  const [readFilter, setReadFilter] = useState<"ALL" | "UNREAD" | "READ">("ALL");
  const [type, setType] = useState<NotificationType | "">("");
  const [page, setPage] = useState(0);
  const router = useRouter();
  const queryClient = useQueryClient();
  const filters = {
    read: readFilter === "ALL" ? undefined : readFilter === "READ",
    type: type || undefined,
    page,
    size: 20,
  };
  const query = useQuery({
    queryKey: notificationKeys.list(filters),
    queryFn: () => getNotifications(filters),
  });

  function invalidate() {
    void queryClient.invalidateQueries({ queryKey: notificationKeys.all });
    void queryClient.invalidateQueries({ queryKey: notificationKeys.unreadCount });
  }

  const read = useMutation({ mutationFn: markNotificationRead, onSettled: invalidate });
  const readAll = useMutation({
    mutationFn: markAllNotificationsRead,
    onSuccess: invalidate,
  });

  function openNotification(notification: NotificationItem) {
    if (!notification.isRead) read.mutate(notification.id);
    if (notification.targetUrl?.startsWith("/")) router.push(notification.targetUrl);
  }

  return (
    <div className="space-y-5">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold text-slate-950">Notifications</h1>
          <p className="mt-1 text-sm text-slate-500">
            Updates from your jobs, risk calculations, and payments
          </p>
        </div>
        <Button
          type="button"
          variant="outline"
          disabled={readAll.isPending}
          onClick={() => readAll.mutate()}
        >
          <CheckCheck className="size-4" />
          Mark all read
        </Button>
      </div>

      <Card>
        <CardContent className="grid gap-3 p-4 sm:grid-cols-2">
          <select
            className="h-10 rounded-md border border-slate-200 bg-white px-3 text-sm"
            value={readFilter}
            onChange={(event) => {
              setReadFilter(event.target.value as typeof readFilter);
              setPage(0);
            }}
          >
            <option value="ALL">All states</option>
            <option value="UNREAD">Unread</option>
            <option value="READ">Read</option>
          </select>
          <select
            className="h-10 rounded-md border border-slate-200 bg-white px-3 text-sm"
            value={type}
            onChange={(event) => {
              setType(event.target.value as NotificationType | "");
              setPage(0);
            }}
          >
            <option value="">All types</option>
            {TYPES.map((value) => (
              <option key={value} value={value}>
                {value.replaceAll("_", " ")}
              </option>
            ))}
          </select>
        </CardContent>
      </Card>

      {query.isPending ? (
        <Card>
          <CardContent className="py-12 text-center text-sm text-slate-500">
            Loading notifications…
          </CardContent>
        </Card>
      ) : query.isError ? (
        <Card>
          <CardContent className="py-12 text-center">
            <p className="text-sm text-red-600">Notifications could not be loaded.</p>
            <Button
              type="button"
              variant="outline"
              className="mt-4"
              onClick={() => void query.refetch()}
            >
              Retry
            </Button>
          </CardContent>
        </Card>
      ) : query.data.items.length === 0 ? (
        <Card>
          <CardContent className="py-16 text-center">
            <p className="font-semibold text-slate-800">No notifications</p>
            <p className="mt-1 text-sm text-slate-500">
              New status updates will appear here.
            </p>
          </CardContent>
        </Card>
      ) : (
        <Card>
          <CardContent className="divide-y divide-slate-100 p-0">
            {query.data.items.map((notification) => (
              <button
                key={notification.id}
                type="button"
                className="flex w-full gap-4 px-5 py-4 text-left hover:bg-slate-50"
                onClick={() => openNotification(notification)}
              >
                <span
                  className={`mt-2 size-2.5 shrink-0 rounded-full ${
                    notification.isRead ? "bg-slate-300" : "bg-blue-600"
                  }`}
                />
                <span className="min-w-0 flex-1">
                  <span className="flex flex-col justify-between gap-1 sm:flex-row">
                    <span className="font-semibold text-slate-900">
                      {notification.title}
                    </span>
                    <span className="text-xs text-slate-400">
                      {new Date(notification.createdAt).toLocaleString()}
                    </span>
                  </span>
                  <span className="mt-1 block text-sm text-slate-600">
                    {notification.message}
                  </span>
                </span>
              </button>
            ))}
          </CardContent>
        </Card>
      )}

      {query.data && query.data.totalPages > 1 ? (
        <div className="flex items-center justify-center gap-3">
          <Button
            type="button"
            variant="outline"
            disabled={page === 0}
            onClick={() => setPage((value) => Math.max(0, value - 1))}
          >
            Previous
          </Button>
          <span className="text-sm text-slate-600">
            {page + 1} / {query.data.totalPages}
          </span>
          <Button
            type="button"
            variant="outline"
            disabled={page + 1 >= query.data.totalPages}
            onClick={() => setPage((value) => value + 1)}
          >
            Next
          </Button>
        </div>
      ) : null}
    </div>
  );
}
