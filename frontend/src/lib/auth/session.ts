import { getCurrentUser } from "@/lib/api/users";
import { clearTokens, storeTokens } from "@/lib/auth/token-storage";
import type { AuthResponse } from "@/lib/auth/types";
import { useAuthStore } from "@/store/auth-store";

export async function establishSession(response: AuthResponse) {
  storeTokens({
    accessToken: response.accessToken,
    refreshToken: response.refreshToken,
  });

  try {
    const user = await getCurrentUser();
    useAuthStore.getState().setAuthenticated(user);
    return user;
  } catch (error) {
    clearAuthSession();
    throw error;
  }
}

export function clearAuthSession() {
  clearTokens();
  useAuthStore.getState().setAnonymous();
}
