export type DemoStatus =
  | "PROCESSING"
  | "ACTIVE"
  | "FAILED"
  | "REVOKED"
  | "DELETED";

export interface DemoListItem {
  demoId: string;
  title: string;
  status: DemoStatus;
  fileSize: number;
  duration: number | null;
  sampleRate: number | null;
  format: string | null;
  voiceTagId: string | null;
  voiceTagOwnerId: string | null;
  voiceTagTextContent: string | null;
  voiceTagLanguageCode: string | null;
  voiceTagVoiceName: string | null;
  createdAt: string;
  updatedAt: string;
  errorMessage: string | null;
}

export interface PresignedUrlRequest {
  fileName: string;
  contentType: string;
  fileSize: number;
}

export interface PresignedUrlResponse {
  uploadUrl: string;
  s3Key: string;
  expiresInSeconds: number;
  issuedAt: string;
  maxSizeBytes: number;
  contentType: string;
}

export interface ConfirmUploadRequest {
  s3Key: string;
  title: string;
  voiceTagId?: string | null;
  watermarkInterval?: number | null;
}

export interface ConfirmUploadResponse {
  demoId: string;
  status: DemoStatus;
}

export interface DemoStatusResponse {
  demoId: string;
  status: DemoStatus;
  title: string;
  duration: number;
  sampleRate: number;
  format: string;
  waveform: number[];
  hlsPlaylistUrl: string;
  errorMessage: string | null;
}

export interface RotateKeyResponse {
  demoId: string;
  newVersion: number;
  rotated: boolean;
  messageKey: string;
}

export interface VoiceTagResponse {
  id: string;
  textContent: string;
  languageCode: string;
  voiceName: string;
  isDefault: boolean;
  createdAt: string;
}

export interface CreateVoiceTagRequest {
  textContent: string;
  languageCode: string;
  voiceName: string;
}

export interface VoiceTagPreviewResponse {
  preSignedUrl: string;
}

export interface VoiceOption {
  voiceName: string;
  gender: string;
}

export interface VoiceWhitelistResponse {
  languageCode: string;
  voices: VoiceOption[];
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

export interface DistributeDemoRequest {
  recipientEmail: string;
  allowDownload: boolean;
}

export interface DistributeDemoResponse {
  distributionId: string;
  threadId: string;
  shareToken: string;
  recipientEmail: string;
  allowDownload: boolean;
  shareLink: string;
}
