"use client";

import { useEffect } from "react";
import { addAuthChannelListener } from "@/lib/use-auth-channel";
import { liveroomSocket } from "../lib/liveroom-socket";
import { useLiveroomStore } from "../stores/use-liveroom-store";


export function useLiveroomSocket(): void {
  const applyEvent = useLiveroomStore((state) => state.applyEvent);
  const setConnection = useLiveroomStore((state) => state.setConnection);
  const setFrameError = useLiveroomStore((state) => state.setFrameError);

  useEffect(() => {
    const offEvent = liveroomSocket.onEvent(applyEvent);
    const offStatus = liveroomSocket.onStatus(setConnection);
    const offError = liveroomSocket.onFrameError(setFrameError);
    return () => {
      offEvent();
      offStatus();
      offError();
    };
  }, [applyEvent, setConnection, setFrameError]);

  useEffect(() => {
    const unsubscribe = addAuthChannelListener((message) => {
      if (message.type === "LOGOUT") {
        liveroomSocket.disconnect();
        useLiveroomStore.getState().reset(null, null, false);
      }
    });
    return unsubscribe;
  }, []);
}