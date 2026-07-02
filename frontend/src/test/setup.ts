import "@testing-library/jest-dom/vitest";

import { cleanup } from "@testing-library/react";
import { afterEach } from "vitest";

import { clearTokens } from "@/lib/auth/token-storage";
import { useAuthStore } from "@/store/auth-store";

afterEach(() => {
  cleanup();
  clearTokens();
  useAuthStore.getState().setAnonymous();
  window.history.replaceState(null, "", "/");
});
