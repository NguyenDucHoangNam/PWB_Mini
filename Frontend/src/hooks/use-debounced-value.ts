"use client";

import { useEffect, useState } from "react";

/**
 * Holds back a fast-changing value until it settles.
 *
 * Search inputs fire on every keystroke, and each one would otherwise become a request that the next
 * keystroke makes irrelevant. Delaying the value means the query key only changes once the user pauses.
 */
export function useDebouncedValue<T>(value: T, delayMs = 250): T {
  const [debounced, setDebounced] = useState(value);

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delayMs);
    return () => clearTimeout(timer);
  }, [value, delayMs]);

  return debounced;
}
