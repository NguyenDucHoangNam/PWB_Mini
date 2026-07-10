import { describe, it, expect, beforeEach, vi } from "vitest";
import { useAuthStore } from "@/features/auth/stores/use-auth-store";

// Mock axios at the module level (refreshAccessToken imports `axios` directly).
const { postMock, getMock } = vi.hoisted(() => ({
  postMock: vi.fn(),
  getMock: vi.fn(),
}));
vi.mock("axios", () => ({
  default: {
    post: postMock,
    get: getMock,
  },
}));

import axios from "axios";
import { refreshAccessToken } from "./auth-refresh";

describe("refreshAccessToken", () => {
  beforeEach(() => {
    useAuthStore.getState().clearAuth();
    vi.clearAllMocks();
  });

  it("rejects and clears auth when refresh response is unsuccessful", async () => {
    vi.mocked(axios.post).mockResolvedValueOnce({
      data: { success: false, message: "nope", data: null, errors: [], timestamp: "" },
    } as never);

    await expect(refreshAccessToken()).rejects.toBeTruthy();
    expect(useAuthStore.getState().accessToken).toBeNull();
  });

  it("stores the new token on success (with existing user)", async () => {
    useAuthStore.getState().setAuth("old.token", {
      username: "u",
      email: "u@x.com",
      fullName: "User",
      status: "ACTIVE",
      oauthProvider: "LOCAL",
    });

    vi.mocked(axios.post).mockResolvedValueOnce({
      data: {
        success: true,
        message: "ok",
        data: { accessToken: "new.token.here", expiresIn: 60 },
        errors: null,
        timestamp: "",
      },
    } as never);

    const token = await refreshAccessToken();
    expect(token).toBe("new.token.here");
    expect(useAuthStore.getState().accessToken).toBe("new.token.here");
    // User is preserved.
    expect(useAuthStore.getState().user?.email).toBe("u@x.com");
  });

  it("fetches profile when user is null", async () => {
    vi.mocked(axios.post).mockResolvedValueOnce({
      data: {
        success: true,
        message: "ok",
        data: { accessToken: "new.token.here", expiresIn: 60 },
        errors: null,
        timestamp: "",
      },
    } as never);
    vi.mocked(axios.get).mockResolvedValueOnce({
      data: {
        success: true,
        message: "ok",
        data: {
          username: "u",
          email: "u@x.com",
          fullName: "User",
          status: "ACTIVE",
          role: "USER",
          oauthProvider: "LOCAL",
        },
        errors: null,
        timestamp: "",
      },
    } as never);

    const token = await refreshAccessToken();
    expect(token).toBe("new.token.here");
    expect(useAuthStore.getState().accessToken).toBe("new.token.here");
    // After the second call resolves, the user is populated.
    expect(useAuthStore.getState().user?.email).toBe("u@x.com");
    expect(vi.mocked(axios.get)).toHaveBeenCalledTimes(1);
  });
});
