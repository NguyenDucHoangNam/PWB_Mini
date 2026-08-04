"use client";

import { useCallback, useEffect, useRef, useState } from "react";

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
  locale?: string,
): boolean {
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
    width: 320,
    text: "continue_with",
    shape: "rectangular",
    theme: "outline",
    locale: locale || "en",
  });

  return true;
}

export function useGoogleIdentity(onCredential: (idToken: string) => void, locale?: string) {
  const callbackRef = useRef(onCredential);
  const containerRef = useRef<HTMLDivElement | null>(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    callbackRef.current = onCredential;
  }, [onCredential]);

  const setContainerRef = useCallback((node: HTMLDivElement | null) => {
    containerRef.current = node;
    if (node && !ready) {
      const clientId = process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID;
      if (!clientId) return;
      const ok = initGis(
        clientId,
        (idToken) => callbackRef.current(idToken),
        node,
        locale,
      );
      if (ok) setReady(true);
    }
  }, [ready, locale]);

  useEffect(() => {
    if (ready) return;
    const container = containerRef.current;
    const clientId = process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID;
    if (!container || !clientId) return;
    const ok = initGis(
      clientId,
      (idToken) => callbackRef.current(idToken),
      container,
      locale,
    );
    if (ok) setReady(true);
  }, [ready, locale]);

  return {
    ready,
    setContainerRef,
  };
}
