export interface ApiResponse<T> {
  success: boolean;
  code: string;
  message: string;
  data: T;
}

export interface AuthUser {
  id: number;
  email: string;
  name: string;
  role: string;
  plan?: string;
}

export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
}

export interface AuthResponse extends AuthUser, AuthTokens {}

export type SignupResponse = AuthUser;

export interface TokenResponse {
  accessToken: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface SignupRequest extends LoginRequest {
  name: string;
}
