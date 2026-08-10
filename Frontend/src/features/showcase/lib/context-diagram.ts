/* Geometry for the C4 level-1 context diagram in the overview section, mirroring
   docs/diagram/c4_level1_system_context.md.

   Every coordinate is a fixed logical pixel on a 1100x550 canvas rather than a responsive
   rule. Connector endpoints have to agree with box edges to within a pixel, and no flex or
   grid arrangement can promise that once a translated label wraps to a different number of
   lines. The canvas keeps its size at every viewport and scrolls sideways when the panel is
   narrower — the standard treatment for a diagram, and the only one that keeps the labels
   readable on a phone. */

export const CONTEXT_CANVAS = { width: 1100, height: 550 } as const;

export type ContextNodeKind = "person" | "system" | "external";

/* The palette is the one in the mermaid source, kept identical on purpose: a reader who has
   seen the diagram in docs/ should recognise this as the same drawing. All three fills are
   dark enough to carry white text, so neither theme needs a variant. */
export const CONTEXT_NODE_STYLE: Record<ContextNodeKind, { fill: string; border: string }> = {
  person: { fill: "#963E2E", border: "#D96B52" },
  system: { fill: "#483D8B", border: "#7A70D6" },
  external: { fill: "#3D3D3D", border: "#707070" },
};

export interface ContextNode {
  /** Doubles as the react key and the message key under `features.technical.diagram.nodes`. */
  key: string;
  kind: ContextNodeKind;
  x: number;
  y: number;
  w: number;
  h: number;
}

export const CONTEXT_NODES: readonly ContextNode[] = [
  { key: "producer", kind: "person", x: 14, y: 112, w: 204, h: 92 },
  { key: "guest", kind: "person", x: 14, y: 222, w: 204, h: 92 },
  { key: "admin", kind: "person", x: 14, y: 332, w: 204, h: 92 },

  { key: "workbench", kind: "system", x: 420, y: 186, w: 260, h: 140 },

  { key: "storage", kind: "external", x: 882, y: 54, w: 204, h: 76 },
  { key: "oauth", kind: "external", x: 882, y: 142, w: 204, h: 76 },
  { key: "tts", kind: "external", x: 882, y: 230, w: 204, h: 76 },
  { key: "turn", kind: "external", x: 882, y: 318, w: 204, h: 76 },
  { key: "smtp", kind: "external", x: 882, y: 406, w: 204, h: 76 },
];

export interface ContextBoundary {
  key: string;
  x: number;
  y: number;
  w: number;
  h: number;
}

export const CONTEXT_BOUNDARIES: readonly ContextBoundary[] = [
  { key: "people", x: 0, y: 74, w: 232, h: 364 },
  { key: "external", x: 868, y: 16, w: 232, h: 480 },
];

export interface ContextEdge {
  /** React key and message key under `features.technical.diagram.edges`. */
  key: string;
  /** Node keys, used only to build the screen-reader description of the relationship. */
  from: string;
  to: string;
  x1: number;
  y1: number;
  x2: number;
  y2: number;
  /** Horizontal centre of the label. */
  labelX: number;
  /* Labels are anchored by their BOTTOM edge, sitting a few pixels above the line at labelX.
     A label that wraps to a third line then grows upward into empty space instead of
     downward onto its own connector. */
  labelBottom: number;
}

/* Connectors leave the node box, not the dashed boundary — a relationship in C4 belongs to
   the element, and crossing the boundary line is what shows it reaches outside. */
export const CONTEXT_EDGES: readonly ContextEdge[] = [
  { key: "producerSystem", from: "producer", to: "workbench", x1: 218, y1: 158, x2: 420, y2: 212, labelX: 326, labelBottom: 181 },
  { key: "guestSystem", from: "guest", to: "workbench", x1: 218, y1: 268, x2: 420, y2: 256, labelX: 326, labelBottom: 255 },
  { key: "adminSystem", from: "admin", to: "workbench", x1: 218, y1: 378, x2: 420, y2: 300, labelX: 326, labelBottom: 330 },

  { key: "systemStorage", from: "workbench", to: "storage", x1: 680, y1: 200, x2: 882, y2: 92, labelX: 774, labelBottom: 143 },
  { key: "systemOauth", from: "workbench", to: "oauth", x1: 680, y1: 230, x2: 882, y2: 180, labelX: 774, labelBottom: 200 },
  { key: "systemTts", from: "workbench", to: "tts", x1: 680, y1: 260, x2: 882, y2: 268, labelX: 774, labelBottom: 257 },
  { key: "systemTurn", from: "workbench", to: "turn", x1: 680, y1: 290, x2: 882, y2: 356, labelX: 774, labelBottom: 314 },
  { key: "systemSmtp", from: "workbench", to: "smtp", x1: 680, y1: 315, x2: 882, y2: 444, labelX: 774, labelBottom: 368 },
];

export const CONTEXT_LEGEND: readonly ContextNodeKind[] = ["person", "system", "external"];
