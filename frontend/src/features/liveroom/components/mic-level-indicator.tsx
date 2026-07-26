"use client";

interface MicLevelIndicatorProps {
  level: number;
  active: boolean;
  barCount?: number;
}

const BAR_HEIGHTS = [4, 8, 12, 16, 20];

function clamp01(value: number): number {
  if (value < 0) return 0;
  if (value > 1) return 1;
  return value;
}

function getBarIntensity(barIndex: number, totalBars: number, normalizedLevel: number): number {
  const threshold = barIndex / totalBars;
  if (normalizedLevel < threshold) return 0;
  const headroom = (normalizedLevel - threshold) / Math.max(1 / totalBars, 0.01);
  return Math.min(1, headroom);
}

export function MicLevelIndicator({
  level,
  active,
  barCount = BAR_HEIGHTS.length,
}: MicLevelIndicatorProps) {
  const normalized = clamp01(level);
  const idleHeights = BAR_HEIGHTS;

  return (
    <div
      className="flex items-end gap-1"
      aria-hidden="true"
      data-testid="mic-level-indicator"
    >
      {Array.from({ length: barCount }).map((_, index) => {
        const intensity = active ? getBarIntensity(index, barCount, normalized) : 0;
        const height = active ? Math.max(3, idleHeights[index] * (0.25 + intensity * 0.75)) : 3;
        const tone =
          index < 2
            ? "bg-emerald-400"
            : index < 4
              ? "bg-emerald-400"
              : "bg-amber-400";

        return (
          <div
            key={index}
            className="flex flex-col justify-end"
            style={{ width: 3, height: 24 }}
          >
            <div
              className={`rounded-sm transition-[height,opacity,background-color] duration-75 ease-out ${
                active ? tone : "bg-neutral-600"
              }`}
              style={{
                height: `${height}px`,
                opacity: active ? 0.35 + intensity * 0.65 : 0.5,
              }}
            />
          </div>
        );
      })}
    </div>
  );
}
