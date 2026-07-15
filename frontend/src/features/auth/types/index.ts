export interface LoginRequest {
  email: string;
  password: string;
}

export type OAuthProvider = "LOCAL" | "GOOGLE";

export type UserStatus = "PENDING_VERIFICATION" | "ACTIVE" | "BANNED" | "DELETED";

export type AuthNextStep = "NONE" | "COMPLETE_PROFILE";

export interface AuthUser {
  userId: string;
  email: string;
  username: string;
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

export interface ResendOtpRequest {
  userId: string;
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

export interface CompleteProfileRequest {
  username: string;
  fullName?: string;
  newPassword?: string;
}

export interface RefreshAccessTokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  userId: string;
  email: string;
  status: UserStatus;
  role?: string;
  nextStep: AuthNextStep;
}
