import { create } from "zustand";
import type { LoginUserInfo } from "../types";

export type OAuthProvider = "LOCAL" | "GOOGLE";

export interface AuthUser extends LoginUserInfo {
  oauthProvider: OAuthProvider;
}

interface AuthState {
  accessToken: string | null;
  user: AuthUser | null;
  // Mirror of accessToken expiry in epoch milliseconds (so we can compute session-timeout from real JWT exp).
  // Stored in memory only (never persisted).
  accessTokenExpiresAt: number | null;
  // True until useBootstrapAuth() has tried to restore the session from the
  // refresh cookie. Guards must wait for this to flip false before redirecting
  // logged-out users, otherwise a hard refresh on /dashboard would race the
  // silent refresh and kick the user to /login.
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
