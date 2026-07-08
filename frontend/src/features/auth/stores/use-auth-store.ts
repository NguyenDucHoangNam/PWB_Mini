import { create } from "zustand";
import { LoginUserInfo } from "../types";

interface AuthState {
  accessToken: string | null;
  user: LoginUserInfo | null;
  lastActivity: number;
  setAuth: (token: string, user: LoginUserInfo) => void;
  clearAuth: () => void;
  setLastActivity: (timestamp: number) => void;
  isAuthenticated: () => boolean;
}

export const useAuthStore = create<AuthState>((set, get) => ({
  accessToken: typeof window !== "undefined" ? localStorage.getItem("accessToken") : null,
  user: typeof window !== "undefined" ? (() => {
    const userStr = localStorage.getItem("authUser");
    try {
      return userStr ? JSON.parse(userStr) : null;
    } catch {
      return null;
    }
  })() : null,
  lastActivity: typeof window !== "undefined" ? (() => {
    const saved = localStorage.getItem("lastActivity");
    return saved ? parseInt(saved, 10) : Date.now();
  })() : Date.now(),

  setAuth: (token, user) => {
    const now = Date.now();
    if (typeof window !== "undefined") {
      localStorage.setItem("accessToken", token);
      localStorage.setItem("authUser", JSON.stringify(user));
      localStorage.setItem("lastActivity", now.toString());
      // Trigger storage event to sync other components/tabs
      window.dispatchEvent(new Event("storage"));
    }
    set({ accessToken: token, user, lastActivity: now });
  },

  clearAuth: () => {
    if (typeof window !== "undefined") {
      localStorage.removeItem("accessToken");
      localStorage.removeItem("authUser");
      localStorage.removeItem("lastActivity");
      window.dispatchEvent(new Event("storage"));
    }
    set({ accessToken: null, user: null, lastActivity: 0 });
  },

  setLastActivity: (timestamp: number) => {
    if (typeof window !== "undefined") {
      localStorage.setItem("lastActivity", timestamp.toString());
    }
    set({ lastActivity: timestamp });
  },

  isAuthenticated: () => {
    return !!get().accessToken;
  },
}));
