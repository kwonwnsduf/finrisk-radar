"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Bell, CheckCheck } from "lucide-react";
import { useRouter } from "next/navigation";
import { useState } from "react";

import { Button } from "@/components/ui/button";
import {
  getNotifications,
  getUnreadNotificationCount,
  markAllNotificationsRead,
  markNotificationRead,
  notificationKeys,
  type NotificationItem,
  type NotificationPage,
} from "@/lib/api/notifications";

const RECENT_FILTER = { page: 0, size: 5 } as const;

export function NotificationBell() {
  const [open, setOpen] = useState(false);
  const router = useRouter();
  const queryClient = useQueryClient();
  const count = useQuery({
    queryKey: notificationKeys.unreadCount,
    queryFn: getUnreadNotificationCount,
    refetchInterval: 30_000,
    refetchOnWindowFocus: true,
    refetchOnReconnect: true,
  });
  const recent = useQuery({
    queryKey: notificationKeys.list(RECENT_FILTER),
    queryFn: () => getNotifications(RECENT_FILTER),
    enabled: open,
  });

  function invalidateNotifications() {
    void queryClient.invalidateQueries({ queryKey: notificationKeys.all });
    void queryClient.invalidateQueries({
      queryKey: notificationKeys.unreadCount,
    });
  }

  const read = useMutation({
    mutationFn: markNotificationRead,
    onMutate: async (id) => {
      await queryClient.cancelQueries({ queryKey: notificationKeys.unreadCount });
      const previousCount = queryClient.getQueryData<{ count: number }>(
        notificationKeys.unreadCount,
      );
      const previousList = queryClient.getQueryData<NotificationPage>(
        notificationKeys.list(RECENT_FILTER),
      );
      const target = previousList?.items.find((item) => item.id === id);
      if (target && !target.isRead) {
        queryClient.setQueryData(notificationKeys.unreadCount, {
          count: Math.max(0, (previousCount?.count ?? 0) - 1),
        });
        queryClient.setQueryData<NotificationPage>(
          notificationKeys.list(RECENT_FILTER),
          (value) =>
            value
              ? {
                  ...value,
                  items: value.items.map((item) =>
                    item.id === id ? { ...item, isRead: true } : item,
                  ),
                }
              : value,
        );
      }
      return { previousCount, previousList };
    },
    onError: (_error, _id, context) => {
      if (context?.previousCount)
        queryClient.setQueryData(notificationKeys.unreadCount, context.previousCount);
      if (context?.previousList)
        queryClient.setQueryData(
          notificationKeys.list(RECENT_FILTER),
          context.previousList,
        );
    },
    onSettled: invalidateNotifications,
  });

  const readAll = useMutation({
    mutationFn: markAllNotificationsRead,
    onMutate: async () => {
      await queryClient.cancelQueries({ queryKey: notificationKeys.unreadCount });
      const previous = queryClient.getQueryData<{ count: number }>(
        notificationKeys.unreadCount,
      );
      queryClient.setQueryData(notificationKeys.unreadCount, { count: 0 });
      return { previous };
    },
    onError: (_error, _variables, context) => {
      if (context?.previous)
        queryClient.setQueryData(notificationKeys.unreadCount, context.previous);
    },
    onSettled: invalidateNotifications,
  });

  function openNotification(notification: NotificationItem) {
    if (!notification.isRead) read.mutate(notification.id);
    setOpen(false);
    if (notification.targetUrl?.startsWith("/")) router.push(notification.targetUrl);
  }

  const unread = count.data?.count ?? 0;
  return (
    <div className="relative">
      <Button
        type="button"
        size="icon"
        variant="ghost"
        aria-label="Notifications"
        aria-expanded={open}
        onClick={() => setOpen((value) => !value)}
      >
        <Bell className="size-5" />
        {unread > 0 ? (
          <span className="absolute right-0 top-0 flex min-w-5 -translate-y-1/4 translate-x-1/4 items-center justify-center rounded-full bg-red-600 px-1 text-[11px] font-bold leading-5 text-white">
            {unread > 99 ? "99+" : unread}
          </span>
        ) : null}
      </Button>
      {open ? (
        <div className="absolute right-0 z-50 mt-2 w-[min(24rem,calc(100vw-2rem))] overflow-hidden rounded-xl border border-slate-200 bg-white shadow-xl">
          <div className="flex items-center justify-between border-b border-slate-100 px-4 py-3">
            <div>
              <p className="font-bold text-slate-950">Notifications</p>
              <p className="text-xs text-slate-500">{unread} unread</p>
            </div>
            <Button
              type="button"
              size="sm"
              variant="ghost"
              disabled={unread === 0 || readAll.isPending}
              onClick={() => readAll.mutate()}
            >
              <CheckCheck className="size-4" />
              Mark all read
            </Button>
          </div>
          <div className="max-h-96 overflow-y-auto">
            {recent.isPending ? (
              <p className="px-4 py-8 text-center text-sm text-slate-500">
                Loading notifications…
              </p>
            ) : recent.isError ? (
              <div className="px-4 py-6 text-center">
                <p className="text-sm text-red-600">Notifications could not be loaded.</p>
                <Button
                  type="button"
                  size="sm"
                  variant="outline"
                  className="mt-3"
                  onClick={() => void recent.refetch()}
                >
                  Retry
                </Button>
              </div>
            ) : recent.data?.items.length ? (
              recent.data.items.map((notification) => (
                <button
                  key={notification.id}
                  type="button"
                  className="block w-full border-b border-slate-100 px-4 py-3 text-left hover:bg-slate-50"
                  onClick={() => openNotification(notification)}
                >
                  <div className="flex gap-3">
                    <span
                      className={`mt-1.5 size-2 shrink-0 rounded-full ${
                        notification.isRead ? "bg-slate-300" : "bg-blue-600"
                      }`}
                    />
                    <span className="min-w-0">
                      <span className="block text-sm font-semibold text-slate-900">
                        {notification.title}
                      </span>
                      <span className="mt-1 block text-xs text-slate-600">
                        {notification.message}
                      </span>
                      <span className="mt-1 block text-[11px] text-slate-400">
                        {new Date(notification.createdAt).toLocaleString()}
                      </span>
                    </span>
                  </div>
                </button>
              ))
            ) : (
              <p className="px-4 py-10 text-center text-sm text-slate-500">
                No notifications yet.
              </p>
            )}
          </div>
          <button
            type="button"
            className="w-full px-4 py-3 text-sm font-semibold text-blue-700 hover:bg-blue-50"
            onClick={() => {
              setOpen(false);
              router.push("/notifications");
            }}
          >
            View all notifications
          </button>
        </div>
      ) : null}
    </div>
  );
}
