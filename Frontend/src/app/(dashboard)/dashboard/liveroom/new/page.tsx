"use client";

import { useTranslations } from "next-intl";
import { Radio } from "lucide-react";
import { Spinner } from "@/components/ui/spinner";
import {
  NEU_ACCENT_TEXT,
  NEU_TEXT,
  NEU_TEXT_MUTED,
  NeuPanel,
  NeuScreen,
} from "@/components/ui/neu";
import { CreateRoomForm } from "@/features/liveroom/components/room-list/create-room-form";
import { useLiveroomProRedirect } from "@/features/liveroom/hooks/use-liveroom-pro-redirect";

export default function NewLiveroomPage() {
  const { resolving } = useLiveroomProRedirect();
  const t = useTranslations("liveroom.create");

  return (
    <NeuScreen className="font-sans">
      {resolving ? (
        <div className="flex min-h-[60vh] flex-1 items-center justify-center">
          <Spinner size="sm" />
        </div>
      ) : (
        <div className="flex flex-1 items-center justify-center">
          <NeuPanel className="flex w-full max-w-2xl flex-col gap-5 p-5 sm:p-6">
            <div className="flex items-center gap-3">
              <span
                className="neu-pressed grid size-11 shrink-0 place-items-center rounded-2xl border-none"
                aria-hidden="true"
              >
                <Radio className={`size-5 ${NEU_ACCENT_TEXT}`} />
              </span>
              <div className="flex min-w-0 flex-col gap-0.5">
                <h1 className={`truncate text-lg font-bold tracking-tight ${NEU_TEXT}`}>
                  {t("title")}
                </h1>
                <p className={`text-xs font-medium ${NEU_TEXT_MUTED}`}>{t("subtitle")}</p>
              </div>
            </div>

            <CreateRoomForm />
          </NeuPanel>
        </div>
      )}
    </NeuScreen>
  );
}
