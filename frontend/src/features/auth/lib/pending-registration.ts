const PENDING_USER_KEY = "pwb_pending_user_id";

export const pendingRegistration = {
  set(userId: string) {
    if (typeof window === "undefined") return;
    sessionStorage.setItem(PENDING_USER_KEY, userId);
  },
  get(): string | null {
    if (typeof window === "undefined") return null;
    return sessionStorage.getItem(PENDING_USER_KEY);
  },
  clear() {
    if (typeof window === "undefined") return;
    sessionStorage.removeItem(PENDING_USER_KEY);
  },
};
