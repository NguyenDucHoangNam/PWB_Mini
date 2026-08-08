# Waveform: tính sẵn ở backend

**Trạng thái**: chưa làm — ghi lại để làm sau khi deploy xong.
**Ngày lập**: 2026-08-08

---

## 1. Vấn đề

[`Frontend/src/features/voice/components/audio-player.tsx`](../Frontend/src/features/voice/components/audio-player.tsx) tải và giải nén **toàn bộ bài hát trong trình duyệt** chỉ để vẽ waveform:

```js
const audioContext = new AudioContext();
const response = await fetch(url);
const arrayBuffer = await response.arrayBuffer();
const audioBuffer = await audioContext.decodeAudioData(arrayBuffer);
setWaveform(extractWaveformData(audioBuffer, 800));   // → resample xuống 360 cột
```

### 1.1 Bộ nhớ

`decodeAudioData` trả về PCM Float32 chưa nén: `thời_lượng × sample_rate × số_kênh × 4`.

File merge là MP3 192kbps (24 KB/s):

| Bài | Tải về | PCM trong RAM |
|---|---|---|
| 4 phút | 5.8 MB | **85 MB** |
| 10 phút | 14 MB | **212 MB** |

Bài **không gắn voice tag không qua ffmpeg** — [`Song.playbackKey()`](../Backend/modules/audio/src/main/java/com/pwb/audio/domain/model/Song.java) trả về `originalS3Key`, mà `AudioFormat` cho phép cả `wav`/`flac`:

| Kịch bản | ArrayBuffer | PCM | Đỉnh |
|---|---|---|---|
| WAV 100MB (~9.5 phút) | 100 MB | 200 MB | **~300 MB** |
| FLAC 100MB (~19 phút) | 100 MB | 400 MB | **~500 MB** |

Tab trên di động thường bị kill quanh 200–400MB. Bài 10 phút đã chạm mép; FLAC thì vượt hẳn. Người dùng chỉ thấy trang tự reload, không có thông báo lỗi.

### 1.2 Tải mạng gấp đôi

`<audio src>` đã stream file; `fetch()` tải lần thứ hai toàn bộ. Hai lượt tranh băng thông nên còn làm chậm lúc bắt đầu phát. Trên S3 là egress tính tiền, nhân đôi mỗi lượt nghe.

### 1.3 Waveform giả

Nhánh `catch` sinh **số ngẫu nhiên** và render y hệt waveform thật:

```js
const fallback = Array.from({ length: WAVE_RESOLUTION }, () => 0.2 + Math.random() * 0.8);
```

Rất dễ kích hoạt: `<audio src>` **không cần CORS**, `fetch()` thì **cần**. Bucket S3 thiếu CORS cho origin frontend → nhạc vẫn phát bình thường, mọi waveform đều là số ngẫu nhiên, không lỗi/không log. Không ai phân biệt được.

### 1.4 Rò AudioContext

`audioContext.close()` nằm sau `await decodeAudioData`, không có `finally`. Lỗi ở `fetch`/`decodeAudioData` → context không đóng. Chrome giới hạn ~6 context/tab → chạm trần → từ đó **mọi bài đều hiện waveform ngẫu nhiên vĩnh viễn**.

### 1.5 Lặp lại khi presigned URL gia hạn

`useEffect` phụ thuộc `[url]`. [`usePresignedUrl`](../Frontend/src/features/voice/hooks/use-presigned-url.ts) refetch trước hạn 5 phút (URL sống 1 giờ) → sau ~55 phút tải + decode lại từ đầu.

Kèm theo một **bug riêng**: `<audio src={url}>` cũng nhận src mới → trình duyệt load lại → **playback nhảy về 0**, mất vị trí đang nghe. Cần tách `<audio src>` khỏi việc URL gia hạn, độc lập với waveform.

---

## 2. Cách A — tính sẵn peaks ở backend

FE chỉ tải ~360 số thay vì cả bài. Xử lý dứt điểm 1.1–1.5.

### 2.1 Điểm khó

Hai đường tạo bài, chỉ một đường có sẵn ffmpeg:

| Đường | Có chạy ffmpeg? | Việc cần làm |
|---|---|---|
| Có voice tag | Có — [`JaffreeAudioProcessorAdapter.embedWatermark`](../Backend/modules/audio/src/main/java/com/pwb/audio/infrastructure/audio/JaffreeAudioProcessorAdapter.java) | Thêm bước xuất peaks vào job sẵn có |
| Upload thuần | **Không** — lên thẳng S3 qua presigned URL, backend không đọc bytes | Cần job nền mới |

### 2.2 Các bước

1. **Sinh peaks**: chạy ffmpeg xuất PCM mono rồi rút gọn thành `N` biên độ (`N = 800`, khớp `WAVE_RESOLUTION` hiện tại). Đặt cạnh `AudioProbeService`.
2. **Lưu**: cột `waveform_peaks JSONB` trên `audio_songs` (mảng 800 số 0–1 ≈ vài KB, không đáng để tách bảng hay đẩy lên S3). Cần migration `V206`.
3. **Nhánh có voice tag**: xuất peaks ngay trong `embedWatermark` — file đã nằm sẵn trên đĩa job, gần như không tốn thêm.
4. **Nhánh upload thuần**: phát event qua outbox sau `createSong` (tái dùng hạ tầng của `SongProcessingRequested`), consumer tải file → sinh peaks → lưu. Bài chưa có peaks vẫn phát được, chỉ chưa có waveform.
5. **API**: trả `waveformPeaks` trong `SongResponse` (null khi chưa sinh xong), hoặc endpoint riêng `GET /songs/{id}/waveform` nếu không muốn phình payload danh sách.
6. **FE**: bỏ hẳn khối `fetch`/`decodeAudioData`; đọc peaks từ response. Chưa có peaks → **mẫu tĩnh trung tính**, tuyệt đối không phải số ngẫu nhiên (xem 1.3).
7. **Bài cũ**: backfill bằng task một lần, hoặc để `null` và sinh dần khi được mở lần đầu.

### 2.3 Cần quyết trước khi làm

- `N = 800` có giữ không (mobile chỉ hiện 170 cột — có thể giảm).
- Backfill bài cũ hay để `null`.
- Peaks nằm trong `SongResponse` hay endpoint riêng.

---

## 3. Nếu cần giảm rủi ro trước khi làm A

Ba sửa nhỏ, độc lập nhau, không đụng backend:

1. Thay fallback ngẫu nhiên bằng mẫu tĩnh — **ưu tiên cao nhất**, đang bịa dữ liệu và trình bày như thật (1.3).
2. Đóng AudioContext trong `finally` (1.4).
3. Tách `<audio src>` khỏi URL gia hạn (1.5).

Có thể thêm ngưỡng: chỉ decode khi bài đủ nhỏ (vd. < 6 phút và < 15MB), còn lại dùng mẫu tĩnh — bỏ được nguy cơ crash nhưng vẫn tải gấp đôi.

> Cả mục 3 lẫn phương án ngưỡng **đã được cân nhắc và quyết định không làm** (2026-08-08). Ghi lại để khỏi bàn lại từ đầu.
