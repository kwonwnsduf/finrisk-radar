import AxiosMockAdapter from "axios-mock-adapter";
import { afterEach, describe, expect, it } from "vitest";

import {
  getAdminIssues,
  getAdminPayment,
  getAdminUsers,
} from "@/lib/api/admin";
import { apiClient } from "@/lib/api/client";

const mock = new AxiosMockAdapter(apiClient);

afterEach(() => {
  mock.reset();
});

describe("admin API", () => {
  it("removes empty filters while preserving pagination and boolean filters", async () => {
    mock.onGet("/api/admin/users").reply((config) => {
      expect(config.params).toEqual({
        page: 2,
        size: 20,
        activeSubscription: false,
      });
      return [200, response({ items: [], page: 2, size: 20, totalElements: 0, totalPages: 0 })];
    });

    await getAdminUsers({
      search: "",
      plan: undefined,
      page: 2,
      size: 20,
      activeSubscription: false,
    });
  });

  it("uses the public order id for payment detail", async () => {
    mock.onGet("/api/admin/payments/order-public-1").reply(200, response({ payment: {} }));

    await getAdminPayment("order-public-1");
    expect(mock.history.get[0]?.url).toBe("/api/admin/payments/order-public-1");
  });

  it("routes document collection failures through the collection endpoint", async () => {
    mock.onGet("/api/admin/operational-issues/collections").reply((config) => {
      expect(config.params).toEqual({ kind: "DOCUMENT", page: 0, size: 20 });
      return [200, response({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })];
    });

    await getAdminIssues("DOCUMENT", 0);
  });
});

function response<T>(data: T) {
  return { success: true, code: "COMMON_000", message: "success", data };
}
