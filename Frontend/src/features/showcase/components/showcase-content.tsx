import { ShowcaseBridge } from "./showcase-bridge";
import { ShowcaseOutro } from "./showcase-outro";
import { WalkthroughHero } from "./walkthrough-hero";
import { WalkthroughLiveRoom } from "./walkthrough-live-room";
import { WalkthroughSong } from "./walkthrough-song";
import { WalkthroughVoiceTag } from "./walkthrough-voice-tag";

/** One walkthrough per module, in the order a producer meets them. */
export function ShowcaseContent() {
  return (
    <div className="flex w-full flex-col font-sans">
      <WalkthroughHero />
      <WalkthroughVoiceTag />
      <WalkthroughSong />
      <WalkthroughLiveRoom />
      <ShowcaseBridge />
      <ShowcaseOutro />
    </div>
  );
}
