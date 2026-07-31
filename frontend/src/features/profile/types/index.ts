export interface ProfileResponse {
  userId: string;
  email: string | null;
  fullName: string | null;
  avatarUrl: string | null;
  status: string | null;
  role: string | null;
}

export interface UpdateProfileRequest {
  fullName: string;
}

export interface AvatarUploadResponse {
  userId: string;
  avatarUrl: string;
  message?: string;
}
