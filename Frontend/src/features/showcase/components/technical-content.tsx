import { TechnicalHero } from "./technical-hero";
import { TechnicalInfra } from "./technical-infra";
import { TechnicalModules } from "./technical-modules";
import { TechnicalNav } from "./technical-nav";
import { TechnicalOutro } from "./technical-outro";
import { TechnicalOverview } from "./technical-overview";
import { TechnicalRealtime } from "./technical-realtime";

export function TechnicalContent() {
  return (
    /* No overflow clipping anywhere on this column — the nav below relies on position:
       sticky, which a clipping ancestor would silently break. */
    <div className="flex w-full flex-col gap-12 border-none bg-[#e0e5ec] p-4 font-sans transition-colors dark:bg-[#1e222b] sm:p-6 md:p-8">
      <TechnicalHero />
      <TechnicalNav />
      <TechnicalOverview />
      <TechnicalModules />
      <TechnicalRealtime />
      <TechnicalInfra />
      <TechnicalOutro />
    </div>
  );
}
