import { act, fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { Sidebar } from "@/components/layout/sidebar";
import { useUiStore } from "@/store/ui-store";

vi.mock("next/navigation", () => ({
  usePathname: () => "/dashboard",
}));

describe("Sidebar", () => {
  beforeEach(() => {
    useUiStore.setState({
      sidebarCollapsed: false,
      mobileSidebarOpen: false,
    });
  });

  it("renders the mobile navigation when opened", () => {
    render(<Sidebar />);
    act(() => useUiStore.getState().toggleMobileSidebar());

    expect(
      screen.getByRole("complementary", { name: "Mobile navigation" }),
    ).toBeInTheDocument();
    expect(document.body).toHaveStyle({ overflow: "hidden" });
  });

  it("closes the mobile navigation from its close action", () => {
    render(<Sidebar />);
    act(() => useUiStore.getState().toggleMobileSidebar());

    fireEvent.click(screen.getByRole("button", { name: "Close menu" }));

    expect(useUiStore.getState().mobileSidebarOpen).toBe(false);
    expect(
      screen.queryByRole("complementary", { name: "Mobile navigation" }),
    ).not.toBeInTheDocument();
  });

  it("closes the mobile navigation with Escape", () => {
    render(<Sidebar />);
    act(() => useUiStore.getState().toggleMobileSidebar());

    fireEvent.keyDown(window, { key: "Escape" });

    expect(useUiStore.getState().mobileSidebarOpen).toBe(false);
  });
});
