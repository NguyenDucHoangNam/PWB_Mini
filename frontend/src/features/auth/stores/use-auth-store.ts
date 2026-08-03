import { create } from "zustand";
import type { AuthUser } from "../types";

interface AuthState {
  accessToken: string | null;
  user: AuthUser | null;
  accessTokenExpiresAt: number | null;
  bootstrapping: boolean;
  setAuth: (token: string, user: AuthUser, expiresAt?: number) => void;
  setUser: (user: AuthUser) => void;
  clearAuth: () => void;
  isAuthenticated: () => boolean;
  setBootstrapping: (value: boolean) => void;
}

/**
 * Auth store - in-memory only.
 *
 * Security: the access token lives in memory (Zustand) and is NEVER written to
 * localStorage / sessionStorage. The httpOnly refresh cookie (issued by the
 * backend) is the only persistent credential carrier. This prevents XSS from
 * exfiltrating the access token.
 *
 * Cross-tab sync is done through BroadcastChannel (`pwb_auth_channel`) which
 * posts `LOGOUT` and `TOKEN_UPDATED` messages. localStorage `storage` events
 * are not used because they are unreliable for non-storage changes.
 *
 * The avatar URL is deliberately NOT kept here. The backend hands out a presigned URL that
 * expires (`pwb.iam.avatar.url-ttl`, 15 minutes by default), and this store lives for the whole
 * session — caching the URL here means every avatar breaks once the window passes. It belongs in
 * the React Query cache, which re-fetches it; see `useAvatarUrl`.
 */
export const useAuthStore = create<AuthState>((set, get) => ({
  accessToken: null,
  user: null,
  accessTokenExpiresAt: null,
  bootstrapping: true,

  setAuth: (token, user, expiresAt) => {
    set({
      accessToken: token,
      user,
      accessTokenExpiresAt: expiresAt ?? get().accessTokenExpiresAt,
      bootstrapping: false,
    });
  },

  setUser: (user) => {
    set({ user });
  },

  clearAuth: () => {
    set({ accessToken: null, user: null, accessTokenExpiresAt: null, bootstrapping: false });
  },

  setBootstrapping: (value) => {
    set({ bootstrapping: value });
  },

  isAuthenticated: () => !!get().accessToken,
}));

export type { AuthUser } from "../types";
