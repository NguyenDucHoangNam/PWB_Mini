import {
  sendSignalAnswer,
  sendSignalIce,
  sendSignalOffer,
  subscribeSignalingAnswers,
  subscribeSignalingIce,
  subscribeSignalingOffers,
} from "../api/ws";
import type { PeerSignalEnvelope } from "../types";

interface PeerEntry {
  connection: RTCPeerConnection;
  remoteStream: MediaStream;
  displayName: string;
  iceBuffer: RTCIceCandidateInit[];
  hasRemoteDescription: boolean;
  pendingIceFlushTimer: ReturnType<typeof setTimeout> | null;
  reconnectAttempts?: number;
  isReconnecting?: boolean;
}

export interface WebRTCPeerManagerOptions {
  roomCode: string;
  localUserId: string;
  localUserDisplayName: string;
}

interface RemoteStreamHandler {
  onRemoteStream: (userId: string, displayName: string, stream: MediaStream) => void;
  onPeerLeft: (userId: string) => void;
}

const ICE_SERVERS: RTCIceServer[] = [
  { urls: "stun:stun.l.google.com:19302" },
];

const BASE_RECONNECT_DELAY_MS = 1_000;
const MAX_RECONNECT_DELAY_MS = 8_000;
const MAX_RECONNECT_ATTEMPTS = 3;

const peerManagerRegistry = new Map<string, WebRTCPeerManager>();

export function getOrCreatePeerManager(
  options: WebRTCPeerManagerOptions,
): WebRTCPeerManager {
  const existing = peerManagerRegistry.get(options.roomCode);
  if (existing && !existing.isDisposed) {
    return existing;
  }
  const manager = new WebRTCPeerManager(options);
  peerManagerRegistry.set(options.roomCode, manager);
  return manager;
}

export function disposePeerManager(roomCode: string): void {
  const manager = peerManagerRegistry.get(roomCode);
  if (!manager) return;
  manager.close();
  peerManagerRegistry.delete(roomCode);
}

export class WebRTCPeerManager {
  private readonly roomCode: string;
  private readonly localUserId: string;
  private readonly peers: Map<string, PeerEntry> = new Map();
  private readonly displayNames: Map<string, string> = new Map();
  private readonly remoteStreamHandlers: Set<RemoteStreamHandler["onRemoteStream"]> = new Set();
  private readonly peerLeftHandlers: Set<RemoteStreamHandler["onPeerLeft"]> = new Set();
  private readonly pendingPeerUserIds: Set<string> = new Set();
  private unsubscribers: Array<() => void> = [];
  private disposed = false;
  private readonly localStream_: { current: MediaStream | null } = { current: null };
  private readonly senderKindMap: WeakMap<RTCRtpSender, string> = new WeakMap();

  private get localStream(): MediaStream | null {
    return this.localStream_.current;
  }

  private set localStream(value: MediaStream | null) {
    this.localStream_.current = value;
  }

  constructor(options: WebRTCPeerManagerOptions) {
    this.roomCode = options.roomCode;
    this.localUserId = options.localUserId;

    const offerSub = subscribeSignalingOffers(options.roomCode, (event) => {
      if (event.fromUserId === this.localUserId) return;
      if (event.toUserId !== this.localUserId) return;
      this.handleRemoteOffer(event).catch((err) => this.logError("handle offer", err));
    });
    this.unsubscribers.push(() => offerSub.unsubscribe());

    const answerSub = subscribeSignalingAnswers(options.roomCode, (event) => {
      if (event.fromUserId === this.localUserId) return;
      if (event.toUserId !== this.localUserId) return;
      this.handleRemoteAnswer(event).catch((err) => this.logError("handle answer", err));
    });
    this.unsubscribers.push(() => answerSub.unsubscribe());

    const iceSub = subscribeSignalingIce(options.roomCode, (event) => {
      if (event.fromUserId === this.localUserId) return;
      if (event.toUserId !== this.localUserId) return;
      this.handleRemoteIce(event).catch((err) => this.logError("handle ice", err));
    });
    this.unsubscribers.push(() => iceSub.unsubscribe());
  }

  async addPeer(localStream: MediaStream | null, remoteUserId: string, displayName?: string): Promise<void> {
    if (this.disposed) return;
    if (remoteUserId === this.localUserId) return;
    if (displayName) {
      this.displayNames.set(remoteUserId, displayName);
    }
    if (!localStream) {
      this.pendingPeerUserIds.add(remoteUserId);
      return;
    }
    this.localStream = localStream;

    const existing = this.peers.get(remoteUserId);
    if (existing) {
      if (existing.isReconnecting) {
        return;
      }
      this.replaceLocalTracks(existing.connection, localStream);
      return;
    }

    const peerConnection = this.createPeerConnection(remoteUserId, localStream);
    const remoteStream = new MediaStream();
    const resolvedDisplayName = displayName ?? this.displayNames.get(remoteUserId) ?? remoteUserId;
    const entry: PeerEntry = {
      connection: peerConnection,
      remoteStream,
      displayName: resolvedDisplayName,
      iceBuffer: [],
      hasRemoteDescription: false,
      pendingIceFlushTimer: null,
    };
    this.peers.set(remoteUserId, entry);

    const offer = await peerConnection.createOffer();
    await peerConnection.setLocalDescription(offer);
    sendSignalOffer(this.roomCode, remoteUserId, {
      sdp: offer.sdp ?? undefined,
    });
  }

  async queuePeerIfNeeded(remoteUserId: string, displayName?: string): Promise<void> {
    if (this.disposed) return;
    if (remoteUserId === this.localUserId) return;
    if (displayName) {
      this.displayNames.set(remoteUserId, displayName);
      const existing = this.peers.get(remoteUserId);
      if (existing) {
        existing.displayName = displayName;
      }
    }
    if (this.peers.has(remoteUserId)) return;
    if (this.localStream) {
      await this.addPeer(this.localStream, remoteUserId, displayName);
      return;
    }
    this.pendingPeerUserIds.add(remoteUserId);
  }

  async onPeerJoined(remoteUserId: string, localStream: MediaStream): Promise<void> {
    if (this.disposed) return;
    await this.addPeer(localStream, remoteUserId);
  }

  removePeer(remoteUserId: string): void {
    const entry = this.peers.get(remoteUserId);
    this.pendingPeerUserIds.delete(remoteUserId);
    if (!entry) return;
    try {
      entry.connection.close();
    } catch {
      /* ignore */
    }
    this.peers.delete(remoteUserId);
    this.peerLeftHandlers.forEach((handler) => handler(remoteUserId));
  }

  setLocalStreamForAllPeers(stream: MediaStream | null): void {
    if (!stream) return;
    this.localStream = stream;
    for (const entry of this.peers.values()) {
      this.replaceLocalTracks(entry.connection, stream);
    }
    if (this.pendingPeerUserIds.size === 0) return;
    const queued = Array.from(this.pendingPeerUserIds);
    this.pendingPeerUserIds.clear();
    for (const userId of queued) {
      void this.addPeer(stream, userId);
    }
  }

  replaceVideoTrackForAllPeers(track: MediaStreamTrack | null): void {
    for (const entry of this.peers.values()) {
      const senders = entry.connection.getSenders();
      const videoSender = senders.find(
        (s) => s.track?.kind === "video" || (!s.track && this.senderKindMap.get(s) === "video"),
      );
      if (videoSender) {
        void videoSender.replaceTrack(track);
      } else if (track) {
        const dummyStream = new MediaStream([track]);
        entry.connection.addTrack(track, dummyStream);
        this.senderKindMap.set(
          senders.find((s) => s.track === track) ?? entry.connection.getSenders().find((s) => s.track === track)!,
          "video",
        );
      }
    }
  }

  setDisplayNameForUser(userId: string, displayName: string): void {
    this.displayNames.set(userId, displayName);
    const existing = this.peers.get(userId);
    if (existing) {
      existing.displayName = displayName;
      this.remoteStreamHandlers.forEach((handler) =>
        handler(userId, displayName, existing.remoteStream),
      );
    }
  }

  onRemoteStream(handler: RemoteStreamHandler["onRemoteStream"]): () => void {
    this.remoteStreamHandlers.add(handler);
    for (const [userId, entry] of this.peers.entries()) {
      if (entry.connection.connectionState === "connected" || entry.remoteStream.getTracks().length > 0) {
        handler(userId, entry.displayName, entry.remoteStream);
      }
    }
    return () => this.remoteStreamHandlers.delete(handler);
  }

  onPeerLeft(handler: RemoteStreamHandler["onPeerLeft"]): () => void {
    this.peerLeftHandlers.add(handler);
    return () => this.peerLeftHandlers.delete(handler);
  }

  get isDisposed(): boolean {
    return this.disposed;
  }

  close(): void {
    if (this.disposed) return;
    this.disposed = true;
    this.unsubscribers.forEach((unsub) => unsub());
    this.unsubscribers = [];
    for (const [userId, entry] of this.peers.entries()) {
      try {
        entry.connection.close();
      } catch {
        /* ignore */
      }
      this.peers.delete(userId);
    }
    this.pendingPeerUserIds.clear();
    this.remoteStreamHandlers.clear();
    this.peerLeftHandlers.clear();
    this.localStream = null;
  }

  private createPeerConnection(remoteUserId: string, localStream: MediaStream): RTCPeerConnection {
    const peerConnection = new RTCPeerConnection({ iceServers: ICE_SERVERS });

    localStream.getTracks().forEach((track) => {
      peerConnection.addTrack(track, localStream);
    });

    for (const sender of peerConnection.getSenders()) {
      if (sender.track) {
        this.senderKindMap.set(sender, sender.track.kind);
      }
    }

    this.attachPeerConnectionHandlers(remoteUserId, peerConnection);
    return peerConnection;
  }

  private async handleRemoteOffer(event: PeerSignalEnvelope): Promise<void> {
    const remoteUserId = event.fromUserId;
    let entry = this.peers.get(remoteUserId);

    if (entry) {
      const state = entry.connection.signalingState;
      if (state !== "stable" && state !== "have-remote-offer") {
        try {
          entry.connection.close();
        } catch {
          /* ignore */
        }
        entry = undefined;
        this.peers.delete(remoteUserId);
      }
    }

    if (!entry) {
      const peerConnection = new RTCPeerConnection({ iceServers: ICE_SERVERS });

      if (this.localStream) {
        this.localStream.getTracks().forEach((track) => {
          peerConnection.addTrack(track, this.localStream!);
        });
        for (const sender of peerConnection.getSenders()) {
          if (sender.track) {
            this.senderKindMap.set(sender, sender.track.kind);
          }
        }
      }

      const remoteStream = new MediaStream();
      const resolvedDisplayName = this.displayNames.get(remoteUserId) ?? remoteUserId;
      entry = {
        connection: peerConnection,
        remoteStream,
        displayName: resolvedDisplayName,
        iceBuffer: [],
        hasRemoteDescription: false,
        pendingIceFlushTimer: null,
      };
      this.peers.set(remoteUserId, entry);
      this.attachPeerConnectionHandlers(remoteUserId, peerConnection);
    }

    await entry.connection.setRemoteDescription({
      type: "offer",
      sdp: event.payload.sdp ?? "",
    });
    entry.hasRemoteDescription = true;
    const answer = await entry.connection.createAnswer();
    await entry.connection.setLocalDescription(answer);
    sendSignalAnswer(this.roomCode, remoteUserId, {
      sdp: answer.sdp ?? undefined,
    });
    this.flushIceBuffer(remoteUserId);
  }

  private async handleRemoteAnswer(event: PeerSignalEnvelope): Promise<void> {
    const remoteUserId = event.fromUserId;
    const entry = this.peers.get(remoteUserId);
    if (!entry) return;
    await entry.connection.setRemoteDescription({
      type: "answer",
      sdp: event.payload.sdp ?? "",
    });
    entry.hasRemoteDescription = true;
    this.flushIceBuffer(remoteUserId);
  }

  private async handleRemoteIce(event: PeerSignalEnvelope): Promise<void> {
    const remoteUserId = event.fromUserId;
    const entry = this.peers.get(remoteUserId);
    if (!entry) return;
    if (!event.payload.candidate) return;
    const candidateInit: RTCIceCandidateInit = {
      candidate: event.payload.candidate,
      sdpMid: event.payload.sdpMid ?? null,
      sdpMLineIndex: event.payload.sdpMLineIndex ?? null,
      usernameFragment: event.payload.usernameFragment ?? null,
    };
    if (!entry.hasRemoteDescription) {
      entry.iceBuffer.push(candidateInit);
      return;
    }
    try {
      await entry.connection.addIceCandidate(candidateInit);
    } catch (err) {
      this.logError(`addIceCandidate ${remoteUserId}`, err);
    }
  }

  private flushIceBuffer(remoteUserId: string): void {
    const entry = this.peers.get(remoteUserId);
    if (!entry) return;
    if (entry.pendingIceFlushTimer) {
      clearTimeout(entry.pendingIceFlushTimer);
      entry.pendingIceFlushTimer = null;
    }
    const buffer = entry.iceBuffer;
    entry.iceBuffer = [];
    for (const candidate of buffer) {
      entry.connection
        .addIceCandidate(candidate)
        .catch((err) => this.logError(`flushIceCandidate ${remoteUserId}`, err));
    }
  }

  private attachPeerConnectionHandlers(remoteUserId: string, peerConnection: RTCPeerConnection): void {
    peerConnection.ontrack = (event) => {
      const target = this.peers.get(remoteUserId);
      if (!target) return;
      if (event.track) {
        const existingTrack = target.remoteStream.getTrackById(event.track.id);
        if (!existingTrack) {
          target.remoteStream.addTrack(event.track);
        }
      }
      for (const track of event.streams[0]?.getTracks() ?? []) {
        const existingTrack = target.remoteStream.getTrackById(track.id);
        if (!existingTrack) {
          target.remoteStream.addTrack(track);
        }
      }
      this.remoteStreamHandlers.forEach((handler) =>
        handler(remoteUserId, target.displayName, target.remoteStream),
      );
    };

    peerConnection.onicecandidate = (event) => {
      if (!event.candidate) return;
      sendSignalIce(this.roomCode, remoteUserId, {
        candidate: event.candidate.candidate,
        sdpMid: event.candidate.sdpMid,
        sdpMLineIndex: event.candidate.sdpMLineIndex,
        usernameFragment: event.candidate.usernameFragment,
      });
    };

    peerConnection.onconnectionstatechange = () => {
      if (peerConnection.connectionState === "failed") {
        this.logError(
          `peer ${remoteUserId} connection failed`,
          new Error(peerConnection.connectionState ?? "unknown"),
        );
        this.scheduleReconnect(remoteUserId);
      }
    };
  }

  private scheduleReconnect(remoteUserId: string): void {
    const entry = this.peers.get(remoteUserId);
    if (!entry) return;
    const attempts = (entry.reconnectAttempts ?? 0) + 1;
    if (attempts > MAX_RECONNECT_ATTEMPTS) {
      this.removePeer(remoteUserId);
      return;
    }
    entry.reconnectAttempts = attempts;
    entry.isReconnecting = true;
    const delay = Math.min(BASE_RECONNECT_DELAY_MS * 2 ** (attempts - 1), MAX_RECONNECT_DELAY_MS);
    const displayName = entry.displayName;
    setTimeout(() => {
      const current = this.peers.get(remoteUserId);
      if (!current || current !== entry) return;
      if (!this.localStream) return;
      if (!current.isReconnecting) return;
      try {
        current.connection.close();
      } catch {
        /* ignore */
      }
      this.peers.delete(remoteUserId);
      current.isReconnecting = false;
      void this.addPeer(this.localStream, remoteUserId, displayName).catch((err) =>
        this.logError(`reconnect ${remoteUserId}`, err),
      );
    }, delay);
  }

  private replaceLocalTracks(connection: RTCPeerConnection, stream: MediaStream): void {
    const senders = connection.getSenders();
    const newTracks = stream.getTracks();
    const newTracksByKind = new Map<string, MediaStreamTrack>();
    for (const track of newTracks) {
      newTracksByKind.set(track.kind, track);
    }

    for (const sender of senders) {
      const senderKind = sender.track?.kind ?? this.senderKindMap.get(sender);
      if (!senderKind) continue;
      const replacement = newTracksByKind.get(senderKind);
      if (replacement && sender.track?.id !== replacement.id) {
        void sender.replaceTrack(replacement);
        this.senderKindMap.set(sender, senderKind);
      } else if (!replacement) {
        this.senderKindMap.set(sender, senderKind);
        void sender.replaceTrack(null);
      }
    }

    for (const track of newTracks) {
      const hasSender = senders.some(
        (s) => (s.track?.kind ?? this.senderKindMap.get(s)) === track.kind,
      );
      if (!hasSender) {
        connection.addTrack(track, stream);
        const addedSender = connection.getSenders().find((s) => s.track === track);
        if (addedSender) {
          this.senderKindMap.set(addedSender, track.kind);
        }
      }
    }
  }

  private logError(context: string, err: unknown): void {
    if (typeof console !== "undefined") {
      console.warn(`[WebRTCPeerManager] ${context}:`, err);
    }
  }
}
