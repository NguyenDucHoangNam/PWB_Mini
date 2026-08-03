import { SongStatus } from "./song-status";

export { SongStatus };

export interface SongListItem {
  id: string;
  userId: string;
  title: string;
  artist: string | null;
  album: string | null;
  fileSizeBytes: number | null;
  durationSeconds: number | null;
  format: string | null;
  status: SongStatus;
  thumbnailUrl: string | null;
  lastError: string | null;
  processed: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface VoiceTagConfig {
  id: string;
  songId: string;
  voiceTagId: string;
  intervalSeconds: number;
  volumePercentage: number;
  duckingPercentage: number;
  startOffsetSeconds: number;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface UploadUrlRequest {
  format: string;
}

export interface UploadUrlResponse {
  storageKey: string;
  url: string;
  expiresAt: string;
}

export interface CreateSongRequest {
  title: string;
  originalS3Key: string;
  durationSeconds: number;
  format: string;
  voiceTagConfig?: ConfigureVoiceTagRequest | null;
}

export interface ConfigureVoiceTagRequest {
  voiceTagId: string;
  intervalSeconds: number;
  volumePercentage: number;
  duckingPercentage: number;
  startOffsetSeconds: number;
  enabled: boolean;
}

export interface SongResponse {
  id: string;
  userId: string;
  title: string;
  artist: string | null;
  album: string | null;
  fileSizeBytes: number | null;
  durationSeconds: number | null;
  format: string | null;
  status: SongStatus;
  thumbnailUrl: string | null;
  lastError: string | null;
  processed: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface AudioUrlResponse {
  url: string;
  expiresAt: string;
  variant: string;
}

export interface DistributionListItem {
  distributionId: string;
  threadId: string;
  shareToken: string;
  recipientEmail: string;
  allowDownload: boolean;
  revoked: boolean;
  playCount: number;
  lastPlayedAt: string | null;
  createdAt: string;
  continuousPlayWeight: number;
  lastSessionHeartbeat: string | null;
  lastSessionKeysRequested: number;
  continuousPlaySupported: boolean;
}

export interface DistributeSongRequest {
  recipientEmail: string;
  allowDownload: boolean;
}

export interface DistributeSongResponse {
  distributionId: string;
  threadId: string;
  shareToken: string;
  recipientEmail: string;
  allowDownload: boolean;
  shareLink: string;
}

export interface UpdateSongRequest {
  title: string;
}
