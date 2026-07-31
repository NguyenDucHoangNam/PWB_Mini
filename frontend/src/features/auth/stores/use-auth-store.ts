import { create } from "zustand";
import type { AuthUser } from "../types";

interface AuthState {
  accessToken: string | null;
  user: AuthUser | null;
  accessTokenExpiresAt: number | null;
  bootstrapping: boolean;
  setAuth: (token: string, user: AuthUser, expiresAt?: number) => void;
  setUser: (user: AuthUser) => void;
  setAvatarUrl: (avatarUrl: string | null) => void;
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

  setAvatarUrl: (avatarUrl) => {
    const currentUser = get().user;
    if (!currentUser) return;
    set({ user: { ...currentUser, avatarUrl } });
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
