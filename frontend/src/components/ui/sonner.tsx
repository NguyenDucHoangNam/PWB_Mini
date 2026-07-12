"use client";

import { useTheme } from "next-themes";
import { Toaster as Sonner, type ToasterProps } from "sonner";
import { useResponsiveToasterPosition } from "@/lib/use-responsive-toaster-position";

const Toaster = ({ ...props }: ToasterProps) => {
  const { theme = "system" } = useTheme();
  const position = useResponsiveToasterPosition();

  return (
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
          <svg className="animate-spin size-5 text-neutral-500 shrink-0" fill="none" viewBox="0 0 24 24">
            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
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
        classNames: {
          toast:
            "cn-toast group relative overflow-hidden cursor-default select-none",
          title: "cn-toast__title",
          description: "cn-toast__description",
        },
      }}
      {...props}
    />
  );
};

export { Toaster };
