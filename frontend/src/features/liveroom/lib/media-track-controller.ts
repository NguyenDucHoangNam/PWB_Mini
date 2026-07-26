"use client";

import type { WebRTCPeerManager } from "./webrtc-peer-manager";

export interface MediaTrackState {
  micEnabled: boolean;
  cameraEnabled: boolean;
}

export type MediaTrackEvent =
  | { type: "MIC_CHANGED"; enabled: boolean }
  | { type: "CAMERA_CHANGED"; enabled: boolean };

export type AcquireTrackResult = MediaStreamTrack | null;

export interface MediaTrackControllerDeps {
  getLocalStream: () => MediaStream | null;
  acquireMic: () => Promise<AcquireTrackResult>;
  acquireCamera: () => Promise<AcquireTrackResult>;
  releaseTrack: (track: MediaStreamTrack) => void;
  getPeerManager: () => WebRTCPeerManager | null;
}

export class MediaTrackController {
  private currentState: MediaTrackState = { micEnabled: false, cameraEnabled: false };
  private readonly listeners: Set<(event: MediaTrackEvent) => void> = new Set();
  private pendingOperation: Promise<void> = Promise.resolve();

  constructor(private readonly deps: MediaTrackControllerDeps) {}

  onChange(listener: (event: MediaTrackEvent) => void): () => void {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  }

  getState(): MediaTrackState {
    return { micEnabled: this.currentState.micEnabled, cameraEnabled: this.currentState.cameraEnabled };
  }

  getMicEnabled(): boolean {
    return this.currentState.micEnabled;
  }

  getCameraEnabled(): boolean {
    return this.currentState.cameraEnabled;
  }

  private emit(event: MediaTrackEvent): void {
    for (const listener of this.listeners) {
      try {
        listener(event);
      } catch (err) {
        console.warn("[MediaTrackController] listener error:", err);
      }
    }
  }

  private async serialize<T>(operation: () => Promise<T>): Promise<T> {
    const previous = this.pendingOperation;
    let releaseNext!: () => void;
    const next = new Promise<void>((resolve) => {
      releaseNext = resolve;
    });
    this.pendingOperation = previous.then(() => next);

    try {
      await previous;
      const result = await operation();
      return result;
    } finally {
      releaseNext();
    }
  }

  private findLocalAudioTrack(): MediaStreamTrack | null {
    const stream = this.deps.getLocalStream();
    if (!stream) return null;
    return stream.getAudioTracks()[0] ?? null;
  }

  private findLocalVideoTrack(): MediaStreamTrack | null {
    const stream = this.deps.getLocalStream();
    if (!stream) return null;
    return stream.getVideoTracks()[0] ?? null;
  }

  async setMicEnabled(enabled: boolean): Promise<void> {
    if (this.currentState.micEnabled === enabled) return;

    return this.serialize(async () => {
      const peerManager = this.deps.getPeerManager();

      let track: MediaStreamTrack | null = null;

      if (enabled) {
        track = await this.deps.acquireMic();
        if (!track) {
          this.emit({ type: "MIC_CHANGED", enabled: false });
          return;
        }
      } else {
        const existing = this.findLocalAudioTrack();
        if (existing) {
          try {
            this.deps.releaseTrack(existing);
          } catch {
            /* ignore */
          }
          try {
            existing.stop();
          } catch {
            /* ignore */
          }
        }
      }

      if (peerManager) {
        await peerManager.replaceAudioTrackForAllPeers(enabled ? track : null);
      }

      this.currentState.micEnabled = enabled;
      this.emit({ type: "MIC_CHANGED", enabled });
    });
  }

  async setCameraEnabled(enabled: boolean): Promise<void> {
    if (this.currentState.cameraEnabled === enabled) return;

    return this.serialize(async () => {
      const peerManager = this.deps.getPeerManager();

      let track: MediaStreamTrack | null = null;

      if (enabled) {
        track = await this.deps.acquireCamera();
        if (!track) {
          this.emit({ type: "CAMERA_CHANGED", enabled: false });
          return;
        }
      } else {
        const stream = this.deps.getLocalStream();
        const existingTracks = stream?.getVideoTracks() ?? [];
        for (const existing of existingTracks) {
          try {
            this.deps.releaseTrack(existing);
          } catch {
            /* ignore */
          }
          try {
            existing.stop();
          } catch {
            /* ignore */
          }
        }
      }

      if (peerManager) {
        await peerManager.replaceVideoTrackForAllPeers(track);
      }

      this.currentState.cameraEnabled = enabled;
      this.emit({ type: "CAMERA_CHANGED", enabled });
    });
  }

  async syncTrackAlignment(): Promise<void> {
    return this.serialize(async () => {
      const peerManager = this.deps.getPeerManager();
      const stream = this.deps.getLocalStream();
      if (!peerManager || !stream) return;

      const audioTrack = this.currentState.micEnabled ? (stream.getAudioTracks()[0] ?? null) : null;
      const videoTrack = this.currentState.cameraEnabled ? (stream.getVideoTracks()[0] ?? null) : null;

      await Promise.all([
        peerManager.replaceAudioTrackForAllPeers(audioTrack),
        peerManager.replaceVideoTrackForAllPeers(videoTrack),
      ]);
    });
  }

  hydrate(micEnabled: boolean, cameraEnabled: boolean): void {
    this.currentState = { micEnabled, cameraEnabled };
  }
}
