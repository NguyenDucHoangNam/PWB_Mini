"use client";

import { useCallback, useEffect, useRef, useState } from "react";

const GIS_SRC = "https://accounts.google.com/gsi/client";
const GIS_SCRIPT_ID = "google-gsi-script";

type GoogleAccountsId = {
  initialize: (config: {
    client_id: string;
    callback: (response: { credential?: string }) => void;
    auto_select?: boolean;
    cancel_on_tap_outside?: boolean;
    ux_mode?: "popup" | "redirect";
    use_fedcm_for_prompt?: boolean;
  }) => void;
  renderButton: (
    parent: HTMLElement,
    options: Record<string, unknown>,
  ) => void;
};

declare global {
  interface Window {
    google?: {
      accounts?: {
        id?: GoogleAccountsId;
      };
    };
  }
}

function initGis(
  clientId: string,
  onCredential: (idToken: string) => void,
  buttonContainer: HTMLElement,
) {
  if (typeof window === "undefined") return false;
  const id = window.google?.accounts?.id;
  if (!id) return false;

  id.initialize({
    client_id: clientId,
    callback: (response) => {
      if (response.credential) {
        onCredential(response.credential);
      }
    },
    cancel_on_tap_outside: true,
    ux_mode: "popup",
  });

  id.renderButton(buttonContainer, {
    type: "standard",
    size: "large",
    width: 300,
  });

  return true;
}

export function useGoogleIdentity(onCredential: (idToken: string) => void) {
  const [ready, setReady] = useState(false);
  const callbackRef = useRef(onCredential);
  const containerRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    callbackRef.current = onCredential;
  }, [onCredential]);

  const setContainerRef = useCallback((node: HTMLDivElement | null) => {
    containerRef.current = node;
  }, []);

  const tryInit = useCallback(() => {
    if (ready) return;
    const clientId = process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID;
    if (!clientId) {
      return;
    }
    const container = containerRef.current;
    if (!container) return;

    if (
      initGis(
        clientId,
        (idToken) => callbackRef.current(idToken),
        container,
      )
    ) {
      setReady(true);
    }
  }, [ready]);

  useEffect(() => {
    tryInit();
  }, [tryInit]);

  const handleLoad = useCallback(() => {
    tryInit();
  }, [tryInit]);

  const triggerClick = useCallback(() => {
    const container = containerRef.current;
    if (!container) return;
    const btn =
      container.querySelector<HTMLElement>('[role="button"]') ??
      container.querySelector<HTMLElement>("div[tabindex]");
    if (btn) {
      btn.click();
    }
  }, []);

  return {
    ready,
    handleLoad,
    triggerClick,
    setContainerRef,
    scriptSrc: GIS_SRC,
    scriptId: GIS_SCRIPT_ID,
  };
}