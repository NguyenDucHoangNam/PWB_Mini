"use client";

import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { NEU_LABEL, NEU_TEXT, NEU_TEXT_MUTED, NeuButton } from "@/components/ui/neu";
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
    <section className="neu-pressed-sm m-2.5 mb-0 flex flex-col gap-2.5 rounded-2xl border-none p-3">
      <h3 className={`flex items-center gap-2 ${NEU_LABEL}`}>
        {t("title")}
        {pending.length > 0 ? (
          <span className="inline-flex min-w-5 items-center justify-center rounded-full bg-rose-600 px-1.5 py-0.5 text-[10px] leading-none font-bold text-white tabular-nums dark:bg-rose-500">
            {pending.length}
          </span>
        ) : null}
      </h3>

      {pending.length === 0 ? (
        <p className={`text-xs font-medium ${NEU_TEXT_MUTED}`}>{t("empty")}</p>
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
              <span className={`min-w-0 flex-1 truncate text-sm font-semibold ${NEU_TEXT}`}>
                {displayName(request)}
              </span>
              <NeuButton
                variant="primary"
                size="sm"
                disabled={approving}
                onClick={() => approve({ roomId, requestId: request.id })}
              >
                {approving ? t("approving") : t("approve")}
              </NeuButton>
              <NeuButton
                size="sm"
                disabled={rejecting}
                onClick={() => reject({ roomId, requestId: request.id })}
              >
                {rejecting ? t("rejecting") : t("reject")}
              </NeuButton>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}