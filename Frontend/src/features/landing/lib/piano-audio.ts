export const WHITE_NOTE_FREQUENCIES = [
  261.63, 293.66, 329.63, 349.23, 392.0, 440.0, 493.88, 523.25,
] as const;

export const BLACK_NOTE_FREQUENCIES = [277.18, 311.13, 369.99, 415.3, 466.16] as const;

const SIDE_PITCH_OFFSET = 1.05;

const NOTE_DURATION_SECONDS = 0.5;

export type Side = "left" | "right";

export function getWhiteNoteFrequency(index: number, side: Side): number {
  const base = WHITE_NOTE_FREQUENCIES[index % WHITE_NOTE_FREQUENCIES.length];
  return side === "left" ? base : base * SIDE_PITCH_OFFSET;
}

export function getBlackNoteFrequency(side: Side): number {
  const i = Math.floor(Math.random() * BLACK_NOTE_FREQUENCIES.length);
  const base = BLACK_NOTE_FREQUENCIES[i];
  return side === "left" ? base : base * SIDE_PITCH_OFFSET;
}

function applyEnvelope(
  gainNode: GainNode,
  audioCtx: AudioContext,
  peak: number,
  sustain: number,
): void {
  const now = audioCtx.currentTime;
  gainNode.gain.setValueAtTime(0, now);
  gainNode.gain.linearRampToValueAtTime(peak, now + 0.01);
  gainNode.gain.exponentialRampToValueAtTime(sustain, now + 0.1);
  gainNode.gain.exponentialRampToValueAtTime(0.01, now + 0.5);
}

function playTone(audioCtx: AudioContext, frequency: number, peak: number, sustain: number): void {
  const oscillator = audioCtx.createOscillator();
  const gainNode = audioCtx.createGain();

  oscillator.type = "triangle";
  oscillator.frequency.setValueAtTime(frequency, audioCtx.currentTime);

  applyEnvelope(gainNode, audioCtx, peak, sustain);

  oscillator.connect(gainNode);
  gainNode.connect(audioCtx.destination);

  oscillator.start();
  oscillator.stop(audioCtx.currentTime + NOTE_DURATION_SECONDS);
}

export function createAudioPlayer(
  audioCtxRef: React.MutableRefObject<AudioContext | null>,
  frequency: number,
  peak: number,
  sustain: number,
): void {
  if (!audioCtxRef.current) {
    audioCtxRef.current = new AudioContext();
  }
  playTone(audioCtxRef.current, frequency, peak, sustain);
}
