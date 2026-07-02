import { act, fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { UsageLimitToast } from "@/components/subscription/usage-limit-toast";
import { USAGE_LIMIT_EVENT } from "@/lib/api/limit-errors";

describe("UsageLimitToast", () => {
  it("shows and dismisses a usage limit message", () => {
    render(<UsageLimitToast />);
    act(() => {
      window.dispatchEvent(new CustomEvent(USAGE_LIMIT_EVENT, { detail: "한도 안내" }));
    });

    expect(screen.getByRole("alert")).toHaveTextContent("한도 안내");
    fireEvent.click(screen.getByRole("button", { name: "안내 닫기" }));
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });
});
