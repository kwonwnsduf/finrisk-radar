"use client";

import { useEffect, type ReactNode } from "react";

import { getCurrentUser } from "@/lib/api/users";
import { clearAuthSession } from "@/lib/auth/session";
import { getTokens } from "@/lib/auth/token-storage";
import { useAuthStore } from "@/store/auth-store";

export function AuthProvider({ children }: { children: ReactNode }) {
  const setAuthenticated = useAuthStore((state) => state.setAuthenticated);
  const setAnonymous = useAuthStore((state) => state.setAnonymous);
  const setInitializing = useAuthStore((state) => state.setInitializing);

  useEffect(() => {
    let active = true;
    const tokens = getTokens();
    if (!tokens) {
      setAnonymous();
      return () => {
        active = false;
      };
    }

    setInitializing();
    void getCurrentUser()
      .then((user) => {
        if (active) {
          setAuthenticated(user);
        }
      })
      .catch(() => {
        if (active) {
          clearAuthSession();
        }
      });

    return () => {
      active = false;
    };
  }, [setAnonymous, setAuthenticated, setInitializing]);

  return children;
}
