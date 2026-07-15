import { describe, it, expect, beforeEach, vi } from "vitest";
import { useAuthStore } from "@/features/auth/stores/use-auth-store";

const sampleAuthUser = {
  userId: "u-1",
  email: "u@x.com",
  username: "u",
  role: "USER",
  status: "ACTIVE" as const,
  oauthProvider: "LOCAL" as const,
};

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

vi.mock("./api-client", () => ({
  apiClient: {
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
    useAuthStore.getState().setAuth("old.token", sampleAuthUser);

    vi.mocked(axios.post).mockResolvedValueOnce({
      data: {
        success: true,
        message: "ok",
        data: {
          accessToken: "new.token.here",
          refreshToken: "rt",
          tokenType: "Bearer",
          expiresIn: 60,
          userId: "u-1",
          email: "u@x.com",
          status: "ACTIVE",
          role: "USER",
          nextStep: "NONE",
        },
        errors: null,
        timestamp: "",
      },
    } as never);

    const token = await refreshAccessToken();
    expect(token).toBe("new.token.here");
    expect(useAuthStore.getState().accessToken).toBe("new.token.here");
    expect(useAuthStore.getState().user?.email).toBe("u@x.com");
  });

  it("synthesizes a user from BE response when no in-memory user exists", async () => {
    vi.mocked(axios.post).mockResolvedValueOnce({
      data: {
        success: true,
        message: "ok",
        data: {
          accessToken: "new.token.here",
          refreshToken: "rt",
          tokenType: "Bearer",
          expiresIn: 60,
          userId: "u-1",
          email: "u@x.com",
          status: "ACTIVE",
          role: "USER",
          nextStep: "NONE",
        },
        errors: null,
        timestamp: "",
      },
    } as never);

    const token = await refreshAccessToken();
    expect(token).toBe("new.token.here");
    expect(useAuthStore.getState().accessToken).toBe("new.token.here");
    expect(useAuthStore.getState().user?.email).toBe("u@x.com");
    expect(vi.mocked(axios.get)).not.toHaveBeenCalled();
  });
});
