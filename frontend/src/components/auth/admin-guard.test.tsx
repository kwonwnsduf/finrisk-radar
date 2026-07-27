import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { AdminGuard } from "@/components/auth/admin-guard";
import { useAuthStore } from "@/store/auth-store";

const replace = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace }),
}));

describe("AdminGuard", () => {
  beforeEach(() => {
    replace.mockReset();
  });

  it("renders the console for an authenticated administrator", () => {
    useAuthStore.getState().setAuthenticated({
      id: 1,
      email: "admin@example.com",
      name: "Admin",
      role: "ROLE_ADMIN",
    });

    render(
      <AdminGuard>
        <div data-testid="admin-content" />
      </AdminGuard>,
    );

    expect(screen.getByTestId("admin-content")).toBeInTheDocument();
  });

  it("does not render the console for a regular user", () => {
    useAuthStore.getState().setAuthenticated({
      id: 2,
      email: "user@example.com",
      name: "User",
      role: "ROLE_USER",
    });

    render(
      <AdminGuard>
        <div data-testid="admin-content" />
      </AdminGuard>,
    );

    expect(screen.queryByTestId("admin-content")).not.toBeInTheDocument();
  });

  it("redirects an anonymous visitor to login", async () => {
    useAuthStore.getState().setAnonymous();

    render(
      <AdminGuard>
        <div data-testid="admin-content" />
      </AdminGuard>,
    );

    await waitFor(() => expect(replace).toHaveBeenCalledWith("/login"));
  });
});
