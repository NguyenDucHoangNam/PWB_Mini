export const FALL_DELAY = 1.0;

export const LEFT_WORD = "PRODUCER".split("");
export const RIGHT_WORD = "WORKBENCH".split("");

export const KEY_FALL_STAGGER = 0.08;
export const BLACK_KEY_FALL_OFFSET = 0.4;

export const BLACK_KEY_OCTAVE_INDICES = [0, 1, 3, 4, 5] as const;

export const WHITE_KEYS_PER_OCTAVE = 7;

export type BlackKeyPosition = {
  centerPercent: number;
  animationDelay: number;
  whiteIndex: number;
};

export function getBlackKeyPositions(totalKeys: number): BlackKeyPosition[] {
  const positions: BlackKeyPosition[] = [];

  for (let i = 0; i < totalKeys; i++) {
    const whiteIndexInOctave = i % WHITE_KEYS_PER_OCTAVE;
    const hasBlackKeyOnRight = BLACK_KEY_OCTAVE_INDICES.includes(
      whiteIndexInOctave as (typeof BLACK_KEY_OCTAVE_INDICES)[number],
    );
    const hasNextWhiteKey = i + 1 < totalKeys;

    if (!hasBlackKeyOnRight || !hasNextWhiteKey) continue;

    const centerPercent = ((i + 1) / totalKeys) * 100;

    positions.push({
      centerPercent,
      whiteIndex: i,
      animationDelay: FALL_DELAY + BLACK_KEY_FALL_OFFSET + i * KEY_FALL_STAGGER,
    });
  }

  return positions;
}
