import {
  Cloud,
  Database,
  KeyRound,
  Layers,
  Music4,
  Radio,
  Server,
  Siren,
  Workflow,
  type LucideIcon,
} from "lucide-react";

export interface TechnicalSection {
  id: string;
  icon: LucideIcon;
}

export const TECHNICAL_SECTIONS: readonly TechnicalSection[] = [
  { id: "overview", icon: Layers },
  { id: "realtime", icon: Radio },
  { id: "infrastructure", icon: Server },
];

export const TECHNICAL_SECTION_IDS: readonly string[] = TECHNICAL_SECTIONS.map(
  (section) => section.id,
);

export const MODULE_CARDS = [
  { key: "iam", icon: KeyRound, stack: ["Spring Security", "JWT", "Redis", "Google OAuth"] },
  { key: "audio", icon: Music4, stack: ["FFmpeg", "EBU R128", "Jaffree", "Kafka", "S3"] },
  { key: "liveroom", icon: Radio, stack: ["STOMP", "WebRTC", "coturn", "PostgreSQL"] },
] as const;

export const INFRA_CARDS = [
  { key: "outbox", icon: Workflow },
  { key: "redis", icon: Database },
] as const;

