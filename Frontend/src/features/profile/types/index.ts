import type { OAuthProvider } from "@/features/auth/types";

export interface ProfileResponse {
  userId: string;
  email: string | null;
  fullName: string | null;
  avatarUrl: string | null;
  status: string | null;
  role: string | null;
  oauthProvider: OAuthProvider | null;
}

export interface UpdateProfileRequest {
  fullName: string;
}

export interface AvatarUploadResponse {
  userId: string;
  avatarUrl: string;
  message?: string;
}
