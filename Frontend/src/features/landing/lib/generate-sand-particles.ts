export interface SandParticle {
  size: number;
  duration: number;
  delay: number;
  left: string;
  sway: number;
}

export function generateSandParticles(count: number): SandParticle[] {
  return Array.from({ length: count }, () => ({
    size: Math.random() * 3 + 1,
    duration: Math.random() * 20 + 20,
    delay: Math.random() * -20,
    left: `${Math.random() * 100}%`,
    sway: Math.random() * 40 - 20,
  }));
}