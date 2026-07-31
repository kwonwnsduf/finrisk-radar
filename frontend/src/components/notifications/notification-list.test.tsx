import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { NotificationList } from "@/components/notifications/notification-list";
import {
  getNotifications,
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
    markNotificationRead: vi.fn(),
    markAllNotificationsRead: vi.fn(),
  };
});

describe("NotificationList", () => {
  it("renders the empty state", async () => {
    vi.mocked(getNotifications).mockResolvedValue({
      items: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    });
    vi.mocked(markNotificationRead).mockRejectedValue(new Error("unused"));
    vi.mocked(markAllNotificationsRead).mockResolvedValue({ updatedCount: 0 });
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    });

    render(
      <QueryClientProvider client={client}>
        <NotificationList />
      </QueryClientProvider>,
    );

    expect(await screen.findByText("No notifications")).toBeInTheDocument();
  });

  it("does not block navigation when marking a notification read fails", async () => {
    vi.mocked(getNotifications).mockResolvedValue({
      items: [
        {
          id: 3,
          type: "REPORT_COMPLETED",
          title: "AI report completed",
          message: "Your AI report is ready.",
          referenceType: "AI_REPORT",
          referenceId: "report-1",
          targetUrl: "/reports/report-1",
          isRead: false,
          createdAt: "2026-07-28T12:00:00",
          readAt: null,
        },
      ],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    });
    vi.mocked(markNotificationRead).mockRejectedValue(new Error("network"));
    const client = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    });
    const user = userEvent.setup();

    render(
      <QueryClientProvider client={client}>
        <NotificationList />
      </QueryClientProvider>,
    );
    await user.click(await screen.findByText("AI report completed"));

    expect(push).toHaveBeenCalledWith("/reports/report-1");
  });
});
