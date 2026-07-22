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

export class WebRTCPeerManager {
  private readonly roomCode: string;
  private readonly localUserId: string;
  private readonly peers: Map<string, PeerEntry> = new Map();
  private readonly remoteStreamHandlers: Set<RemoteStreamHandler["onRemoteStream"]> = new Set();
  private readonly peerLeftHandlers: Set<RemoteStreamHandler["onPeerLeft"]> = new Set();
  private unsubscribers: Array<() => void> = [];
  private disposed = false;
  private localStream: MediaStream | null = null;

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

  async addPeer(localStream: MediaStream, remoteUserId: string): Promise<void> {
    if (this.disposed) return;
    this.localStream = localStream;

    const existing = this.peers.get(remoteUserId);
    if (existing) {
      this.replaceLocalTracks(existing.connection, localStream);
      return;
    }

    const peerConnection = this.createPeerConnection(remoteUserId, localStream);
    const remoteStream = new MediaStream();
    const entry: PeerEntry = {
      connection: peerConnection,
      remoteStream,
      displayName: remoteUserId,
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

  async onPeerJoined(remoteUserId: string, localStream: MediaStream): Promise<void> {
    if (this.disposed) return;
    await this.addPeer(localStream, remoteUserId);
  }

  removePeer(remoteUserId: string): void {
    const entry = this.peers.get(remoteUserId);
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
  }

  onRemoteStream(handler: RemoteStreamHandler["onRemoteStream"]): () => void {
    this.remoteStreamHandlers.add(handler);
    return () => this.remoteStreamHandlers.delete(handler);
  }

  onPeerLeft(handler: RemoteStreamHandler["onPeerLeft"]): () => void {
    this.peerLeftHandlers.add(handler);
    return () => this.peerLeftHandlers.delete(handler);
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
    this.remoteStreamHandlers.clear();
    this.peerLeftHandlers.clear();
    this.localStream = null;
  }

  private createPeerConnection(remoteUserId: string, localStream: MediaStream): RTCPeerConnection {
    const peerConnection = new RTCPeerConnection({ iceServers: ICE_SERVERS });

    localStream.getTracks().forEach((track) => {
      peerConnection.addTrack(track, localStream);
    });

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
      }

      const remoteStream = new MediaStream();
      entry = {
        connection: peerConnection,
        remoteStream,
        displayName: remoteUserId,
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
      });
    };

    peerConnection.onconnectionstatechange = () => {
      if (peerConnection.connectionState === "failed") {
        this.logError(
          `peer ${remoteUserId} connection failed`,
          new Error(peerConnection.connectionState ?? "unknown"),
        );
      }
    };
  }

  private replaceLocalTracks(connection: RTCPeerConnection, stream: MediaStream): void {
    for (const track of stream.getTracks()) {
      const sender = connection.getSenders().find((s) => s.track?.kind === track.kind);
      if (sender) {
        void sender.replaceTrack(track);
      } else {
        connection.addTrack(track, stream);
      }
    }
  }

  private logError(context: string, err: unknown): void {
    if (typeof console !== "undefined") {
      console.warn(`[WebRTCPeerManager] ${context}:`, err);
    }
  }
}
