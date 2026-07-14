"use client";

import Script from "next/script";
import { useEffect, useId, useRef, useState } from "react";
import { useTranslations } from "next-intl";

const TURNSTILE_SCRIPT_SRC = "https://challenges.cloudflare.com/turnstile/v0/api.js";
const TURNSTILE_SCRIPT_ID = "cf-turnstile-script";
const MAX_LOAD_ATTEMPTS = 3;
const RENDER_TIMEOUT_MS = 8000;

type TurnstileApi = {
  render: (
    container: HTMLElement,
    options: {
      sitekey: string;
      callback?: (token: string) => void;
      "error-callback"?: () => void;
      "expired-callback"?: () => void;
      theme?: "light" | "dark" | "auto";
      size?: "normal" | "flexible" | "compact";
    },
  ) => string;
  reset: (widgetId?: string) => void;
  remove: (widgetId?: string) => void;
};

declare global {
  interface Window {
    turnstile?: TurnstileApi;
  }
}

interface CaptchaWidgetProps {
  siteKey: string | null;
  onTokenChange: (token: string | null) => void;
  theme?: "light" | "dark" | "auto";
}

export function CaptchaWidget({ siteKey, onTokenChange, theme = "auto" }: CaptchaWidgetProps) {
  const t = useTranslations("auth.captcha");
  const containerId = useId();
  const containerRef = useRef<HTMLDivElement | null>(null);
  const widgetIdRef = useRef<string | null>(null);
  const [attempts, setAttempts] = useState(0);
  const [loadFailed, setLoadFailed] = useState(false);
  const [scriptReady, setScriptReady] = useState(false);
  const onTokenChangeRef = useRef(onTokenChange);

  useEffect(() => {
    onTokenChangeRef.current = onTokenChange;
  }, [onTokenChange]);

  useEffect(() => {
    if (!siteKey || !scriptReady || !containerRef.current || loadFailed) return;
    const api = window.turnstile;
    if (!api) {
      setLoadFailed(true);
      return;
    }

    const handleToken = (token: string) => {
      onTokenChangeRef.current(token);
    };
    const handleError = () => {
      onTokenChangeRef.current(null);
    };
    const handleExpired = () => {
      onTokenChangeRef.current(null);
    };

    const timeoutId = window.setTimeout(() => {
      if (!widgetIdRef.current) {
        setLoadFailed(true);
      }
    }, RENDER_TIMEOUT_MS);

    widgetIdRef.current = api.render(containerRef.current, {
      sitekey: siteKey,
      theme,
      callback: handleToken,
      "error-callback": handleError,
      "expired-callback": handleExpired,
    });

    return () => {
      window.clearTimeout(timeoutId);
      if (widgetIdRef.current && window.turnstile) {
        window.turnstile.remove(widgetIdRef.current);
      }
      widgetIdRef.current = null;
    };
  }, [siteKey, scriptReady, theme, loadFailed, attempts]);

  const handleRetry = () => {
    setLoadFailed(false);
    setScriptReady(false);
    setAttempts((prev) => prev + 1);
  };

  if (!siteKey) return null;

  if (loadFailed) {
    return (
      <div className="flex flex-col items-center gap-2 rounded-lg border border-red-200 bg-red-50 p-3 text-center text-xs text-red-700 dark:border-red-900 dark:bg-red-950/20 dark:text-red-300">
        <span>{t("loadFailed")}</span>
        <button
          type="button"
          onClick={handleRetry}
          disabled={attempts >= MAX_LOAD_ATTEMPTS}
          className="rounded-md bg-red-600 px-3 py-1 text-xs font-semibold text-white hover:bg-red-700 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {t("retry")}
        </button>
      </div>
    );
  }

  return (
    <>
      <Script
        key={attempts}
        id={TURNSTILE_SCRIPT_ID}
        src={TURNSTILE_SCRIPT_SRC}
        strategy="afterInteractive"
        async
        defer
        onLoad={() => setScriptReady(true)}
        onError={() => setLoadFailed(true)}
      />
      <div
        id={containerId}
        ref={containerRef}
        data-testid="captcha-widget"
        className="cf-turnstile-container flex justify-center"
      />
    </>
  );
}