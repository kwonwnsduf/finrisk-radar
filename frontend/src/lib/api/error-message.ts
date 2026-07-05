import axios from "axios";

export function apiErrorMessage(error: unknown, fallback: string) {
  if (!axios.isAxiosError(error)) return fallback;
  const data = error.response?.data as { message?: string } | undefined;
  return data?.message ?? fallback;
}
