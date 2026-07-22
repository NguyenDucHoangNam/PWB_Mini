"use client";

import { useEffect } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { Spinner } from "@/components/ui/spinner";
import { useAuthStore } from "@/features/auth/stores/use-auth-store";
import { useParticipants, liveRoomParticipantsKey } from "../api/participants";
import { subscribeRoomParticipants } from "../api/ws";
import {
  type ParticipantSummary,
  type ParticipantWsEvent,
} from "../types";

interface ParticipantsListProps {
  roomCode: string;
  hostUserId?: string;
}

function formatTime(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return new Intl.DateTimeFormat(undefined, {
    timeStyle: "short",
  }).format(d);
}

export function ParticipantsList({ roomCode, hostUserId }: ParticipantsListProps) {
  const t = useTranslations("liveroom.participant");
  const tExists = useTranslations("liveroom.existsCheck");
  const currentUserId = useAuthStore((state) => state.user?.userId ?? null);
  const queryClient = useQueryClient();

  const { data, isLoading, isError } = useParticipants({ roomCode });
  const participants: ParticipantSummary[] = data?.data ?? [];

  useEffect(() => {
    if (!roomCode) return;
    const handle = subscribeRoomParticipants(roomCode, (event: ParticipantWsEvent) => {
      if (event.type === "PARTICIPANT_JOINED") {
        const joinedAt = event.timestamp ?? new Date().toISOString();
        const newP: ParticipantSummary = {
          participantId: event.userId ?? String(Date.now()),
          userId: event.userId ?? "",
          displayName: event.displayName ?? "Listener",
          roleAtJoin: event.roleAtJoin ?? "USER",
          joinedAt,
          micMuted: true,
          cameraOff: true,
          lastSeenAt: joinedAt,
        };
        queryClient.setQueryData(liveRoomParticipantsKey(roomCode), (old: unknown) => {
          if (!old || typeof old !== "object" || !("data" in old)) {
            return { data: [newP] };
          }
          const list = ((old as { data?: ParticipantSummary[] }).data ?? []).slice();
          if (list.some((p) => p.userId === newP.userId)) {
            return old;
          }
          list.push(newP);
          return { ...(old as Record<string, unknown>), data: list };
        });
      } else if (event.type === "PARTICIPANT_LEFT") {
        const targetUserId = event.userId;
        if (!targetUserId) return;
        queryClient.setQueryData(liveRoomParticipantsKey(roomCode), (old: unknown) => {
          if (!old || typeof old !== "object" || !("data" in old)) return old;
          const list = ((old as { data?: ParticipantSummary[] }).data ?? []).filter(
            (p) => p.userId !== targetUserId,
          );
          return { ...(old as Record<string, unknown>), data: list };
        });
      }
    });
    return () => {
      handle.unsubscribe();
    };
  }, [queryClient, roomCode]);

  if (isLoading) {
    return (
      <div className="flex items-center gap-2 text-sm text-neutral-500">
        <Spinner size="sm" />
        <span>{tExists("checking")}</span>
      </div>
    );
  }

  if (isError && participants.length === 0) {
    return (
      <p className="text-xs text-red-600 dark:text-red-400">
        {t("empty")}
      </p>
    );
  }

  return (
    <div className="flex flex-col gap-3">
      <div className="flex items-center justify-between">
        <h2 className="text-base font-semibold text-black dark:text-white">
          {t("title")}
        </h2>
        <span className="text-xs text-neutral-500 dark:text-neutral-400">
          {t("count", { count: participants.length })}
        </span>
      </div>

      {participants.length === 0 ? (
        <div className="rounded-lg border border-dashed border-neutral-200 p-6 text-center text-xs text-neutral-500 dark:border-neutral-800 dark:text-neutral-400">
          {t("empty")}
        </div>
      ) : (
        <ul className="flex flex-col divide-y divide-neutral-200 rounded-lg border border-neutral-200 dark:divide-neutral-800 dark:border-neutral-800">
          {participants.map((p) => {
            const isHost = hostUserId && p.userId === hostUserId;
            const isCurrent = currentUserId && p.userId === currentUserId;
            return (
              <li
                key={`${p.participantId}-${p.userId}`}
                className="flex items-center justify-between gap-3 px-4 py-3 text-sm"
              >
                <div className="flex flex-col gap-0.5 min-w-0">
                  <span className="truncate font-medium text-black dark:text-white">
                    {p.displayName}
                    {isHost && (
                      <span className="ml-2 inline-flex items-center rounded border border-purple-300 px-1.5 py-0.5 text-[10px] font-semibold uppercase text-purple-700 dark:border-purple-700 dark:text-purple-300">
                        {t("hostBadge")}
                      </span>
                    )}
                    {isCurrent && (
                      <span className="ml-2 inline-flex items-center rounded border border-blue-300 px-1.5 py-0.5 text-[10px] font-semibold uppercase text-blue-700 dark:border-blue-700 dark:text-blue-300">
                        {t("youBadge")}
                      </span>
                    )}
                  </span>
                  <span className="text-xs text-neutral-500 dark:text-neutral-400">
                    {p.roleAtJoin}
                  </span>
                </div>
                <span className="shrink-0 text-xs text-neutral-500 dark:text-neutral-400">
                  {t("joinedAt", { time: formatTime(p.joinedAt) })}
                </span>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
