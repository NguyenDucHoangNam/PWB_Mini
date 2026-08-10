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
