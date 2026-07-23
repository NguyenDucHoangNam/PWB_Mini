import type { LiveRoomJoinRequest, LiveRoomViewerStatus } from "../types";

export function buildPendingRequestFromStatus(
  status: LiveRoomViewerStatus,
  viewerUserId: string | null,
): LiveRoomJoinRequest {
  return {
    id: status.pendingRequestId ?? "",
    roomCode: status.roomCode,
    userId: viewerUserId ?? "",
    displayName: "",
    message: null,
    status: status.pendingStatus ?? "PENDING",
    decisionReason: null,
    decidedByUserId: null,
    decidedAt: null,
    createdAt: status.createdAt,
  };
}
