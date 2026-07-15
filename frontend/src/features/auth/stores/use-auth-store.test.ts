import { describe, it, expect, beforeEach } from "vitest";
import { useAuthStore } from "./use-auth-store";

const sampleAuthUser = {
  userId: "u-1",
  email: "u@x.com",
  username: "u",
  role: "USER",
  status: "ACTIVE" as const,
  oauthProvider: "LOCAL" as const,
};

describe("useAuthStore", () => {
  beforeEach(() => {
    useAuthStore.getState().clearAuth();
  });

  it("starts with empty auth", () => {
    const state = useAuthStore.getState();
    expect(state.accessToken).toBeNull();
    expect(state.user).toBeNull();
    expect(state.accessTokenExpiresAt).toBeNull();
    expect(state.isAuthenticated()).toBe(false);
  });

  it("setAuth stores token, user and expiry", () => {
    const expiresAt = Date.now() + 60_000;
    useAuthStore.getState().setAuth(
      "abc.def.ghi",
      sampleAuthUser,
      expiresAt,
    );
    const s = useAuthStore.getState();
    expect(s.accessToken).toBe("abc.def.ghi");
    expect(s.user?.email).toBe("u@x.com");
    expect(s.accessTokenExpiresAt).toBe(expiresAt);
    expect(s.isAuthenticated()).toBe(true);
  });

  it("setUser updates the user object without touching the token", () => {
    useAuthStore.getState().setAuth("token", sampleAuthUser);
    useAuthStore.getState().setUser({ ...sampleAuthUser, oauthProvider: "GOOGLE" });
    const s = useAuthStore.getState();
    expect(s.accessToken).toBe("token");
    expect(s.user?.oauthProvider).toBe("GOOGLE");
  });

  it("clearAuth resets everything", () => {
    useAuthStore.getState().setAuth("token", sampleAuthUser);
    useAuthStore.getState().clearAuth();
    const s = useAuthStore.getState();
    expect(s.accessToken).toBeNull();
    expect(s.user).toBeNull();
    expect(s.accessTokenExpiresAt).toBeNull();
  });
});
