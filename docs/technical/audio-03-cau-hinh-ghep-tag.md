# Audio — Cấu hình ghép tag

> Bảng `audio_song_tag_configs` · `GET /songs/{id}/voice-tag-config`
> Bối cảnh: [Audio — Tour](audio-00-tour.md) → dùng bởi [pipeline xử lý](audio-04-pipeline-xu-ly.md)

---

## 1. Bài toán

Người dùng phải nói được: **chèn tag vào đâu, bao lâu một lần, to cỡ nào, và có hạ nhạc nền xuống khi tag vang lên không.**

Bốn tham số nghe đơn giản, nhưng chúng có ràng buộc lẫn nhau và ràng buộc với chính bài hát. Chọn sai một tham số thì kết quả không phải "hơi khác ý" mà là **file hỏng**: các bản sao tag chồng lên nhau, hoặc không có tag nào được chèn cả.

File này là bảng điều khiển của [pipeline](audio-04-pipeline-xu-ly.md); mọi giá trị ở đây đều biến thành một mảnh trong đồ thị lọc FFmpeg.

---

## 2. Bốn tham số

| Tham số | Mặc định | Chặn | Nghĩa |
|---|---|---|---|
| `intervalSeconds` | **60** | — | Cách bao nhiêu giây chèn tag một lần |
| `volumePercentage` | **100** | kẹp `0–100` | Âm lượng tag, **sau khi** đã cân độ to (mục 4) |
| `duckingPercentage` | **100** | kẹp `0–100` | Nhạc nền còn lại bao nhiêu % khi tag đang vang. `100` = không hạ |
| `startOffsetSeconds` | **0** | `max(0, …)` | Giây thứ mấy chèn tag đầu tiên |

Cộng một cờ `enabled`.

```java
(volumePercentage  != null) ? clampPercentage(volumePercentage)  : DEFAULT_VOLUME_PERCENTAGE,
(duckingPercentage != null) ? clampPercentage(duckingPercentage) : NO_DUCKING,
(startOffsetSeconds!= null) ? Math.max(0, startOffsetSeconds)    : 0,
```

**Kẹp giá trị, không ném lỗi.** Gửi `volumePercentage: 250` thì nhận về 100, không nhận về lỗi. Quyết định này hợp lý cho một thanh trượt trên giao diện — nhưng nó có nghĩa là client **không bao giờ biết** giá trị mình gửi đã bị sửa. Phải đọc lại cấu hình mới thấy.

### 2.1. `duckingPercentage = 100` nghĩa là "không làm gì"

Cách đặt tên hơi ngược trực giác: **100 là mặc định và là *tắt*.** Hằng số trong code gọi đúng tên nó:

```java
private static final int NO_DUCKING = 100;
```

Nghĩa của con số là *"nhạc nền còn lại bao nhiêu phần trăm"*, nên `100` = còn nguyên, `40` = hạ xuống còn 40%, `0` = tắt hẳn nhạc trong lúc tag vang. Đọc là "còn lại", không phải "hạ đi".

Trong đồ thị lọc, giá trị 100 làm cả nhánh biến mất:

```java
if (request.duckingPercentage() >= NO_DUCKING) {
    return "[0:a]anull[bed]";     // không đụng gì vào bài hát
}
```

### 2.2. Ràng buộc duy nhất được kiểm — và nó kiểm rất muộn

`intervalSeconds` **phải lớn hơn thời lượng voice tag**. Ngắn hơn thì bản sao thứ hai bắt đầu trước khi bản thứ nhất kết thúc, và chúng chồng lên nhau.

Nhưng chỗ kiểm không nằm ở đây:

```java
// JaffreeAudioProcessorAdapter
private void assertIntervalFitsTag(AudioProcessingRequest request, double voiceTagDuration) {
    if (request.intervalSeconds() <= voiceTagDuration) {
        throw ... AudioErrorCode.INVALID_TAG_INTERVAL;   // AUDIO_022
    }
}
```

Nó nằm **trong pipeline**, sau khi đã tải cả hai file về đĩa và đo bằng ffprobe. Lý do là hợp lý — lúc tạo cấu hình thì chưa ai biết chắc voice tag dài bao nhiêu giây, vì con số trong database do lúc tạo tag ghi lại.

Cái giá: người dùng đặt `intervalSeconds: 5` cho một tag 8 giây sẽ **không bị báo lỗi ngay**. Bài hát được tạo, chuyển sang `PROCESSING`, xếp hàng Kafka, worker tải hai file về, chạy ffprobe hai lần — rồi mới hỏng và chuyển `FAILED`. Một lỗi nhập liệu đơn giản đi hết cả pipeline mới lộ.

Trần 10 giây cho voice tag ([audio-02 §4](audio-02-voice-tag.md)) làm việc này hiếm xảy ra, vì mặc định `intervalSeconds` là 60.

---

## 3. Bốn tham số biến thành đồ thị lọc như thế nào

Đây là chỗ trừu tượng trở thành cụ thể. `WatermarkFilterBuilder` dựng một chuỗi filtergraph của FFmpeg từ bốn con số.

### 3.1. `startOffsetSeconds` và `intervalSeconds` → số lần chèn

```java
private static int countInsertions(AudioProcessingRequest request, double songDurationSeconds) {
    if (request.startOffsetSeconds() >= songDurationSeconds) {
        return 0;
    }
    double remaining = songDurationSeconds - request.startOffsetSeconds();
    int count = (int) Math.floor(remaining / request.intervalSeconds()) + 1;
    return Math.min(count, MAX_INSERTIONS);   // 500
}
```

Điểm bắt đầu vượt quá độ dài bài hát → **0 lần chèn**, và cả đồ thị rút gọn thành `[0:a]anull[out]` — chép nguyên bài hát, không ducking, không limiter. Bài hát vẫn được đánh dấu `PROCESSED` và có một file "đã xử lý" **giống hệt bản gốc**.

Đây là một cách âm thầm tạo ra bài hát không hề được đóng dấu. Không có cảnh báo nào.

Trần 500 lần chèn chặn trường hợp `intervalSeconds: 1` trên một bài dài.

### 3.2. `volumePercentage` → hai bộ lọc `volume` nối nhau

```java
String prepared = "[1:a]aresample=44100"
        + matchFilter(matchGainDb)                  // ví dụ  ,volume=7.500dB
        + ",volume=" + decimal(tagVolume);          // ví dụ  ,volume=0.800
```

**Hai** bộ lọc âm lượng, cố tình không gộp:

> *"The loudness match, kept as its own `volume` filter rather than folded into the linear figure beside it. The two are separate decisions — one the pipeline made, one the user made — and the logged filtergraph is the only place anyone can see either of them."*

Gộp hai con số lại thành một sẽ ra đúng kết quả âm thanh, nhưng khi đọc log filtergraph để gỡ lỗi thì không còn phân biệt được "máy cân bao nhiêu" với "người dùng chọn bao nhiêu". Đây là một quyết định **vì khả năng chẩn đoán**, không vì âm thanh.

### 3.3. `duckingPercentage` → một biểu thức theo thời gian

```java
double duckFactor = request.duckingPercentage() / 100.0;
String expression = "'if(lt(t," + start + "),1,"
        + "if(lt(mod(t-" + start + "," + interval + ")," + tagDuration + ")," + duckFactor + ",1))'";
return "[0:a]volume=" + expression + ":eval=frame[bed]";
```

Đọc ra tiếng Việt: *"trước điểm bắt đầu thì giữ nguyên; sau đó, nếu thời điểm hiện tại rơi vào khoảng `tagDuration` giây đầu của mỗi chu kỳ `interval` thì nhân âm lượng với `duckFactor`, còn lại giữ nguyên."*

`mod(t - start, interval)` là cách tính "đang ở giây thứ mấy trong chu kỳ hiện tại" — nó khiến một biểu thức duy nhất phục vụ được cả 500 lần chèn.

`eval=frame` bắt FFmpeg tính lại biểu thức cho **mỗi khung**, thay vì tính một lần rồi dùng mãi. Thiếu nó thì ducking không bao giờ bật.

### 3.4. Ghép lại

```
[bed][tagtrack]amix=inputs=2:duration=first:normalize=0,alimiter=limit=0.950:level=disabled[out]
```

`normalize=0` — `amix` mặc định chia âm lượng cho số đầu vào, tức là ghép hai luồng sẽ làm bài hát nhỏ đi một nửa. Tắt đi để bài hát giữ nguyên độ to.

`duration=first` — độ dài kết quả bằng độ dài bài hát. Phần đuôi của tag track (do padding, xem [audio-04](audio-04-pipeline-xu-ly.md)) bị cắt.

`alimiter` và `level=disabled` — xem mục 4.

---

## 4. Vì sao phải có limiter

```java
/**
 * Where the summed mix is allowed to peak. Song and tag are added together at full scale, so a track
 * already mastered close to 0 dBFS goes over the moment a tag lands on it, and libmp3lame clips what it
 * is handed. Clipping does not make the tag louder — it flattens both signals into the same wall of
 * distortion, which is the opposite of standing out. The limiter buys the sum somewhere to go.
 */
private static final String OUTPUT_CEILING = "0.950";
```

Nhạc thương mại được master gần sát mức tối đa. Cộng thêm một tín hiệu nữa lên trên là **vượt trần**, và bộ mã hoá MP3 xử lý phần vượt bằng cách cắt phẳng — nghe ra tiếng rè, và rè cả bài hát lẫn tag.

`level=disabled` cũng có comment riêng: mặc định `alimiter` sẽ **chuẩn hoá đầu ra ngược lên 0 dBFS**, và làm vậy thì công ducking vừa làm ở mục 3.3 bị xoá sạch, đồng thời một bài hát vốn nhỏ tiếng bị thổi to lên.

Hai dòng cấu hình này là ví dụ rõ nhất trong cả dự án về việc **hiểu công cụ mình dùng**: cả hai đều là hành vi mặc định của FFmpeg, và cả hai đều phải tắt.

---

## 5. Cấu hình chỉ tạo được một lần

`SongTagConfig` được lưu **duy nhất** ở `SongUseCaseImpl.createSong`, khi request có `voiceTagConfig`. Không có endpoint nào:

- Thêm cấu hình cho bài đã tạo
- Sửa cấu hình đã có
- Gỡ cấu hình

`GET /songs/{id}/voice-tag-config` chỉ đọc. `PATCH /songs/{id}` chỉ đổi được `title`.

Hệ quả dây chuyền:

```
Không sửa được cấu hình
  → muốn đổi âm lượng tag phải tải lại bài hát từ đầu
  → cấu hình cũ vẫn còn, nên voice tag cũ không xoá được ([audio-02 §5])
  → muốn xoá tag phải xoá tất cả bài hát đang dùng nó
```

Đây là **giới hạn lớn nhất của module Audio**, và nó không nằm ở một chỗ nào cụ thể — nó là hệ quả của việc chỉ có một lối vào đường xử lý.

`retry-processing` chạy lại pipeline với **đúng cấu hình cũ**, nên nó không phải cách chữa; nó chỉ dành cho lần chạy hỏng vì lý do hạ tầng.

Cờ `enabled` càng cho thấy điều này: nó tồn tại trong model và trong bảng, nhưng **không có đường nào bật/tắt nó sau khi tạo**.

---

## 6. Quyết định & đánh đổi

| Quyết định | Thay vì | Vì sao | Cái giá |
|---|---|---|---|
| Kẹp giá trị ngoài khoảng | Ném lỗi | Thanh trượt trên giao diện không bao giờ gửi giá trị sai | Client không biết giá trị đã bị sửa |
| `duckingPercentage` nghĩa là "còn lại", 100 = tắt | "Hạ đi bao nhiêu", 0 = tắt | Cùng đơn vị với `volumePercentage` | Đọc tên dễ hiểu ngược |
| Kiểm `interval > tagDuration` trong pipeline | Kiểm lúc tạo cấu hình | Lúc tạo chưa biết chắc tag dài bao nhiêu | Lỗi nhập liệu đi hết pipeline mới lộ |
| Hai bộ lọc `volume` riêng | Gộp thành một | Log filtergraph phân biệt được máy cân và người chọn | Đồ thị dài hơn một chút |
| Ducking bằng biểu thức, không cắt đoạn | Tách bài hát thành từng đoạn rồi ghép | Một bộ lọc phục vụ cả 500 lần chèn | Phải bật `eval=frame`, tốn CPU hơn |
| Luôn có limiter khi có chèn | Tin rằng tổng không vượt trần | Nhạc master sát 0 dBFS chắc chắn vượt | Thêm một bộ lọc vào chuỗi |
| `startOffset` vượt độ dài → chép nguyên | Báo lỗi | Không có gì để chèn thì không có gì để hỏng | Bài hát `PROCESSED` mà không hề có tag, không cảnh báo |
| Cấu hình chỉ tạo một lần | Cho sửa | Đường xử lý chỉ có một lối vào, dễ suy luận | Xem mục 5 — kéo theo cả chuỗi hệ quả |

---

## 7. Tự kiểm chứng

**Xem cấu hình của một bài hát:**

```bash
docker exec -e PGPASSWORD="$(grep -E '^POSTGRES_PASSWORD=' .env | cut -d= -f2-)" pwb-postgres psql -U pwb_user -d pwb_db -c "SELECT interval_seconds, volume_percentage, ducking_percentage, start_offset_seconds, enabled FROM audio_song_tag_configs;"
```

**Đọc qua API:**

```bash
T=$(curl -s -X POST http://localhost:8080/api/v1/auth/login -H "Content-Type: application/json" -d '{"email":"pro1@gmail.com","password":"@NamHoang511"}' | grep -o '"accessToken":"[^"]*' | cut -d'"' -f4)
```

```bash
curl -s "http://localhost:8080/api/v1/songs/<songId>/voice-tag-config" -H "Authorization: Bearer $T"
```

Response mang kèm cả `voiceTagName`, để giao diện khỏi phải tải danh sách tag chỉ để đặt nhãn cho cái nó đang hiển thị.

**Thấy giá trị bị kẹt im lặng** — tạo bài hát với `volumePercentage: 250`, rồi đọc lại cấu hình: nhận `100`, không có cảnh báo nào ở response tạo.

**Thấy đồ thị lọc thật** — pipeline ghi filtergraph vào log. Tạo một bài có voice tag rồi tìm trong log backend chuỗi bắt đầu bằng `[1:a]aresample=44100`. Đây là cách duy nhất nhìn thấy cả gain cân độ to lẫn âm lượng người dùng chọn ([audio-04 §4](audio-04-pipeline-xu-ly.md)).

**Thấy trường hợp không chèn gì** — tạo bài hát với `startOffsetSeconds` lớn hơn độ dài bài. Kết quả `PROCESSED`, có `processed_s3_key`, nhưng file y hệt bản gốc.

---

## 8. Giới hạn hiện tại

| Giới hạn | Chi tiết |
|---|---|
| **Không sửa, không gỡ, không thêm cấu hình sau khi tạo** | Giới hạn lớn nhất của module — mục 5 |
| Cờ `enabled` không có đường bật/tắt | Tồn tại trong model và bảng nhưng không API nào chạm tới |
| Giá trị ngoài khoảng bị sửa im lặng | Mục 2 |
| `interval` không hợp lệ chỉ lộ sau cả pipeline | Mục 2.2 |
| `startOffset` quá lớn tạo ra bản "đã xử lý" không có tag | Mục 3.1, không cảnh báo |
| Không xem trước được kết quả ghép | Voice tag có preview ([audio-02 §3.2](audio-02-voice-tag.md)), bản ghép thì không — phải chạy hết pipeline mới nghe được |
| Bốn tham số không đủ cho mọi ý muốn | Không đặt được tag ở những mốc thời gian tuỳ ý, chỉ đặt được theo chu kỳ đều |
