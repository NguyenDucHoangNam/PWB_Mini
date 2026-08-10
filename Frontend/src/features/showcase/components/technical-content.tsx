"use client";

import { useCallback, useEffect, useRef, useState, type ComponentType } from "react";
import { useReducedMotion } from "framer-motion";
import { TECHNICAL_SECTION_IDS } from "@/features/showcase/lib/technical-sections";
import { TechnicalHero } from "./technical-hero";
import { TechnicalInfra } from "./technical-infra";
import { TechnicalNav } from "./technical-nav";
import { TechnicalOutro } from "./technical-outro";
import { TechnicalOverview } from "./technical-overview";
import { TechnicalPager } from "./technical-pager";
import { TechnicalRealtime } from "./technical-realtime";

const PANELS: Record<string, ComponentType> = {
  overview: TechnicalOverview,
  realtime: TechnicalRealtime,
  infrastructure: TechnicalInfra,
};

const LAST_SECTION_ID = TECHNICAL_SECTION_IDS[TECHNICAL_SECTION_IDS.length - 1];

/* Each section runs long enough that stacking all four turned the page into one endless
   scroll where the reader lost track of which one they were in. Only the selected panel is
   mounted, so scrolling stays inside a single topic and moving on is a deliberate click. */
export function TechnicalContent() {
  const prefersReducedMotion = useReducedMotion();
  const [active, setActive] = useState(TECHNICAL_SECTION_IDS[0]);
  const topRef = useRef<HTMLDivElement>(null);

  /* Only a switch the reader asked for scrolls the page. Landing on a shared #hash must
     not, or the page opens already scrolled past its own heading. */
  const pendingScroll = useRef(false);

  /* The hash is read after mount, not during render: the server has no way to know it, and
     branching on it during the first render would break hydration. */
  useEffect(() => {
    const readHash = () => {
      const id = window.location.hash.slice(1);
      if (id && TECHNICAL_SECTION_IDS.includes(id)) setActive(id);
    };

    readHash();
    window.addEventListener("hashchange", readHash);
    return () => window.removeEventListener("hashchange", readHash);
  }, []);

  const select = useCallback((id: string) => {
    setActive((current) => {
      if (current !== id) pendingScroll.current = true;
      return id;
    });
    /* replaceState, not push: the tabs are one page, and stacking them into history would
       make the back button walk the reader backwards through panels instead of leaving. */
    window.history.replaceState(null, "", `#${id}`);
  }, []);

  useEffect(() => {
    if (!pendingScroll.current) return;
    pendingScroll.current = false;

    topRef.current?.scrollIntoView({
      behavior: prefersReducedMotion ? "auto" : "smooth",
      block: "start",
    });
  }, [active, prefersReducedMotion]);

  const ActivePanel = PANELS[active] ?? PANELS[TECHNICAL_SECTION_IDS[0]];

  return (
    /* No overflow clipping anywhere on this column — the nav below relies on position:
       sticky, which a clipping ancestor would silently break. */
    <div className="flex w-full flex-col gap-12 border-none bg-[#e0e5ec] p-4 font-sans transition-colors dark:bg-[#1e222b] sm:p-6 md:p-8">
      <TechnicalHero />

      {/* Zero-height scroll marker. The bar cannot be the target itself: it is sticky, so once
          pinned it reports its pinned position and scrolling to it does nothing. The negative
          margins cancel the extra column gap this empty row would otherwise add, and the
          scroll margin drops the bar at its own resting offset rather than under the header. */}
      <div ref={topRef} aria-hidden="true" className="-my-6 h-0 scroll-mt-[4.5rem]" />

      <TechnicalNav active={active} onSelect={select} />

      <ActivePanel />

      <TechnicalPager active={active} onSelect={select} />

      {active === LAST_SECTION_ID ? <TechnicalOutro /> : null}
    </div>
  );
}
