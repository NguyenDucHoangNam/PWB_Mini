export type VoiceTagType = "TTS" | "UPLOADED";

/**
 * Mirrors `pwb.audio.voice-tag.max-duration-seconds`. A tag is stamped over a song at an interval, so it
 * has to stay short — the server measures the real duration with ffprobe and is the authority; this only
 * spares the user an upload that was always going to be refused.
 */
export const VOICE_TAG_MAX_DURATION_SECONDS = 10;

export type SongStatus = "UPLOADED" | "PROCESSING" | "PROCESSED" | "FAILED";

export type TtsVoiceGender = "FEMALE" | "MALE";

export interface TtsVoice {
  name: string;
  languageCode: string;
  gender: TtsVoiceGender;
}

export interface VoiceTag {
  id: string;
  userId: string;
  name: string;
  tagType: VoiceTagType;
  sourceText: string | null;
  languageCode: string | null;
  /** Absent on tags created before voices became selectable. */
  voiceName?: string | null;
  durationSeconds: number;
  fileSizeBytes: number;
  isDefault: boolean;
  createdAt: string;
  updatedAt: string;
}

/** Fixed at upload and never edited afterwards — the API exposes it read-only. */
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
  /** Whether a voice tag was merged into this song; which one is not part of a listing. */
  hasVoiceTag: boolean;
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

export interface AudioUrl {
  url: string;
  expiresAt: string;
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
  /** Omit to let the provider pick its default voice for the language. */
  voiceName?: string | null;
}

export interface PreviewTtsRequest {
  text: string;
  languageCode: string;
  voiceName?: string | null;
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

/**
 * What a listener actually cares about. `READY` deliberately covers both `UPLOADED` and `PROCESSED`:
 * a plain upload and a finished merge are equally playable, and the difference between them is a
 * detail of the merge job rather than something worth putting in a filter.
 */
export type SongView = "ALL" | "READY" | "PROCESSING" | "FAILED";

export const SONG_VIEW_STATUSES: Record<SongView, SongStatus[]> = {
  ALL: [],
  READY: ["UPLOADED", "PROCESSED"],
  PROCESSING: ["PROCESSING"],
  FAILED: ["FAILED"],
};

export interface ListSongsParams {
  page: number;
  size: number;
  /** Omit or pass an empty list to include every status. Filtering is server-side so paging stays correct. */
  status?: SongStatus[] | null;
}