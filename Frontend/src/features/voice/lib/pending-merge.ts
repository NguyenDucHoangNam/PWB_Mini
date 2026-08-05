/**
 * Remembers that an upload left a voice tag merge running, so the song's page can announce the outcome.
 *
 * The page would otherwise have to infer it from watching the status change, which only works when the
 * page loads before the merge lands. A short song behind a warm queue can finish first, and a user who
 * uploaded and got no confirmation has no way to tell that from a merge that quietly failed.
 *
 * Session storage, not a query param: the flag is a handoff between two screens in one visit, and it
 * should not survive a shared link or a fresh tab.
 */
const KEY_PREFIX = "pwb:voice:pending-merge:";

function storage(): Storage | null {
  try {
    return typeof window === "undefined" ? null : window.sessionStorage;
  } catch {
    // Storage can be blocked outright: the merge still runs, only the toast is lost.
    return null;
  }
}

export function markMergePending(songId: string) {
  storage()?.setItem(KEY_PREFIX + songId, "1");
}

export function isMergePending(songId: string) {
  return storage()?.getItem(KEY_PREFIX + songId) === "1";
}

export function clearMergePending(songId: string) {
  storage()?.removeItem(KEY_PREFIX + songId);
}
