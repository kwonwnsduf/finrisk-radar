import http from "k6/http";
import { check, sleep } from "k6";

const baseUrl = (__ENV.BASE_URL || "https://app.fin-risk.com").replace(/\/$/, "");

export const options = {
  vus: 1,
  iterations: 3,
  thresholds: {
    http_req_failed: ["rate<0.01"],
    http_req_duration: ["p(95)<1500"],
    checks: ["rate>0.99"],
  },
  tags: { suite: "production-smoke" },
};

export default function () {
  const ready = http.get(`${baseUrl}/readyz`, {
    tags: { endpoint: "readyz" },
  });
  check(ready, {
    "readyz returns 200": (response) => response.status === 200,
  });

  const home = http.get(`${baseUrl}/`, {
    tags: { endpoint: "home" },
  });
  check(home, {
    "home returns 200": (response) => response.status === 200,
  });

  sleep(1);
}
