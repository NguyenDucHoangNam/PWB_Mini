export type VoiceTagType = "TTS" | "UPLOADED";

export type SongStatus = "UPLOADED" | "PROCESSING" | "PROCESSED" | "FAILED";

export interface VoiceTag {
  id: string;
  userId: string;
  name: string;
  tagType: VoiceTagType;
  sourceText: string | null;
  languageCode: string | null;
  durationSeconds: number;
  fileSizeBytes: number;
  isDefault: boolean;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface VoiceTagConfig {
  id: string;
  songId: string;
  voiceTagId: string;
  voiceTagName: string;
  intervalSeconds: number;
  volumePercentage: number;
  fadeInDurationMs: number;
  fadeOutDurationMs: number;
  startOffsetSeconds: number;
  enabled: boolean;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface Song {
  id: string;
  userId: string;
  title: string;
  artist: string | null;
  album: string | null;
  format: string;
  status: SongStatus;
  fileSizeBytes: number;
  durationSeconds: number | null;
  processed: boolean;
  thumbnailUrl: string | null;
  lastError: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface ProcessingStatus {
  songId: string;
  status: SongStatus;
  processedS3Key: string | null;
  durationSeconds: number | null;
  lastError: string | null;
  message: string | null;
  updatedAt: string | null;
}

export interface AudioUrl {
  url: string;
  expiresAt: string;
}

export interface CreateTtsVoiceTagRequest {
  name: string;
  text: string;
  languageCode: string;
}

export interface UploadVoiceTagRequest {
  name: string;
}

export interface UpdateVoiceTagRequest {
  name?: string;
  text?: string;
  languageCode?: string;
}

export interface UploadSongRequest {
  title: string;
  artist?: string;
  album?: string;
  voiceTagConfig?: ConfigureVoiceTagRequest;
}

export interface UpdateSongRequest {
  title?: string;
  artist?: string;
  album?: string;
}

export interface ConfigureVoiceTagRequest {
  voiceTagId: string;
  intervalSeconds: number;
  volumePercentage: number;
  fadeInDurationMs: number;
  fadeOutDurationMs: number;
  startOffsetSeconds?: number;
  enabled?: boolean;
}

export interface ListVoiceTagsParams {
  page: number;
  size: number;
  type?: VoiceTagType;
}

export interface ListSongsParams {
  page: number;
  size: number;
  status?: SongStatus;
}