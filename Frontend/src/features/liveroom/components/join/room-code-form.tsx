"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { KeyRound, Search } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { JoinStepIndicator } from "./join-step-indicator";
import { isCompleteRoomCode, normalizeRoomCode } from "../../utils/format-room-code";
import { ROOM_CODE_LENGTH } from "../../types";

export function RoomCodeForm() {
  const t = useTranslations("liveroom.join");
  const router = useRouter();
  const [code, setCode] = useState("");

  const complete = isCompleteRoomCode(code);

  return (
    <form
      className="flex w-full flex-col gap-6"
      onSubmit={(event) => {
        event.preventDefault();
        if (complete) router.push(`/dashboard/liveroom/join/${normalizeRoomCode(code)}`);
      }}
    >
      <JoinStepIndicator current={1} />

      <div className="flex flex-col items-center gap-3 text-center">
        <div className="grid size-12 place-items-center rounded-xl border border-border bg-muted text-foreground">
          <KeyRound className="size-5" aria-hidden="true" />
        </div>
        <div className="flex flex-col gap-1">
          <h2 className="text-lg font-bold tracking-tight text-foreground">{t("title")}</h2>
          <p className="text-sm text-muted-foreground">{t("subtitle")}</p>
        </div>
      </div>

      <div className="flex flex-col gap-2">
        <Label
          htmlFor="liveroom-code"
          className="text-xs font-semibold uppercase tracking-wide text-muted-foreground"
        >
          {t("codeLabel")}
        </Label>
        <Input
          id="liveroom-code"
          value={code}
          onChange={(event) => setCode(normalizeRoomCode(event.target.value))}
          placeholder={t("codePlaceholder")}
          autoComplete="off"
          autoCapitalize="characters"
          spellCheck={false}
          inputMode="text"
          maxLength={ROOM_CODE_LENGTH}
          className="h-14 text-center font-mono text-2xl font-bold tracking-[0.4em]"
        />
      </div>

      <Button type="submit" className="h-11 w-full font-semibold" disabled={!complete}>
        <Search className="mr-1.5 size-4" />
        {t("lookup")}
      </Button>
    </form>
  );
}