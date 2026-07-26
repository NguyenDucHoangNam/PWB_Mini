"use client";

export interface PeerStatsSnapshot {
  userId: string;
  rttSeconds: number;
  packetLossRate: number;
  jitterSeconds: number;
  bitrateKbps: number;
  frameRate: number;
  resolution: string;
  timestamp: number;
}

export type PeerStatsListener = (snapshot: PeerStatsSnapshot) => void;

const DEFAULT_INTERVAL_MS = 2_000;

interface MonitorEntry {
  intervalId: ReturnType<typeof setInterval>;
  previousBytesReceived: number;
  previousTimestamp: number;
}

export class PeerStatsMonitor {
  private readonly monitors: Map<string, MonitorEntry> = new Map();
  private readonly listeners: Set<PeerStatsListener> = new Set();

  startMonitoring(
    userId: string,
    peerConnection: RTCPeerConnection,
    intervalMs: number = DEFAULT_INTERVAL_MS,
  ): void {
    if (this.monitors.has(userId)) return;

    const entry: MonitorEntry = {
      intervalId: setInterval(() => {
        void this.collectStats(userId, peerConnection, entry);
      }, intervalMs),
      previousBytesReceived: 0,
      previousTimestamp: 0,
    };

    this.monitors.set(userId, entry);
  }

  stopMonitoring(userId: string): void {
    const entry = this.monitors.get(userId);
    if (!entry) return;
    clearInterval(entry.intervalId);
    this.monitors.delete(userId);
  }

  stopAll(): void {
    for (const userId of [...this.monitors.keys()]) {
      this.stopMonitoring(userId);
    }
  }

  onStats(listener: PeerStatsListener): () => void {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  }

  private async collectStats(
    userId: string,
    peerConnection: RTCPeerConnection,
    entry: MonitorEntry,
  ): Promise<void> {
    try {
      const report = await peerConnection.getStats();
      const snapshot = this.parseReport(userId, report, entry);
      if (!snapshot) return;
      for (const listener of this.listeners) {
        try {
          listener(snapshot);
        } catch (err) {
          console.warn("[PeerStatsMonitor] listener error:", err);
        }
      }
    } catch (err) {
      console.warn(`[PeerStatsMonitor] getStats failed for ${userId}:`, err);
    }
  }

  private parseReport(
    userId: string,
    report: RTCStatsReport,
    entry: MonitorEntry,
  ): PeerStatsSnapshot | null {
    let rttSeconds = 0;
    let packetLossRate = 0;
    let jitterSeconds = 0;
    let frameRate = 0;
    let resolution = "";
    let bytesReceived = 0;
    let inboundVideoFound = false;

    for (const stat of report.values()) {
      if (stat.type === "candidate-pair") {
        const pair = stat as RTCIceCandidatePairStats;
        if (pair.state === "succeeded" && pair.nominated) {
          rttSeconds = pair.currentRoundTripTime ?? 0;
        }
      }

      if (stat.type === "inbound-rtp") {
        const inbound = stat as RTCInboundRtpStreamStats;
        if (inbound.kind !== "video") continue;
        const packetsLost = inbound.packetsLost ?? 0;
        const packetsReceived = inbound.packetsReceived ?? 0;
        const total = packetsLost + packetsReceived;
        packetLossRate = total > 0 ? packetsLost / total : 0;
        jitterSeconds = inbound.jitter ?? 0;
        frameRate = inbound.framesPerSecond ?? 0;
        resolution = `${inbound.frameWidth ?? 0}x${inbound.frameHeight ?? 0}`;
        bytesReceived = inbound.bytesReceived ?? 0;
        inboundVideoFound = true;
      }
    }

    if (!inboundVideoFound) return null;

    const now = Date.now();
    let bitrateKbps = 0;
    if (entry.previousTimestamp > 0 && bytesReceived >= entry.previousBytesReceived) {
      const deltaBytes = bytesReceived - entry.previousBytesReceived;
      const deltaSeconds = (now - entry.previousTimestamp) / 1000;
      if (deltaSeconds > 0) {
        bitrateKbps = (deltaBytes * 8) / 1000 / deltaSeconds;
      }
    }
    entry.previousBytesReceived = bytesReceived;
    entry.previousTimestamp = now;

    return {
      userId,
      rttSeconds,
      packetLossRate,
      jitterSeconds,
      bitrateKbps,
      frameRate,
      resolution,
      timestamp: now,
    };
  }
}
