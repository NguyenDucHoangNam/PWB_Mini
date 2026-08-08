export function countCodePoints(value: string): number {
  return [...value].length;
}

export function truncateCodePoints(value: string, max: number): string {
  const points = [...value];
  return points.length <= max ? value : points.slice(0, max).join("");
}