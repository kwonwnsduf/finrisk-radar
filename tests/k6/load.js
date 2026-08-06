import http from "k6/http";
import { check, fail, sleep } from "k6";

const baseUrl = (__ENV.BASE_URL || "https://app.fin-risk.com").replace(/\/$/, "");
const productionHost = "https://app.fin-risk.com";

if (baseUrl === productionHost && __ENV.ALLOW_PRODUCTION_LOAD !== "true") {
  throw new Error(
    "Set ALLOW_PRODUCTION_LOAD=true to run this read-only test against production.",
  );
}

if (!__ENV.TEST_EMAIL || !__ENV.TEST_PASSWORD) {
  throw new Error(
    "TEST_EMAIL and TEST_PASSWORD are required for the authenticated load test.",
  );
}

export const options = {
  stages: [
    { duration: "30s", target: 3 },
    { duration: "1m", target: 5 },
    { duration: "30s", target: 0 },
  ],
  thresholds: {
    http_req_failed: ["rate<0.01"],
    http_req_duration: ["p(95)<1000", "p(99)<2000"],
    "http_req_duration{endpoint:assets}": ["p(95)<750"],
    checks: ["rate>0.99"],
  },
  tags: { suite: "production-read-only-load" },
};

export function setup() {
  const response = http.post(
    `${baseUrl}/api/auth/login`,
    JSON.stringify({
      email: __ENV.TEST_EMAIL,
      password: __ENV.TEST_PASSWORD,
    }),
    { headers: { "Content-Type": "application/json" }, tags: { endpoint: "login" } },
  );
  const authenticated = check(response, {
    "test account login succeeds": (result) => result.status === 200,
    "login returns an access token": (result) =>
      typeof result.json("data.accessToken") === "string",
  });
  if (!authenticated) fail(`Test account login failed with HTTP ${response.status}.`);
  return { accessToken: response.json("data.accessToken") };
}

export default function (data) {
  const params = {
    headers: { Authorization: `Bearer ${data.accessToken}` },
    tags: { endpoint: "assets" },
  };
  const assets = http.get(`${baseUrl}/api/assets`, params);
  check(assets, {
    "assets returns 200": (response) => response.status === 200,
    "assets response succeeds": (response) => response.json("success") === true,
  });

  const health = http.get(`${baseUrl}/api/health`, {
    tags: { endpoint: "health" },
  });
  check(health, {
    "health returns 200": (response) => response.status === 200,
  });

  sleep(1);
}
