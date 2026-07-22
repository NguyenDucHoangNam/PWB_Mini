"use client";

import { useState, useTransition } from "react";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { ArrowRight, Hash } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Spinner } from "@/components/ui/spinner";
import { checkRoomExists } from "../api/rooms";
import { asApiError } from "@/lib/api-client";
import { resolveLiveroomErrorMessage } from "../lib/resolve-liveroom-error-message";

const ROOM_CODE_LENGTH = 6;

function sanitizeCode(raw: string): string {
  return raw.toUpperCase().replace(/[^A-Z0-9]/g, "").slice(0, ROOM_CODE_LENGTH);
}

export function JoinRoomByCodeCard() {
  const router = useRouter();
  const [isPending, startTransition] = useTransition();
  const [code, setCode] = useState("");
  const [checking, setChecking] = useState(false);

  const t = useTranslations("liveroom.joinByCode");
  const tErrors = useTranslations("liveroom.errors");
  const tCommon = useTranslations("common");

  const handleChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    setCode(sanitizeCode(event.target.value));
  };

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const trimmed = code.trim();
    if (trimmed.length !== ROOM_CODE_LENGTH) return;

    setChecking(true);
    try {
      const res = await checkRoomExists({ roomCode: trimmed });
      setChecking(false);
      if (res.success && res.data?.exists && res.data?.active) {
        startTransition(() => {
          router.push(`/live-rooms/${trimmed}`);
        });
      } else {
        toast.error(tErrors("roomNotFound"));
      }
    } catch (err) {
      setChecking(false);
      const handler = asApiError((apiErr) => {
        toast.error(resolveLiveroomErrorMessage(apiErr, tErrors, tCommon));
      });
      handler(err as Error);
    }
  };

  return (
    <div className="flex flex-col gap-3 rounded-xl border border-neutral-200 bg-white p-5 shadow-sm dark:border-neutral-800 dark:bg-black">
      <div className="flex items-center gap-2">
        <Hash className="size-5 text-neutral-700 dark:text-neutral-300" />
        <h2 className="text-base font-semibold text-black dark:text-white">
          {t("title")}
        </h2>
      </div>
      <p className="text-sm text-neutral-500 dark:text-neutral-400">{t("subtitle")}</p>
      <form
        onSubmit={handleSubmit}
        className="flex flex-col gap-2 sm:flex-row sm:items-center"
      >
        <div className="flex-1">
          <label htmlFor="join-room-code" className="sr-only">
            {t("inputLabel")}
          </label>
          <Input
            id="join-room-code"
            name="roomCode"
            value={code}
            onChange={handleChange}
            placeholder={t("placeholder")}
            inputMode="text"
            autoComplete="off"
            autoCorrect="off"
            spellCheck={false}
            maxLength={ROOM_CODE_LENGTH}
            disabled={isPending}
            className="font-mono tracking-widest uppercase"
          />
        </div>
        <Button type="submit" disabled={isPending || checking || code.length !== ROOM_CODE_LENGTH}>
          {isPending || checking ? (
            <span className="flex items-center gap-2">
              <Spinner size="sm" />
              {tCommon("loading")}
            </span>
          ) : (
            <span className="flex items-center gap-2">
              {t("submitBtn")}
              <ArrowRight className="size-4" />
            </span>
          )}
        </Button>
      </form>
      <p className="text-xs text-neutral-500 dark:text-neutral-400">{t("hint")}</p>
    </div>
  );
}
