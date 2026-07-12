"use client";

import { useTheme } from "next-themes";
import { useEffect } from "react";
import { Toaster as Sonner, type ToasterProps } from "sonner";
import { useResponsiveToasterPosition } from "@/lib/use-responsive-toaster-position";

const TOAST_SELECTOR = "[data-sonner-toast]";
const PROGRESS_CLASS = "cn-toast__progress";
const FILL_CLASS = "cn-toast__progress-fill";

const injectProgressBar = (toastEl: HTMLElement) => {
  if (toastEl.dataset.cnProgressInjected === "true") return;
  toastEl.dataset.cnProgressInjected = "true";

  const setup = () => {
    if (!toastEl.isConnected) return;

    const track = document.createElement("div");
    track.className = PROGRESS_CLASS;
    const fill = document.createElement("span");
    fill.className = FILL_CLASS;
    track.appendChild(fill);
    toastEl.appendChild(track);

    const syncPlayState = () => {
      fill.style.animationPlayState =
        toastEl.getAttribute("data-expanded") === "true" ? "paused" : "running";
    };

    requestAnimationFrame(syncPlayState);

    const observer = new MutationObserver(() => {
      if (!toastEl.isConnected) {
        observer.disconnect();
        return;
      }
      syncPlayState();
    });
    observer.observe(toastEl, { attributes: true, attributeFilter: ["data-expanded"] });

    const removeObserver = new MutationObserver(() => {
      if (!toastEl.isConnected) {
        observer.disconnect();
        removeObserver.disconnect();
      }
    });
    removeObserver.observe(document.body, { childList: true, subtree: true });
  };

  if (toastEl.getAttribute("data-mounted") === "true") {
    setup();
  } else {
    const waitObserver = new MutationObserver(() => {
      if (toastEl.getAttribute("data-mounted") === "true") {
        waitObserver.disconnect();
        setup();
      } else if (!toastEl.isConnected) {
        waitObserver.disconnect();
      }
    });
    waitObserver.observe(toastEl, { attributes: true, attributeFilter: ["data-mounted"] });
  }
};

const ToastProgressInjector = () => {
  useEffect(() => {
    const observer = new MutationObserver((mutations) => {
      for (const mutation of mutations) {
        mutation.addedNodes.forEach((node) => {
          if (!(node instanceof HTMLElement)) return;
          if (node.matches?.(TOAST_SELECTOR)) {
            injectProgressBar(node);
          }
          node.querySelectorAll?.(TOAST_SELECTOR).forEach((child) => {
            if (child instanceof HTMLElement) injectProgressBar(child);
          });
        });
      }
    });
    observer.observe(document.body, { childList: true, subtree: true });
    document.querySelectorAll(TOAST_SELECTOR).forEach((el) => {
      if (el instanceof HTMLElement) injectProgressBar(el);
    });
    return () => observer.disconnect();
  }, []);
  return null;
};

const Toaster = ({ ...props }: ToasterProps) => {
  const { theme = "system" } = useTheme();
  const position = useResponsiveToasterPosition();

  return (
    <>
      <Sonner
        theme={theme as ToasterProps["theme"]}
        className="toaster group"
        position={position}
        visibleToasts={3}
        closeButton
        richColors={false}
        gap={10}
        icons={{
          success: <></>,
          error: <></>,
          info: <></>,
          warning: <></>,
          loading: (
            <svg
              className="animate-spin size-5 text-neutral-500 shrink-0"
              fill="none"
              viewBox="0 0 24 24"
            >
              <circle
                className="opacity-25"
                cx="12"
                cy="12"
                r="10"
                stroke="currentColor"
                strokeWidth="4"
              />
              <path
                className="opacity-75"
                fill="currentColor"
                d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
              />
            </svg>
          ),
        }}
        style={
          {
            "--normal-bg": "var(--popover)",
            "--normal-text": "var(--popover-foreground)",
            "--normal-border": "var(--border)",
            "--border-radius": "var(--radius)",
          } as React.CSSProperties
        }
        toastOptions={{
          unstyled: false,
          duration: 4000,
          classNames: {
            toast:
              "cn-toast group relative overflow-hidden cursor-default select-none",
            title: "cn-toast__title",
            description: "cn-toast__description",
          },
        }}
        {...props}
      />
      <ToastProgressInjector />
    </>
  );
};

export { Toaster };
