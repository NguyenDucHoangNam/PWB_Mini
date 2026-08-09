"use client";

import { useTranslations } from "next-intl";
import { NEU_INPUT } from "@/components/ui/neu";
import { MessageCircle, Send } from "lucide-react";
import { Button } from "@/components/ui/button";
import { UserAvatar } from "../ui/user-avatar";
import { useActiveTrackComments } from "../../hooks/use-active-track-comments";
import { appDestinations } from "../../lib/liveroom-destinations";
import { liveroomSocket } from "../../lib/liveroom-socket";
import { useLiveroomStore } from "../../stores/use-liveroom-store";
import { TRACK_COMMENT_MAX_LENGTH } from "../../types";
import { countCodePoints } from "../../utils/count-code-points";
import { displayName } from "../../utils/participant-sort";

export interface Draft {
  songId: string | null;
  text: string;
  pinned: number | null;
}

export const EMPTY_DRAFT: Draft = { songId: null, text: "", pinned: null };

function formatClock(totalSeconds: number): string {
  const safe = Math.max(0, Math.floor(totalSeconds));
  return `${Math.floor(safe / 60)}:${String(safe % 60).padStart(2, "0")}`;
}

export function TrackCommentLane({
  roomId,
  songId,
  position,
  draft,
  onDraftChange,
}: {
  roomId: string;
  songId: string | null;
  position: number;
  draft: Draft;
  onDraftChange: (draft: Draft) => void;
}) {
  const t = useTranslations("liveroom.room.comments");
  const comments = useLiveroomStore((state) => state.trackComments.items);
  const participants = useLiveroomStore((state) => state.participants);
  const connected = useLiveroomStore((state) => state.connection.status === "connected");



  const fresh = draft.songId === songId;
  const text = fresh ? draft.text : "";
  const pinned = fresh ? draft.pinned : null;

  const active = useActiveTrackComments(comments, position);
  const used = countCodePoints(text);
  const remaining = TRACK_COMMENT_MAX_LENGTH - used;
  const canSend = connected && Boolean(songId) && used > 0 && remaining >= 0;

  const send = () => {
    if (!canSend || !songId) return;
    liveroomSocket.publish(appDestinations.commentAdd(roomId), {
      songId,
      positionSeconds: pinned ?? position,
      content: text,
    });
    onDraftChange(EMPTY_DRAFT);
  };

  return (
    <div className="flex flex-wrap items-center gap-x-3 gap-y-2">
      <div className="flex min-w-0 flex-1 basis-64 items-center gap-2 overflow-hidden">
        <MessageCircle className="size-4 shrink-0 text-indigo-600 dark:text-indigo-400" aria-hidden />
        {active.length === 0 ? (
          <p className="truncate text-xs font-medium text-slate-600 dark:text-slate-400">
            {songId ? t("empty") : t("noSong")}
          </p>
        ) : (
          <ul className="flex min-w-0 items-center gap-3 overflow-hidden">
            {active.map((comment) => (
              <li
                key={comment.id}
                className="flex min-w-0 animate-in items-center gap-1.5 fade-in slide-in-from-bottom-1"
              >
                <UserAvatar
                  email={comment.userEmail}
                  avatarUrl={participants[comment.userId]?.avatarUrl ?? null}
                  seed={comment.userId}
                  className="size-5 text-[8px]"
                />
                <span className="shrink-0 text-[10px] font-bold tabular-nums text-slate-600 dark:text-slate-400">
                  {formatClock(comment.positionSeconds)}
                </span>
                <span className="truncate text-xs font-medium text-slate-900 dark:text-slate-100">
                  <span className="font-semibold">{displayName(comment)}</span>{" "}
                  {comment.content}
                </span>
              </li>
            ))}
          </ul>
        )}
      </div>

      <div className="flex shrink-0 items-center gap-2">
        {pinned !== null ? (
          <span className="neu-pressed-sm shrink-0 rounded-full border-none px-2.5 py-0.5 text-[10px] font-bold tabular-nums text-slate-600 dark:text-slate-400">
            {t("atTime", { time: formatClock(pinned) })}
          </span>
        ) : null}
        <input
          value={text}
          disabled={!songId}
          onFocus={() => onDraftChange({ songId, text, pinned: pinned ?? position })}
          onChange={(event) =>
            onDraftChange({ songId, text: event.target.value, pinned: pinned ?? position })
          }
          onKeyDown={(event) => {
            if (event.key === "Enter") {
              event.preventDefault();
              send();
            }
          }}
          placeholder={t("placeholder")}
          aria-label={t("placeholder")}
          className={`${NEU_INPUT} h-10 w-48 rounded-xl disabled:opacity-50 md:w-56`}
        />
        <Button
          size="icon"
          className="size-9 shrink-0"
          aria-label={t("send")}
          disabled={!canSend}
          onClick={send}
        >
          <Send className="size-4" />
        </Button>
      </div>

      {remaining < 0 ? (
        <p className="basis-full text-right text-xs font-semibold text-rose-700 dark:text-rose-400">
          {t("tooLong", { max: TRACK_COMMENT_MAX_LENGTH })}
        </p>
      ) : null}
    </div>
  );
}