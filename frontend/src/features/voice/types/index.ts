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
  createdAt: string;
  updatedAt: string;
}

export interface VoiceTagConfig {
  id: string;
  songId: string;
  voiceTagId: string;
  voiceTagName?: string;
  intervalSeconds: number;
  volumePercentage: number;
  duckingPercentage: number;
  startOffsetSeconds: number;
  enabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface Song {
  id: string;
  userId: string;
  title: string;
  artist: string | null;
  album: string | null;
  format: string | null;
  status: SongStatus;
  fileSizeBytes: number | null;
  durationSeconds: number | null;
  processed: boolean;
  thumbnailUrl: string | null;
  lastError: string | null;
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

export type AudioVariant = "ORIGINAL" | "PROCESSED";

export interface AudioUrl {
  url: string;
  expiresAt: string;
  /** Which rendition was actually served; can differ from the one requested. */
  variant?: AudioVariant | null;
}

export interface UploadUrlResponse {
  storageKey: string;
  url: string;
  expiresAt: string;
}

export interface CreateTtsVoiceTagRequest {
  name: string;
  text: string;
  languageCode: string;
}

export interface UpdateVoiceTagRequest {
  name: string;
}

export interface CreateSongRequest {
  title: string;
  originalS3Key: string;
  durationSeconds: number;
  format: string;
  voiceTagConfig?: ConfigureVoiceTagRequest | null;
}

export interface UpdateSongRequest {
  title: string;
}

export interface ConfigureVoiceTagRequest {
  voiceTagId: string;
  intervalSeconds: number;
  volumePercentage: number;
  duckingPercentage: number;
  startOffsetSeconds: number;
  enabled?: boolean;
}

export interface ListVoiceTagsParams {
  page: number;
  size: number;
}

export interface ListSongsParams {
  page: number;
  size: number;
}