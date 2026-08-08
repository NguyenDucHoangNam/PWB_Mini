import { ShowcaseBridge } from "./showcase-bridge";
import { ShowcaseGuide } from "./showcase-guide";
import { ShowcaseHero } from "./showcase-hero";
import { ShowcaseOutro } from "./showcase-outro";

export function ShowcaseContent() {
  return (
    <div className="flex w-full flex-col font-sans">
      <ShowcaseHero />
      <ShowcaseGuide />
      <ShowcaseBridge />
      <ShowcaseOutro />
    </div>
  );
}
