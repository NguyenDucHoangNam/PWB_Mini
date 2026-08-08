"use client";

import Link from "next/link";
import { useTranslations } from "next-intl";
import { ArrowLeft } from "lucide-react";
import { Spinner } from "@/components/ui/spinner";
import { CreateRoomForm } from "@/features/liveroom/components/room-list/create-room-form";
import { useLiveroomProRedirect } from "@/features/liveroom/hooks/use-liveroom-pro-redirect";

export default function NewLiveroomPage() {
  const { resolving } = useLiveroomProRedirect();
  const t = useTranslations("liveroom.create");
  const tActions = useTranslations("voice.actions");

  if (resolving) {
    return (
      <div className="flex min-h-[60vh] items-center justify-center">
        <Spinner size="sm" />
      </div>
    );
  }

  return (
    <div className="flex flex-1 flex-col gap-6 font-sans">
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-start gap-3">
          <div className="hidden h-12 w-1 shrink-0 rounded-full bg-foreground/80 sm:block" aria-hidden="true" />
          <div className="flex flex-col gap-0.5">
            <h1 className="text-xl font-bold tracking-tight text-foreground sm:text-2xl">
              {t("title")}
            </h1>
            <p className="text-xs uppercase tracking-widest text-muted-foreground/70 sm:text-[11px]">
              {t("subtitle")}
            </p>
          </div>
        </div>
        <Link
          href="/dashboard/liveroom"
          className="key-press flex shrink-0 items-center gap-2 rounded-lg border border-border px-4 py-2 text-sm font-medium text-muted-foreground beat-16th transition-colors ease-hammer hover:bg-muted hover:text-foreground"
        >
          <ArrowLeft className="size-4" aria-hidden="true" />
          {tActions("back")}
        </Link>
      </div>

      <div className="flex flex-1 flex-col items-center justify-center pb-6">
        <div className="w-full max-w-lg rounded-xl border border-border bg-card p-6 shadow-xs sm:p-8">
          <CreateRoomForm />
        </div>
      </div>
    </div>
  );
}
