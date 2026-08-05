"use client";

import { useCallback, useState } from "react";
import { hasPassed } from "../lib/server-clock";
import {
  clearKicked,
  readKicked,
  writeKicked,
  type KickedRecord,
} from "../lib/liveroom-storage";


export function useKickedCache(roomId: string) {
  const [kicked, setKicked] = useState<KickedRecord | null>(() =>
    roomId ? readKicked(roomId) : null,
  );

  const markKicked = useCallback(
    (record: KickedRecord) => {
      writeKicked(roomId, record);
      setKicked(record);
    },
    [roomId],
  );

  const clear = useCallback(() => {
    clearKicked(roomId);
    setKicked(null);
  }, [roomId]);

  const cooldownOver = kicked ? hasPassed(kicked.cooldownUntil) : true;

  return { kicked, markKicked, clearKicked: clear, cooldownOver };
}