# 10 - Codebase Templates

**Mục đích**: Template code base để copy cho mỗi task (Entity, Service, Controller, FE Hook, ...).

**Khi nào dùng**: KHI bắt đầu code 1 task mới, copy template phù hợp rồi sửa.

---

## 📋 Mục lục

1. [Backend: Entity](#1-backend-entity)
2. [Backend: Repository](#2-backend-repository)
3. [Backend: Service (Interface)](#3-backend-service-interface)
4. [Backend: Service (Implementation)](#4-backend-service-implementation)
5. [Backend: Controller (REST)](#5-backend-controller-rest)
6. [Backend: STOMP Controller](#6-backend-stomp-controller)
7. [Backend: DTO (Request)](#7-backend-dto-request)
8. [Backend: DTO (Response)](#8-backend-dto-response)
9. [Backend: ErrorCode Enum](#9-backend-errorcode-enum)
10. [Backend: Exception Handler](#10-backend-exception-handler)
11. [Frontend: Custom Hook](#11-frontend-custom-hook)
12. [Frontend: Component](#12-frontend-component)
13. [Frontend: API Client](#13-frontend-api-client)
14. [Frontend: STOMP Client](#14-frontend-stomp-client)
15. [Frontend: Zustand Store](#15-frontend-zustand-store)
16. [Frontend: WebRTC Peer Manager](#16-frontend-webrtc-peer-manager)
17. [Backend: Flyway Migration](#17-backend-flyway-migration)
18. [Backend: Background Job](#18-backend-background-job)

---

## 1. Backend: Entity

```java
package com.pwb.liveroom.core.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "liveroom_xxx")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class XxxEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "field_name", nullable = false, length = 100)
    private String fieldName;

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private XxxStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (updatedAt == null) updatedAt = OffsetDateTime.now();
        if (status == null) status = XxxStatus.ACTIVE;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
```

**Lưu ý**:
- KHÔNG dùng `@Data` (gây vấn đề với FK)
- KHÔng có `@Version` trừ khi là PlaybackState
- `@PrePersist` cho audit columns
- `@PreUpdate` cho updatedAt

---

## 2. Backend: Repository

```java
package com.pwb.liveroom.core.repository;

import com.pwb.liveroom.core.model.XxxEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface XxxRepository extends JpaRepository<XxxEntity, UUID> {

    Optional<XxxEntity> findByRoomIdAndUserId(UUID roomId, UUID userId);

    boolean existsByRoomId(UUID roomId);

    @Modifying
    @Query("UPDATE XxxEntity SET state = :newState WHERE roomId = :roomId AND state = :oldState")
    int updateStateByRoomIdAndState(@Param("roomId") UUID roomId,
                                     @Param("oldState") XxxStatus oldState,
                                     @Param("newState") XxxStatus newState);

    @Modifying
    @Query(value = """
        INSERT INTO liveroom_xxx (id, room_id, user_id, count, created_at)
        VALUES (gen_random_uuid(), :roomId, :userId, 1, now())
        ON CONFLICT (room_id, user_id)
        DO UPDATE SET count = liveroom_xxx.count + 1
        """, nativeQuery = true)
    void incrementCount(@Param("roomId") UUID roomId, @Param("userId") UUID userId);
}
```

---

## 3. Backend: Service (Interface)

```java
package com.pwb.liveroom.core.service;

import com.pwb.liveroom.api.dto.request.CreateXxxRequest;
import com.pwb.liveroom.api.dto.response.XxxResponse;

import java.util.Locale;
import java.util.UUID;

public interface XxxFacade {

    XxxResponse create(CreateXxxRequest request, UUID currentUserId, Locale locale);

    XxxResponse getById(UUID id, UUID currentUserId);

    void doSomething(UUID id, UUID currentUserId);
}
```

---

## 4. Backend: Service (Implementation)

```java
package com.pwb.liveroom.core.service;

import com.pwb.liveroom.api.dto.request.CreateXxxRequest;
import com.pwb.liveroom.api.dto.response.XxxResponse;
import com.pwb.liveroom.core.model.XxxEntity;
import com.pwb.liveroom.core.repository.XxxRepository;
import com.pwb.liveroom.infrastructure.exception.BusinessException;
import com.pwb.liveroom.infrastructure.exception.LiveroomErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class XxxFacadeImpl implements XxxFacade {

    private static final String MSG_XXX_CREATED = "liveroom.xxx.created";

    private final XxxRepository xxxRepository;
    private final MessageSource messageSource;

    @Transactional
    @Override
    public XxxResponse create(CreateXxxRequest request, UUID currentUserId, Locale locale) {
        log.info("Creating xxx: userId={}", currentUserId);

        XxxEntity entity = XxxEntity.builder()
            .fieldName(request.getFieldName())
            .status(XxxStatus.ACTIVE)
            .build();

        XxxEntity saved = xxxRepository.save(entity);

        log.info("Xxx created: id={}", saved.getId());

        return XxxResponse.fromEntity(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public XxxResponse getById(UUID id, UUID currentUserId) {
        XxxEntity entity = xxxRepository.findById(id)
            .orElseThrow(() -> new BusinessException(LiveroomErrorCode.XXX_NOT_FOUND));

        return XxxResponse.fromEntity(entity);
    }

    @Transactional
    @Override
    public void doSomething(UUID id, UUID currentUserId) {
        log.info("Doing something: id={}, userId={}", id, currentUserId);

        XxxEntity entity = xxxRepository.findById(id)
            .orElseThrow(() -> new BusinessException(LiveroomErrorCode.XXX_NOT_FOUND));

        // ... business logic
    }
}
```

**Lưu ý**:
- KHÔNG hardcode message, dùng `MessageSource`
- `@Transactional` cho write operations
- `@Transactional(readOnly = true)` cho read operations
- Log có context (id, userId, ...)

---

## 5. Backend: Controller (REST)

```java
package com.pwb.liveroom.infrastructure.web;

import com.pwb.liveroom.api.dto.request.CreateXxxRequest;
import com.pwb.liveroom.api.dto.response.XxxResponse;
import com.pwb.liveroom.api.dto.response.ApiResponse;
import com.pwb.liveroom.core.service.XxxFacade;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/liverooms/xxx")
@RequiredArgsConstructor
public class XxxController {

    private final XxxFacade xxxFacade;
    private final MessageSource messageSource;

    @PostMapping
    public ResponseEntity<ApiResponse<XxxResponse>> create(
            @Valid @RequestBody CreateXxxRequest request,
            @AuthenticationPrincipal UUID currentUserId) {
        XxxResponse data = xxxFacade.create(request, currentUserId, LocaleContextHolder.getLocale());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(message("liveroom.xxx.created"), data));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<XxxResponse>> getById(
            @PathVariable UUID id,
            @AuthenticationPrincipal UUID currentUserId) {
        XxxResponse data = xxxFacade.getById(id, currentUserId);
        return ResponseEntity.ok(ApiResponse.success(message("liveroom.xxx.get"), data));
    }

    @PostMapping("/{id}/action")
    public ResponseEntity<ApiResponse<Void>> doAction(
            @PathVariable UUID id,
            @AuthenticationPrincipal UUID currentUserId) {
        xxxFacade.doSomething(id, currentUserId);
        return ResponseEntity.ok(ApiResponse.success(message("liveroom.xxx.action"), null));
    }

    private String message(String key, Object... args) {
        return messageSource.getMessage(key, args, key, LocaleContextHolder.getLocale());
    }
}
```

---

## 6. Backend: STOMP Controller

```java
package com.pwb.liveroom.infrastructure.ws;

import com.pwb.liveroom.api.dto.request.MusicPlayRequest;
import com.pwb.liveroom.core.service.MusicService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

@Slf4j
@Controller
@RequiredArgsConstructor
public class MusicStompController {

    private final MusicService musicService;

    @MessageMapping("/liveroom/{roomId}/music/play")
    public void play(@DestinationVariable UUID roomId,
                      @Payload MusicPlayRequest request,
                      Principal principal) {
        UUID userId = UUID.fromString(principal.getName());
        log.info("STOMP music/play: roomId={}, userId={}, songId={}", roomId, userId, request.getSongId());

        try {
            musicService.play(roomId, request.getSongId(), userId);
        } catch (BusinessException e) {
            log.warn("Business error: {}", e.getMessage());
            throw e;
        }
    }

    @MessageMapping("/liveroom/{roomId}/music/pause")
    public void pause(@DestinationVariable UUID roomId, Principal principal) {
        UUID userId = UUID.fromString(principal.getName());
        musicService.pause(roomId, userId);
    }

    @MessageMapping("/liveroom/{roomId}/music/get-state")
    public void getState(@DestinationVariable UUID roomId, Principal principal) {
        UUID userId = UUID.fromString(principal.getName());
        musicService.getStateAndSend(roomId, userId);
    }
}
```

---

## 7. Backend: DTO (Request)

```java
package com.pwb.liveroom.api.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateXxxRequest {

    @NotBlank(message = "{validation.blank}")
    @Size(min = 1, max = 100, message = "{validation.size}")
    private String fieldName;

    @Min(value = 1, message = "{validation.min}")
    @Max(value = 7, message = "{validation.max}")
    private Integer maxValue;

    @Email(message = "{validation.email}")
    private String email;
}
```

---

## 8. Backend: DTO (Response)

```java
package com.pwb.liveroom.api.dto.response;

import com.pwb.liveroom.core.model.XxxEntity;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class XxxResponse {

    private UUID id;
    private String fieldName;
    private String status;
    private OffsetDateTime createdAt;

    public static XxxResponse fromEntity(XxxEntity entity) {
        return XxxResponse.builder()
            .id(entity.getId())
            .fieldName(entity.getFieldName())
            .status(entity.getStatus().name())
            .createdAt(entity.getCreatedAt())
            .build();
    }
}
```

---

## 9. Backend: ErrorCode Enum

```java
package com.pwb.liveroom.infrastructure.exception;

import org.springframework.http.HttpStatus;

public enum LiveroomErrorCode implements ErrorCode {

    XXX_NOT_FOUND(HttpStatus.NOT_FOUND, "LIVEROOM_XXX_NOT_FOUND", "Xxx not found"),
    XXX_ALREADY_EXISTS(HttpStatus.CONFLICT, "LIVEROOM_XXX_ALREADY_EXISTS", "Xxx already exists"),
    XXX_INVALID(HttpStatus.BAD_REQUEST, "LIVEROOM_XXX_INVALID", "Xxx is invalid"),
    XXX_FORBIDDEN(HttpStatus.FORBIDDEN, "LIVEROOM_XXX_FORBIDDEN", "Not allowed to access this xxx");

    private final HttpStatus httpStatus;
    private final String code;
    private final String defaultMessage;

    LiveroomErrorCode(HttpStatus httpStatus, String code, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    @Override
    public HttpStatus httpStatus() { return httpStatus; }

    @Override
    public String code() { return code; }

    @Override
    public String defaultMessage() { return defaultMessage; }
}
```

---

## 10. Backend: Exception Handler

```java
package com.pwb.liveroom.infrastructure.exception;

import com.pwb.liveroom.api.dto.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final MessageSource messageSource;

    public GlobalExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        ErrorCode code = ex.getErrorCode();
        String message = messageSource.getMessage(code.code(), null, code.defaultMessage(), LocaleContextHolder.getLocale());
        log.warn("Business exception: code={}, message={}", code.code(), message);
        return ResponseEntity.status(code.httpStatus())
            .body(ApiResponse.error(code.code(), message));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String errors = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest()
            .body(ApiResponse.error("VALIDATION_FAILED", errors));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.internalServerError()
            .body(ApiResponse.error("INTERNAL_ERROR", "Internal server error"));
    }
}
```

---

## 11. Frontend: Custom Hook

```typescript
// frontend/src/features/liveroom/hooks/use-xxx.ts
import { useState, useEffect } from "react";

interface UseXxxOptions {
  roomId: string;
  // ... other options
}

interface UseXxxReturn {
  data: XxxData | null;
  isLoading: boolean;
  error: Error | null;
  // ... other returns
}

export function useXxx(options: UseXxxOptions): UseXxxReturn {
  const { roomId } = options;
  const [data, setData] = useState<XxxData | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  useEffect(() => {
    if (!roomId) return;

    let cancelled = false;
    setIsLoading(true);

    fetchData(roomId)
      .then((result) => {
        if (!cancelled) setData(result);
      })
      .catch((err) => {
        if (!cancelled) setError(err);
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [roomId]);

  return { data, isLoading, error };
}

async function fetchData(roomId: string): Promise<XxxData> {
  const response = await fetch(`/api/v1/liverooms/${roomId}/xxx`);
  if (!response.ok) throw new Error("Failed to fetch");
  const json = await response.json();
  return json.data;
}
```

---

## 12. Frontend: Component

```typescript
// frontend/src/features/liveroom/components/MyComponent.tsx
"use client";

import { useTranslations } from "next-intl";
import { useXxx } from "../hooks/use-xxx";

interface MyComponentProps {
  roomId: string;
  onAction?: () => void;
}

export function MyComponent({ roomId, onAction }: MyComponentProps) {
  const t = useTranslations("liveroom");
  const { data, isLoading, error } = useXxx({ roomId });

  if (isLoading) return <div>{t("common.loading")}</div>;
  if (error) return <div>{t("common.error")}</div>;
  if (!data) return null;

  return (
    <div className="p-4">
      <h2>{data.title}</h2>
      <button onClick={onAction}>
        {t("xxx.action")}
      </button>
    </div>
  );
}
```

**Lưu ý**:
- `"use client"` directive
- Dùng `useTranslations` cho i18n
- KHÔNG hardcode Vietnamese string
- Typing đầy đủ

---

## 13. Frontend: API Client

```typescript
// frontend/src/features/liveroom/api/xxx-client.ts
import { liveroomClient } from "./liveroom-client";

export interface XxxData {
  id: string;
  fieldName: string;
}

export const xxxApi = {
  create: async (data: XxxData): Promise<XxxData> => {
    const response = await liveroomClient.post("/api/v1/liverooms/xxx", data);
    return response.data;
  },

  getById: async (id: string): Promise<XxxData> => {
    const response = await liveroomClient.get(`/api/v1/liverooms/xxx/${id}`);
    return response.data;
  },

  doAction: async (id: string): Promise<void> => {
    await liveroomClient.post(`/api/v1/liverooms/xxx/${id}/action`);
  },
};
```

---

## 14. Frontend: STOMP Client

```typescript
// frontend/src/features/liveroom/lib/stomp-client.ts
import { Client, IMessage } from "@stomp/stompjs";

export function createStompClient(getToken: () => string): Client {
  return new Client({
    brokerURL: `${process.env.NEXT_PUBLIC_WS_URL}/ws/liveroom`,
    connectHeaders: {
      Authorization: `Bearer ${getToken()}`
    },
    reconnectDelay: 1000,
    heartbeatIncoming: 4000,
    heartbeatOutgoing: 4000,
  });
}

export function subscribeToTopic(
  client: Client,
  topic: string,
  handler: (message: IMessage) => void
): () => void {
  if (!client.connected) return () => {};
  const subscription = client.subscribe(topic, handler);
  return () => subscription.unsubscribe();
}
```

---

## 15. Frontend: Zustand Store

```typescript
// frontend/src/features/liveroom/stores/use-xxx-store.ts
import { create } from "zustand";

interface XxxState {
  data: XxxData | null;
  isLoading: boolean;
  error: Error | null;
  setData: (data: XxxData) => void;
  setLoading: (loading: boolean) => void;
  setError: (error: Error | null) => void;
  reset: () => void;
}

export const useXxxStore = create<XxxState>((set) => ({
  data: null,
  isLoading: false,
  error: null,
  setData: (data) => set({ data, error: null }),
  setLoading: (isLoading) => set({ isLoading }),
  setError: (error) => set({ error, isLoading: false }),
  reset: () => set({ data: null, isLoading: false, error: null }),
}));
```

---

## 16. Frontend: WebRTC Peer Manager

```typescript
// frontend/src/features/liveroom/lib/webrtc-peer-manager.ts
export class WebRTCPeerManager {
  private peerConnections = new Map<string, RTCPeerConnection>();
  private localStream: MediaStream | null = null;
  private onSignal: (targetUserId: string, payload: WebRTCSignal) => void;

  constructor(onSignal: (targetUserId: string, payload: WebRTCSignal) => void) {
    this.onSignal = onSignal;
  }

  async startLocalStream(): Promise<MediaStream> {
    this.localStream = await navigator.mediaDevices.getUserMedia({
      video: true,
      audio: true
    });
    return this.localStream;
  }

  async createPeer(targetUserId: string): Promise<RTCPeerConnection> {
    const pc = new RTCPeerConnection({
      iceServers: [{ urls: "stun:stun.l.google.com:19302" }]
    });

    pc.onicecandidate = (event) => {
      if (event.candidate) {
        this.onSignal(targetUserId, {
          type: "candidate",
          candidate: event.candidate
        });
      }
    };

    if (this.localStream) {
      this.localStream.getTracks().forEach((track) => {
        pc.addTrack(track, this.localStream!);
      });
    }

    this.peerConnections.set(targetUserId, pc);
    return pc;
  }

  async createOffer(targetUserId: string): Promise<void> {
    const pc = this.peerConnections.get(targetUserId) || await this.createPeer(targetUserId);
    const offer = await pc.createOffer();
    await pc.setLocalDescription(offer);
    this.onSignal(targetUserId, { type: "offer", sdp: offer });
  }

  async handleAnswer(targetUserId: string, sdp: RTCSessionDescriptionInit): Promise<void> {
    const pc = this.peerConnections.get(targetUserId);
    if (pc) await pc.setRemoteDescription(sdp);
  }

  async handleOffer(targetUserId: string, sdp: RTCSessionDescriptionInit): Promise<void> {
    const pc = await this.createPeer(targetUserId);
    await pc.setRemoteDescription(sdp);
    const answer = await pc.createAnswer();
    await pc.setLocalDescription(answer);
    this.onSignal(targetUserId, { type: "answer", sdp: answer });
  }

  async handleCandidate(targetUserId: string, candidate: RTCIceCandidateInit): Promise<void> {
    const pc = this.peerConnections.get(targetUserId);
    if (pc) await pc.addIceCandidate(candidate);
  }

  closeAll(): void {
    this.peerConnections.forEach((pc) => pc.close());
    this.peerConnections.clear();
    this.localStream?.getTracks().forEach((track) => track.stop());
  }
}

export interface WebRTCSignal {
  type: "offer" | "answer" | "candidate";
  sdp?: RTCSessionDescriptionInit;
  candidate?: RTCIceCandidateInit;
}
```

---

## 17. Backend: Flyway Migration

```sql
-- src/main/resources/db/migration/V2__liveroom_xxx.sql

CREATE TABLE liveroom_xxx (
    id UUID PRIMARY KEY,
    -- fields
    field_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- version chỉ cho PlaybackState
    -- version INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_liveroom_xxx_field ON liveroom_xxx(field_name);

-- Constraint
ALTER TABLE liveroom_xxx
    ADD CONSTRAINT uq_liveroom_xxx_field UNIQUE (field_name);
```

**Lưu ý**:
- Version file: `V{version}__{description}.sql`
- Version tăng dần: `V1`, `V2`, `V3`...
- KHÔNG sửa migration đã apply (tạo migration mới)

---

## 18. Backend: Background Job

```java
package com.pwb.liveroom.infrastructure.job;

import com.pwb.liveroom.core.repository.XxxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class XxxJob {

    private final XxxRepository xxxRepository;

    @Scheduled(fixedDelay = 60_000) // Run every 60s
    @SchedulerLock(name = "XxxJob", lockAtLeastFor = "5s", lockAtMostFor = "5m")
    public void processXxx() {
        log.debug("XxxJob started");

        OffsetDateTime threshold = OffsetDateTime.now().minusMinutes(5);
        int processed = xxxRepository.processOldRecords(threshold);

        log.info("XxxJob processed: {} records", processed);
    }
}
```

**Lưu ý**:
- `@SchedulerLock` để đảm bảo chỉ 1 instance chạy
- `lockAtLeastFor` = thời gian lock tối thiểu (tránh 2 instance chạy cùng lúc)
- `lockAtMostFor` = thời gian lock tối đa (tránh nếu instance chết)

---

## ✅ Checklist khi sử dụng template

Khi copy template, nhớ:

- [ ] Đổi tên class/interface/bean
- [ ] Đổi table name, column name
- [ ] Thêm business logic (KHÔNG giữ nguyên template)
- [ ] Thêm validation phù hợp
- [ ] Thêm error code phù hợp
- [ ] Thêm i18n key phù hợp
- [ ] Thêm log có context
- [ ] KHÔNG thêm comment (no-code-comments rule)
- [ ] Self-review với `08-self-review-checklist.md`

---

**Cập nhật lần cuối**: 2026-07-26
