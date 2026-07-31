import type { AuthResponse, AuthUser } from "../types";

export function mapAuthResponseToUser(
  response: Pick<AuthResponse, "userId" | "email" | "fullName" | "avatarUrl" | "status" | "role">,
  fallback?: Partial<AuthUser>,
): AuthUser {
  return {
    userId: response.userId,
    email: response.email,
    fullName: response.fullName ?? fallback?.fullName ?? "",
    avatarUrl: response.avatarUrl ?? fallback?.avatarUrl ?? null,
    role: response.role,
    status: response.status,
    oauthProvider: fallback?.oauthProvider ?? "LOCAL",
  };
}