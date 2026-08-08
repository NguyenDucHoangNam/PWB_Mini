"use client";

import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { asApiError } from "@/lib/api-client";
import { UserAvatar } from "../ui/user-avatar";
import { useApproveJoinRequest, useRejectJoinRequest } from "../../api/join-requests";
import { resolveLiveroomErrorMessage } from "../../lib/resolve-liveroom-error-message";
import { useLiveroomStore } from "../../stores/use-liveroom-store";
import { displayName } from "../../utils/participant-sort";

export function JoinRequestQueue({ roomId }: { roomId: string }) {
  const t = useTranslations("liveroom.room.requests");
  const tCommon = useTranslations("common");
  const tErrors = useTranslations("liveroom.errors");
  const requests = useLiveroomStore((state) => state.joinRequests);

  const { mutate: approve, isPending: approving } = useApproveJoinRequest({
    onCapacityRejected: (request) => {
      toast.warning(t("capacityToast", { name: displayName(request) }));
    },
    mutationConfig: {
      onSuccess: (response, variables) => {
        useLiveroomStore.getState().removeJoinRequest(variables.requestId);
        if (response.data?.state === "APPROVED") {
          toast.success(t("approvedToast", { name: displayName(response.data) }));
        }
      },
      onError: asApiError((err) => {
        toast.error(resolveLiveroomErrorMessage(err, tErrors, tCommon));
      }),
    },
  });

  const { mutate: reject, isPending: rejecting } = useRejectJoinRequest({
    mutationConfig: {
      onSuccess: (response, variables) => {
        useLiveroomStore.getState().removeJoinRequest(variables.requestId);
        if (response.data) {
          toast.success(t("rejectedToast", { name: displayName(response.data) }));
        }
      },
      onError: asApiError((err) => {
        toast.error(resolveLiveroomErrorMessage(err, tErrors, tCommon));
      }),
    },
  });

  const pending = Object.values(requests);

  return (
    <section className="flex flex-col gap-2 border-b border-neutral-200 px-3 py-3 dark:border-neutral-800">
      <h3 className="flex items-center gap-2 text-xs font-semibold tracking-wide text-neutral-500 uppercase dark:text-neutral-400">
        {t("title")}
        {pending.length > 0 ? (
          <span className="inline-flex min-w-5 items-center justify-center rounded-full bg-red-600 px-1.5 py-0.5 text-[10px] leading-none font-bold text-white tabular-nums">
            {pending.length}
          </span>
        ) : null}
      </h3>

      {pending.length === 0 ? (
        <p className="text-xs text-neutral-500 dark:text-neutral-400">{t("empty")}</p>
      ) : (
        <ul className="flex flex-col gap-2">
          {pending.map((request) => (
            <li key={request.id} className="flex items-center gap-2">
              <UserAvatar
                email={request.userEmail}
                avatarUrl={request.avatarUrl}
                seed={request.userId}
                className="size-8 text-xs"
              />
              <span className="min-w-0 flex-1 truncate text-sm text-black dark:text-white">
                {displayName(request)}
              </span>
              <Button
                size="sm"
                className="h-11 md:h-8"
                disabled={approving}
                onClick={() => approve({ roomId, requestId: request.id })}
              >
                {approving ? t("approving") : t("approve")}
              </Button>
              <Button
                size="sm"
                variant="ghost"
                className="h-11 md:h-8"
                disabled={rejecting}
                onClick={() => reject({ roomId, requestId: request.id })}
              >
                {rejecting ? t("rejecting") : t("reject")}
              </Button>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}