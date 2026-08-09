"use client";

import type { LucideIcon } from "lucide-react";
import { ArrowRight, CornerDownRight } from "lucide-react";
import { useTranslations } from "next-intl";
import {
  Eyebrow,
  Reveal,
  SectionLead,
  SectionTitle,
} from "@/components/marketing/section-primitives";
import { StepMediaFrame, type StepMedia } from "./walkthrough-media";
import { WalkthroughBranch, WalkthroughStep } from "./walkthrough-step";

export interface BranchSpec {
  key: string;
  slot: string;
  media?: StepMedia | null;
  factKeys: readonly string[];
}

export interface StepSpec {
  key: string;
  slot?: string | null;
  media?: StepMedia | null;
  factKeys?: readonly string[];
  note?: boolean;
  branches?: readonly BranchSpec[];
}

export interface ModuleSpec {
  anchor: string;
  namespace: string;
  icon: LucideIcon;
  tone?: "base" | "raised";
  specKeys: readonly string[];
  steps: readonly StepSpec[];
  next?: { anchor?: string };
}

export function WalkthroughModule({ spec }: { spec: ModuleSpec }) {
  const t = useTranslations(spec.namespace);
  const Icon = spec.icon;

  const factsOf = (scope: string, keys: readonly string[] | undefined) =>
    (keys ?? []).map((factKey) => ({
      label: t(`${scope}.facts.${factKey}.label`),
      value: t(`${scope}.facts.${factKey}.value`),
    }));

  const illustratedIndex = (position: number) =>
    spec.steps.slice(0, position).filter((step) => step.slot).length;

  return (
    <section id={spec.anchor} className="neu-raised scroll-mt-20 rounded-3xl bg-[#e0e5ec] p-6 sm:p-10 dark:bg-[#1e222b] border-none">
      <Reveal>
        <Eyebrow>
          <Icon className="size-3.5" aria-hidden="true" />
          {t("eyebrow")}
        </Eyebrow>
        <SectionTitle>{t("title")}</SectionTitle>
        <SectionLead>{t("lead")}</SectionLead>
      </Reveal>

      <Reveal>
        <dl className="neu-pressed mt-10 grid gap-3 rounded-3xl bg-[#e0e5ec] p-3 sm:grid-cols-2 lg:grid-cols-4 dark:bg-[#1e222b] border-none">
          {spec.specKeys.map((key) => (
            <div key={key} className="neu-raised-sm rounded-2xl bg-[#e0e5ec] px-5 py-4 dark:bg-[#1e222b]">
              <dt className="font-mono text-xs font-bold uppercase tracking-[0.16em] text-slate-500 dark:text-slate-400">
                {t(`specs.${key}.label`)}
              </dt>
              <dd className="mt-1.5 text-base font-bold tracking-tight text-slate-900 dark:text-slate-100">
                {t(`specs.${key}.value`)}
              </dd>
            </div>
          ))}
        </dl>
      </Reveal>

      <ol className="mt-14 flex flex-col gap-12 sm:mt-16 sm:gap-14">
        {spec.steps.map((step, position) => {
          const scope = `steps.${step.key}`;

          return (
            <WalkthroughStep
              key={step.key}
              index={String(position + 1).padStart(2, "0")}
              title={t(`${scope}.title`)}
              body={t(`${scope}.body`)}
              facts={factsOf(scope, step.factKeys)}
              note={step.note ? t(`${scope}.note`) : undefined}
              reversed={illustratedIndex(position) % 2 === 1}
              media={
                step.slot && (
                  <StepMediaFrame
                    slot={step.slot}
                    hint={t(`${scope}.mediaHint`)}
                    media={step.media ?? null}
                  />
                )
              }
              last={position === spec.steps.length - 1}
            >
              {step.branches && (
                <div className="grid gap-5 lg:grid-cols-2 lg:gap-6">
                  {step.branches.map((branch) => (
                    <WalkthroughBranch
                      key={branch.key}
                      badge={t(`${scope}.${branch.key}.badge`)}
                      title={t(`${scope}.${branch.key}.title`)}
                      body={t(`${scope}.${branch.key}.body`)}
                      facts={factsOf(`${scope}.${branch.key}`, branch.factKeys)}
                    >
                      <StepMediaFrame
                        slot={branch.slot}
                        hint={t(`${scope}.${branch.key}.mediaHint`)}
                        media={branch.media ?? null}
                      />
                    </WalkthroughBranch>
                  ))}
                </div>
              )}
            </WalkthroughStep>
          );
        })}
      </ol>

      {spec.next && (
        <Reveal>
          <NextModuleCard
            eyebrow={t("next.eyebrow")}
            title={t("next.title")}
            body={t("next.body")}
            anchor={spec.next.anchor}
          />
        </Reveal>
      )}
    </section>
  );
}

interface NextModuleCardProps {
  eyebrow: string;
  title: string;
  body: string;
  anchor?: string;
}

function NextModuleCard({ eyebrow, title, body, anchor }: NextModuleCardProps) {
  const content = (
    <>
      <span className="inline-flex items-center gap-2.5 font-mono text-xs font-bold uppercase tracking-[0.2em] text-slate-500 dark:text-slate-400">
        <CornerDownRight className="size-4 text-indigo-600 dark:text-indigo-400" aria-hidden="true" />
        {eyebrow}
      </span>
      <h3 className="mt-3 flex items-center gap-3 font-heading text-xl font-bold tracking-tight text-slate-900 sm:text-2xl dark:text-slate-50">
        {title}
        {anchor && (
          <ArrowRight
            aria-hidden="true"
            className="size-5 shrink-0 text-indigo-600 transition-transform group-hover:translate-x-1 dark:text-indigo-400"
          />
        )}
      </h3>
      <p className="mt-2.5 max-w-2xl text-pretty text-sm font-medium leading-relaxed text-slate-600 sm:text-base dark:text-slate-300">
        {body}
      </p>
    </>
  );

  const className = "neu-raised group mt-14 block rounded-3xl bg-[#e0e5ec] p-6 sm:mt-16 sm:p-8 dark:bg-[#1e222b] border-none focus-visible:outline-2 focus-visible:outline-indigo-600 focus-visible:outline-offset-2 transition-all";

  return anchor ? (
    <a
      href={`#${anchor}`}
      className={className}
    >
      {content}
    </a>
  ) : (
    <div className={className}>{content}</div>
  );
}
