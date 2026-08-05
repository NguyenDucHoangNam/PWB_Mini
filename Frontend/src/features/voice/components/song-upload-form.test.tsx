import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
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
  private listeners: Record<string, () => void> = {};
  addEventListener(event: string, handler: () => void) {
    this.listeners[event] = handler;
  }
  setRequestHeader() {}
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
 * Picks a file and submits. Whether a merge follows is decided by what `createSong` is stubbed to
 * return, not by the voice tag checkbox — the status on the response is what the form reacts to.
 */
async function submitUpload() {
  renderForm();
  const fileInput = document.querySelector("#song-file") as HTMLInputElement;
  const file = new File(["x".repeat(1024)], "intro.mp3", { type: "audio/mpeg" });
  fireEvent.change(fileInput, { target: { files: [file] } });

  await waitFor(() => expect(screen.getByText("intro.mp3")).toBeInTheDocument());
  fireEvent.click(screen.getByRole("button", { name: /upload song/i }));
}

describe("SongUploadForm — waiting out the voice tag merge", () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    StubXhr.instances = [];
    vi.stubGlobal("XMLHttpRequest", StubXhr);
    push.mockClear();
    toastSuccess.mockClear();
    toastError.mockClear();
    toastInfo.mockClear();
    getSong.mockReset();

    getPresignedUploadUrl.mockResolvedValue({
      success: true,
      data: { storageKey: "songs/raw/1.mp3", url: "https://s3.example/put" },
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

  it("does not navigate or claim success while the merge is still running", async () => {
    getSong.mockResolvedValue({ success: true, data: { id: SONG_ID, status: "PROCESSING" } });

    await submitUpload();

    // Advance first: the poll is on a timer, so nothing has been asked yet at this point.
    await vi.advanceTimersByTimeAsync(6000);

    expect(getSong).toHaveBeenCalled();
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

    await vi.advanceTimersByTimeAsync(5000);

    await waitFor(() => expect(push).toHaveBeenCalledWith(`/dashboard/songs/${SONG_ID}`));
    expect(toastSuccess).toHaveBeenCalledTimes(1);
    expect(toastInfo).not.toHaveBeenCalled();
  });

  it("reports a failed merge as an error rather than a success", async () => {
    getSong.mockResolvedValue({ success: true, data: { id: SONG_ID, status: "FAILED" } });

    await submitUpload();

    await vi.advanceTimersByTimeAsync(3000);

    await waitFor(() => expect(toastError).toHaveBeenCalledTimes(1));
    expect(toastSuccess).not.toHaveBeenCalled();
    expect(push).toHaveBeenCalledWith(`/dashboard/songs/${SONG_ID}`);
  });

  it("keeps polling through a failed read instead of giving up on the merge", async () => {
    getSong
      .mockRejectedValueOnce(new Error("network"))
      .mockResolvedValue({ success: true, data: { id: SONG_ID, status: "PROCESSED" } });

    await submitUpload();

    await vi.advanceTimersByTimeAsync(5000);

    await waitFor(() => expect(toastSuccess).toHaveBeenCalledTimes(1));
    expect(toastError).not.toHaveBeenCalled();
  });

  it("goes straight through when the song carries no voice tag", async () => {
    createSong.mockResolvedValue({
      success: true,
      data: { id: SONG_ID, status: "PROCESSED" },
    });

    await submitUpload();

    await waitFor(() => expect(toastSuccess).toHaveBeenCalledTimes(1));
    expect(getSong).not.toHaveBeenCalled();
    expect(push).toHaveBeenCalledWith(`/dashboard/songs/${SONG_ID}`);
  });

  it("hands the wait over to the song page when the user opts to run it in the background", async () => {
    getSong.mockResolvedValue({ success: true, data: { id: SONG_ID, status: "PROCESSING" } });

    await submitUpload();

    const leave = await screen.findByRole("button", { name: /run in background/i });
    fireEvent.click(leave);

    expect(push).toHaveBeenCalledWith(`/dashboard/songs/${SONG_ID}`);
    expect(toastInfo).toHaveBeenCalledTimes(1);
    expect(toastSuccess).not.toHaveBeenCalled();

    // The merge finishing after the handover must not produce a second announcement.
    getSong.mockResolvedValue({ success: true, data: { id: SONG_ID, status: "PROCESSED" } });
    await vi.advanceTimersByTimeAsync(5000);
    expect(toastSuccess).not.toHaveBeenCalled();
  });
});
