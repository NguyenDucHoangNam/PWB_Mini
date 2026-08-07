import { ShowcaseEngineering } from "./showcase-engineering";
import { TechnicalHero } from "./technical-hero";
import { TechnicalOutro } from "./technical-outro";

export function TechnicalContent() {
  return (
    <div className="flex w-full flex-col font-sans">
      <TechnicalHero />
      <ShowcaseEngineering />
      <TechnicalOutro />
    </div>
  );
}
