import type { AuthResponse, AuthUser } from "../types";

/**
 * The response's `avatarUrl` is intentionally dropped: it is a presigned URL that expires, and
 * `AuthUser` is held for the whole session. Avatars are read through `useAvatarUrl` instead.
 *
 * `oauthProvider` comes from the response rather than from which endpoint was called: a local
 * account that has since been linked to Google reports `GOOGLE`, which is what decides whether
 * "change password" applies.
 */
export function mapAuthResponseToUser(
  response: Pick<
    AuthResponse,
    "userId" | "email" | "fullName" | "status" | "role" | "oauthProvider"
  >,
  fallback?: Partial<AuthUser>,
): AuthUser {
  return {
    userId: response.userId,
    email: response.email,
    fullName: response.fullName ?? fallback?.fullName ?? "",
    role: response.role,
    status: response.status,
    oauthProvider: response.oauthProvider ?? fallback?.oauthProvider ?? "LOCAL",
  };
}