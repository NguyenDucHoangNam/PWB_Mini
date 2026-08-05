/**
 * Reading how long an audio file is, without uploading it first. Used both to stamp a song with its
 * duration and to reject an over-long voice tag before the bytes ever leave the browser.
 *
 * Returns seconds as a float, or 0 when the duration could not be determined — callers decide whether
 * that is fatal. Fractions are preserved: rounding here would let a 10.4s clip pass a 10s limit.
 */

const METADATA_TIMEOUT_MS = 5000;

/**
 * Exact, but decodes the entire file into memory as PCM — a 100 MB MP3 can balloon past a gigabyte.
 * Only worth paying when the cheap path could not read the duration at all.
 */
async function decodeAudioDuration(file: File): Promise<number> {
  try {
    const arrayBuffer = await file.arrayBuffer();
    const AudioCtx =
      window.AudioContext ||
      (window as unknown as { webkitAudioContext: typeof AudioContext }).webkitAudioContext;
    if (AudioCtx) {
      const audioCtx = new AudioCtx();
      const audioBuffer = await audioCtx.decodeAudioData(arrayBuffer);
      const duration = audioBuffer.duration;
      await audioCtx.close();
      if (Number.isFinite(duration) && duration > 0) {
        return duration;
      }
    }
  } catch {
    // Not decodable here; the caller falls back to treating the duration as unknown.
  }
  return 0;
}

/** Reads the container header only: no full decode, so memory stays flat regardless of file size. */
function readDurationFromMetadata(file: File): Promise<number> {
  return new Promise((resolve) => {
    const url = URL.createObjectURL(file);
    const audio = new Audio();
    audio.preload = "metadata";

    const finish = (duration: number) => {
      URL.revokeObjectURL(url);
      resolve(Number.isFinite(duration) && duration > 0 ? duration : 0);
    };

    const timeoutId = setTimeout(() => finish(0), METADATA_TIMEOUT_MS);

    audio.addEventListener("loadedmetadata", () => {
      clearTimeout(timeoutId);
      // Some streams report Infinity until the playhead is forced to the end.
      if (audio.duration === Infinity || Number.isNaN(audio.duration)) {
        audio.currentTime = 1e101;
        audio.ontimeupdate = () => {
          audio.ontimeupdate = null;
          finish(audio.duration);
        };
        return;
      }
      finish(audio.duration);
    });

    audio.addEventListener("error", () => {
      clearTimeout(timeoutId);
      finish(0);
    });

    audio.src = url;
  });
}

export async function readAudioDuration(file: File): Promise<number> {
  const fromMetadata = await readDurationFromMetadata(file);
  if (fromMetadata > 0) {
    return fromMetadata;
  }
  // Some VBR MP3s and exotic containers report no usable duration in the header; decoding is the only
  // way left to find out.
  return decodeAudioDuration(file);
}
