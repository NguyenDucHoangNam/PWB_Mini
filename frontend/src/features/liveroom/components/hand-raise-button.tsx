"use client";

import { Hand } from "lucide-react";
import { useTranslations } from "next-intl";
import { useHandRaiseStore } from "../stores/hand-raise-store";
import { useHandRaise } from "../hooks/use-hand-raise";
import { resolveLiveroomErrorMessage } from "../lib/resolve-liveroom-error-message";
import { asApiError } from "@/lib/api-client";
import { useUpdateMyHandRaise } from "../api/hand-raise";
import { toast } from "sonner";

interface HandRaiseButtonProps {
  roomCode: string;
  localUserId: string;
}

export function HandRaiseButton({ roomCode, localUserId }: HandRaiseButtonProps) {
  const tErrors = useTranslations("liveroom.errors");
  const tCommon = useTranslations("common");
  const tImmersive = useTranslations("liveroom.immersive");
  const raisedBy = useHandRaiseStore((state) => state.raisedBy);
  const isRaised = raisedBy.has(localUserId);

  const { mutate: broadcastHandRaise } = useUpdateMyHandRaise({
    mutationConfig: {
      onError: asApiError((err) => {
        toast.error(
          resolveLiveroomErrorMessage(
            err,
            (k) => tErrors(k as never),
            (k) => tCommon(k as never),
          ),
        );
      }),
    },
  });

  const { toggle } = useHandRaise({
    roomCode,
    localUserId,
    enabled: true,
    onBroadcast: (action) => {
      broadcastHandRaise({ roomCode, action });
    },
  });

  return (
    <button
      type="button"
      onClick={() => toggle(isRaised)}
      aria-label={tImmersive("handRaise")}
      aria-pressed={isRaised}
      title={tImmersive("handRaise")}
      className={`relative inline-flex size-12 items-center justify-center rounded-full transition ${
        isRaised
          ? "bg-amber-500 text-white hover:bg-amber-600"
          : "bg-neutral-700 text-white hover:bg-neutral-600"
      }`}
    >
      <Hand className="size-5" />
    </button>
  );
}
