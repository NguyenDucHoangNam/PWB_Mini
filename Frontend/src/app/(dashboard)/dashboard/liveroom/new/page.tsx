"use client";

import Link from "next/link";
import { useTranslations } from "next-intl";
import { ArrowLeft, Radio } from "lucide-react";
import { Spinner } from "@/components/ui/spinner";
import { PageHeader } from "@/components/layout/page-header";
import { NeuPanel, NeuScreen, neuButton } from "@/components/ui/neu";
import { CreateRoomForm } from "@/features/liveroom/components/room-list/create-room-form";
import { useLiveroomProRedirect } from "@/features/liveroom/hooks/use-liveroom-pro-redirect";

export default function NewLiveroomPage() {
  const { resolving } = useLiveroomProRedirect();
  const t = useTranslations("liveroom.create");
  const tActions = useTranslations("voice.actions");

  return (
    <NeuScreen className="font-sans">
      {resolving ? (
        <div className="flex min-h-[60vh] flex-1 items-center justify-center">
          <Spinner size="sm" />
        </div>
      ) : (
        <>
          <PageHeader
            title={t("title")}
            subtitle={t("subtitle")}
            icon={Radio}
            actions={
              <Link href="/dashboard/liveroom" className={neuButton()}>
                <ArrowLeft className="size-4" aria-hidden="true" />
                {tActions("back")}
              </Link>
            }
          />

          <div className="flex flex-1 flex-col items-center justify-center pb-6">
            <NeuPanel className="w-full max-w-lg p-6 sm:p-8">
              <CreateRoomForm />
            </NeuPanel>
          </div>
        </>
      )}
    </NeuScreen>
  );
}
