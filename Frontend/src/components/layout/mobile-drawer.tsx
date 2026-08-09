"use client";

import { useEffect, useRef } from "react";
import { createPortal } from "react-dom";

interface MobileDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  children: React.ReactNode;
}

export function MobileDrawer({ isOpen, onClose, children }: MobileDrawerProps) {
  const panelRef = useRef<HTMLDivElement>(null);

  // Close on Escape key
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        onClose();
      }
    };

    if (isOpen) {
      document.body.style.overflow = "hidden";
      window.addEventListener("keydown", handleKeyDown);
    }

    return () => {
      document.body.style.overflow = "";
      window.removeEventListener("keydown", handleKeyDown);
    };
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  // React Portal to body
  return createPortal(
    <div className="fixed inset-0 z-50 flex justify-end font-sans">
      {/* Backdrop with fade animation */}
      <div
        className="fixed inset-0 bg-black/50 transition-opacity duration-300 animate-in fade-in"
        onClick={onClose}
        aria-hidden="true"
      />

      {/* Drawer Panel with slide-in animation */}
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        className="neu-raised relative z-10 flex h-full w-[88%] max-w-[400px] flex-col bg-[#e0e5ec] p-6 border-none shadow-neu-raised transition-transform duration-300 animate-in slide-in-from-right sm:w-[80%] sm:max-w-[360px] dark:bg-[#1e222b]"
      >
        {/* Close Button */}
        <button
          onClick={onClose}
          type="button"
          aria-label="Close menu"
          className="neu-button absolute top-4 right-4 flex size-10 items-center justify-center rounded-full text-slate-700 focus-visible:outline-2 focus-visible:outline-indigo-600 focus-visible:outline-offset-2 dark:text-slate-200"
        >
          <svg
            className="size-5"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
            strokeWidth="2"
          >
            <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>

        <div className="mt-8 flex flex-col gap-5 sm:gap-6">{children}</div>
      </div>
    </div>,
    document.body,
  );
}
