export const VIDEO_CONSTRAINTS: MediaTrackConstraints = {
  width: { ideal: 640, max: 1280 },
  height: { ideal: 360, max: 720 },
  frameRate: { ideal: 24, max: 30 },
  facingMode: "user",
};

export const AUDIO_CONSTRAINTS: MediaTrackConstraints = {
  echoCancellation: true,
  noiseSuppression: true,
  autoGainControl: true,
};

export type MediaErrorKind =
  | "denied"
  | "notFound"
  | "busy"
  | "overconstrained"
  | "unsupported"
  | "unknown";

export function classifyMediaError(error: unknown): MediaErrorKind {
  if (typeof DOMException !== "undefined" && error instanceof DOMException) {
    switch (error.name) {
      case "NotAllowedError":
      case "SecurityError":
        return "denied";
      case "NotFoundError":
      case "DevicesNotFoundError":
        return "notFound";
      case "NotReadableError":
      case "TrackStartError":
        return "busy";
      case "OverconstrainedError":
        return "overconstrained";
      default:
        return "unknown";
    }
  }
  if (error instanceof Error && error.name === "TypeError") return "unsupported";
  return "unknown";
}

export function mediaDevicesAvailable(): boolean {
  return (
    typeof navigator !== "undefined" &&
    typeof navigator.mediaDevices !== "undefined" &&
    typeof navigator.mediaDevices.getUserMedia === "function"
  );
}