"use client";

import { cn } from "@/lib/utils";

export function RangeSlider({
  value,
  max,
  step = 1,
  disabled,
  ariaLabel,
  onChange,
  onCommit,
  className = "",
}: {
  value: number;
  max: number;
  step?: number;
  disabled?: boolean;
  ariaLabel: string;
  onChange?: (value: number) => void;
  onCommit?: (value: number) => void;
  className?: string;
}) {
  const safeMax = max > 0 ? max : 1;
  const percent = Math.min(100, Math.max(0, (value / safeMax) * 100));

  return (
    <input
      type="range"
      min={0}
      max={safeMax}
      step={step}
      value={Math.min(value, safeMax)}
      disabled={disabled}
      aria-label={ariaLabel}
      onChange={(event) => onChange?.(Number(event.target.value))}
      onPointerUp={(event) => onCommit?.(Number((event.target as HTMLInputElement).value))}
      onKeyUp={(event) => onCommit?.(Number((event.target as HTMLInputElement).value))}
      style={{
        background: `linear-gradient(to right, currentColor ${percent}%, rgb(163 163 163 / 0.35) ${percent}%)`,
      }}
      className={cn(
        "h-1.5 w-full cursor-pointer appearance-none rounded-full text-black transition-[height] outline-none hover:h-2 focus-visible:ring-2 focus-visible:ring-ring/50 disabled:cursor-not-allowed disabled:opacity-50 dark:text-white",
        "[&::-moz-range-thumb]:size-3 [&::-moz-range-thumb]:rounded-full [&::-moz-range-thumb]:border-0 [&::-moz-range-thumb]:bg-current [&::-moz-range-thumb]:shadow",
        "[&::-webkit-slider-thumb]:size-3 [&::-webkit-slider-thumb]:appearance-none [&::-webkit-slider-thumb]:rounded-full [&::-webkit-slider-thumb]:bg-current [&::-webkit-slider-thumb]:shadow [&::-webkit-slider-thumb]:transition-transform hover:[&::-webkit-slider-thumb]:scale-125",
        className,
      )}
    />
  );
}
