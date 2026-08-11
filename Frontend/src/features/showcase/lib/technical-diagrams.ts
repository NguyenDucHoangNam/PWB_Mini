/* Geometry for the five architecture diagrams in docs/diagram/, redrawn for the technical
   page. One spec per diagram, all rendered by the same component.

   Every coordinate is a fixed logical pixel rather than a responsive rule. Connector
   endpoints have to agree with box edges to within a pixel, and no flex or grid arrangement
   can promise that once a translated label wraps to a different number of lines. Each canvas
   keeps its size at every viewport and scrolls sideways when the panel is narrower — the
   standard treatment for a diagram, and the only one that keeps labels readable on a phone.

   Consequence worth knowing before editing: moving a box means recomputing every edge that
   touches it, and every label position derived from those edges. */

/* The four classDef colours the mermaid sources share, kept identical on purpose: a reader
   who has seen these diagrams in docs/ should recognise them as the same drawings. All four
   fills are dark enough to carry white text, so neither theme needs a variant. */
export type DiagramKind = "actor" | "app" | "data" | "external";

export const DIAGRAM_KIND_STYLE: Record<DiagramKind, { fill: string; border: string }> = {
  actor: { fill: "#963E2E", border: "#D96B52" },
  app: { fill: "#483D8B", border: "#7A70D6" },
  data: { fill: "#0D6B56", border: "#22A385" },
  external: { fill: "#3D3D3D", border: "#707070" },
};

export interface DiagramNode {
  /** React key, and the message key under `<diagram>.nodes`. */
  key: string;
  kind: DiagramKind;
  x: number;
  y: number;
  w: number;
  h: number;
}

export interface DiagramBoundary {
  key: string;
  x: number;
  y: number;
  w: number;
  h: number;
}

export interface DiagramEdge {
  /** React key, and the message key under `<diagram>.edges`. */
  key: string;
  /** Node keys. Used for the screen-reader description, not for drawing. */
  from: string;
  to: string;
  x1: number;
  y1: number;
  x2: number;
  y2: number;
  /* Labels are anchored by their BOTTOM edge, sitting a few pixels above the line at labelX,
     so a label that wraps to a third line grows upward into empty space rather than downward
     onto its own connector. Omitted where the relationship is already obvious from the two
     boxes it joins and the gap is too tight to hold text — an unreadable label is worse than
     no label. */
  labelX?: number;
  labelBottom?: number;
}

export interface DiagramSpec {
  /** Message key under `features.technical.diagrams`. */
  id: string;
  width: number;
  height: number;
  boundaries: readonly DiagramBoundary[];
  nodes: readonly DiagramNode[];
  edges: readonly DiagramEdge[];
  /** Which colours to explain, in the order shown. */
  legend: readonly DiagramKind[];
}

/* ---------------------------------------------------------------- level 1: system context */

export const CONTEXT_DIAGRAM: DiagramSpec = {
  id: "context",
  width: 1100,
  height: 550,
  boundaries: [
    { key: "people", x: 0, y: 74, w: 232, h: 364 },
    { key: "external", x: 868, y: 16, w: 232, h: 480 },
  ],
  nodes: [
    { key: "producer", kind: "actor", x: 14, y: 112, w: 204, h: 92 },
    { key: "guest", kind: "actor", x: 14, y: 222, w: 204, h: 92 },
    { key: "admin", kind: "actor", x: 14, y: 332, w: 204, h: 92 },

    { key: "workbench", kind: "app", x: 420, y: 186, w: 260, h: 140 },

    { key: "storage", kind: "external", x: 882, y: 54, w: 204, h: 76 },
    { key: "oauth", kind: "external", x: 882, y: 142, w: 204, h: 76 },
    { key: "tts", kind: "external", x: 882, y: 230, w: 204, h: 76 },
    { key: "turn", kind: "external", x: 882, y: 318, w: 204, h: 76 },
    { key: "smtp", kind: "external", x: 882, y: 406, w: 204, h: 76 },
  ],
  edges: [
    { key: "producerSystem", from: "producer", to: "workbench", x1: 218, y1: 158, x2: 420, y2: 212, labelX: 326, labelBottom: 181 },
    { key: "guestSystem", from: "guest", to: "workbench", x1: 218, y1: 268, x2: 420, y2: 256, labelX: 326, labelBottom: 255 },
    { key: "adminSystem", from: "admin", to: "workbench", x1: 218, y1: 378, x2: 420, y2: 300, labelX: 326, labelBottom: 330 },

    { key: "systemStorage", from: "workbench", to: "storage", x1: 680, y1: 200, x2: 882, y2: 92, labelX: 774, labelBottom: 143 },
    { key: "systemOauth", from: "workbench", to: "oauth", x1: 680, y1: 230, x2: 882, y2: 180, labelX: 774, labelBottom: 200 },
    { key: "systemTts", from: "workbench", to: "tts", x1: 680, y1: 260, x2: 882, y2: 268, labelX: 774, labelBottom: 257 },
    { key: "systemTurn", from: "workbench", to: "turn", x1: 680, y1: 290, x2: 882, y2: 356, labelX: 774, labelBottom: 314 },
    { key: "systemSmtp", from: "workbench", to: "smtp", x1: 680, y1: 315, x2: 882, y2: 444, labelX: 774, labelBottom: 368 },
  ],
  legend: ["actor", "app", "external"],
};

/* -------------------------------------------------------------------- level 2: containers */

/* The mermaid source nests three sub-groups inside the VPS (entry / services / data). Those
   are crutches for mermaid's auto-placement rather than C4 semantics, and dropping them buys
   back the horizontal room the arrow labels need. The three tiers survive as rows. */
export const CONTAINER_DIAGRAM: DiagramSpec = {
  id: "containers",
  width: 1100,
  height: 700,
  boundaries: [
    { key: "vps", x: 0, y: 140, w: 700, h: 470 },
    { key: "external", x: 800, y: 140, w: 300, h: 470 },
  ],
  nodes: [
    { key: "browser", kind: "actor", x: 430, y: 0, w: 240, h: 92 },

    { key: "nginx", kind: "app", x: 24, y: 192, w: 200, h: 88 },
    { key: "coturn", kind: "app", x: 248, y: 192, w: 200, h: 88 },
    { key: "next", kind: "app", x: 24, y: 344, w: 200, h: 88 },
    { key: "spring", kind: "app", x: 248, y: 344, w: 200, h: 88 },
    { key: "postgres", kind: "data", x: 24, y: 496, w: 200, h: 88 },
    { key: "redis", kind: "data", x: 248, y: 496, w: 200, h: 88 },
    { key: "kafka", kind: "data", x: 472, y: 496, w: 200, h: 88 },

    { key: "storage", kind: "external", x: 822, y: 192, w: 256, h: 88 },
    { key: "oauth", kind: "external", x: 822, y: 292, w: 256, h: 88 },
    { key: "tts", kind: "external", x: 822, y: 392, w: 256, h: 88 },
    { key: "smtp", kind: "external", x: 822, y: 492, w: 256, h: 88 },
  ],
  edges: [
    { key: "browserNginx", from: "browser", to: "nginx", x1: 490, y1: 92, x2: 124, y2: 192, labelX: 307, labelBottom: 136 },
    { key: "browserCoturn", from: "browser", to: "coturn", x1: 580, y1: 92, x2: 348, y2: 192, labelX: 464, labelBottom: 136 },
    { key: "browserStorage", from: "browser", to: "storage", x1: 670, y1: 36, x2: 822, y2: 236, labelX: 746, labelBottom: 130 },
    { key: "browserOauth", from: "browser", to: "oauth", x1: 670, y1: 66, x2: 822, y2: 336, labelX: 746, labelBottom: 197 },

    { key: "nginxNext", from: "nginx", to: "next", x1: 124, y1: 280, x2: 124, y2: 344 },
    { key: "nginxSpring", from: "nginx", to: "spring", x1: 124, y1: 280, x2: 348, y2: 344, labelX: 300, labelBottom: 324 },

    { key: "springPostgres", from: "spring", to: "postgres", x1: 348, y1: 432, x2: 124, y2: 496, labelX: 180, labelBottom: 474 },
    { key: "springRedis", from: "spring", to: "redis", x1: 348, y1: 432, x2: 348, y2: 496 },
    { key: "springKafka", from: "spring", to: "kafka", x1: 348, y1: 432, x2: 572, y2: 496, labelX: 505, labelBottom: 471 },

    /* This column sits at 665 rather than the midpoint: any further left and it collides with
       the Kafka label above, any further right and it runs into the external boxes. */
    { key: "springStorage", from: "spring", to: "storage", x1: 448, y1: 380, x2: 822, y2: 236, labelX: 665, labelBottom: 290 },
    { key: "springOauth", from: "spring", to: "oauth", x1: 448, y1: 392, x2: 822, y2: 336, labelX: 665, labelBottom: 353 },
    { key: "springTts", from: "spring", to: "tts", x1: 448, y1: 404, x2: 822, y2: 436, labelX: 665, labelBottom: 416 },
    { key: "springSmtp", from: "spring", to: "smtp", x1: 448, y1: 416, x2: 822, y2: 536, labelX: 665, labelBottom: 479 },
  ],
  legend: ["actor", "app", "data", "external"],
};

/* ------------------------------------------------------------ level 3: backend layer cake */

export const LAYERS_DIAGRAM: DiagramSpec = {
  id: "layers",
  width: 1100,
  height: 620,
  boundaries: [{ key: "backend", x: 60, y: 20, w: 980, h: 540 }],
  nodes: [
    { key: "api", kind: "app", x: 150, y: 80, w: 320, h: 110 },
    { key: "application", kind: "app", x: 150, y: 250, w: 320, h: 110 },
    { key: "domain", kind: "data", x: 150, y: 420, w: 320, h: 110 },
    { key: "infrastructure", kind: "app", x: 640, y: 250, w: 320, h: 110 },
  ],
  edges: [
    { key: "apiApplication", from: "api", to: "application", x1: 310, y1: 190, x2: 310, y2: 250, labelX: 430, labelBottom: 226 },
    { key: "applicationDomain", from: "application", to: "domain", x1: 310, y1: 360, x2: 310, y2: 420, labelX: 430, labelBottom: 396 },
    { key: "infrastructureDomain", from: "infrastructure", to: "domain", x1: 740, y1: 360, x2: 470, y2: 470, labelX: 605, labelBottom: 409 },
  ],
  legend: ["app", "data"],
};

/* ------------------------------------------------------------------ deployment on the VPS */

/* No labels on the arrows inside the machine. Each joins two containers whose relationship is
   already stated in their own subtitles — ports, volumes, memory limits, which is the actual
   content of a deployment diagram — and the column gaps cannot hold readable text. The two
   arrows that cross into the machine keep their labels. */
export const DEPLOYMENT_DIAGRAM: DiagramSpec = {
  id: "deployment",
  width: 1100,
  height: 830,
  boundaries: [
    { key: "vps", x: 2, y: 140, w: 1096, h: 620 },
    { key: "network", x: 26, y: 190, w: 770, h: 540 },
    /* Taller than its one box needs. This boundary is only 232 wide, so its caption wraps to
       two lines — an absolutely positioned span is width-capped by the canvas — and coturn has
       to start below where that caption ends. */
    { key: "hostNetwork", x: 846, y: 190, w: 232, h: 270 },
  ],
  nodes: [
    { key: "browser", kind: "actor", x: 420, y: 0, w: 260, h: 92 },

    /* Taller boxes than the other diagrams carry: a deployment diagram's content is the
       ports, volumes and memory limits, and those subtitles run to three lines. */
    { key: "nginx", kind: "app", x: 50, y: 246, w: 200, h: 116 },
    { key: "certbot", kind: "app", x: 300, y: 246, w: 200, h: 116 },
    { key: "next", kind: "app", x: 50, y: 414, w: 200, h: 116 },
    { key: "spring", kind: "app", x: 300, y: 414, w: 200, h: 116 },
    { key: "postgres", kind: "data", x: 50, y: 582, w: 200, h: 116 },
    { key: "redis", kind: "data", x: 300, y: 582, w: 200, h: 116 },
    { key: "kafka", kind: "data", x: 550, y: 582, w: 200, h: 116 },

    { key: "coturn", kind: "app", x: 870, y: 270, w: 184, h: 160 },
  ],
  edges: [
    /* Sits higher above its line than the other labels do: the VPS caption runs along y=152
       and this is the only clear band left between it and the browser box. */
    { key: "browserNginx", from: "browser", to: "nginx", x1: 480, y1: 92, x2: 150, y2: 246, labelX: 315, labelBottom: 148 },
    { key: "browserCoturn", from: "browser", to: "coturn", x1: 620, y1: 92, x2: 962, y2: 270, labelX: 791, labelBottom: 175 },

    { key: "nginxNext", from: "nginx", to: "next", x1: 150, y1: 362, x2: 150, y2: 414 },
    { key: "nginxSpring", from: "nginx", to: "spring", x1: 150, y1: 362, x2: 400, y2: 414 },
    { key: "springPostgres", from: "spring", to: "postgres", x1: 400, y1: 530, x2: 150, y2: 582 },
    { key: "springRedis", from: "spring", to: "redis", x1: 400, y1: 530, x2: 400, y2: 582 },
    { key: "springKafka", from: "spring", to: "kafka", x1: 400, y1: 530, x2: 650, y2: 582 },
  ],
  legend: ["actor", "app", "data"],
};

/* ------------------------------------------------------------------ outbox, end to end */

/* The whole argument of the outbox chapter is which things share a transaction and which do
   not, so the two dashed frames carry more weight here than any box does: everything in the
   top frame either happens together or not at all, and everything in the bottom frame happens
   later, on its own time, with the user long gone.

   Only two arrows are labelled — the two that cross between those frames. Inside a frame the
   relationships are already stated by the boxes' own subtitles, and the column gaps are too
   tight to hold readable text in either language. */
export const OUTBOX_DIAGRAM: DiagramSpec = {
  id: "outbox",
  width: 1100,
  height: 700,
  boundaries: [
    { key: "transaction", x: 20, y: 120, w: 620, h: 250 },
    { key: "async", x: 20, y: 430, w: 1060, h: 210 },
  ],
  nodes: [
    { key: "user", kind: "actor", x: 50, y: 20, w: 240, h: 76 },
    { key: "usecase", kind: "app", x: 50, y: 190, w: 240, h: 100 },

    /* Side by side inside the same frame, deliberately the same colour and size: the point is
       that the second write is nothing special — just another row, in the same database, in
       the same transaction. */
    { key: "dbBusiness", kind: "data", x: 370, y: 160, w: 250, h: 85 },
    { key: "dbOutbox", kind: "data", x: 370, y: 260, w: 250, h: 85 },

    { key: "relay", kind: "app", x: 790, y: 210, w: 260, h: 100 },

    { key: "topic", kind: "data", x: 790, y: 490, w: 260, h: 100 },
    { key: "consumers", kind: "app", x: 420, y: 490, w: 260, h: 100 },
    { key: "targets", kind: "external", x: 50, y: 490, w: 260, h: 100 },
  ],
  edges: [
    { key: "userUsecase", from: "user", to: "usecase", x1: 170, y1: 96, x2: 170, y2: 190 },

    { key: "usecaseBusiness", from: "usecase", to: "dbBusiness", x1: 290, y1: 220, x2: 370, y2: 200 },
    { key: "usecaseOutbox", from: "usecase", to: "dbOutbox", x1: 290, y1: 260, x2: 370, y2: 295 },

    { key: "outboxRelay", from: "dbOutbox", to: "relay", x1: 620, y1: 300, x2: 790, y2: 260, labelX: 705, labelBottom: 220 },
    { key: "relayTopic", from: "relay", to: "topic", x1: 920, y1: 310, x2: 920, y2: 490, labelX: 920, labelBottom: 420 },

    { key: "topicConsumers", from: "topic", to: "consumers", x1: 790, y1: 540, x2: 680, y2: 540 },
    { key: "consumersTargets", from: "consumers", to: "targets", x1: 420, y1: 540, x2: 310, y2: 540 },
  ],
  legend: ["actor", "app", "data", "external"],
};

/* --------------------------------------------------------------- publish / subscribe, alone */

/* Deliberately abstract: no room, no controller, no product noun anywhere on it. The point of
   the section it belongs to is that the sender addresses a channel and never a person, and any
   concrete label would invite the reader to work out who is talking to whom instead — which is
   the exact habit the model breaks. */
export const PUBSUB_DIAGRAM: DiagramSpec = {
  id: "pubsub",
  width: 1000,
  height: 470,
  boundaries: [{ key: "broker", x: 350, y: 40, w: 300, h: 380 }],
  nodes: [
    { key: "sender", kind: "app", x: 20, y: 170, w: 200, h: 100 },

    { key: "topic", kind: "data", x: 380, y: 80, w: 240, h: 90 },
    { key: "queue", kind: "data", x: 380, y: 290, w: 240, h: 90 },

    { key: "listenerA", kind: "actor", x: 760, y: 20, w: 200, h: 76 },
    { key: "listenerB", kind: "actor", x: 760, y: 112, w: 200, h: 76 },
    { key: "listenerC", kind: "actor", x: 760, y: 204, w: 200, h: 76 },
    { key: "listenerD", kind: "actor", x: 760, y: 300, w: 200, h: 76 },
  ],
  edges: [
    { key: "senderTopic", from: "sender", to: "topic", x1: 220, y1: 195, x2: 380, y2: 140, labelX: 300, labelBottom: 152 },
    { key: "senderQueue", from: "sender", to: "queue", x1: 220, y1: 245, x2: 380, y2: 320, labelX: 300, labelBottom: 320 },

    /* Three unlabelled arrows off one box is the whole argument: the sender drew one of them,
       and the other two cost it nothing. */
    { key: "topicA", from: "topic", to: "listenerA", x1: 620, y1: 110, x2: 760, y2: 58 },
    { key: "topicB", from: "topic", to: "listenerB", x1: 620, y1: 125, x2: 760, y2: 150 },
    { key: "topicC", from: "topic", to: "listenerC", x1: 620, y1: 140, x2: 760, y2: 242 },

    { key: "queueD", from: "queue", to: "listenerD", x1: 620, y1: 335, x2: 760, y2: 338 },
  ],
  legend: ["app", "data", "actor"],
};

/* ------------------------------------------------------- the STOMP pipeline, inbound to out */

/* Not a C4 level — this one is drawn for the realtime section alone, and its subject is a
   path rather than a structure: one frame's journey from the browser, through the three
   inbound gates, into a use case, and back out through the broker to two kinds of
   destination. Read it clockwise.

   Two things are deliberately absent. The `ERROR` frame path is not drawn: it belongs to the
   trap callout below the diagram, where there is room to say why a refused frame takes the
   whole socket with it, and a tenth box plus a crossing edge would cost more than it explains.
   Nor are the `/app` destination patterns on the arrows — they sit in the controller box's
   own subtitle, which is where this diagram style already puts detail.

   The two return lines run down the empty left column on purpose: it is the only band wide
   enough to carry both a line and its label without either crossing a box. */
export const REALTIME_DIAGRAM: DiagramSpec = {
  id: "realtime",
  width: 1100,
  height: 830,
  boundaries: [
    { key: "inbound", x: 330, y: 30, w: 300, h: 440 },
    { key: "app", x: 690, y: 30, w: 390, h: 440 },
    { key: "broker", x: 350, y: 545, w: 730, h: 240 },
  ],
  nodes: [
    { key: "browser", kind: "actor", x: 20, y: 70, w: 240, h: 88 },

    /* The three gates keep the order they are registered in, top to bottom. That order is
       load-bearing — authentication first so the other two have a principal, throttling
       before authorisation so a flood is stopped before it can cost 500 membership
       queries — so the drawing must not reshuffle them for looks. */
    { key: "auth", kind: "app", x: 350, y: 90, w: 260, h: 88 },
    { key: "rate", kind: "app", x: 350, y: 210, w: 260, h: 88 },
    { key: "scope", kind: "app", x: 350, y: 330, w: 260, h: 88 },

    { key: "controller", kind: "app", x: 710, y: 90, w: 260, h: 88 },
    { key: "usecase", kind: "app", x: 710, y: 210, w: 260, h: 88 },
    { key: "publisher", kind: "app", x: 710, y: 330, w: 260, h: 88 },

    { key: "broker", kind: "data", x: 790, y: 630, w: 260, h: 88 },
    { key: "topics", kind: "data", x: 390, y: 590, w: 280, h: 80 },
    { key: "queues", kind: "data", x: 390, y: 690, w: 280, h: 80 },
  ],
  edges: [
    /* Label parked above both boxes rather than in the 90px gap between them, which cannot
       hold two lines of text in either language. */
    { key: "browserAuth", from: "browser", to: "auth", x1: 260, y1: 114, x2: 350, y2: 134, labelX: 305, labelBottom: 62 },

    { key: "authRate", from: "auth", to: "rate", x1: 480, y1: 178, x2: 480, y2: 210 },
    { key: "rateScope", from: "rate", to: "scope", x1: 480, y1: 298, x2: 480, y2: 330 },
    { key: "scopeController", from: "scope", to: "controller", x1: 610, y1: 374, x2: 710, y2: 134 },

    { key: "controllerUsecase", from: "controller", to: "usecase", x1: 840, y1: 178, x2: 840, y2: 210 },
    { key: "usecasePublisher", from: "usecase", to: "publisher", x1: 840, y1: 298, x2: 840, y2: 330 },

    /* The longest run on the canvas, and the only one that earns its length: the gap between
       the publisher and the broker is where the after-commit wait happens. */
    { key: "publisherBroker", from: "publisher", to: "broker", x1: 870, y1: 418, x2: 870, y2: 630, labelX: 870, labelBottom: 540 },

    { key: "brokerTopics", from: "broker", to: "topics", x1: 790, y1: 665, x2: 670, y2: 635 },
    { key: "brokerQueues", from: "broker", to: "queues", x1: 790, y1: 700, x2: 670, y2: 720 },

    /* Both labels are pinned to a height where their own line has already cleared the
       interceptor column — any lower and the wider of the two runs into the third gate. */
    { key: "topicsBrowser", from: "topics", to: "browser", x1: 390, y1: 630, x2: 230, y2: 158, labelX: 261, labelBottom: 250 },
    { key: "queuesBrowser", from: "queues", to: "browser", x1: 390, y1: 730, x2: 120, y2: 158, labelX: 201, labelBottom: 330 },
  ],
  legend: ["actor", "app", "data"],
};

/* ------------------------------------------------------------------------ CI/CD pipeline */

/* The mermaid source folds the test stage into one node. It is two jobs running in parallel —
   `backend` and `frontend` in ci.yml — and `build` waits on both, which is the part worth
   seeing: a red frontend lint stops the backend image shipping too. */
export const PIPELINE_DIAGRAM: DiagramSpec = {
  id: "pipeline",
  width: 1100,
  height: 580,
  boundaries: [
    { key: "actions", x: 0, y: 50, w: 320, h: 460 },
    { key: "vps", x: 810, y: 50, w: 290, h: 460 },
  ],
  nodes: [
    { key: "trigger", kind: "app", x: 20, y: 100, w: 280, h: 96 },
    { key: "testBackend", kind: "app", x: 20, y: 236, w: 134, h: 110 },
    { key: "testFrontend", kind: "app", x: 166, y: 236, w: 134, h: 110 },
    { key: "build", kind: "app", x: 20, y: 386, w: 280, h: 96 },

    { key: "registry", kind: "external", x: 450, y: 228, w: 230, h: 110 },

    { key: "pull", kind: "actor", x: 830, y: 100, w: 250, h: 96 },
    { key: "up", kind: "actor", x: 830, y: 236, w: 250, h: 96 },
    { key: "health", kind: "actor", x: 830, y: 372, w: 250, h: 96 },
  ],
  edges: [
    { key: "triggerBackend", from: "trigger", to: "testBackend", x1: 87, y1: 196, x2: 87, y2: 236 },
    { key: "triggerFrontend", from: "trigger", to: "testFrontend", x1: 233, y1: 196, x2: 233, y2: 236 },
    { key: "backendBuild", from: "testBackend", to: "build", x1: 87, y1: 346, x2: 120, y2: 386 },
    { key: "frontendBuild", from: "testFrontend", to: "build", x1: 233, y1: 346, x2: 200, y2: 386 },

    { key: "buildRegistry", from: "build", to: "registry", x1: 300, y1: 434, x2: 450, y2: 300, labelX: 375, labelBottom: 361 },
    { key: "registryPull", from: "registry", to: "pull", x1: 680, y1: 270, x2: 830, y2: 148, labelX: 750, labelBottom: 203 },

    { key: "pullUp", from: "pull", to: "up", x1: 955, y1: 196, x2: 955, y2: 236 },
    { key: "upHealth", from: "up", to: "health", x1: 955, y1: 332, x2: 955, y2: 372 },
  ],
  legend: ["app", "external", "actor"],
};

export const SECURITY_DIAGRAM: DiagramSpec = {
  id: "security",
  width: 1100,
  height: 1400,
  boundaries: [
    { key: "edge", x: 20, y: 130, w: 1060, h: 130 },
    { key: "frontend", x: 20, y: 320, w: 1060, h: 130 },
    { key: "chain", x: 20, y: 510, w: 1060, h: 130 },
    { key: "application", x: 20, y: 700, w: 1060, h: 130 },
    { key: "tokens", x: 20, y: 890, w: 1060, h: 130 },
    { key: "websocket", x: 20, y: 1080, w: 1060, h: 130 },
  ],
  nodes: [
    { key: "client", kind: "actor", x: 420, y: 16, w: 260, h: 76 },

    { key: "tls", kind: "app", x: 50, y: 170, w: 220, h: 70 },
    { key: "ipOverwrite", kind: "app", x: 310, y: 170, w: 260, h: 70 },
    { key: "actuatorBlock", kind: "app", x: 610, y: 170, w: 220, h: 70 },
    { key: "serverTokens", kind: "app", x: 870, y: 170, w: 200, h: 70 },

    { key: "csp", kind: "app", x: 50, y: 360, w: 220, h: 70 },
    { key: "hsts", kind: "app", x: 310, y: 360, w: 220, h: 70 },
    { key: "xFrame", kind: "app", x: 570, y: 360, w: 240, h: 70 },
    { key: "permissions", kind: "app", x: 850, y: 360, w: 220, h: 70 },

    { key: "correlationId", kind: "app", x: 50, y: 550, w: 170, h: 70 },
    { key: "cors", kind: "app", x: 240, y: 550, w: 140, h: 70 },
    { key: "jwtAuth", kind: "data", x: 400, y: 550, w: 200, h: 70 },
    { key: "httpRateLimit", kind: "actor", x: 620, y: 550, w: 220, h: 70 },
    { key: "authorization", kind: "app", x: 860, y: 550, w: 210, h: 70 },

    { key: "globalLimit", kind: "actor", x: 50, y: 740, w: 240, h: 70 },
    { key: "endpointRules", kind: "actor", x: 310, y: 740, w: 240, h: 70 },
    { key: "iamGuards", kind: "actor", x: 570, y: 740, w: 240, h: 70 },
    { key: "bruteForce", kind: "actor", x: 830, y: 740, w: 240, h: 70 },

    { key: "jwtAccess", kind: "data", x: 50, y: 930, w: 300, h: 70 },
    { key: "refreshToken", kind: "data", x: 390, y: 930, w: 300, h: 70 },
    { key: "reuseDetect", kind: "data", x: 730, y: 930, w: 340, h: 70 },

    { key: "stompAuth", kind: "app", x: 50, y: 1120, w: 300, h: 70 },
    { key: "subscriptionScope", kind: "app", x: 390, y: 1120, w: 300, h: 70 },
    { key: "wsRateLimit", kind: "actor", x: 730, y: 1120, w: 340, h: 70 },

    { key: "business", kind: "data", x: 420, y: 1260, w: 260, h: 60 },
  ],
  edges: [
    { key: "clientEdge", from: "client", to: "tls", x1: 550, y1: 92, x2: 550, y2: 130 },
    { key: "edgeFrontend", from: "tls", to: "csp", x1: 550, y1: 260, x2: 550, y2: 320 },
    { key: "frontendChain", from: "csp", to: "correlationId", x1: 550, y1: 450, x2: 550, y2: 510 },
    { key: "chainApp", from: "correlationId", to: "globalLimit", x1: 550, y1: 640, x2: 550, y2: 700 },
    { key: "appTokens", from: "globalLimit", to: "jwtAccess", x1: 550, y1: 830, x2: 550, y2: 890 },
    { key: "tokensWs", from: "jwtAccess", to: "stompAuth", x1: 550, y1: 1020, x2: 550, y2: 1080 },
    { key: "wsBusiness", from: "stompAuth", to: "business", x1: 550, y1: 1210, x2: 550, y2: 1260 },
  ],
  legend: ["actor", "app", "data"],
};
