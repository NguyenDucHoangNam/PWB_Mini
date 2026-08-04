"use client";

import { Globe } from "lucide-react";

export function FlagVN({ className = "h-3.5 w-[20px] rounded-[1px] shrink-0" }: { className?: string }) {
  return (
    <svg viewBox="0 0 3 2" className={className} aria-hidden="true">
      <rect width="3" height="2" fill="#da251d" />
      <polygon
        fill="#ffff00"
        points="1.5,0.4 1.618,0.764 2,0.764 1.691,0.988 1.809,1.352 1.5,1.128 1.191,1.352 1.309,0.988 1,0.764 1.382,0.764"
      />
    </svg>
  );
}

export function FlagUS({ className = "h-3.5 w-[20px] rounded-[1px] shrink-0" }: { className?: string }) {
  return (
    <svg viewBox="0 0 190 100" className={className} aria-hidden="true">
      <rect width="190" height="100" fill="#bb133e" />
      <path d="M0,7.69h190M0,23.08h190M0,38.46h190M0,53.85h190M0,69.23h190M0,84.62h190" stroke="#fff" strokeWidth="7.69" />
      <rect width="76" height="53.85" fill="#002147" />
      <circle cx="12.6" cy="9" r="2.5" fill="#fff" />
      <circle cx="25.3" cy="9" r="2.5" fill="#fff" />
      <circle cx="38" cy="9" r="2.5" fill="#fff" />
      <circle cx="50.6" cy="9" r="2.5" fill="#fff" />
      <circle cx="63.3" cy="9" r="2.5" fill="#fff" />
      <circle cx="19" cy="18" r="2.5" fill="#fff" />
      <circle cx="31.6" cy="18" r="2.5" fill="#fff" />
      <circle cx="44.3" cy="18" r="2.5" fill="#fff" />
      <circle cx="57" cy="18" r="2.5" fill="#fff" />
      <circle cx="12.6" cy="27" r="2.5" fill="#fff" />
      <circle cx="25.3" cy="27" r="2.5" fill="#fff" />
      <circle cx="38" cy="27" r="2.5" fill="#fff" />
      <circle cx="50.6" cy="27" r="2.5" fill="#fff" />
      <circle cx="63.3" cy="27" r="2.5" fill="#fff" />
      <circle cx="19" cy="36" r="2.5" fill="#fff" />
      <circle cx="31.6" cy="36" r="2.5" fill="#fff" />
      <circle cx="44.3" cy="36" r="2.5" fill="#fff" />
      <circle cx="57" cy="36" r="2.5" fill="#fff" />
      <circle cx="12.6" cy="45" r="2.5" fill="#fff" />
      <circle cx="25.3" cy="45" r="2.5" fill="#fff" />
      <circle cx="38" cy="45" r="2.5" fill="#fff" />
      <circle cx="50.6" cy="45" r="2.5" fill="#fff" />
      <circle cx="63.3" cy="45" r="2.5" fill="#fff" />
    </svg>
  );
}

export function FlagGB({ className = "h-3.5 w-[20px] rounded-[1px] shrink-0" }: { className?: string }) {
  return (
    <svg viewBox="0 0 60 30" className={className} aria-hidden="true">
      <clipPath id="gb-s"><path d="M0,0 v30 h60 v-30 z"/></clipPath>
      <clipPath id="gb-t"><path d="M30,15 m-30,0 l60,30 m0,-30 l-60,30 h60 v-30 z"/></clipPath>
      <g clipPath="url(#gb-s)">
        <path d="M0,0 v30 h60 v-30 z" fill="#012169"/>
        <path d="M0,0 l60,30 M60,0 l-60,30" stroke="#fff" strokeWidth="6"/>
        <path d="M0,0 l60,30 M60,0 l-60,30" stroke="#C8102E" strokeWidth="4" clipPath="url(#gb-t)"/>
        <path d="M30,0 v30 M0,15 h60" stroke="#fff" strokeWidth="10"/>
        <path d="M30,0 v30 M0,15 h60" stroke="#C8102E" strokeWidth="6"/>
      </g>
    </svg>
  );
}

export function LanguageFlagIcon({
  langCode,
  className = "h-3.5 w-[20px] rounded-[1px] shrink-0",
}: {
  langCode: string | null | undefined;
  className?: string;
}) {
  if (!langCode) return <Globe className="size-3.5 text-neutral-500" />;
  const code = langCode.toLowerCase();
  if (code.includes("vi")) {
    return <FlagVN className={className} />;
  }
  if (code.includes("gb")) {
    return <FlagGB className={className} />;
  }
  if (code.includes("us") || code === "en-us" || code === "en") {
    return <FlagUS className={className} />;
  }
  return <Globe className="size-3.5 text-neutral-500" />;
}
