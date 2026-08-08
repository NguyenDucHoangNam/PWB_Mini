"use client";

import { useState } from "react";
import { AvatarInitials } from "./avatar-initials";

export function UserAvatar({
  email,
  avatarUrl,
  seed,
  className = "size-10 text-sm",
}: {
  email: string;
  avatarUrl?: string | null;
  seed?: string;
  className?: string;
}) {
  const [brokenUrl, setBrokenUrl] = useState<string | null>(null);

  if (!avatarUrl || brokenUrl === avatarUrl) {
    return <AvatarInitials email={email} seed={seed} className={className} />;
  }

  return (
    <img
      key={avatarUrl}
      src={avatarUrl}
      alt=""
      aria-hidden
      onError={() => setBrokenUrl(avatarUrl)}
      className={`shrink-0 rounded-full bg-neutral-200 object-cover dark:bg-neutral-800 ${className}`}
    />
  );
}