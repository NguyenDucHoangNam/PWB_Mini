"use client";

import { useMemo, useState } from "react";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { asApiError } from "@/lib/api-client";
import { NEU_TEXT_MUTED } from "@/components/ui/neu";
import { KickParticipantDialog } from "./kick-participant-dialog";
import { ParticipantRow } from "./participant-row";
import { useMuteParticipant } from "../../api/participants";
import { resolveLiveroomErrorMessage } from "../../lib/resolve-liveroom-error-message";
import { useLiveroomStore } from "../../stores/use-liveroom-store";
import { sortParticipants } from "../../utils/participant-sort";
import type { Participant } from "../../types";

export function ParticipantList({ roomId }: { roomId: string }) {
  const t = useTranslations("liveroom.room.participants");
  const tCommon = useTranslations("common");
  const tErrors = useTranslations("liveroom.errors");

  const participants = useLiveroomStore((state) => state.participants);
  const myUserId = useLiveroomStore((state) => state.myUserId);
  const isOwner = useLiveroomStore((state) => state.isOwner);
  const [kickTarget, setKickTarget] = useState<Participant | null>(null);

  const ordered = useMemo(
    () => sortParticipants(Object.values(participants)),
    [participants],
  );

  const { mutate: mute, isPending: muting } = useMuteParticipant({
    mutationConfig: {
      onError: asApiError((err) => {
        toast.error(resolveLiveroomErrorMessage(err, tErrors, tCommon));
      }),
    },
  });

  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <ul className="flex flex-1 flex-col gap-2 overflow-y-auto p-2.5">
        {ordered.length === 0 ? (
          <li className={`px-2 py-6 text-center text-sm font-medium ${NEU_TEXT_MUTED}`}>
            {t("empty")}
          </li>
        ) : (
          ordered.map((participant) => (
            <ParticipantRow
              key={participant.userId}
              participant={participant}
              isMe={participant.userId === myUserId}
              canModerate={isOwner}
              muting={muting}
              onKick={setKickTarget}
              onMute={(target) => mute({ roomId, targetUserId: target.userId })}
            />
          ))
        )}
      </ul>

      <KickParticipantDialog
        roomId={roomId}
        participant={kickTarget}
        open={Boolean(kickTarget)}
        onOpenChange={(open) => !open && setKickTarget(null)}
      />
    </div>
  );
}