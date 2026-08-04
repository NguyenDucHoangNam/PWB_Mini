const PENDING_USER_KEY = "pwb_pending_user_id";

export const pendingRegistration = {
  set(userId: string) {
    if (typeof window === "undefined") return;
    const encoded = btoa(userId);
    sessionStorage.setItem(PENDING_USER_KEY, encoded);
  },
  get(): string | null {
    if (typeof window === "undefined") return null;
    const encoded = sessionStorage.getItem(PENDING_USER_KEY);
    if (!encoded) return null;
    try {
      return atob(encoded);
    } catch {
      return null;
    }
  },
  clear() {
    if (typeof window === "undefined") return;
    sessionStorage.removeItem(PENDING_USER_KEY);
  },
};
