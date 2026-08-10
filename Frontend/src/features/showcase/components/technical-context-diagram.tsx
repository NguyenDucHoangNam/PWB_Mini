"use client";

import { useTranslations } from "next-intl";
import {
  CONTEXT_BOUNDARIES,
  CONTEXT_CANVAS,
  CONTEXT_EDGES,
  CONTEXT_LEGEND,
  CONTEXT_NODES,
  CONTEXT_NODE_STYLE,
} from "@/features/showcase/lib/context-diagram";

/* The C4 level-1 context diagram. Boxes are HTML and connectors are SVG rather than one
   drawing in either technology: SVG text does not wrap, and this page runs in two languages
   whose labels differ in length, so a pure-SVG diagram would overflow its boxes the moment
   the reader switches locale. Absolute positioning on a fixed canvas keeps the HTML boxes
   exactly where the hand-computed connector endpoints expect them. */
export function TechnicalContextDiagram() {
  const t = useTranslations("features.technical.diagram");

  return (
    <figure className="neu-pressed rounded-3xl bg-[#e0e5ec] p-4 dark:bg-[#1e222b] sm:p-6">
      {/* A scrollable region needs its own tab stop, or a keyboard reader cannot pan the
          diagram on a narrow screen. */}
      <div
        role="group"
        tabIndex={0}
        aria-label={t("title")}
        className="neu-scroll-thin overflow-x-auto rounded-2xl focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600"
      >
        <div
          className="relative mx-auto"
          style={{ width: CONTEXT_CANVAS.width, height: CONTEXT_CANVAS.height }}
        >
          {/* currentColor rather than a fill per element: markers resolve currentColor against
              their own inherited colour, so setting it once here keeps every arrowhead the
              same shade as the line it caps, in both themes. */}
          <svg
            aria-hidden="true"
            viewBox={`0 0 ${CONTEXT_CANVAS.width} ${CONTEXT_CANVAS.height}`}
            className="absolute inset-0 h-full w-full text-slate-400 dark:text-slate-500"
          >
            <defs>
              <marker
                id="c4-arrow"
                viewBox="0 0 10 10"
                refX="8"
                refY="5"
                markerWidth="6"
                markerHeight="6"
                orient="auto-start-reverse"
              >
                <path
                  d="M2 1L8 5L2 9"
                  fill="none"
                  stroke="currentColor"
                  strokeWidth="1.5"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                />
              </marker>
            </defs>

            {CONTEXT_BOUNDARIES.map(({ key, x, y, w, h }) => (
              <rect
                key={key}
                x={x}
                y={y}
                width={w}
                height={h}
                rx={14}
                fill="none"
                stroke="currentColor"
                strokeWidth={1}
                strokeDasharray="5 5"
                opacity={0.55}
              />
            ))}

            {CONTEXT_EDGES.map(({ key, x1, y1, x2, y2 }) => (
              <line
                key={key}
                x1={x1}
                y1={y1}
                x2={x2}
                y2={y2}
                stroke="currentColor"
                strokeWidth={1.25}
                markerEnd="url(#c4-arrow)"
              />
            ))}
          </svg>

          {CONTEXT_BOUNDARIES.map(({ key, x, y }) => (
            <span
              key={key}
              className="absolute font-mono text-[0.62rem] font-bold uppercase tracking-[0.16em] text-slate-500 dark:text-slate-400"
              style={{ left: x + 14, top: y + 12 }}
            >
              {t(`boundaries.${key}`)}
            </span>
          ))}

          {CONTEXT_NODES.map(({ key, kind, x, y, w, h }) => {
            const palette = CONTEXT_NODE_STYLE[kind];

            return (
              <div
                key={key}
                className="absolute flex flex-col items-center justify-center rounded-lg border px-3 text-center"
                style={{
                  left: x,
                  top: y,
                  width: w,
                  height: h,
                  backgroundColor: palette.fill,
                  borderColor: palette.border,
                }}
              >
                <span className="text-[0.9rem] font-bold leading-tight text-white">
                  {t(`nodes.${key}.name`)}
                </span>
                <span className="mt-1 font-mono text-[0.6rem] uppercase tracking-[0.1em] text-white/65">
                  {t(`types.${kind}`)}
                </span>
                <span className="mt-1.5 text-[0.72rem] font-medium leading-snug text-white/85">
                  {t(`nodes.${key}.desc`)}
                </span>
              </div>
            );
          })}

          {/* The fan of connectors is dense enough that a label always sits across some other
              line. Painting the panel's own surface behind each one is what keeps them
              legible — the surrounding colour is fixed here, so the match is exact. */}
          {CONTEXT_EDGES.map(({ key, labelX, labelBottom }) => (
            <div
              key={key}
              className="absolute -translate-x-1/2 -translate-y-full bg-[#e0e5ec] px-1.5 text-center dark:bg-[#1e222b]"
              style={{ left: labelX, top: labelBottom, width: 180 }}
            >
              <span className="block text-[0.7rem] font-semibold leading-snug text-slate-600 dark:text-slate-300">
                {t(`edges.${key}.label`)}
              </span>
              <span className="block font-mono text-[0.62rem] font-bold leading-snug text-slate-400 dark:text-slate-500">
                {t(`edges.${key}.tech`)}
              </span>
            </div>
          ))}

          <div className="absolute flex items-center gap-6" style={{ left: 0, top: 512 }}>
            {CONTEXT_LEGEND.map((kind) => (
              <span key={kind} className="flex items-center gap-2">
                <span
                  aria-hidden="true"
                  className="size-3 shrink-0 rounded-sm border"
                  style={{
                    backgroundColor: CONTEXT_NODE_STYLE[kind].fill,
                    borderColor: CONTEXT_NODE_STYLE[kind].border,
                  }}
                />
                <span className="font-mono text-[0.62rem] font-bold uppercase tracking-[0.14em] text-slate-500 dark:text-slate-400">
                  {t(`legend.${kind}`)}
                </span>
              </span>
            ))}
          </div>
        </div>
      </div>

      {/* The drawing carries the relationships in its geometry, which a screen reader cannot
          follow. This spells each one out and is the only reason the SVG can stay aria-hidden. */}
      <figcaption className="sr-only">
        <p>{t("summary")}</p>
        <ul>
          {CONTEXT_EDGES.map(({ key, from, to }) => (
            <li key={key}>
              {t("relation", {
                from: t(`nodes.${from}.name`),
                to: t(`nodes.${to}.name`),
                label: t(`edges.${key}.label`),
                tech: t(`edges.${key}.tech`),
              })}
            </li>
          ))}
        </ul>
      </figcaption>
    </figure>
  );
}
