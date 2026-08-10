"use client";

import { Radio } from "lucide-react";
import { useTranslations } from "next-intl";
import { Reveal } from "@/components/marketing/section-primitives";
import { TechnicalPlaceholder, TechnicalSectionShell } from "./technical-shell";

const STACK = ["WebSocket", "STOMP", "SockJS", "Spring Messaging"] as const;

export function TechnicalRealtime() {
  const t = useTranslations("features.technical");

  return (
    <TechnicalSectionShell
      id="realtime"
      index={3}
      icon={Radio}
      eyebrow={t("sections.realtime.eyebrow")}
      title={t("sections.realtime.title")}
    >
      <Reveal className="mt-10">
        <ul className="flex flex-wrap gap-2">
          {STACK.map((tech) => (
            <li
              key={tech}
              className="neu-pressed-sm rounded-full bg-[#e0e5ec] px-3 py-1 font-mono text-[0.68rem] font-semibold text-slate-500 dark:bg-[#1e222b] dark:text-slate-400"
            >
              {tech}
            </li>
          ))}
        </ul>

        <TechnicalPlaceholder className="mt-7 min-h-[20rem]" />
      </Reveal>
    </TechnicalSectionShell>
  );
}
