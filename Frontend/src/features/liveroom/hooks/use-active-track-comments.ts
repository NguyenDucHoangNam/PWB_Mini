"use client";

import type { TrackComment } from "../types";

const VISIBLE_WINDOW_S = 4;



export function useActiveTrackComments(
  comments: TrackComment[],
  position: number,
): TrackComment[] {
  return comments.filter(
    (comment) =>
      comment.positionSeconds <= position &&
      comment.positionSeconds > position - VISIBLE_WINDOW_S,
  );
}