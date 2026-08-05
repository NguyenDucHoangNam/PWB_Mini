"use client";

import { useMemo } from "react";
import { VideoTile } from "./video-tile";
import { useLiveroomStore } from "../../stores/use-liveroom-store";
import { computeGrid, gridTemplateStyle } from "../../utils/grid-layout";
import { sortParticipants } from "../../utils/participant-sort";

export function VideoGrid({ localStream }: { localStream: MediaStream | null }) {
  const participants = useLiveroomStore((state) => state.participants);
  const peers = useLiveroomStore((state) => state.rtc.peers);
  const myUserId = useLiveroomStore((state) => state.myUserId);

  const ordered = useMemo(
    () => sortParticipants(Object.values(participants)),
    [participants],
  );

  const shape = computeGrid(ordered.length);

  if (ordered.length === 0) return <div className="flex-1" />;

  return (
    <div className="min-h-0 flex-1 p-2 md:p-3">
      <div
        className="grid size-full gap-2 md:gap-3"
        style={gridTemplateStyle(shape)}
      >
        {ordered.map((participant, index) => {
          const isMe = participant.userId === myUserId;
          return (
            <VideoTile
              key={participant.userId}
              participant={participant}
              isMe={isMe}
              stream={isMe ? localStream : (peers[participant.userId]?.stream ?? null)}
              connectionState={peers[participant.userId]?.state}
              featured={shape.featureFirst && index === 0}
            />
          );
        })}
      </div>
    </div>
  );
}