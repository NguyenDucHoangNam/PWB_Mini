"use client";

import type { LucideIcon } from "lucide-react";
import { ArrowRight, CornerDownRight } from "lucide-react";
import { useTranslations } from "next-intl";
import {
  Eyebrow,
  Reveal,
  Section,
  SectionLead,
  SectionTitle,
} from "@/components/marketing/section-primitives";
import { StepMediaFrame, type StepMedia } from "./walkthrough-media";
import { WalkthroughBranch, WalkthroughStep } from "./walkthrough-step";

/** One side of a step that forks: two ways to the same place, only one of which needs doing. */
export interface BranchSpec {
  key: string;
  slot: string;
  /** Set once the capture exists; until then the frame renders as a labelled placeholder. */
  media?: StepMedia | null;
  factKeys: readonly string[];
}

export interface StepSpec {
  key: string;
  /** Slot id printed on the frame. Omitted on a forking step, whose media lives on the branches. */
  slot?: string | null;
  media?: StepMedia | null;
  factKeys?: readonly string[];
  note?: boolean;
  branches?: readonly BranchSpec[];
}

export interface ModuleSpec {
  /** Scroll target, and what the hero index links to. */
  anchor: string;
  /** i18n namespace holding every string this module renders. */
  namespace: string;
  icon: LucideIcon;
  /** Alternated between modules so the reader can see where one ends and the next begins. */
  tone?: "base" | "raised";
  specKeys: readonly string[];
  steps: readonly StepSpec[];
  /**
   * The closing hand-off card. Omitted entirely on the last module, where the technical bridge that
   * follows is the hand-off; `anchor` is dropped while the next module exists in name only.
   */
  next?: { anchor?: string };
}

/**
 * Renders one module's walkthrough: a header, its spec strip, the numbered steps, and a card handing
 * the reader to the next module. Every module has the same shape, so they share this and differ only
 * in their spec and their strings.
 */
export function WalkthroughModule({ spec }: { spec: ModuleSpec }) {
  const t = useTranslations(spec.namespace);
  const Icon = spec.icon;

  const factsOf = (scope: string, keys: readonly string[] | undefined) =>
    (keys ?? []).map((factKey) => ({
      label: t(`${scope}.facts.${factKey}.label`),
      value: t(`${scope}.facts.${factKey}.value`),
    }));

  /** Position among the illustrated steps, which is what the left/right alternation follows. */
  const illustratedIndex = (position: number) =>
    spec.steps.slice(0, position).filter((step) => step.slot).length;

  return (
    <Section id={spec.anchor} tone={spec.tone} className="scroll-mt-16">
      <Reveal>
        <Eyebrow>
          <Icon className="size-3.5" aria-hidden="true" />
          {t("eyebrow")}
        </Eyebrow>
        <SectionTitle>{t("title")}</SectionTitle>
        <SectionLead>{t("lead")}</SectionLead>
      </Reveal>

      <Reveal>
        <dl className="mt-12 grid gap-px overflow-hidden rounded-2xl border border-border bg-border sm:grid-cols-2 lg:grid-cols-4">
          {spec.specKeys.map((key) => (
            <div key={key} className="bg-card px-5 py-4">
              <dt className="font-mono text-[0.68rem] uppercase tracking-[0.16em] text-muted-foreground">
                {t(`specs.${key}.label`)}
              </dt>
              <dd className="mt-2 text-base font-semibold tracking-tight text-foreground">
                {t(`specs.${key}.value`)}
              </dd>
            </div>
          ))}
        </dl>
      </Reveal>

      {/* Matches the step rhythm of the workflow rail on the landing page. */}
      <ol className="mt-16 flex flex-col gap-12 sm:mt-20 sm:gap-14">
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
              /* Counted over the illustrated steps only, so a fork in the middle does not break the
                 zig-zag by taking a turn of its own. */
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

      {/* Hands the reader to the next module rather than ending on the last step. */}
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
    </Section>
  );
}

interface NextModuleCardProps {
  eyebrow: string;
  title: string;
  body: string;
  /** Present only once the next module exists on the page; otherwise the card is inert. */
  anchor?: string;
}

function NextModuleCard({ eyebrow, title, body, anchor }: NextModuleCardProps) {
  const content = (
    <>
      <span className="inline-flex items-center gap-2.5 font-mono text-[0.68rem] uppercase tracking-[0.2em] text-muted-foreground">
        <CornerDownRight className="size-3.5" aria-hidden="true" />
        {eyebrow}
      </span>
      <h3 className="mt-4 flex items-center gap-3 font-heading text-xl font-semibold tracking-tight text-foreground sm:text-2xl">
        {title}
        {anchor && (
          <ArrowRight
            aria-hidden="true"
            className="size-5 shrink-0 text-muted-foreground beat-16th transition-transform group-hover:translate-x-1 group-hover:text-foreground"
          />
        )}
      </h3>
      <p className="mt-3 max-w-2xl text-pretty text-sm leading-relaxed text-muted-foreground sm:text-base">
        {body}
      </p>
    </>
  );

  const className = "mt-16 block rounded-2xl border border-dashed border-border bg-card p-6 sm:mt-20 sm:p-8";

  return anchor ? (
    <a
      href={`#${anchor}`}
      className={`group ${className} beat-16th transition-colors hover:border-foreground/30`}
    >
      {content}
    </a>
  ) : (
    <div className={className}>{content}</div>
  );
}
