import { create } from "zustand";
import { LoginUserInfo } from "../types";

interface AuthState {
  accessToken: string | null;
  user: LoginUserInfo | null;
  setAuth: (token: string, user: LoginUserInfo) => void;
  clearAuth: () => void;
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

  setAuth: (token, user) => {
    if (typeof window !== "undefined") {
      localStorage.setItem("accessToken", token);
      localStorage.setItem("authUser", JSON.stringify(user));
      // Trigger storage event to sync other components/tabs
      window.dispatchEvent(new Event("storage"));
    }
    set({ accessToken: token, user });
  },

  clearAuth: () => {
    if (typeof window !== "undefined") {
      localStorage.removeItem("accessToken");
      localStorage.removeItem("authUser");
      window.dispatchEvent(new Event("storage"));
    }
    set({ accessToken: null, user: null });
  },

  isAuthenticated: () => {
    return !!get().accessToken;
  },
}));
