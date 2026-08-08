"use client";

import { useEffect, useState } from "react";
import { secondsUntil } from "../../lib/server-clock";


export function useCountdownSeconds(deadlineIso: string | null | undefined): number {
  const [, setTick] = useState(0);

  useEffect(() => {
    if (!deadlineIso) return;
    const id = window.setInterval(() => setTick((value) => value + 1), 1000);
    return () => window.clearInterval(id);
  }, [deadlineIso]);

  return secondsUntil(deadlineIso);
}

export function formatDuration(totalSeconds: number): string {
  const safe = Math.max(0, Math.floor(totalSeconds));
  const minutes = Math.floor(safe / 60);
  const seconds = safe % 60;
  if (minutes <= 0) return `${seconds}s`;
  return `${minutes}:${String(seconds).padStart(2, "0")}`;
}

export function CountdownText({
  deadline,
  className = "",
}: {
  deadline: string | null | undefined;
  className?: string;
}) {
  const seconds = useCountdownSeconds(deadline);
  return (
    <span className={className} suppressHydrationWarning>
      {formatDuration(seconds)}
    </span>
  );
}