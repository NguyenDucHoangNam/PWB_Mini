import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { act, render, screen, fireEvent } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { NextIntlClientProvider } from "next-intl";
import enMessages from "@/../messages/en.json";

// vi.mock factories are hoisted above the file's own statements, so anything they close over has to
// come from vi.hoisted rather than a plain const.
const { push, toastSuccess, toastError, toastInfo, getPresignedUploadUrl, createSong, getSong } =
  vi.hoisted(() => ({
    push: vi.fn(),
    toastSuccess: vi.fn(),
    toastError: vi.fn(),
    toastInfo: vi.fn(),
    getPresignedUploadUrl: vi.fn(),
    createSong: vi.fn(),
    getSong: vi.fn(),
  }));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push, replace: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => "/dashboard/songs/new",
}));

vi.mock("sonner", () => ({
  toast: { success: toastSuccess, error: toastError, info: toastInfo },
}));

vi.mock("../api/songs", () => ({
  SONGS_KEY: "songs",
  getPresignedUploadUrl,
  createSong,
  getSong,
}));

vi.mock("../api/voice-tags", () => ({
  useListVoiceTags: () => ({ data: { data: { content: [] } } }),
}));

// The real one builds an <audio> element, which jsdom cannot decode.
vi.mock("../lib/read-audio-duration", () => ({
  readAudioDuration: () => Promise.resolve(316),
}));

import { SongUploadForm } from "./song-upload-form";

const SONG_ID = "song-1";

/** Stands in for the PUT to storage, which the form drives through a raw XMLHttpRequest. */
class StubXhr {
  static instances: StubXhr[] = [];
  upload = { addEventListener: vi.fn() };
  status = 200;
  headers: Record<string, string> = {};
  private listeners: Record<string, () => void> = {};
  addEventListener(event: string, handler: () => void) {
    this.listeners[event] = handler;
  }
  setRequestHeader(name: string, value: string) {
    this.headers[name] = value;
  }
  open() {}
  send() {
    queueMicrotask(() => this.listeners.load?.());
  }
  abort() {
    this.listeners.abort?.();
  }
  constructor() {
    StubXhr.instances.push(this);
  }
}

function renderForm() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <NextIntlClientProvider locale="en" messages={enMessages as never}>
        <SongUploadForm />
      </NextIntlClientProvider>
    </QueryClientProvider>,
  );
}

/**
 * Runs the timers forward by {@code ms} and lets every promise the form is waiting on settle.
 *
 * <p>These tests used to reach for `waitFor`, which fails about one run in five here. The reason is not
 * obvious: `waitFor` decides whether timers are faked by looking for a global `jest` object, and under
 * Vitest there is none — so it always takes its real-timer branch and schedules its own polling through
 * `setTimeout`, which *is* faked. It only made progress at all because the suite ran with
 * `shouldAdvanceTime: true`, tying fake time to the wall clock. That left `waitFor`'s 1s default timeout
 * racing the form's 2s poll interval: any assertion needing one more poll lost, and whether it needed one
 * depended on where the promise chain happened to sit when the previous advance returned.
 *
 * <p>Driving the clock explicitly removes the race rather than widening it. Each call advances a known
 * amount and returns with the resulting work already flushed, so a poll either has happened or has not —
 * there is no window in which the answer depends on timing. `act` is what makes React apply the state
 * updates the advance triggers; without it the assertions read a stale DOM.
 */
async function tick(ms = 0) {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(ms);
  });
}

/** One turn of the form's merge poll. */
const POLL = 2000;

/**
 * Picks a file and submits. Whether a merge follows is decided by what `createSong` is stubbed to
 * return, not by the voice tag checkbox — the status on the response is what the form reacts to.
 *
 * <p>Returns once the submit chain has run as far as it can without the clock moving: the presigned URL,
 * the storage PUT and `createSong` have all resolved, so a song that needs merging is already sitting on
 * its first poll timer.
 */
async function submitUpload() {
  renderForm();
  const fileInput = document.querySelector("#song-file") as HTMLInputElement;
  const file = new File(["x".repeat(1024)], "intro.mp3", { type: "audio/mpeg" });
  fireEvent.change(fileInput, { target: { files: [file] } });

  await tick();
  expect(screen.getByText("intro.mp3")).toBeInTheDocument();

  fireEvent.click(screen.getByRole("button", { name: /upload song/i }));
  await tick();
}

describe("SongUploadForm — waiting out the voice tag merge", () => {
  beforeEach(() => {
    // No `shouldAdvanceTime`: the clock moves only when a test says so, which is the whole point.
    vi.useFakeTimers();
    StubXhr.instances = [];
    vi.stubGlobal("XMLHttpRequest", StubXhr);
    push.mockClear();
    toastSuccess.mockClear();
    toastError.mockClear();
    toastInfo.mockClear();
    getSong.mockReset();

    getPresignedUploadUrl.mockResolvedValue({
      success: true,
      data: {
        storageKey: "songs/raw/1.mp3",
        url: "https://s3.example/put",
        contentType: "audio/mpeg",
      },
    });
    createSong.mockResolvedValue({
      success: true,
      data: { id: SONG_ID, status: "PROCESSING" },
    });
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  /**
   * Both values are signed into the presigned URL, so storage rejects the PUT if either differs. The
   * content type in particular must be the server's, not the browser's guess from the file: those
   * disagree per platform for the same extension, and previously the client picked it freely, which is
   * what let an upload be labelled `text/html`.
   */
  it("declares the file's real size and sends back exactly the content type that was signed", async () => {
    getSong.mockResolvedValue({ success: true, data: { id: SONG_ID, status: "PROCESSING" } });

    await submitUpload();

    expect(getPresignedUploadUrl).toHaveBeenCalledWith({ format: "mp3", sizeBytes: 1024 });
    expect(StubXhr.instances[0].headers["Content-Type"]).toBe("audio/mpeg");
  });

  it("does not navigate or claim success while the merge is still running", async () => {
    getSong.mockResolvedValue({ success: true, data: { id: SONG_ID, status: "PROCESSING" } });

    await submitUpload();

    // Advance first: the poll is on a timer, so nothing has been asked yet at this point.
    expect(getSong).not.toHaveBeenCalled();
    await tick(POLL);

    expect(getSong).toHaveBeenCalledTimes(1);
    expect(push).not.toHaveBeenCalled();
    expect(toastSuccess).not.toHaveBeenCalled();
    // The user is still on the upload screen, told what is happening rather than shown a half-done song.
    expect(screen.getByText(/ready the moment this finishes/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /run in background/i })).toBeInTheDocument();
  });

  it("reports success and opens the song only once the merge lands", async () => {
    getSong
      .mockResolvedValueOnce({ success: true, data: { id: SONG_ID, status: "PROCESSING" } })
      .mockResolvedValue({ success: true, data: { id: SONG_ID, status: "PROCESSED" } });

    await submitUpload();

    // First poll still says PROCESSING, so nothing may be announced yet.
    await tick(POLL);
    expect(push).not.toHaveBeenCalled();

    // Second poll lands.
    await tick(POLL);

    expect(push).toHaveBeenCalledWith(`/dashboard/songs/${SONG_ID}`);
    expect(toastSuccess).toHaveBeenCalledTimes(1);
    expect(toastInfo).not.toHaveBeenCalled();
  });

  it("reports a failed merge as an error rather than a success", async () => {
    getSong.mockResolvedValue({ success: true, data: { id: SONG_ID, status: "FAILED" } });

    await submitUpload();

    await tick(POLL);

    expect(toastError).toHaveBeenCalledTimes(1);
    expect(toastSuccess).not.toHaveBeenCalled();
    expect(push).toHaveBeenCalledWith(`/dashboard/songs/${SONG_ID}`);
  });

  it("keeps polling through a failed read instead of giving up on the merge", async () => {
    getSong
      .mockRejectedValueOnce(new Error("network"))
      .mockResolvedValue({ success: true, data: { id: SONG_ID, status: "PROCESSED" } });

    await submitUpload();

    // The read that fails must cost a poll, not the merge.
    await tick(POLL);
    expect(toastSuccess).not.toHaveBeenCalled();

    await tick(POLL);

    expect(toastSuccess).toHaveBeenCalledTimes(1);
    expect(toastError).not.toHaveBeenCalled();
  });

  it("goes straight through when the song carries no voice tag", async () => {
    createSong.mockResolvedValue({
      success: true,
      data: { id: SONG_ID, status: "PROCESSED" },
    });

    await submitUpload();

    // No clock movement at all: without a merge to wait for there is nothing on a timer.
    expect(toastSuccess).toHaveBeenCalledTimes(1);
    expect(getSong).not.toHaveBeenCalled();
    expect(push).toHaveBeenCalledWith(`/dashboard/songs/${SONG_ID}`);
  });

  it("hands the wait over to the song page when the user opts to run it in the background", async () => {
    getSong.mockResolvedValue({ success: true, data: { id: SONG_ID, status: "PROCESSING" } });

    await submitUpload();

    fireEvent.click(screen.getByRole("button", { name: /run in background/i }));

    expect(push).toHaveBeenCalledWith(`/dashboard/songs/${SONG_ID}`);
    expect(toastInfo).toHaveBeenCalledTimes(1);
    expect(toastSuccess).not.toHaveBeenCalled();

    // The merge finishing after the handover must not produce a second announcement.
    getSong.mockResolvedValue({ success: true, data: { id: SONG_ID, status: "PROCESSED" } });
    await tick(POLL);
    await tick(POLL);
    expect(toastSuccess).not.toHaveBeenCalled();
  });
});
