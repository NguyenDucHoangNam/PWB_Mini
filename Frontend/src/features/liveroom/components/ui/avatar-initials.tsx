"use client";

import { initialsOf } from "../../utils/participant-sort";

const PALETTE = [
  "bg-rose-500",
  "bg-orange-500",
  "bg-amber-500",
  "bg-emerald-500",
  "bg-teal-500",
  "bg-sky-500",
  "bg-indigo-500",
  "bg-fuchsia-500",
];

function paletteFor(seed: string): string {
  let hash = 0;
  for (let index = 0; index < seed.length; index += 1) {
    hash = (hash * 31 + seed.charCodeAt(index)) >>> 0;
  }
  return PALETTE[hash % PALETTE.length];
}

export function AvatarInitials({
  email,
  seed,
  className = "size-10 text-sm",
}: {
  email: string;
  seed?: string;
  className?: string;
}) {
  return (
    <span
      aria-hidden
      className={`inline-flex shrink-0 items-center justify-center rounded-full font-semibold text-white ${paletteFor(
        seed ?? email,
      )} ${className}`}
    >
      {initialsOf(email)}
    </span>
  );
}