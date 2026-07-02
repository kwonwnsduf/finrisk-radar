import { create } from "zustand";

import type { AuthUser } from "@/lib/auth/types";

export type AuthStatus = "initializing" | "authenticated" | "anonymous";

interface AuthState {
  status: AuthStatus;
  user: AuthUser | null;
  setInitializing: () => void;
  setAuthenticated: (user: AuthUser) => void;
  setAnonymous: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  status: "initializing",
  user: null,
  setInitializing: () => set({ status: "initializing", user: null }),
  setAuthenticated: (user) => set({ status: "authenticated", user }),
  setAnonymous: () => set({ status: "anonymous", user: null }),
}));
