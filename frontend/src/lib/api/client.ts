import axios from "axios";

export const apiClient = axios.create({
  baseURL: process.env.NEXT_PUBLIC_API_BASE_URL ?? "/backend-api",
  headers: {
    "Content-Type": "application/json",
  },
  timeout: 5_000,
});
