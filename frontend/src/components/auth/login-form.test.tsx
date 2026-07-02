import { StrictMode } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { LoginForm } from "@/components/auth/login-form";
import { useAuthStore } from "@/store/auth-store";

const mocks = vi.hoisted(() => ({
  replace: vi.fn(),
  login: vi.fn(),
  exchangeOAuthCode: vi.fn(),
  establishSession: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: mocks.replace }),
  useSearchParams: () => new URLSearchParams(window.location.search),
}));

vi.mock("@/lib/api/auth", () => ({
  login: mocks.login,
  exchangeOAuthCode: mocks.exchangeOAuthCode,
  getAuthErrorMessage: (_error: unknown, fallback: string) => fallback,
}));

vi.mock("@/lib/auth/session", () => ({
  establishSession: mocks.establishSession,
}));

const authResponse = {
  id: 1,
  email: "user@example.com",
  name: "User",
  role: "ROLE_USER",
  accessToken: "access-token",
  refreshToken: "refresh-token",
};

describe("LoginForm", () => {
  beforeEach(() => {
    Object.values(mocks).forEach((mock) => mock.mockReset());
    useAuthStore.getState().setAnonymous();
    window.history.replaceState(null, "", "/login");
    mocks.establishSession.mockResolvedValue(authResponse);
  });

  it("logs in with email and navigates to the dashboard", async () => {
    mocks.login.mockResolvedValue(authResponse);
    const user = userEvent.setup();
    render(<LoginForm />);

    await user.type(screen.getByLabelText("이메일"), "user@example.com");
    await user.type(screen.getByLabelText("비밀번호"), "password123");
    await user.click(screen.getByRole("button", { name: "로그인" }));

    await waitFor(() =>
      expect(mocks.login).toHaveBeenCalledWith({
        email: "user@example.com",
        password: "password123",
      }),
    );
    expect(mocks.establishSession).toHaveBeenCalledWith(authResponse);
    expect(mocks.replace).toHaveBeenCalledWith("/dashboard");
  });

  it("exchanges an OAuth code once under Strict Mode and removes it from the URL", async () => {
    window.history.replaceState(null, "", "/login?oauthCode=one-time-code");
    mocks.exchangeOAuthCode.mockResolvedValue(authResponse);

    render(
      <StrictMode>
        <LoginForm />
      </StrictMode>,
    );

    await waitFor(() => expect(mocks.exchangeOAuthCode).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(mocks.replace).toHaveBeenCalledWith("/dashboard"));
    expect(window.location.pathname).toBe("/login");
    expect(window.location.search).toBe("");
  });
});
