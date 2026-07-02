import { describe, expect, it } from "vitest";

import {
  clearTokens,
  getTokens,
  storeTokens,
  updateAccessToken,
} from "@/lib/auth/token-storage";

describe("token storage", () => {
  it("stores both tokens in sessionStorage and updates only access token", () => {
    storeTokens({ accessToken: "access-one", refreshToken: "refresh-one" });
    updateAccessToken("access-two");

    expect(getTokens()).toEqual({
      accessToken: "access-two",
      refreshToken: "refresh-one",
    });
  });

  it("clears malformed and explicit token state", () => {
    window.sessionStorage.setItem("finrisk-radar.auth.tokens", "invalid-json");
    expect(getTokens()).toBeNull();

    storeTokens({ accessToken: "access", refreshToken: "refresh" });
    clearTokens();
    expect(getTokens()).toBeNull();
  });
});
