"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { Search } from "lucide-react";
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

      <div className="flex flex-col gap-2">
        <Label htmlFor="liveroom-code">{t("codeLabel")}</Label>
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
          className="h-14 text-center font-mono text-2xl tracking-[0.4em] md:h-12"
        />
      </div>

      <Button type="submit" className="h-11 w-full md:h-9" disabled={!complete}>
        <Search className="size-4" />
        {t("lookup")}
      </Button>
    </form>
  );
}