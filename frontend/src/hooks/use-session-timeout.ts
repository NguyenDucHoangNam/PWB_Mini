"use client";

import { useState, useEffect, useCallback } from "react";
import { useAuthStore } from "@/features/auth/stores/use-auth-store";

const WARNING_BEFORE_EXPIRY = 60; // Show warning 60 seconds before token expiry
const MIN_CHECK_INTERVAL = 5; // Check every 5 seconds

export interface SessionStatus {
  isExpiring: boolean;
  secondsRemaining: number;
  extendSession: () => void;
}

export function useSessionTimeout(): SessionStatus {
  const { accessToken, lastActivity, setLastActivity } = useAuthStore();
  const [secondsRemaining, setSecondsRemaining] = useState(0);
  const [isExpiring, setIsExpiring] = useState(false);

  const extendSession = useCallback(() => {
    setLastActivity(Date.now());
    setIsExpiring(false);
  }, [setLastActivity]);

  useEffect(() => {
    if (!accessToken) {
      setSecondsRemaining(0);
      setIsExpiring(false);
      return;
    }

    const checkSession = () => {
      const now = Date.now();
      const timeSinceActivity = now - lastActivity;
      // Assume session timeout is 30 minutes (1800000ms)
      const sessionTimeout = 30 * 60 * 1000;
      const remaining = Math.max(0, sessionTimeout - timeSinceActivity);
      
      setSecondsRemaining(Math.floor(remaining / 1000));
      setIsExpiring(remaining <= WARNING_BEFORE_EXPIRY * 1000 && remaining > 0);
    };

    // Initial check
    checkSession();

    // Set up interval
    const interval = setInterval(checkSession, MIN_CHECK_INTERVAL * 1000);

    // Update on activity
    const handleActivity = () => {
      checkSession();
    };
    window.addEventListener("mousemove", handleActivity);
    window.addEventListener("keydown", handleActivity);
    window.addEventListener("click", handleActivity);
    window.addEventListener("scroll", handleActivity);

    return () => {
      clearInterval(interval);
      window.removeEventListener("mousemove", handleActivity);
      window.removeEventListener("keydown", handleActivity);
      window.removeEventListener("click", handleActivity);
      window.removeEventListener("scroll", handleActivity);
    };
  }, [accessToken, lastActivity, setLastActivity]);

  return { isExpiring, secondsRemaining, extendSession };
}
