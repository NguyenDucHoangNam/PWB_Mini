"use client";

import { useParams, useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { ProUpgradePrompt } from "@/features/liveroom/components/pro-upgrade-prompt";
import { RoomStatusBadge } from "@/features/liveroom/components/room-status-badge";
import { RoomModeBadge } from "@/features/liveroom/components/room-mode-badge";
import { UpdateRoomForm } from "@/features/liveroom/components/update-room-form";
import { ParticipantsList } from "@/features/liveroom/components/participants-list";
import { useProGuard } from "@/features/auth/hooks/use-pro-guard";
import { useEndRoom, useRoom } from "@/features/liveroom/api/rooms";
import { useAuthStore } from "@/features/auth/stores/use-auth-store";
import { asApiError } from "@/lib/api-client";
import { resolveLiveroomErrorMessage } from "@/features/liveroom/lib/resolve-liveroom-error-message";

export default function LiveRoomDetailPage() {
  const params = useParams();
  const router = useRouter();
  const roomCode = (params?.roomCode as string) ?? "";
  const { isPro } = useProGuard();

  const tActions = useTranslations("liveroom.actions");
  const tCommon = useTranslations("common");
  const tErrors = useTranslations("liveroom.errors");
  const tCard = useTranslations("liveroom.card");
  const tForm = useTranslations("liveroom.form");

  const currentUserId = useAuthStore((state) => state.user?.userId ?? null);

  const { data: roomRes, isLoading, isError } = useRoom({ roomCode });

  const showError = (err: unknown) => {
    toast.error(
      resolveLiveroomErrorMessage(
        err,
        (k) => tErrors(k as never),
        (k) => tCommon(k as never),
      ),
    );
  };

  const { mutate: endRoomMutate } = useEndRoom({
    mutationConfig: {
      onError: asApiError((err) => showError(err)),
    },
  });

  if (!isPro) {
    return <ProUpgradePrompt />;
  }

  if (isLoading) {
    return (
      <div className="flex items-center justify-center gap-3 p-12 text-sm text-neutral-500">
        <Spinner size="md" />
      </div>
    );
  }

  if (isError || !roomRes?.data) {
    return (
      <div className="flex flex-col items-center gap-3 p-12 text-center">
        <p className="text-sm text-red-600 dark:text-red-400">{tErrors("roomNotFound")}</p>
        <Button variant="outline" onClick={() => router.push("/dashboard/live-rooms")}>
          {tActions("back")}
        </Button>
      </div>
    );
  }

  const room = roomRes.data;
  const isHost = currentUserId === room.hostUserId;

  if (!isHost) {
    return (
      <div className="flex flex-col items-center gap-3 p-12 text-center">
        <p className="text-sm text-red-600 dark:text-red-400">{tErrors("notHost")}</p>
        <Button variant="outline" onClick={() => router.push("/dashboard/live-rooms")}>
          {tActions("back")}
        </Button>
      </div>
    );
  }

  const isTerminal = room.status === "ENDED";

  const handleEnd = () => {
    endRoomMutate({ roomCode });
  };

  return (
    <div className="flex flex-col gap-6 font-sans">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
        <div className="flex flex-col gap-1">
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="text-2xl font-bold tracking-tight text-black dark:text-white">
              {room.title}
            </h1>
            <RoomStatusBadge status={room.status} />
            <RoomModeBadge mode={room.mode} />
          </div>
          <div className="flex flex-wrap items-center gap-2 text-xs text-neutral-500 dark:text-neutral-400">
            <span className="font-mono">{room.roomCode}</span>
            <span>
              {tCard("capacity", {
                current: room.currentParticipantCount,
                max: room.maxParticipants,
              })}
            </span>
          </div>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" onClick={() => router.push("/dashboard/live-rooms")}>
            {tActions("back")}
          </Button>
          {!isTerminal && (
            <Button variant="destructive" onClick={handleEnd}>
              {tCard("end")}
            </Button>
          )}
        </div>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <div className="rounded-xl border border-neutral-200 bg-white p-5 dark:border-neutral-800 dark:bg-black">
          <h2 className="mb-4 text-sm font-semibold text-black dark:text-white">
            {tForm("titleLabel")}
          </h2>
          <UpdateRoomForm room={room} />
        </div>
        <div className="rounded-xl border border-neutral-200 bg-white p-5 dark:border-neutral-800 dark:bg-black">
          <ParticipantsList roomCode={room.roomCode} hostUserId={room.hostUserId} />
        </div>
      </div>
    </div>
  );
}
