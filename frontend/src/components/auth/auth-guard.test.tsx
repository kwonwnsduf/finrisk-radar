import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { AuthGuard } from "@/components/auth/auth-guard";
import { useAuthStore } from "@/store/auth-store";

const replace = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace }),
}));

describe("AuthGuard", () => {
  beforeEach(() => replace.mockReset());

  it("renders protected content for authenticated users", () => {
    useAuthStore.getState().setAuthenticated({
      id: 1,
      email: "user@example.com",
      name: "User",
      role: "ROLE_USER",
    });

    render(<AuthGuard><div>protected</div></AuthGuard>);

    expect(screen.getByText("protected")).toBeInTheDocument();
  });

  it("redirects anonymous users to login", async () => {
    useAuthStore.getState().setAnonymous();

    render(<AuthGuard><div>protected</div></AuthGuard>);

    await waitFor(() => expect(replace).toHaveBeenCalledWith("/login"));
    expect(screen.queryByText("protected")).not.toBeInTheDocument();
  });
});
