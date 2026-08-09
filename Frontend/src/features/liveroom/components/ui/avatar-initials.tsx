"use client";

import { initialsOf } from "../../utils/participant-sort";

const PALETTE = [
  "bg-rose-700",
  "bg-orange-700",
  "bg-amber-700",
  "bg-emerald-700",
  "bg-teal-700",
  "bg-sky-700",
  "bg-indigo-700",
  "bg-fuchsia-700",
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