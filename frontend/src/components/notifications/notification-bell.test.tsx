import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { NotificationBell } from "@/components/notifications/notification-bell";
import {
  getNotifications,
  getUnreadNotificationCount,
  markAllNotificationsRead,
  markNotificationRead,
} from "@/lib/api/notifications";

const push = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ push }) }));
vi.mock("@/lib/api/notifications", async (importOriginal) => {
  const actual =
    await importOriginal<typeof import("@/lib/api/notifications")>();
  return {
    ...actual,
    getNotifications: vi.fn(),
    getUnreadNotificationCount: vi.fn(),
    markNotificationRead: vi.fn(),
    markAllNotificationsRead: vi.fn(),
  };
});

describe("NotificationBell", () => {
  it("shows unread count and opens a linked notification", async () => {
    vi.mocked(getUnreadNotificationCount).mockResolvedValue({ count: 2 });
    vi.mocked(getNotifications).mockResolvedValue({
      items: [
        {
          id: 11,
          type: "BACKTEST_COMPLETED",
          title: "Backtest completed",
          message: "Your backtest has completed.",
          referenceType: "BACKTEST",
          referenceId: "job-1",
          targetUrl: "/backtests?jobId=job-1",
          isRead: false,
          createdAt: "2026-07-28T12:00:00",
          readAt: null,
        },
      ],
      page: 0,
      size: 5,
      totalElements: 1,
      totalPages: 1,
    });
    vi.mocked(markNotificationRead).mockResolvedValue({
      id: 11,
      type: "BACKTEST_COMPLETED",
      title: "Backtest completed",
      message: "Your backtest has completed.",
      referenceType: "BACKTEST",
      referenceId: "job-1",
      targetUrl: "/backtests?jobId=job-1",
      isRead: true,
      createdAt: "2026-07-28T12:00:00",
      readAt: "2026-07-28T12:01:00",
    });
    vi.mocked(markAllNotificationsRead).mockResolvedValue({ updatedCount: 0 });
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    });
    const user = userEvent.setup();

    render(
      <QueryClientProvider client={client}>
        <NotificationBell />
      </QueryClientProvider>,
    );

    expect(await screen.findByText("2")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Notifications" }));
    await user.click(await screen.findByText("Backtest completed"));

    expect(vi.mocked(markNotificationRead).mock.calls[0]?.[0]).toBe(11);
    expect(push).toHaveBeenCalledWith("/backtests?jobId=job-1");
  });
});
