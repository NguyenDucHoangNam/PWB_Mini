import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
import { renderHook, act } from "@testing-library/react";
import { useExpiryCountdown, useCooldown } from "./use-otp-countdown";

describe("useExpiryCountdown", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("starts with the full TTL when no server timestamp is provided", () => {
    const { result } = renderHook(() =>
      useExpiryCountdown({ ttlSeconds: 300, serverTimestamp: null }),
    );
    expect(result.current.remaining).toBe(300);
  });

  it("decrements by 1 each second", () => {
    const { result } = renderHook(() =>
      useExpiryCountdown({ ttlSeconds: 60, serverTimestamp: null }),
    );

    act(() => {
      vi.advanceTimersByTime(1000);
    });
    expect(result.current.remaining).toBe(59);

    act(() => {
      vi.advanceTimersByTime(5000);
    });
    expect(result.current.remaining).toBe(54);
  });

  it("clamps to zero and stops decrementing past zero", () => {
    const { result } = renderHook(() =>
      useExpiryCountdown({ ttlSeconds: 3, serverTimestamp: null }),
    );

    act(() => {
      vi.advanceTimersByTime(5000);
    });
    expect(result.current.remaining).toBe(0);
  });

  it("subtracts elapsed time when serverTimestamp is provided", () => {
    const now = Date.now();
    vi.setSystemTime(now);

    const serverTimestamp = now - 10_000;
    const { result } = renderHook(() =>
      useExpiryCountdown({ ttlSeconds: 30, serverTimestamp }),
    );

    expect(result.current.remaining).toBe(20);
  });
});

describe("useCooldown", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("starts with remaining = 0 (no active cooldown)", () => {
    const { result } = renderHook(() => useCooldown({ ttlSeconds: 60 }));
    expect(result.current.remaining).toBe(0);
  });

  it("reset() activates the countdown for ttlSeconds", () => {
    const { result } = renderHook(() => useCooldown({ ttlSeconds: 60 }));

    act(() => {
      result.current.reset();
    });

    expect(result.current.remaining).toBe(60);

    act(() => {
      vi.advanceTimersByTime(1000);
    });
    expect(result.current.remaining).toBe(59);
  });

  it("setFromServer uses override TTL when provided", () => {
    const { result } = renderHook(() => useCooldown({ ttlSeconds: 60 }));

    act(() => {
      result.current.setFromServer(Date.now(), 120);
    });

    expect(result.current.remaining).toBe(120);
  });

  it("setFromServer subtracts elapsed time when given a server timestamp", () => {
    const now = Date.now();
    vi.setSystemTime(now);
    const serverTimestamp = now - 15_000;

    const { result } = renderHook(() => useCooldown({ ttlSeconds: 60 }));

    act(() => {
      result.current.setFromServer(serverTimestamp);
    });

    expect(result.current.remaining).toBe(45);
  });

  it("expires after the TTL elapses", () => {
    const { result } = renderHook(() => useCooldown({ ttlSeconds: 3 }));

    act(() => {
      result.current.reset();
    });
    expect(result.current.remaining).toBe(3);

    act(() => {
      vi.advanceTimersByTime(5000);
    });
    expect(result.current.remaining).toBe(0);
  });
});