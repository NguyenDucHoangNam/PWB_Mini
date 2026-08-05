"use client";

import { useCallback, useEffect, useState } from "react";

const AUTO_JOIN_MS = 3000;
const TICK_MS = 250;


export function useAutoJoinCountdown(onElapsed: () => void) {
  const [deadline, setDeadline] = useState<number | null>(() => Date.now() + AUTO_JOIN_MS);
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    if (deadline === null) return;
    const id = window.setInterval(() => setNow(Date.now()), TICK_MS);
    return () => window.clearInterval(id);
  }, [deadline]);

  const elapsed = deadline !== null && now >= deadline;

  useEffect(() => {
    if (elapsed) onElapsed();
  }, [elapsed, onElapsed]);

  const cancel = useCallback(() => setDeadline(null), []);

  const remaining =
    deadline === null ? 0 : Math.max(0, Math.ceil((deadline - now) / 1000));

  return { remaining, cancelled: deadline === null, cancel };
}