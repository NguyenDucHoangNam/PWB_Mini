"use client";

import * as React from "react";
import { cn } from "@/lib/utils";

/**
 * Soft UI primitives.
 *
 * The whole style rests on one rule: every control is the *same* matte colour as
 * the surface behind it, and depth comes only from a light shadow up-left plus a
 * dark one down-right. Raised = outer pair, pressed/inputs = the same pair inset.
 * Nothing here draws a border, a gradient or a texture — that would be
 * skeuomorphism, not neumorphism.
 *
 * Shadows carry no contrast for WCAG, so every state that matters (active tab,
 * selection, focus, status) also gets a non-shadow cue: colour, an outline, a
 * dot or an icon.
 */

/** The one matte colour shared by the page and everything sitting on it. */
export const NEU_SURFACE = "bg-[#e0e5ec] dark:bg-[#1e222b]";

/** Focus must be visible without relying on shadows. */
export const NEU_FOCUS =
  "focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600 dark:focus-visible:outline-indigo-400";

/**
 * Text ramps, measured against the two surfaces. Muted is slate-600 rather than
 * the more usual slate-500 because slate-500 lands at 3.76:1 on #e0e5ec — the
 * matte surface is light enough to eat a whole step of the ramp.
 */
export const NEU_TEXT = "text-slate-900 dark:text-slate-100";
export const NEU_TEXT_SOFT = "text-slate-700 dark:text-slate-200";
export const NEU_TEXT_MUTED = "text-slate-600 dark:text-slate-400";
export const NEU_ACCENT_TEXT = "text-indigo-600 dark:text-indigo-400";
export const NEU_DANGER_TEXT = "text-rose-700 dark:text-rose-400";

/**
 * Sunken field. The surface colour is spelled out rather than left to the
 * `neu-input` utility so that `cn()` drops any `bg-*` a base component set —
 * `dark:bg-input/30` on the shared Input would otherwise tint the well.
 */
export const NEU_INPUT = cn(
  "neu-input w-full rounded-2xl border-none px-4 text-sm font-medium outline-none",
  NEU_SURFACE,
  "text-slate-900 placeholder:text-slate-500 dark:text-slate-100 dark:placeholder:text-slate-400",
);

export const NEU_LABEL = cn(
  "text-xs font-bold uppercase tracking-[0.14em]",
  NEU_TEXT_MUTED,
);

export const NEU_ERROR_TEXT = cn("text-xs font-semibold", NEU_DANGER_TEXT);

/**
 * Pass to the shared Dialog so a popup is a slab lifted off the same matte plane:
 * no ring, no divider under the footer, just depth.
 */
export const NEU_DIALOG_CONTENT = cn(
  "neu-raised gap-6 rounded-3xl border-none p-6 ring-0",
  NEU_SURFACE,
);
export const NEU_DIALOG_FOOTER = "mx-0 mb-0 gap-3 border-t-0 bg-transparent p-0";

type NeuButtonVariant = "default" | "primary" | "ghost" | "danger";
type NeuButtonSize = "sm" | "md" | "lg" | "icon-sm" | "icon" | "icon-lg";

const BUTTON_VARIANTS: Record<NeuButtonVariant, string> = {
  default: "neu-button text-slate-700 dark:text-slate-200",
  primary: "neu-button-primary",
  ghost:
    "neu-ghost text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100",
  danger: "neu-button-danger",
};

const BUTTON_SIZES: Record<NeuButtonSize, string> = {
  sm: "h-9 gap-2 rounded-xl px-3.5 text-xs",
  md: "h-11 gap-2 rounded-2xl px-4 text-sm",
  lg: "h-12 gap-2.5 rounded-2xl px-6 text-sm",
  "icon-sm": "size-9 rounded-xl",
  icon: "size-11 rounded-2xl",
  "icon-lg": "size-12 rounded-2xl",
};

/**
 * Class string for a soft button. Exported separately from the component so a
 * `next/link` can *be* the button rather than wrap one.
 */
export function neuButton(
  { variant = "default", size = "md" }: { variant?: NeuButtonVariant; size?: NeuButtonSize } = {},
  className?: string,
) {
  return cn(
    "inline-flex shrink-0 cursor-pointer select-none items-center justify-center border-none font-semibold whitespace-nowrap",
    "disabled:pointer-events-none disabled:opacity-50",
    NEU_FOCUS,
    BUTTON_VARIANTS[variant],
    BUTTON_SIZES[size],
    className,
  );
}

export interface NeuButtonProps extends React.ComponentProps<"button"> {
  variant?: NeuButtonVariant;
  size?: NeuButtonSize;
}

export function NeuButton({
  variant = "default",
  size = "md",
  className,
  type = "button",
  ...props
}: NeuButtonProps) {
  return <button type={type} className={neuButton({ variant, size }, className)} {...props} />;
}

type NeuTone = "raised" | "raised-sm" | "tile" | "pressed" | "pressed-sm" | "flat";

const TONES: Record<NeuTone, string> = {
  raised: "neu-raised",
  "raised-sm": "neu-raised-sm",
  /** Raised, and lifts further on hover — for cards and rows you can act on. */
  tile: "neu-tile",
  pressed: "neu-pressed",
  "pressed-sm": "neu-pressed-sm",
  flat: NEU_SURFACE,
};

export interface NeuPanelProps extends React.ComponentProps<"div"> {
  tone?: NeuTone;
  /** Render as something other than a div — `section`, `article`, `ul`, `li`. */
  as?: React.ElementType;
}

/** A soft slab. `pressed` reads as a container/well, `raised` as an object on it. */
export function NeuPanel({ tone = "raised", as: Tag = "div", className, ...props }: NeuPanelProps) {
  return <Tag className={cn("rounded-3xl border-none", TONES[tone], className)} {...props} />;
}

/**
 * The matte page canvas.
 *
 * Full-bleed on purpose: no rounding, no padding of its own. The surface is meant
 * to run edge to edge behind the whole shell, so a screen is a stack of slabs on
 * one continuous plane — not a card floating on a different-coloured page. It
 * still paints the colour itself so a screen rendered outside a matte shell is
 * not left sitting on white; the layout supplies the gutters.
 */
export function NeuScreen({ className, ...props }: React.ComponentProps<"div">) {
  return (
    <div
      className={cn(
        "flex w-full flex-1 flex-col gap-6 border-none transition-colors",
        NEU_SURFACE,
        className,
      )}
      {...props}
    />
  );
}

type NeuBadgeTone = "muted" | "accent" | "danger";

const BADGE_TONES: Record<NeuBadgeTone, string> = {
  muted: "neu-pressed-sm text-slate-600 dark:text-slate-300",
  accent: `neu-raised-sm ${NEU_ACCENT_TEXT}`,
  danger: `neu-pressed-sm ${NEU_DANGER_TEXT}`,
};

export interface NeuBadgeProps extends React.ComponentProps<"span"> {
  tone?: NeuBadgeTone;
}

export function NeuBadge({ tone = "muted", className, ...props }: NeuBadgeProps) {
  return (
    <span
      className={cn(
        "inline-flex shrink-0 items-center gap-1.5 rounded-full border-none px-2.5 py-1 text-xs font-semibold",
        BADGE_TONES[tone],
        className,
      )}
      {...props}
    />
  );
}

export interface NeuFieldProps {
  label: string;
  htmlFor?: string;
  /** Renders the required marker after the label. */
  required?: boolean;
  /** Validation message; also wires `role="alert"`. */
  error?: string | null;
  errorId?: string;
  hint?: string;
  className?: string;
  children: React.ReactNode;
}

/** Label + control + validation message, spaced the same way on every form. */
export function NeuField({
  label,
  htmlFor,
  required,
  error,
  errorId,
  hint,
  className,
  children,
}: NeuFieldProps) {
  return (
    <div className={cn("flex flex-col gap-2", className)}>
      <label htmlFor={htmlFor} className={NEU_LABEL}>
        {label}
        {required && (
          <span className="ml-1 text-rose-700 dark:text-rose-400" aria-hidden="true">
            *
          </span>
        )}
      </label>
      {children}
      {hint && <p className={cn("text-xs leading-relaxed", NEU_TEXT_MUTED)}>{hint}</p>}
      {error && (
        <p id={errorId} role="alert" className={NEU_ERROR_TEXT}>
          {error}
        </p>
      )}
    </div>
  );
}

export interface NeuCheckboxProps extends Omit<React.ComponentProps<"input">, "type"> {
  label: React.ReactNode;
}

/**
 * The native input stays — it just goes invisible and drives a sunken box that
 * fills with the accent when checked. Checked is carried by colour *and* a tick,
 * never by the shadow flip alone.
 */
export function NeuCheckbox({ label, className, disabled, ...props }: NeuCheckboxProps) {
  return (
    <label
      className={cn(
        "group/check flex cursor-pointer select-none items-center gap-3 text-sm font-semibold",
        NEU_TEXT_SOFT,
        disabled && "cursor-not-allowed opacity-50",
        className,
      )}
    >
      <span className="relative inline-flex shrink-0">
        <input type="checkbox" className="peer sr-only" disabled={disabled} {...props} />
        <span
          aria-hidden="true"
          className={cn(
            "neu-pressed-sm grid size-6 place-items-center rounded-lg border-none text-transparent transition-all",
            "peer-checked:bg-indigo-600 peer-checked:text-white peer-checked:shadow-none dark:peer-checked:bg-indigo-500",
            "peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-indigo-600 dark:peer-focus-visible:outline-indigo-400",
          )}
        >
          <svg viewBox="0 0 16 16" fill="none" className="size-3.5">
            <path
              d="M3 8.5l3.2 3.2L13 4.8"
              stroke="currentColor"
              strokeWidth="2.4"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          </svg>
        </span>
      </span>
      <span>{label}</span>
    </label>
  );
}

/** Range input on the soft groove. See the `neu-range` utility for the shape. */
export function NeuSlider({ className, ...props }: React.ComponentProps<"input">) {
  return <input type="range" className={cn("neu-range", className)} {...props} />;
}

export interface NeuDropzoneProps {
  onFiles: (file: File | null) => void;
  disabled?: boolean;
  /** Clicking anywhere in the zone opens this input. */
  inputRef: React.RefObject<HTMLInputElement | null>;
  className?: string;
  children: (state: { isDragging: boolean }) => React.ReactNode;
}

/**
 * Drop target as a sunken well — the style has no borders, so a dashed rectangle
 * is not available; "you can put something in here" is said with depth instead.
 * A real `<button>`, so it is reachable by keyboard rather than click-only.
 */
export function NeuDropzone({
  onFiles,
  disabled,
  inputRef,
  className,
  children,
}: NeuDropzoneProps) {
  const [isDragging, setIsDragging] = React.useState(false);

  return (
    <button
      type="button"
      disabled={disabled}
      onClick={() => inputRef.current?.click()}
      onDragOver={(e) => {
        e.preventDefault();
        e.stopPropagation();
        setIsDragging(true);
      }}
      onDragLeave={(e) => {
        e.preventDefault();
        e.stopPropagation();
        setIsDragging(false);
      }}
      onDrop={(e) => {
        e.preventDefault();
        e.stopPropagation();
        setIsDragging(false);
        onFiles(e.dataTransfer.files?.[0] ?? null);
      }}
      className={cn(
        "neu-pressed flex w-full flex-1 cursor-pointer flex-col items-center justify-center gap-3 rounded-3xl border-none p-6 text-center transition-all sm:p-8",
        NEU_FOCUS,
        // Drag-over is also announced by the accent colour inside, not by depth alone.
        isDragging && "outline-2 outline-offset-2 outline-indigo-600 dark:outline-indigo-400",
        disabled && "cursor-not-allowed opacity-50",
        className,
      )}
    >
      {children({ isDragging })}
    </button>
  );
}

/**
 * Skeleton block. The pulse is dropped under reduced-motion; the sunken shape
 * still says "something is loading here".
 */
export function NeuSkeleton({ className, ...props }: React.ComponentProps<"div">) {
  return (
    <div
      className={cn(
        "neu-pressed-sm animate-pulse rounded-xl border-none motion-reduce:animate-none",
        className,
      )}
      {...props}
    />
  );
}
