import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { SignupForm } from "@/components/auth/signup-form";

const mocks = vi.hoisted(() => ({ replace: vi.fn(), signup: vi.fn() }));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: mocks.replace }),
}));

vi.mock("@/lib/api/auth", () => ({
  signup: mocks.signup,
  getAuthErrorMessage: (_error: unknown, fallback: string) => fallback,
}));

describe("SignupForm", () => {
  beforeEach(() => {
    mocks.replace.mockReset();
    mocks.signup.mockReset();
  });

  it("creates an account and returns to login", async () => {
    mocks.signup.mockResolvedValue({ id: 1 });
    const user = userEvent.setup();
    render(<SignupForm />);

    await user.type(screen.getByLabelText("이름"), "Fin User");
    await user.type(screen.getByLabelText("이메일"), "user@example.com");
    await user.type(screen.getByLabelText("비밀번호"), "password123");
    await user.click(screen.getByRole("button", { name: "회원가입" }));

    await waitFor(() =>
      expect(mocks.signup).toHaveBeenCalledWith({
        name: "Fin User",
        email: "user@example.com",
        password: "password123",
      }),
    );
    expect(mocks.replace).toHaveBeenCalledWith("/login?registered=1");
  });
});
