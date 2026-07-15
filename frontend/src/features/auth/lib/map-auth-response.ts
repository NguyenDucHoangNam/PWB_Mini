import type { AuthResponse, AuthUser } from "../types";

export function mapAuthResponseToUser(
  response: Pick<AuthResponse, "userId" | "email" | "status" | "role">,
  fallback?: Partial<AuthUser>,
): AuthUser {
  return {
    userId: response.userId,
    email: response.email,
    username: fallback?.username ?? "",
    role: response.role,
    status: response.status,
    oauthProvider: fallback?.oauthProvider ?? "LOCAL",
  };
}
