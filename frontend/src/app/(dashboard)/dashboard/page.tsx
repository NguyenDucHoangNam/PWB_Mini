"use client";

import Link from "next/link";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import {
  CreateVoiceTagModal,
  StatCard,
  UploadDemoModal,
  useDemos,
  useDistributions,
  useVoiceTags,
} from "@/features/audio";
import type { DemoListItem } from "@/features/audio/types";
import type { VoiceTagResponse } from "@/features/audio/types";
import { useState } from "react";

function useDemosSummary() {
  const { data, isLoading } = useDemos({ page: 0, size: 20 });
  if (!data?.success || !data.data) {
    return { items: [] as DemoListItem[], isLoading, totalElements: 0 };
  }
  return {
    items: data.data.content,
    totalElements: data.data.totalElements,
    isLoading,
  };
}

function VoiceTagTile({ tag }: { tag: VoiceTagResponse }) {
  return (
    <Link
      href={`/voice-tags/${tag.id}`}
      className="flex flex-col gap-1 rounded-lg border border-neutral-200 p-3 transition-colors hover:bg-neutral-50 dark:border-neutral-800 dark:hover:bg-neutral-900/40"
    >
      <span className="line-clamp-1 text-sm font-medium text-black dark:text-white">
        {tag.textContent}
      </span>
      <span className="text-xs text-neutral-500 dark:text-neutral-400">
        {tag.languageCode} - {tag.voiceName}
      </span>
    </Link>
  );
}

export default function DashboardPage() {
  const t = useTranslations("dashboard");
  const tCommon = useTranslations("dashboard.common");
  const [uploadOpen, setUploadOpen] = useState(false);
  const [createTagOpen, setCreateTagOpen] = useState(false);

  const { items: demos, totalElements: demosTotal, isLoading: demosLoading } =
    useDemosSummary();

  const { data: tagsRes, isLoading: tagsLoading } = useVoiceTags();
  const tags: VoiceTagResponse[] = tagsRes?.success ? tagsRes.data ?? [] : [];
  const defaultTags = tags.filter((tag) => tag.isDefault);

  return (
    <div className="flex flex-col gap-8 font-sans">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div className="flex flex-col gap-2">
          <h1 className="text-2xl font-bold tracking-tight text-black dark:text-white">
            {t("title")}
          </h1>
          <p className="text-sm text-neutral-500 dark:text-neutral-400">{t("subtitle")}</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button onClick={() => setUploadOpen(true)}>{t("uploadDemo")}</Button>
          <Button variant="outline" onClick={() => setCreateTagOpen(true)}>
            {t("createTag")}
          </Button>
        </div>
      </div>

      <div className="grid grid-cols-1 gap-4 sm:gap-6 lg:grid-cols-3">
        {/* Demos widget */}
        <StatCard
          title={t("demosCardTitle")}
          description={t("demosCardDesc")}
          primary={{
            value: demosLoading ? "..." : demosTotal,
            label: t("demosTotalLabel"),
          }}
          secondary={[
            {
              value: demosLoading
                ? "..."
                : demos.filter((d) => d.status === "ACTIVE").length,
              label: t("demosActiveCount"),
            },
            {
              value: demosLoading
                ? "..."
                : demos.filter((d) => d.status === "PROCESSING").length,
              label: t("demosProcessingCount"),
            },
          ]}
          ctaLabel={t("openLibrary")}
          ctaHref="/demos"
          footer={
            <Button size="sm" onClick={() => setUploadOpen(true)}>
              {t("uploadDemo")}
            </Button>
          }
        />

        {/* Voice Tags widget */}
        <StatCard
          title={t("voiceTagsCardTitle")}
          description={t("voiceTagsCardDesc")}
          primary={{
            value: tagsLoading ? "..." : tags.length,
            label: t("voiceTagsTotalCount"),
          }}
          secondary={[
            {
              value: tagsLoading ? "..." : defaultTags.length,
              label: t("voiceTagsDefaultCount"),
            },
          ]}
          ctaLabel={t("viewAll")}
          ctaHref="/voice-tags"
          footer={
            <Button size="sm" variant="outline" onClick={() => setCreateTagOpen(true)}>
              {t("createTag")}
            </Button>
          }
        >
          {!tagsLoading && tags.length > 0 && (
            <div className="flex flex-col gap-2">
              {tags.slice(0, 3).map((tag) => (
                <VoiceTagTile key={tag.id} tag={tag} />
              ))}
            </div>
          )}
          {!tagsLoading && tags.length === 0 && (
            <p className="text-xs text-neutral-500 dark:text-neutral-400">
              {t("emptyVoiceTags")}
            </p>
          )}
        </StatCard>

        {/* Distributions widget */}
        <StatCard
          title={t("distributionsCardTitle")}
          description={t("distributionsCardDesc")}
          primary={{
            value: demosLoading ? "..." : demosTotal,
            label: t("distributionsTotalLabel"),
          }}
          secondary={[]}
          ctaLabel={t("viewAll")}
          ctaHref="/distributions"
          footer={
            <Link href="/demos">
              <Button size="sm" variant="outline">
                {t("shareDemo")}
              </Button>
            </Link>
          }
        >
          {demosLoading ? (
            <div className="flex items-center justify-center py-4 text-xs text-neutral-500">
              <Spinner size="sm" />
            </div>
          ) : demos.filter((d) => d.status === "ACTIVE").length === 0 ? (
            <p className="text-xs text-neutral-500 dark:text-neutral-400">
              {t("emptyDistributions")}
            </p>
          ) : (
            <div className="flex flex-col gap-1">
              <span className="text-xs font-medium text-neutral-500 dark:text-neutral-400">
                Active shares per demo:
              </span>
              {demos
                .filter((d) => d.status === "ACTIVE")
                .slice(0, 3)
                .map((demo) => (
                  <DistributionSummaryRow key={demo.demoId} demo={demo} />
                ))}
            </div>
          )}
        </StatCard>
      </div>

      <div className="rounded-xl border border-dashed border-neutral-300 bg-neutral-50 p-4 text-sm text-neutral-600 dark:border-neutral-700 dark:bg-neutral-900/40 dark:text-neutral-300">
        <p className="font-semibold text-black dark:text-white">{t("hintsTitle")}</p>
        <ul className="mt-1 list-disc pl-5">
          <li>{t("hintsActive")}</li>
          <li>{t("hintsDefaultTag")}</li>
        </ul>
      </div>

      <UploadDemoModal open={uploadOpen} onOpenChange={setUploadOpen} />
      <CreateVoiceTagModal open={createTagOpen} onOpenChange={setCreateTagOpen} />

      {tCommon("loading") ? null : null}
    </div>
  );
}

function DistributionSummaryRow({ demo }: { demo: DemoListItem }) {
  const { data } = useDistributions({
    demoId: demo.demoId,
    page: 0,
    size: 1,
    includeRevoked: true,
  });
  const total = data?.success && data.data ? data.data.totalElements : 0;
  return (
    <Link
      href={`/distributions/${demo.demoId}`}
      className="flex items-center justify-between rounded-md border border-neutral-200 px-3 py-1.5 text-xs hover:bg-neutral-100 dark:border-neutral-800 dark:hover:bg-neutral-800/50"
    >
      <span className="truncate text-neutral-700 dark:text-neutral-300">{demo.title}</span>
      <span className="font-semibold tabular-nums text-black dark:text-white">{total}</span>
    </Link>
  );
}
