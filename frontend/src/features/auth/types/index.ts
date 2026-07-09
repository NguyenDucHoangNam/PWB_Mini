export interface LoginRequest {
  usernameOrEmail: string;
  password: string;
}

export type OAuthProvider = "LOCAL" | "GOOGLE";

export interface LoginUserInfo {
  username: string;
  email: string;
  fullName: string;
  role?: string;
  status: string;
  oauthProvider: OAuthProvider;
}

export interface LoginResponse {
  accessToken: string;
  expiresIn: number;
  user: LoginUserInfo;
}

export interface Oauth2LoginRequest {
  idToken: string;
  linkingPassword?: string;
}

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
  confirmPassword: string;
  fullName: string;
}

export interface RegisterResponse {
  username: string;
  email: string;
  fullName: string;
  status: string;
}

export interface VerifyOtpRequest {
  email: string;
  otpCode: string;
}

export interface VerifyOtpResponse {
  accessToken: string;
  expiresIn: number;
  user: LoginUserInfo;
}

export interface ResendOtpRequest {
  email: string;
}

export interface ForgotPasswordRequest {
  email: string;
}

export interface ResetPasswordRequest {
  token: string;
  newPassword: string;
  confirmPassword: string;
}

export interface CheckUsernameResponse {
  username: string;
  available: boolean;
}

export interface UserProfileResponse {
  username: string;
  email: string;
  fullName: string;
  role: string;
  status: string;
  avatarUrl: string | null;
  phone: string | null;
  oauthProvider: OAuthProvider;
  deletionRequestedAt: string | null;
}

export interface UpdateProfileRequest {
  fullName: string;
  phone: string | null;
  avatarUrl: string | null;
}

export interface AvatarUploadResponse {
  avatarUrl: string;
}

export interface ChangePasswordRequest {
  oldPassword: string;
  newPassword: string;
  confirmPassword: string;
}

export interface DeleteAccountRequest {
  password?: string;
  idToken?: string;
}

export interface ActiveSessionResponse {
  sessionUuid: string;
  ipAddress: string;
  deviceInfo: string;
  location: string;
  createdAt: string; // ISO date string
  isCurrent: boolean;
}

export interface RefreshResponse {
  accessToken: string;
  expiresIn: number;
}

export interface RegistrationInProgressData {
  redirectTo: string;
  email: string;
}