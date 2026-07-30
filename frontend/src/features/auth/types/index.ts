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

export type AuthNextStep = "NONE";

export interface AuthUser {
  userId: string;
  email: string;
  fullName: string;
  role?: string;
  status: UserStatus;
  oauthProvider: OAuthProvider;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  userId: string;
  email: string;
  fullName?: string;
  status: UserStatus;
  role?: string;
  nextStep: AuthNextStep;
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

export type OtpPurpose = "REGISTER" | "RESET_PASSWORD";

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

export interface RefreshAccessTokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  userId: string;
  email: string;
  fullName?: string;
  status: UserStatus;
  role?: string;
  nextStep: AuthNextStep;
}