import type { RtcConfig } from "../types";

interface ManagerOptions {
  config: RtcConfig;
  publishOffer: (targetUserId: string, sdp: string) => void;
  publishAnswer: (targetUserId: string, sdp: string) => void;
  publishIce: (targetUserId: string, candidate: RTCIceCandidateInit) => void;
  onRemoteStream: (userId: string, stream: MediaStream | null) => void;
  onRemoteVideoActive: (userId: string, active: boolean) => void;
  onPeerState: (userId: string, state: RTCPeerConnectionState) => void;
  onPayloadTooLarge?: () => void;
}

interface Peer {
  pc: RTCPeerConnection;
  remoteStream: MediaStream;
  pendingIce: RTCIceCandidateInit[];
  polite: boolean;
  mayOffer: boolean;
  makingOffer: boolean;
  offeredAt: number;
  iceRestarts: number;
  closed: boolean;
}

const MAX_ICE_RESTARTS = 2;
const MAX_ORPHAN_ICE = 30;
const MAX_RECALLS = 3;
const MEDIA_KINDS = ["audio", "video"] as const;

const VIDEO_BUDGET_BPS = 1_200_000;
const VIDEO_MIN_BPS = 120_000;
const VIDEO_MAX_BPS = 600_000;
const AUDIO_MAX_BPS = 48_000;

function isStopped(transceiver: RTCRtpTransceiver): boolean {
  return transceiver.direction === "stopped" || transceiver.currentDirection === "stopped";
}

export class PeerConnectionManager {
  private peers = new Map<string, Peer>();
  private orphanIce = new Map<string, RTCIceCandidateInit[]>();
  private recalls = new Map<string, number>();
  private audioTrack: MediaStreamTrack | null = null;
  private videoTrack: MediaStreamTrack | null = null;
  private destroyed = false;

  constructor(private readonly options: ManagerOptions) {}

  get peerIds(): string[] {
    return [...this.peers.keys()];
  }

  hasPeer(userId: string): boolean {
    return this.peers.has(userId);
  }

  setAudioTrack(track: MediaStreamTrack | null): void {
    this.audioTrack = track;
    this.peers.forEach((peer) => this.applyLocalTracks(peer.pc, false));
  }

  setVideoTrack(track: MediaStreamTrack | null): void {
    this.videoTrack = track;
    this.peers.forEach((peer) => this.applyLocalTracks(peer.pc, false));
  }

  async callPeer(userId: string): Promise<void> {
    if (this.destroyed) return;
    const peer = this.ensurePeer(userId, true);
    peer.mayOffer = true;
    await this.sendOffer(userId, peer);
  }

  isStalled(userId: string, thresholdMs: number): boolean {
    const peer = this.peers.get(userId);
    if (!peer || peer.closed) return false;
    if (peer.pc.connectionState === "connected") return false;
    if (peer.offeredAt === 0) return false;
    return Date.now() - peer.offeredAt >= thresholdMs;
  }

  async recall(userId: string): Promise<void> {
    if (this.destroyed) return;
    const attempts = this.recalls.get(userId) ?? 0;
    if (attempts >= MAX_RECALLS) return;
    this.removePeer(userId);
    this.recalls.set(userId, attempts + 1);
    await this.callPeer(userId);
  }

  async handleOffer(fromUserId: string, sdp: string): Promise<void> {
    if (this.destroyed) return;
    const peer = this.ensurePeer(fromUserId, false);
    const collision = peer.makingOffer || peer.pc.signalingState !== "stable";
    if (collision && !peer.polite) return;

    try {
      await peer.pc.setRemoteDescription({ type: "offer", sdp });
      await this.flushIce(peer);
      this.applyLocalTracks(peer.pc, false);
      await peer.pc.setLocalDescription();
      this.rebalanceEncodings();
    } catch {
      return;
    }

    const answer = peer.pc.localDescription?.sdp ?? "";
    if (!answer || !this.withinSdpLimit(answer)) return;
    this.options.publishAnswer(fromUserId, answer);
  }

  async handleAnswer(fromUserId: string, sdp: string): Promise<void> {
    const peer = this.peers.get(fromUserId);
    if (!peer || peer.closed) return;
    if (peer.pc.signalingState !== "have-local-offer") return;
    try {
      await peer.pc.setRemoteDescription({ type: "answer", sdp });
      await this.flushIce(peer);
      this.rebalanceEncodings();
    } catch {

    }
  }

  async handleIce(fromUserId: string, candidate: RTCIceCandidateInit): Promise<void> {
    const peer = this.peers.get(fromUserId);
    if (!peer || peer.closed) {
      this.queueOrphanIce(fromUserId, candidate);
      return;
    }
    if (!peer.pc.remoteDescription) {
      peer.pendingIce.push(candidate);
      return;
    }
    try {
      await peer.pc.addIceCandidate(candidate);
    } catch {

    }
  }

  removePeer(userId: string): void {
    const peer = this.peers.get(userId);
    this.orphanIce.delete(userId);
    this.recalls.delete(userId);
    if (!peer) return;
    peer.closed = true;
    peer.pc.onicecandidate = null;
    peer.pc.ontrack = null;
    peer.pc.onconnectionstatechange = null;
    peer.pc.oniceconnectionstatechange = null;
    peer.pc.onnegotiationneeded = null;
    peer.remoteStream.getTracks().forEach((track) => {
      track.onmute = null;
      track.onunmute = null;
      track.onended = null;
    });
    try {
      peer.pc.close();
    } catch {

    }
    this.peers.delete(userId);
    this.rebalanceEncodings();
    this.options.onRemoteStream(userId, null);
  }

  destroy(): void {
    this.destroyed = true;
    this.peerIds.forEach((userId) => this.removePeer(userId));
    this.orphanIce.clear();
    this.recalls.clear();
  }

  private ensurePeer(userId: string, initiator: boolean): Peer {
    const existing = this.peers.get(userId);
    if (existing) return existing;

    const pc = new RTCPeerConnection({
      iceServers: this.options.config.iceServers.map((server) => ({
        urls: server.urls,
        username: server.username ?? undefined,
        credential: server.credential ?? undefined,
      })),
    });

    const peer: Peer = {
      pc,
      remoteStream: new MediaStream(),
      pendingIce: [],
      polite: !initiator,
      mayOffer: initiator,
      makingOffer: false,
      offeredAt: 0,
      iceRestarts: 0,
      closed: false,
    };

    pc.onicecandidate = (event) => {
      if (!event.candidate) return;
      const init = event.candidate.toJSON();
      if ((init.candidate ?? "").length > this.options.config.maxCandidateLength) {
        this.options.onPayloadTooLarge?.();
        return;
      }
      this.options.publishIce(userId, init);
    };

    pc.ontrack = (event) => {
      this.attachRemoteTrack(userId, peer, event.track);
    };

    pc.onnegotiationneeded = () => {
      if (!peer.mayOffer || peer.closed) return;
      void this.sendOffer(userId, peer);
    };

    pc.onconnectionstatechange = () => {
      if (peer.closed) return;
      this.options.onPeerState(userId, pc.connectionState);
      if (pc.connectionState === "connected") {
        this.recalls.delete(userId);
        this.rebalanceEncodings();
      }
      if (pc.connectionState === "failed") this.recover(userId, peer);
    };

    if (initiator) this.applyLocalTracks(pc, true);

    this.peers.set(userId, peer);
    this.rebalanceEncodings();
    this.options.onPeerState(userId, pc.connectionState);
    void this.flushOrphanIce(userId, peer);
    return peer;
  }

  private rebalanceEncodings(): void {
    const share = Math.floor(VIDEO_BUDGET_BPS / Math.max(1, this.peers.size));
    const videoBitrate = Math.min(VIDEO_MAX_BPS, Math.max(VIDEO_MIN_BPS, share));
    const scaleDown = videoBitrate >= 400_000 ? 1 : videoBitrate >= 250_000 ? 1.5 : 2;

    this.peers.forEach((peer) => {
      if (peer.closed) return;
      peer.pc.getTransceivers().forEach((transceiver) => {
        if (isStopped(transceiver)) return;
        const audio = transceiver.receiver.track.kind === "audio";
        this.limitSender(
          transceiver.sender,
          audio ? AUDIO_MAX_BPS : videoBitrate,
          audio ? undefined : scaleDown,
        );
      });
    });
  }

  private limitSender(
    sender: RTCRtpSender,
    maxBitrate: number,
    scaleResolutionDownBy?: number,
  ): void {
    const params = sender.getParameters();
    const encodings = params.encodings?.length ? params.encodings : [{}];
    const current = encodings[0];
    if (
      current.maxBitrate === maxBitrate &&
      current.scaleResolutionDownBy === scaleResolutionDownBy
    ) {
      return;
    }

    encodings[0] = { ...current, maxBitrate, scaleResolutionDownBy };
    void sender.setParameters({ ...params, encodings }).catch(() => undefined);
  }

  private applyLocalTracks(pc: RTCPeerConnection, create: boolean): void {
    MEDIA_KINDS.forEach((kind) => {
      let transceiver = pc
        .getTransceivers()
        .find((candidate) => !isStopped(candidate) && candidate.receiver.track.kind === kind);

      if (!transceiver) {
        if (!create) return;
        transceiver = pc.addTransceiver(kind, { direction: "sendrecv" });
      }
      if (transceiver.direction !== "sendrecv") transceiver.direction = "sendrecv";

      const track = kind === "audio" ? this.audioTrack : this.videoTrack;
      if (transceiver.sender.track === track) return;
      void transceiver.sender.replaceTrack(track).catch(() => undefined);
    });
  }

  private attachRemoteTrack(userId: string, peer: Peer, track: MediaStreamTrack): void {
    if (peer.closed) return;
    if (!peer.remoteStream.getTracks().includes(track)) {
      peer.remoteStream
        .getTracks()
        .filter((existing) => existing.kind === track.kind)
        .forEach((stale) => peer.remoteStream.removeTrack(stale));
      peer.remoteStream.addTrack(track);
    }

    if (track.kind === "video") {
      const report = () => {
        if (peer.closed) return;
        this.options.onRemoteVideoActive(userId, !track.muted && track.readyState === "live");
      };
      track.onmute = report;
      track.onunmute = report;
      track.onended = report;
      report();
    }

    this.options.onRemoteStream(userId, peer.remoteStream);
  }

  private async sendOffer(userId: string, peer: Peer): Promise<void> {
    if (peer.closed || peer.makingOffer) return;
    peer.makingOffer = true;
    try {
      await peer.pc.setLocalDescription();
      const sdp = peer.pc.localDescription?.sdp ?? "";
      if (!sdp || !this.withinSdpLimit(sdp)) return;
      peer.offeredAt = Date.now();
      this.options.publishOffer(userId, sdp);
    } catch {

    } finally {
      peer.makingOffer = false;
    }
  }

  private recover(userId: string, peer: Peer): void {
    if (peer.iceRestarts >= MAX_ICE_RESTARTS) return;
    peer.iceRestarts += 1;
    peer.mayOffer = true;
    try {
      peer.pc.restartIce();
    } catch {

    }
  }

  private queueOrphanIce(userId: string, candidate: RTCIceCandidateInit): void {
    const queued = this.orphanIce.get(userId) ?? [];
    if (queued.length >= MAX_ORPHAN_ICE) return;
    queued.push(candidate);
    this.orphanIce.set(userId, queued);
  }

  private async flushOrphanIce(userId: string, peer: Peer): Promise<void> {
    const queued = this.orphanIce.get(userId);
    if (!queued) return;
    this.orphanIce.delete(userId);
    peer.pendingIce.push(...queued);
    if (peer.pc.remoteDescription) await this.flushIce(peer);
  }

  private async flushIce(peer: Peer): Promise<void> {
    const queued = peer.pendingIce;
    peer.pendingIce = [];
    for (const candidate of queued) {
      try {
        await peer.pc.addIceCandidate(candidate);
      } catch {

      }
    }
  }

  private withinSdpLimit(sdp: string): boolean {
    if (sdp.length <= this.options.config.maxSdpLength) return true;
    this.options.onPayloadTooLarge?.();
    return false;
  }
}