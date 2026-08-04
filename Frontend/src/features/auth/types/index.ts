export interface LoginRequest {
  email: string;
  password: string;
}

export type OAuthProvider = "LOCAL" | "GOOGLE";

export type UserStatus =
  | "PENDING_VERIFICATION"
  | "ACTIVE"
  | "PENDING_DELETION"
  | "BANNED"
  | "DELETED";

/** Client-side view of the signed-in user, built straight from the API response. */
export interface AuthUser {
  userId: string;
  email: string;
  fullName: string;
  role?: string;
  status: UserStatus;
  oauthProvider: OAuthProvider;
}

/**
 * Mirrors the backend `AuthResponse`.
 *
 * The refresh token is deliberately absent: the backend delivers it only as an HttpOnly cookie,
 * so it is unreachable from JS by design. Every auth request must therefore be credentialed
 * (`withCredentials`) for `POST /auth/refresh` to work.
 *
 * `avatarUrl` is a short-lived presigned URL (see `pwb.iam.avatar.url-ttl`, 15 minutes by
 * default), not a permanent link — it must not be cached beyond that window.
 */
export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  userId: string;
  email: string;
  fullName?: string;
  avatarUrl?: string | null;
  status: UserStatus;
  role?: string;
  /**
   * Omitted from the payload when null — the backend serialises with NON_NULL inclusion — so
   * treat absence as "unknown" and fall back to LOCAL rather than assuming it is always present.
   */
  oauthProvider?: OAuthProvider | null;
}

export interface OAuth2LoginRequest {
  idToken: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  fullName: string;
}

export interface AuthMessageResponse {
  userId: string;
  message: string;
}

export interface VerifyOtpRequest {
  userId: string;
  code: string;
}

export type VerifyOtpResponse = AuthResponse;

/** Must match the backend `OtpPurpose` enum exactly — Jackson rejects unknown values. */
export type OtpPurpose = "REGISTER" | "PASSWORD_RESET";

export interface ResendOtpRequest {
  userId: string;
  purpose: OtpPurpose;
}

export interface AuthMessageResponseWithTimestamp extends AuthMessageResponse {
  sentAt?: string;
}

export type ResendOtpResponse = AuthMessageResponseWithTimestamp;

export interface ForgotPasswordRequest {
  email: string;
}

export interface ResetPasswordRequest {
  token: string;
  newPassword: string;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

/** `POST /auth/refresh` returns the same shape as any other authentication response. */
export type RefreshAccessTokenResponse = AuthResponse;