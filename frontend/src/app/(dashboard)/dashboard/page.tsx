"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import {
  CreateVoiceTagModal,
  StatCard,
  useVoiceTags,
} from "@/features/audio";
import type { VoiceTagResponse } from "@/features/audio/types";

export default function DashboardPage() {
  const tDashboard = useTranslations("dashboard");
  const tDashboardCommon = useTranslations("dashboard.common");

  const { data: tagsRes, isLoading } = useVoiceTags();
  const tags: VoiceTagResponse[] = tagsRes?.success ? tagsRes.data ?? [] : [];
  const total = tags.length;
  const defaultCount = tags.filter((tag) => tag.isDefault).length;

  const [createOpen, setCreateOpen] = useState(false);

  return (
    <div className="flex flex-col gap-6 font-sans">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
        <div className="flex flex-col gap-1">
          <h1 className="text-2xl font-bold tracking-tight text-black dark:text-white">
            {tDashboard("title")}
          </h1>
          <p className="text-sm text-neutral-500 dark:text-neutral-400">
            {tDashboard("subtitle")}
          </p>
        </div>
        <Button
          onClick={() => setCreateOpen(true)}
          className="self-start sm:self-auto"
        >
          {tDashboard("createTag")}
        </Button>
      </div>

      {isLoading ? (
        <div className="flex items-center justify-center gap-3 p-12 text-sm text-neutral-500">
          <Spinner size="md" />
          {tDashboardCommon("loading")}
        </div>
      ) : total === 0 ? (
        <div className="flex flex-col items-center gap-3 rounded-xl border border-dashed border-neutral-300 p-12 text-center dark:border-neutral-700">
          <h2 className="text-lg font-semibold text-black dark:text-white">
            {tDashboard("emptyVoiceTags")}
          </h2>
          <Button onClick={() => setCreateOpen(true)} className="mt-2">
            {tDashboard("createTag")}
          </Button>
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <StatCard
            title={tDashboard("voiceTagsCardTitle")}
            description={tDashboard("voiceTagsCardDesc")}
            primary={{
              value: total,
              label: tDashboard("voiceTagsTotalCount"),
            }}
            secondary={[
              {
                value: defaultCount,
                label: tDashboard("voiceTagsDefaultCount"),
              },
            ]}
            ctaLabel={tDashboard("viewAll")}
            ctaHref="/voice-tags"
          />
        </div>
      )}

      <CreateVoiceTagModal open={createOpen} onOpenChange={setCreateOpen} />
    </div>
  );
}
