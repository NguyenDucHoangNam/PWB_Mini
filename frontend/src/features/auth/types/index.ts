export interface LoginRequest {
  usernameOrEmail: string;
  password: string;
  captchaToken?: string;
}

export type OAuthProvider = "LOCAL" | "GOOGLE";

export interface LoginUserInfo {
  username: string;
  email: string;
  fullName: string;
  role?: string;
  status: string;
  avatarUrl?: string | null;
  oauthProvider: OAuthProvider;
}

export interface LoginResponse {
  accessToken: string;
  expiresIn: number;
  accessTokenExpiresAt?: string;
  user: LoginUserInfo;
  refreshToken?: string;
  refreshTokenMaxAgeSeconds?: number;
  redirectTo?: string;
  redirectEmail?: string;
}

export interface Oauth2LoginRequest {
  idToken: string;
  linkingPassword?: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  fullName: string;
  captchaToken?: string;
  otp?: string;
}

export interface RegisterResponse {
  username: string;
  email: string;
  fullName: string;
  status: string;
}

export interface VerifyOtpRequest {
  email: string;
  otp: string;
  captchaToken?: string;
}

export interface VerifyOtpResponse {
  accessToken: string;
  expiresIn: number;
  accessTokenExpiresAt?: string;
  user: LoginUserInfo;
}

export interface ResendOtpRequest {
  email: string;
  captchaToken?: string;
}

export interface ResendOtpResponse {
  email: string;
  sentAt: string;
}

export interface ForgotPasswordRequest {
  email: string;
  captchaToken?: string;
}

export interface ResetPasswordRequest {
  token: string;
  newPassword: string;
  confirmPassword: string;
  captchaToken?: string;
}

export interface CheckUsernameResponse {
  username: string;
  available: boolean;
}

export interface UserProfileResponse {
  username?: string;
  email: string;
  fullName: string;
  role: string;
  status: string;
  avatarUrl: string | null;
  phone: string | null;
  oauthProvider?: OAuthProvider;
  deletionRequestedAt: string | null;
}

export interface UpdateProfileRequest {
  fullName: string;
  phone: string | null;
  avatarUrl: string | null;
}

export interface AvatarUploadResponse {
  avatarUrl: string;
  sizeBytes?: number;
  contentType?: string;
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
  sessionPublicId: string;
  ipAddress: string;
  deviceInfo: string;
  location: string;
  createdAt: string;
  isCurrent: boolean;
}

export interface RefreshResponse {
  accessToken: string;
  expiresIn: number;
  accessTokenExpiresAt?: string;
  refreshToken?: string;
  refreshTokenMaxAgeSeconds?: number;
}

export interface RegistrationInProgressData {
  redirectTo: string;
  email: string;
}
