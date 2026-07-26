# Phase 0: Foundation

**Thời gian**: 2-3 ngày
**Mục tiêu**: Codebase sạch, build pass, "Hello World" chạy được
**Doc tham chiếu chính**: `liveroom-data-model.md`, `liveroom-concurrency.md`, `liveroom-ws-protocol.md`

---

## 🤖 AGENT BRIEFING

> **Copy toàn bộ khối code dưới đây → paste cho AI agent (Cursor).**
> Agent sẽ tự đọc docs và làm theo đúng quy trình.

```
Bạn là Senior Backend Dev + Frontend Dev cho dự án PWB MiNi (Spring Boot multi-module + Next.js 14 + TypeScript).

Project: d:\Learning\Project\PWB_MiNi\
Module mới cần tạo: liveroom (folder: Backend/modules/liveroom/, FE: frontend/src/features/liveroom/)

## 8 WORKSPACE RULES (BẮT BUỘC TUÂN THỦ - đọc file .cursor/rules/):
1. senior-dev-coding-standards.mdc (SOLID, Clean Code)
2. prefer-lombok-backend.mdc (@Slf4j, @RequiredArgsConstructor, @Builder)
3. no-code-comments-backend.mdc (KHÔNG comment trong Java)
4. no-code-comments-frontend.mdc (KHÔNG comment trong TS/TSX)
5. no-hardcoded-messages-backend.mdc (dùng MessageSource, i18n)
6. no-auto-create-tests-backend.mdc (KHÔNG tự viết test)
7. no-auto-commit-push-backend.mdc (KHÔNG tự git commit/push)
8. respond-in-vietnamese.mdc (trả lời user bằng tiếng Việt)

## PHASE 0 LÀ GÌ:
Foundation - tạo skeleton Maven module + 11 entity classes + Flyway migration + WebSocket/STOMP config + MessageSource (i18n) + JWT Auth filter + Health check + FE features folder + API gateway route. Build & start thành công.

## BẠN PHẢI ĐỌC TRƯỚC KHI CODE (theo thứ tự):
1. docs/liveroom/planning/README.md (overview toàn project)
2. docs/liveroom/planning/00-reading-guide.md (cách đọc docs gốc)
3. docs/liveroom/planning/02-phase-0-foundation.md (FILE NÀY - chi tiết 10 tasks)
4. docs/liveroom/planning/10-codebase-templates.md (copy template code)
5. docs/liveroom/planning/08-self-review-checklist.md (checklist tự review trước commit)
6. docs/liveroom/data-model.md §2-3 (entity + schema)
7. docs/liveroom/concurrency.md §1-2 (locking strategy: KHÔNG @Version trên LiveRoom)
8. docs/liveroom/ws-protocol.md §1-2 (WebSocket setup)
9. docs/liveroom/i18n-keys.md (i18n keys có sẵn)
10. docs/liveroom/permission-matrix.md §1 (roles cho auth filter)

## 10 TASKS BẠN PHẢI LÀM (theo thứ tự):

### Task 0.1 - Maven module skeleton
Tạo folder Backend/modules/liveroom/ với pom.xml (Spring Boot Web, Data JPA, WebSocket, Validation, Security, Flyway, PostgreSQL, Lombok) + folder structure: api/, core/{model,service,repository}/, infrastructure/{web,config,ws}/, resources/db/migration/. Đăng ký module trong parent pom.xml. Copy template từ 10-codebase-templates.md §1.

### Task 0.2 - 11 Entity classes
Tạo 11 entity trong com.pwb.liveroom.core.model package: LiveRoom, RoomSessionCycle, Participant, JoinRequest, RejectCounter, ChatMessage, PlaybackState, Annotation, RoomAdminAction, RoomOwnershipHistory, UserPrivacyAction. Dùng template §1 Backend Entity.
⚠️ QUAN TRỌNG:
- LiveRoom KHÔNG có @Version (quyết định C2)
- PlaybackState CÓ @Version (sẽ dùng ở Phase 4)
- @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder (KHÔNG @Data)
- @PrePersist + @PreUpdate cho audit columns (createdAt, updatedAt)
- Doc tham chiếu: data-model.md §3.1-3.11.

### Task 0.3 - Flyway migration V1
Tạo file Backend/modules/liveroom/src/main/resources/db/migration/V1__liveroom_initial_schema.sql. 11 bảng với columns + types + NOT NULL + UNIQUE + FK + indexes theo data-model.md §3. Doc tham chiếu: data-model.md §2-3. ⚠️ LiveRoom KHÔNG có cột version.

### Task 0.4 - WebSocketConfig + STOMP
Tạo 2 file trong com.pwb.liveroom.infrastructure.config: WebSocketConfig.java (broker /topic,/queue, prefix /app, endpoint /ws/liveroom) + StompChannelInterceptor.java (JWT auth trong CONNECT). Doc tham chiếu: ws-protocol.md §1-2.

### Task 0.5 - MessageSource (i18n)
Tạo 3 file trong Backend/modules/liveroom/src/main/resources/messages/: messages.properties (fallback EN), messages_en.properties, messages_vi.properties. Copy 30 keys đầu tiên từ liveroom-i18n-keys.md. Locale resolve từ Accept-Language header.

### Task 0.6 - JWT Auth filter + SecurityConfig
Tạo SecurityConfig.java (CSRF disable, CORS, stateless session, /health và /ws/** permitAll, còn lại authenticated) + JwtAuthFilter.java (parse Bearer token, set userId vào SecurityContext). Doc tham chiếu: permission-matrix.md §1.

### Task 0.7 - Health check + Swagger
Tạo HealthController với GET /api/v1/liverooms/health → 200 OK "LiveRoom module is running". Tạo OpenApiConfig cho Swagger UI.

### Task 0.8 - FE features folder
Tạo folder frontend/src/features/liveroom/ với sub-folders: api/, components/, hooks/, lib/, schemas/, stores/, types/. Tạo file placeholder cho mỗi folder + file liveroom-client.ts (createApiClient wrapper).

### Task 0.9 - API gateway route
Setup route /api/v1/liverooms/** trong Backend/gateway/ → service liveroom (strip prefix).

### Task 0.10 - Verify build pass
Chạy mvn clean install (BE) + pnpm build (FE) + start app → curl health → verify Swagger + WS endpoint. Báo cáo kết quả.

## YÊU CẦU ĐẶC BIỆT (BẮT BUỘC):
- KHÔNG thêm comment vào code (rule no-code-comments)
- KHÔNG hardcode Vietnamese/English message, dùng MessageSource với key
- Dùng Lombok: @Slf4j + @RequiredArgsConstructor cho Service, @Data + @Builder cho DTO, @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder cho Entity (KHÔNG @Data cho Entity)
- KHÔNG có @Version trên LiveRoom
- Tuân thủ 8 rules trong .cursor/rules/
- Không commit tự động (rule no-auto-commit)
- Trả lời user bằng tiếng Việt

## OUTPUT MONG ĐỢI:
- Code đầy đủ cho 10 tasks
- Mỗi task có Acceptance Criteria PASS (xem section TASK DETAILS bên dưới)
- Tự check 08-self-review-checklist.md trước khi báo cáo
- Báo cáo cuối: (1) file nào đã tạo/sửa, (2) checklist tự review pass/fail, (3) warning nếu có, (4) ngày hoàn thành dự kiến

## BẮT ĐẦU ĐỌC 10 DOCS TRÊN. SAU ĐÓ LÀM TỪNG TASK THEO THỨ TỰ.
```

---

## 📋 TASK DETAILS

---

## 📋 Tổng quan Phase 0

| Task | Thời gian | Độ khó | Trạng thái |
|---|---|---|---|
| 0.1 Maven module skeleton | 2 giờ | ⭐ | ⏳ |
| 0.2 Entity classes (11) | 4 giờ | ⭐⭐ | ⏳ |
| 0.3 Flyway migration | 3 giờ | ⭐⭐ | ⏳ |
| 0.4 WebSocketConfig + STOMP | 2 giờ | ⭐⭐⭐ | ⏳ |
| 0.5 MessageSource (i18n) | 1 giờ | ⭐ | ⏳ |
| 0.6 JWT Auth filter | 2 giờ | ⭐⭐ | ⏳ |
| 0.7 Health check + Swagger | 1 giờ | ⭐ | ⏳ |
| 0.8 FE features folder | 2 giờ | ⭐ | ⏳ |
| 0.9 API gateway route | 1 giờ | ⭐ | ⏳ |
| 0.10 Verify build pass | 1 giờ | ⭐ | ⏳ |

---

## Task 0.1: Maven module skeleton

**Mục tiêu**: Tạo Maven module `liveroom` đúng cấu trúc multi-module.

**Doc tham chiếu**:
- `liveroom-data-model.md` §2 (Entity overview)

**Action items**:
1. Tạo folder `Backend/modules/liveroom/`
2. Tạo `pom.xml` với dependencies:
   - Spring Boot Starter Web
   - Spring Boot Starter Data JPA
   - Spring Boot Starter WebSocket
   - Spring Boot Starter Validation
   - Spring Boot Starter Security
   - Flyway Core + Flyway DB specific
   - PostgreSQL driver
   - Lombok (provided)
   - Spring Boot Starter Test (test scope)
3. Tạo sub-folder:
   - `src/main/java/com/pwb/liveroom/`
     - `api/` (DTOs, controllers)
     - `core/`
       - `model/` (entities)
       - `service/` (services)
       - `repository/` (repositories)
     - `infrastructure/`
       - `web/` (controllers, filters)
       - `config/` (WebSocketConfig, SecurityConfig)
       - `ws/` (STOMP handlers)
   - `src/main/resources/`
     - `db/migration/` (Flyway)

**Acceptance Criteria**:
- [ ] `mvn clean install` pass
- [ ] Module được nhận trong parent `pom.xml`
- [ ] Folder structure đúng convention

---

## Task 0.2: 11 Entity classes

**Mục tiêu**: Tạo 11 entity classes (chỉ field + Lombok, chưa có quan hệ phức tạp).

**Doc tham chiếu**:
- `liveroom-data-model.md` §3 (Entity chi tiết)

**11 entities cần tạo**:

| # | Entity class | Bảng | Số fields |
|---|---|---|---|
| 1 | `LiveRoom` | `liveroom_rooms` | 18 |
| 2 | `RoomSessionCycle` | `liveroom_room_session_cycles` | 8 |
| 3 | `Participant` | `liveroom_participants` | 14 |
| 4 | `JoinRequest` | `liveroom_join_requests` | 12 |
| 5 | `RejectCounter` | `liveroom_reject_counters` | 6 |
| 6 | `ChatMessage` | `liveroom_chat_messages` | 8 |
| 7 | `PlaybackState` | `liveroom_playback_states` | 11 |
| 8 | `Annotation` | `liveroom_annotations` | 9 |
| 9 | `RoomAdminAction` | `liveroom_admin_actions` | 9 |
| 10 | `RoomOwnershipHistory` | `liveroom_ownership_history` | 7 |
| 11 | `UserPrivacyAction` | `liveroom_privacy_actions` | 7 |

**Template**:

```java
@Entity
@Table(name = "liveroom_rooms")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LiveRoom {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "room_code", nullable = false, length = 6, unique = true)
    private String roomCode;

    // ... các fields khác theo data-model.md §3.1

    // KHÔNG dùng @Version (đã quyết định ở C2)
    // KHÔNG dùng @Data (vì có FK relationships)

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
        if (updatedAt == null) updatedAt = OffsetDateTime.now();
        if (status == null) status = RoomStatus.ACTIVE;
        if (currentParticipantCount == null) currentParticipantCount = 0;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
```

**Acceptance Criteria**:
- [ ] 11 entity classes compile pass
- [ ] Mỗi entity có `@Entity`, `@Table(name=...)`
- [ ] `@Id` + `@GeneratedValue(UUID)`
- [ ] `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder` (KHÔNG `@Data`)
- [ ] `@PrePersist` + `@PreUpdate` cho audit columns
- [ ] LiveRoom KHÔNG có `@Version` (chỉ PlaybackState có)
- [ ] Mỗi field có `@Column(name, nullable, length, unique...)` đúng DB schema

---

## Task 0.3: Flyway migration

**Mục tiêu**: Tạo migration `V1__liveroom_initial_schema.sql` cho 11 bảng.

**Doc tham chiếu**:
- `liveroom-data-model.md` §3 (Entity chi tiết - cột + type + constraint)

**File**: `Backend/modules/liveroom/src/main/resources/db/migration/V1__liveroom_initial_schema.sql`

**Template**:

```sql
-- Table 1: liveroom_rooms
CREATE TABLE liveroom_rooms (
    id UUID PRIMARY KEY,
    room_code VARCHAR(6) NOT NULL,
    room_name VARCHAR(100) NOT NULL,
    room_name_normalized VARCHAR(100) NOT NULL,
    owner_id UUID NOT NULL,
    max_participants SMALLINT NOT NULL DEFAULT 7,
    current_participant_count SMALLINT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ended_reason VARCHAR(30),
    ended_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    owner_left_at TIMESTAMPTZ,
    owner_grace_seconds SMALLINT NOT NULL DEFAULT 60,
    reserved_owner_slot BOOLEAN NOT NULL DEFAULT FALSE,
    current_session_cycle_id UUID,
    -- KHÔNG có version (đã quyết định ở C2)
    CONSTRAINT uq_liveroom_rooms_room_code UNIQUE (room_code)
);

CREATE INDEX idx_liveroom_rooms_owner ON liveroom_rooms(owner_id);
CREATE INDEX idx_liveroom_rooms_status ON liveroom_rooms(status);

-- ... (tương tự cho 10 bảng còn lại, theo data-model.md §3.2-3.11)

-- Table 8: liveroom_playback_states (CHỈ entity có @Version)
CREATE TABLE liveroom_playback_states (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL,
    song_id UUID,
    status VARCHAR(20) NOT NULL,
    current_position_seconds DOUBLE PRECISION NOT NULL DEFAULT 0,
    volume_percent SMALLINT NOT NULL DEFAULT 80,
    sequence_number BIGINT NOT NULL DEFAULT 0,
    last_updated_at TIMESTAMPTZ NOT NULL,
    last_updated_by UUID,
    version INTEGER NOT NULL DEFAULT 0,  -- @Version optimistic lock
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_liveroom_playback_states_room UNIQUE (room_id)
);
```

**Acceptance Criteria**:
- [ ] Migration file execute thành công với `mvn flyway:migrate`
- [ ] 11 bảng được tạo đúng schema
- [ ] Foreign keys được tạo (nếu có)
- [ ] Indexes được tạo
- [ ] LiveRoom KHÔNG có cột `version`
- [ ] PlaybackState CÓ cột `version`

---

## Task 0.4: WebSocketConfig + STOMP

**Mục tiêu**: Setup WebSocket + STOMP cho module.

**Doc tham chiếu**:
- `liveroom-ws-protocol.md` §1-2 (Overview + Topic structure)

**Files cần tạo**:
1. `Backend/modules/liveroom/src/main/java/com/pwb/liveroom/infrastructure/config/WebSocketConfig.java`
2. `Backend/modules/liveroom/src/main/java/com/pwb/liveroom/infrastructure/config/StompChannelInterceptor.java`

**Template WebSocketConfig**:

```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompChannelInterceptor stompChannelInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/liveroom")
                .setAllowedOrigins("http://localhost:3000", "https://pwb.example.com")
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompChannelInterceptor);
    }
}
```

**Template StompChannelInterceptor** (JWT auth):

```java
@Component
@RequiredArgsConstructor
public class StompChannelInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                UUID userId = jwtService.validateAndGetUserId(token);
                accessor.setUser(new UsernamePasswordAuthenticationToken(userId, null, List.of()));
            }
        }
        return message;
    }
}
```

**Acceptance Criteria**:
- [ ] WebSocket endpoint `/ws/liveroom` accessible
- [ ] STOMP CONNECT với JWT token pass auth
- [ ] Subscribe `/topic/liveroom/{roomId}/...` hoạt động
- [ ] Send `/app/liveroom/{roomId}/...` route đúng

---

## Task 0.5: MessageSource (i18n)

**Mục tiêu**: Setup i18n cho backend (theo rule `no-hardcoded-messages-backend.mdc`).

**Doc tham chiếu**:
- `liveroom-i18n-keys.md` (toàn bộ)

**Files cần tạo**:
1. `Backend/modules/liveroom/src/main/resources/messages/messages.properties` (fallback)
2. `Backend/modules/liveroom/src/main/resources/messages/messages_en.properties`
3. `Backend/modules/liveroom/src/main/resources/messages/messages_vi.properties`

**Template keys** (copy từ `liveroom-i18n-keys.md`):

```properties
# messages.properties (fallback = EN)
liveroom.create.success=Room created successfully
liveroom.join.request.success=Join request sent
liveroom.approve.success=User approved
liveroom.rejected.capacity=Room is full
liveroom.invalid.room_code=Invalid room code
# ... (tất cả keys trong liveroom-i18n-keys.md)
```

**Acceptance Criteria**:
- [ ] 3 file properties được tạo
- [ ] MessageSource bean được register
- [ ] Locale resolve từ `Accept-Language` header
- [ ] Test với `curl -H "Accept-Language: vi"` trả về tiếng Việt

---

## Task 0.6: JWT Auth filter

**Mục tiêu**: Setup JWT auth cho REST API.

**Doc tham chiếu**:
- `liveroom-permission-matrix.md` §1 (Roles)

**Files cần tạo**:
1. `Backend/modules/liveroom/src/main/java/com/pwb/liveroom/infrastructure/web/JwtAuthFilter.java`
2. `Backend/modules/liveroom/src/main/java/com/pwb/liveroom/infrastructure/config/SecurityConfig.java`

**Template SecurityConfig**:

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(CorsConfigurer::configurationSource)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/liverooms/health", "/ws/**").permitAll()
                .requestMatchers("/api/v1/liverooms/**").authenticated()
                .anyRequest().permitAll())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }
}
```

**Acceptance Criteria**:
- [ ] Request không có JWT → 401
- [ ] Request có JWT hợp lệ → pass
- [ ] User ID được set vào SecurityContext
- [ ] `@PreAuthorize` works (e.g. `@PreAuthorize("hasRole('PRO')")`)

---

## Task 0.7: Health check + Swagger

**Mục tiêu**: Health endpoint + Swagger UI cho API docs.

**Files cần tạo**:
1. `Backend/modules/liveroom/src/main/java/com/pwb/liveroom/api/HealthController.java`
2. `Backend/modules/liveroom/src/main/java/com/pwb/liveroom/infrastructure/config/OpenApiConfig.java`

**Template HealthController**:

```java
@RestController
@RequestMapping("/api/v1/liverooms")
public class HealthController {

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<String>> health() {
        return ResponseEntity.ok(ApiResponse.success("OK", "LiveRoom module is running"));
    }
}
```

**Acceptance Criteria**:
- [ ] `GET /api/v1/liverooms/health` → 200 OK
- [ ] Swagger UI accessible ở `/swagger-ui.html`
- [ ] OpenAPI JSON có tất cả endpoints

---

## Task 0.8: FE features folder

**Mục tiêu**: Setup folder structure cho frontend.

**Doc tham chiếu**:
- `liveroom-screen-inventory.md` §1 (Component tree)

**Folders cần tạo**:
```
frontend/src/features/liveroom/
├── api/
│   ├── liveroom-client.ts
│   ├── participants.ts
│   ├── ws.ts
│   └── leave-beacon.ts
├── components/
│   └── (empty - sẽ thêm ở Phase 1+)
├── hooks/
│   ├── use-media-session-lifecycle.ts
│   └── use-live-room-media.ts
├── lib/
│   ├── media-session.ts
│   └── webrtc-peer-manager.ts
├── schemas/
│   └── room-schema.ts
├── stores/
│   └── use-live-room-media-store.ts
├── types/
│   └── liveroom.types.ts
└── index.ts
```

**Template** `liveroom-client.ts`:

```typescript
import { createApiClient } from "@/lib/api-client";

export const liveroomClient = createApiClient("/api/v1/liverooms");
```

**Acceptance Criteria**:
- [ ] Folder structure đúng convention
- [ ] Base API client hoạt động
- [ ] Route `/live-room/test` render placeholder

---

## Task 0.9: API gateway route

**Mục tiêu**: Setup gateway route `/api/v1/liverooms/*` → liveroom module.

**Doc tham chiếu**:
- Architecture docs của project

**Action items**:
1. Mở `Backend/gateway/` config (Spring Cloud Gateway hoặc Nginx)
2. Thêm route:
   - Path: `/api/v1/liverooms/**`
   - Service: `liveroom`
   - Strip prefix: `/api/v1/liverooms`

**Acceptance Criteria**:
- [ ] `GET http://gateway/api/v1/liverooms/health` → 200 OK
- [ ] Route đúng đến liveroom service

---

## Task 0.10: Verify build pass

**Mục tiêu**: Đảm bảo tất cả compile + start thành công.

**Checklist**:
- [ ] `mvn clean install` pass (Backend)
- [ ] `mvn spring-boot:run` start thành công
- [ ] Swagger UI accessible
- [ ] `pnpm build` pass (Frontend)
- [ ] `pnpm dev` start thành công
- [ ] Connect to DB thành công
- [ ] Flyway migration applied (check table `flyway_schema_history`)

---

## 🚦 Definition of Done Phase 0

- [ ] Tất cả 10 tasks DONE
- [ ] Backend build + start pass
- [ ] Frontend build + start pass
- [ ] DB schema migrated thành công
- [ ] WebSocket endpoint accessible
- [ ] Health check 200 OK
- [ ] JWT auth works
- [ ] i18n works (EN + VI)
- [ ] Có bằng chứng: screenshots / curl output

---

## ⚠️ Common Pitfalls

1. **Quên `@PrePersist`/`@PreUpdate`** → `created_at` NULL → DB constraint fail
2. **Dùng `@Data` cho entity** → `equals/hashCode` gây infinite loop với FK
3. **Quên `@Version` trên PlaybackState** → race condition music không protect
4. **Thêm `@Version` trên LiveRoom** → conflict với pessimistic lock (đã quyết định ở C2)
5. **Flyway migration sai tên cột** → startup fail
6. **JWT filter order sai** → CORS hoặc auth fail
7. **WebSocket CORS không config** → connect fail từ frontend

---

## 📋 Next Step

Sau khi Phase 0 DONE → chuyển sang `03-phase-1-create-join.md`.

---

**Cập nhật**: 2026-07-26
