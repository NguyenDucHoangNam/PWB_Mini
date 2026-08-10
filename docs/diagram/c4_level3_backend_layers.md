# C4 mức 3 — Bốn tầng bên trong container backend

> **Chuẩn C4 — Level 3: Component**: Chi tiết giải phẫu 4 tầng thành phần bên trong container Backend (`pwb-backend`). Cả ba module `iam`, `audio`, `liveroom` đều áp dụng đồng nhất cấu trúc 4 tầng này.

---

## 1. Biểu đồ Mermaid

```mermaid
graph TD
    classDef appBox fill:#483D8B,stroke:#7A70D6,stroke-width:2px,color:#FFFFFF,rx:8px,ry:8px;
    classDef domainBox fill:#0D6B56,stroke:#22A385,stroke-width:2px,color:#FFFFFF,rx:8px,ry:8px;

    subgraph BACKEND ["pwb-backend — cả ba module iam · audio · liveroom đều lặp lại bốn tầng này"]
        direction TD
        
        API["<b>api</b><br/>[Tầng]<br/><i>11 REST controller + 4 STOMP controller · DTO request & response</i>"]:::appBox
        
        APP["<b>application</b><br/>[Tầng]<br/><i>57 use case impl · command → view/dto · @Transactional đặt ở method</i>"]:::appBox
        
        DOM["<b>domain</b><br/>[Tầng — Java thuần, không Spring, không JPA]<br/><i>model · vo · enums · 17 repository port · service port</i>"]:::domainBox
        
        INFRA["<b>infrastructure</b><br/>[Tầng]<br/><i>18 JPA entity · mapper viết tay · adapter · realtime STOMP · scheduler</i>"]:::appBox

        API --> APP
        APP --> DOM
        INFRA --> DOM
    end
```

---

## 2. Mô tả Chi tiết Các Tầng Thành phần (Components)

| Tầng / Component | Đặc trưng Kỹ thuật | Trách nhiệm chính |
|---|---|---|
| **`api`** | Spring Controllers | Tiếp nhận request: 11 REST controllers + 4 STOMP controllers. Quản lý DTO Request & Response. |
| **`application`** | Use Cases & Commands | 57 Use Case Implementations. Chuyển đổi Command sang View/DTO. Quản lý giao tác với `@Transactional` đặt tại cấp method. |
| **`domain`** | Pure Java (Không Spring, Không JPA) | Trái tim nghiệp vụ: Models, Value Objects (VO), Enums, 17 Repository Ports và Service Ports. |
| **`infrastructure`** | JPA, Redis, Adapters | Implement các Ports của Domain: 18 JPA Entities, Mappers viết tay, Adapters kết nối hệ thống ngoài, Realtime STOMP & Schedulers. |
