"use client";

import {
  FALL_DELAY,
  KEY_FALL_STAGGER,
  getBlackKeyPositions,
} from "../lib/piano-positions";
import type { Side } from "../lib/piano-audio";
import { BlackKey } from "./black-key";
import { PianoKey } from "./piano-key";

const KEY_BOX_SIZE = "clamp(2.2rem, 5vw, 3.5rem)";
const BLACK_KEY_HEIGHT = "clamp(3rem, 7vw, 4.8rem)";

type KeyboardProps = {
  chars: string[];
  side: Side;
};

export function Keyboard({ chars, side }: KeyboardProps) {
  const blackKeys = getBlackKeyPositions(chars.length);

  return (
    <div className="relative w-full">
      <WhiteKeyRow chars={chars} side={side} />
      <BlackKeyOverlay
        blackKeys={blackKeys}
        chars={chars}
        side={side}
      />
    </div>
  );
}

function WhiteKeyRow({ chars, side }: { chars: string[]; side: Side }) {
  return (
    <div
      className="grid w-full"
      style={{
        gridTemplateColumns: `repeat(${chars.length}, 1fr)`,
        columnGap: "0px",
      }}
    >
      {chars.map((char, index) => (
        <PianoKey
          key={`${side}-${index}`}
          char={char}
          index={index}
          side={side}
          animationDelay={getKeyFallDelay(side, index, chars.length)}
        />
      ))}
    </div>
  );
}

function getKeyFallDelay(side: Side, index: number, total: number): number {
  if (side === "left") {
    return FALL_DELAY + index * KEY_FALL_STAGGER;
  }
  return FALL_DELAY + (total - 1 - index) * KEY_FALL_STAGGER;
}

type BlackKeyOverlayProps = {
  blackKeys: ReturnType<typeof getBlackKeyPositions>;
  chars: string[];
  side: Side;
};

function BlackKeyOverlay({ blackKeys, chars, side }: BlackKeyOverlayProps) {
  return (
    <div
      className="pointer-events-none absolute"
      style={{
        top: `calc(${KEY_BOX_SIZE} + 0px)`,
        left: 0,
        right: 0,
        height: BLACK_KEY_HEIGHT,
      }}
    >
      {blackKeys.map((bk, idx) => (
        <BlackKey
          key={`${side}-black-${idx}`}
          side={side}
          animationDelay={bk.animationDelay}
          totalKeys={chars.length}
          centerPercent={bk.centerPercent}
        />
      ))}
    </div>
  );
}
