import { ShowcaseBridge } from "./showcase-bridge";
import { ShowcaseOutro } from "./showcase-outro";
import { WalkthroughHero } from "./walkthrough-hero";
import { WalkthroughLiveRoom } from "./walkthrough-live-room";
import { WalkthroughSong } from "./walkthrough-song";
import { WalkthroughVoiceTag } from "./walkthrough-voice-tag";

export function ShowcaseContent() {
  return (
    <div className="flex w-full flex-col font-sans bg-[#e0e5ec] dark:bg-[#1e222b] gap-12 p-4 sm:p-6 md:p-8 rounded-3xl border-none transition-colors">
      <WalkthroughHero />
      <WalkthroughVoiceTag />
      <WalkthroughSong />
      <WalkthroughLiveRoom />
      <ShowcaseBridge />
      <ShowcaseOutro />
    </div>
  );
}
