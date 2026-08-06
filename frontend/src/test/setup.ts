import "@testing-library/jest-dom/vitest";

import { cleanup } from "@testing-library/react";
import { afterEach } from "vitest";

import { clearTokens } from "@/lib/auth/token-storage";
import { useAuthStore } from "@/store/auth-store";
import { useUiStore } from "@/store/ui-store";

afterEach(() => {
  cleanup();
  clearTokens();
  useAuthStore.getState().setAnonymous();
  useUiStore.setState({
    sidebarCollapsed: false,
    mobileSidebarOpen: false,
  });
  window.history.replaceState(null, "", "/");
});
