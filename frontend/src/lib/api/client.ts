import axios from "axios";

export const apiClient = axios.create({
  baseURL: "/backend-api",
  headers: {
    "Content-Type": "application/json",
  },
  timeout: 5_000,
});
