import MockAdapter from "axios-mock-adapter";
import { beforeEach, describe, expect, it } from "vitest";

import { apiClient, authRefreshClient } from "@/lib/api/client";
import { getTokens, storeTokens } from "@/lib/auth/token-storage";
import { USAGE_LIMIT_EVENT } from "@/lib/api/limit-errors";

const apiMock = new MockAdapter(apiClient);
const refreshMock = new MockAdapter(authRefreshClient);

describe("API client authentication", () => {
  beforeEach(() => {
    apiMock.reset();
    refreshMock.reset();
    window.history.replaceState(null, "", "/login");
  });

  it("attaches the access token to protected requests", async () => {
    storeTokens({ accessToken: "access-token", refreshToken: "refresh-token" });
    apiMock.onGet("/api/users/me").reply((config) => [
      200,
      { authorization: config.headers?.Authorization },
    ]);

    const response = await apiClient.get<{ authorization: string }>(
      "/api/users/me",
    );

    expect(response.data.authorization).toBe("Bearer access-token");
  });

  it("uses one refresh request for concurrent 401 responses and retries both", async () => {
    storeTokens({ accessToken: "expired-access", refreshToken: "refresh-token" });
    apiMock.onGet("/api/users/me").reply((config) => {
      if (config.headers?.Authorization === "Bearer renewed-access") {
        return [200, { ok: true }];
      }
      return [401, { code: "AUTH_003" }];
    });
    refreshMock.onPost("/api/auth/refresh").reply(200, {
      success: true,
      code: "SUCCESS",
      message: "Request succeeded.",
      data: { accessToken: "renewed-access" },
    });

    const [first, second] = await Promise.all([
      apiClient.get("/api/users/me"),
      apiClient.get("/api/users/me"),
    ]);

    expect(first.status).toBe(200);
    expect(second.status).toBe(200);
    expect(refreshMock.history.post).toHaveLength(1);
    expect(getTokens()).toEqual({
      accessToken: "renewed-access",
      refreshToken: "refresh-token",
    });
  });

  it("clears tokens when refresh fails", async () => {
    storeTokens({ accessToken: "expired-access", refreshToken: "refresh-token" });
    apiMock.onGet("/api/users/me").reply(401);
    refreshMock.onPost("/api/auth/refresh").reply(401);

    await expect(apiClient.get("/api/users/me")).rejects.toBeDefined();

    expect(getTokens()).toBeNull();
    expect(refreshMock.history.post).toHaveLength(1);
  });

  it("does not refresh authentication endpoint failures", async () => {
    storeTokens({ accessToken: "access-token", refreshToken: "refresh-token" });
    apiMock.onPost("/api/auth/login").reply(401);

    await expect(apiClient.post("/api/auth/login", {})).rejects.toBeDefined();

    expect(refreshMock.history.post).toHaveLength(0);
  });

  it("announces a usage limit error without attempting token refresh", async () => {
    let message = "";
    window.addEventListener(USAGE_LIMIT_EVENT, (event) => {
      message = (event as CustomEvent<string>).detail;
    }, { once: true });
    apiMock.onPost("/api/day3/stubs/backtests").reply(429, { code: "USAGE_001" });

    await expect(apiClient.post("/api/day3/stubs/backtests")).rejects.toBeDefined();

    expect(message).toContain("백테스트");
    expect(refreshMock.history.post).toHaveLength(0);
  });
});
