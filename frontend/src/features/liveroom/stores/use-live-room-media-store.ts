"use client";

import { create } from "zustand";

export interface RemotePeerStream {
  userId: string;
  displayName: string;
  stream: MediaStream;
}

export interface MediaSocket {
  sendOffer: (toUserId: string, sdp: RTCSessionDescriptionInit) => void;
  sendAnswer: (toUserId: string, sdp: RTCSessionDescriptionInit) => void;
  sendIce: (
    toUserId: string,
    candidate: RTCIceCandidateInit,
  ) => void;
}

export interface PeerManagerHandle {
  addPeer: (remoteUserId: string, remoteDisplayName: string) => Promise<void>;
  removePeer: (remoteUserId: string) => void;
  close: () => void;
  onRemoteStream: (handler: (userId: string, displayName: string, stream: MediaStream) => void) => () => void;
  onPeerLeft: (handler: (userId: string) => void) => () => void;
  replaceAudioTrackForAllPeers: (track: MediaStreamTrack | null) => void;
}

interface LiveRoomMediaState {
  localStream: MediaStream | null;
  micMuted: boolean;
  cameraOff: boolean;
  errorMessage: string | null;
  remotePeers: RemotePeerStream[];
  peerManager: PeerManagerHandle | null;
  mediaSocket: MediaSocket | null;
  speakingUsers: Set<string>;
  remoteMediaStates: Record<string, { micMuted: boolean; cameraOff: boolean }>;

  setLocalStream: (stream: MediaStream | null) => void;
  setMicMuted: (muted: boolean) => void;
  setCameraOff: (off: boolean) => void;
  setErrorMessage: (msg: string | null) => void;
  setPeerManager: (manager: PeerManagerHandle | null) => void;
  setMediaSocket: (socket: MediaSocket | null) => void;
  upsertRemotePeer: (peer: RemotePeerStream) => void;
  removeRemotePeer: (userId: string) => void;
  setSpeakingUsers: (users: Set<string>) => void;
  addSpeaker: (userId: string) => void;
  removeSpeaker: (userId: string) => void;
  setRemoteMediaState: (userId: string, state: { micMuted: boolean; cameraOff: boolean }) => void;
  bulkSetRemoteMediaStates: (states: Record<string, { micMuted: boolean; cameraOff: boolean }>) => void;
  reset: () => void;
}

export const useLiveRoomMediaStore = create<LiveRoomMediaState>((set) => ({
  localStream: null,
  micMuted: true,
  cameraOff: true,
  errorMessage: null,
  remotePeers: [],
  peerManager: null,
  mediaSocket: null,
  speakingUsers: new Set(),
  remoteMediaStates: {},

  setLocalStream: (stream) => set({ localStream: stream }),
  setMicMuted: (muted) => set({ micMuted: muted }),
  setCameraOff: (off) => set({ cameraOff: off }),
  setErrorMessage: (msg) => set({ errorMessage: msg }),
  setPeerManager: (manager) => set({ peerManager: manager }),
  setMediaSocket: (socket) => set({ mediaSocket: socket }),
  upsertRemotePeer: (peer) =>
    set((state) => {
      const existing = state.remotePeers.findIndex((p) => p.userId === peer.userId);
      if (existing >= 0) {
        const next = state.remotePeers.slice();
        next[existing] = peer;
        return { remotePeers: next };
      }
      return { remotePeers: [...state.remotePeers, peer] };
    }),
  removeRemotePeer: (userId) =>
    set((state) => ({
      remotePeers: state.remotePeers.filter((p) => p.userId !== userId),
    })),
  setSpeakingUsers: (users) => set({ speakingUsers: users }),
  addSpeaker: (userId) =>
    set((state) => {
      if (state.speakingUsers.has(userId)) return state;
      const next = new Set(state.speakingUsers);
      next.add(userId);
      return { speakingUsers: next };
    }),
  removeSpeaker: (userId) =>
    set((state) => {
      if (!state.speakingUsers.has(userId)) return state;
      const next = new Set(state.speakingUsers);
      next.delete(userId);
      return { speakingUsers: next };
    }),
  setRemoteMediaState: (userId, mediaState) =>
    set((state) => {
      const existing = state.remoteMediaStates[userId];
      if (existing && existing.micMuted === mediaState.micMuted && existing.cameraOff === mediaState.cameraOff) return state;
      return { remoteMediaStates: { ...state.remoteMediaStates, [userId]: mediaState } };
    }),
  bulkSetRemoteMediaStates: (states) =>
    set((state) => {
      let changed = false;
      const next = { ...state.remoteMediaStates };
      for (const [userId, ms] of Object.entries(states)) {
        const existing = next[userId];
        if (existing && existing.micMuted === ms.micMuted && existing.cameraOff === ms.cameraOff) continue;
        next[userId] = ms;
        changed = true;
      }
      return changed ? { remoteMediaStates: next } : state;
    }),
  reset: () =>
    set({
      localStream: null,
      micMuted: false,
      cameraOff: false,
      errorMessage: null,
      remotePeers: [],
      peerManager: null,
      mediaSocket: null,
      speakingUsers: new Set(),
      remoteMediaStates: {},
    }),
}));

export function applyTrackMutedFlag(stream: MediaStream | null, kind: "audio" | "video", muted: boolean): void {
  if (!stream) return;
  for (const track of stream.getTracks()) {
    if (track.kind === kind) {
      track.enabled = !muted;
    }
  }
}
