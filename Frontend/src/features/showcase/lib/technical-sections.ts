import {
  Cloud,
  Database,
  KeyRound,
  Layers,
  Music4,
  Radio,
  Rocket,
  Server,
  ShieldCheck,
  Siren,
  Workflow,
  type LucideIcon,
} from "lucide-react";

export interface TechnicalSection {
  /** Doubles as the anchor id and the message key under `features.technical.nav`. */
  id: string;
  icon: LucideIcon;
}

/* Anchors stay in English so a shared URL keeps working when the reader switches locale. */
export const TECHNICAL_SECTIONS: readonly TechnicalSection[] = [
  { id: "overview", icon: Layers },
  { id: "realtime", icon: Radio },
  { id: "infrastructure", icon: Server },
];

/* Frozen separately so the scroll-spy effect has a stable dependency — mapping
   TECHNICAL_SECTIONS inside the component would re-run the effect on every render. */
export const TECHNICAL_SECTION_IDS: readonly string[] = TECHNICAL_SECTIONS.map(
  (section) => section.id,
);

/** Stack tokens are product names — they stay out of the message bundles on purpose. */
export const MODULE_CARDS = [
  { key: "iam", icon: KeyRound, stack: ["Spring Security", "JWT", "Redis", "Google OAuth"] },
  { key: "audio", icon: Music4, stack: ["FFmpeg", "EBU R128", "Jaffree", "Kafka", "S3"] },
  { key: "liveroom", icon: Radio, stack: ["STOMP", "WebRTC", "coturn", "PostgreSQL"] },
] as const;

export const INFRA_CARDS = [
  { key: "outbox", icon: Workflow },
  { key: "redis", icon: Database },
  { key: "security", icon: ShieldCheck },
  { key: "deploy", icon: Rocket },
] as const;
