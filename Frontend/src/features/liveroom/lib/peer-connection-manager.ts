import type { RtcConfig } from "../types";

interface ManagerOptions {
  config: RtcConfig;
  publishOffer: (targetUserId: string, sdp: string) => void;
  publishAnswer: (targetUserId: string, sdp: string) => void;
  publishIce: (targetUserId: string, candidate: RTCIceCandidateInit) => void;
  onRemoteStream: (userId: string, stream: MediaStream) => void;
  onPeerState: (userId: string, state: RTCPeerConnectionState) => void;
  onPayloadTooLarge?: () => void;
}

interface Peer {
  pc: RTCPeerConnection;
  audioSender: RTCRtpSender;
  videoSender: RTCRtpSender;
  pendingIce: RTCIceCandidateInit[];
  remoteDescriptionSet: boolean;
  restarted: boolean;
}


export class PeerConnectionManager {
  private peers = new Map<string, Peer>();
  private audioTrack: MediaStreamTrack | null = null;
  private videoTrack: MediaStreamTrack | null = null;

  constructor(private readonly options: ManagerOptions) {}

  get peerIds(): string[] {
    return [...this.peers.keys()];
  }

  setAudioTrack(track: MediaStreamTrack | null): void {
    this.audioTrack = track;
    this.peers.forEach((peer) => {
      void peer.audioSender.replaceTrack(track);
    });
  }

  setVideoTrack(track: MediaStreamTrack | null): void {
    this.videoTrack = track;
    this.peers.forEach((peer) => {
      void peer.videoSender.replaceTrack(track);
    });
  }

  async callNewcomer(userId: string): Promise<void> {
    const peer = this.ensurePeer(userId);
    const offer = await peer.pc.createOffer();
    await peer.pc.setLocalDescription(offer);
    if (!this.withinSdpLimit(offer.sdp)) return;
    this.options.publishOffer(userId, offer.sdp ?? "");
  }

  async handleOffer(fromUserId: string, sdp: string): Promise<void> {
    const peer = this.ensurePeer(fromUserId);
    await peer.pc.setRemoteDescription({ type: "offer", sdp });
    peer.remoteDescriptionSet = true;
    await this.flushIce(peer);
    const answer = await peer.pc.createAnswer();
    await peer.pc.setLocalDescription(answer);
    if (!this.withinSdpLimit(answer.sdp)) return;
    this.options.publishAnswer(fromUserId, answer.sdp ?? "");
  }

  async handleAnswer(fromUserId: string, sdp: string): Promise<void> {
    const peer = this.peers.get(fromUserId);
    if (!peer) return;
    await peer.pc.setRemoteDescription({ type: "answer", sdp });
    peer.remoteDescriptionSet = true;
    await this.flushIce(peer);
  }

  async handleIce(fromUserId: string, candidate: RTCIceCandidateInit): Promise<void> {
    const peer = this.peers.get(fromUserId);
    if (!peer) return;
    if (!peer.remoteDescriptionSet) {
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
    if (!peer) return;
    peer.pc.onicecandidate = null;
    peer.pc.ontrack = null;
    peer.pc.onconnectionstatechange = null;
    peer.pc.close();
    this.peers.delete(userId);
  }

  destroy(): void {
    this.peerIds.forEach((userId) => this.removePeer(userId));
  }

  private ensurePeer(userId: string): Peer {
    const existing = this.peers.get(userId);
    if (existing) return existing;

    const pc = new RTCPeerConnection({
      iceServers: this.options.config.iceServers.map((server) => ({
        urls: server.urls,
        username: server.username ?? undefined,
        credential: server.credential ?? undefined,
      })),
    });

    const audioSender = pc.addTransceiver("audio", { direction: "sendrecv" }).sender;
    const videoSender = pc.addTransceiver("video", { direction: "sendrecv" }).sender;
    if (this.audioTrack) void audioSender.replaceTrack(this.audioTrack);
    if (this.videoTrack) void videoSender.replaceTrack(this.videoTrack);

    const peer: Peer = {
      pc,
      audioSender,
      videoSender,
      pendingIce: [],
      remoteDescriptionSet: false,
      restarted: false,
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
      const [stream] = event.streams;
      if (stream) this.options.onRemoteStream(userId, stream);
    };

    pc.onconnectionstatechange = () => {
      this.options.onPeerState(userId, pc.connectionState);
      if (pc.connectionState === "failed" && !peer.restarted) {
        peer.restarted = true;
        try {
          pc.restartIce();
        } catch {

        }
      }
    };

    this.peers.set(userId, peer);
    return peer;
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

  private withinSdpLimit(sdp: string | undefined): boolean {
    if ((sdp ?? "").length <= this.options.config.maxSdpLength) return true;
    this.options.onPayloadTooLarge?.();
    return false;
  }
}