"use client";

import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { standardSchemaResolver as zodResolver } from "@hookform/resolvers/standard-schema";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { asApiError } from "@/lib/api-client";
import { resolveErrorI18nKey } from "@/lib/error-code-to-i18n";
import { useCreateTtsVoiceTag } from "../api/voice-tags";
import { ttsFormSchema, LANGUAGE_CODES, type TtsFormValues } from "../schemas/voice-tag-schema";

interface TtsFormProps {
  onCancel?: () => void;
  onSuccess?: () => void;
}

export function TtsForm({ onCancel, onSuccess }: TtsFormProps) {
  const t = useTranslations("voice.voiceTags.form");
  const tActions = useTranslations("voice.actions");
  const tCommon = useTranslations("common");
  const tErrors = useTranslations("voice.errors");
  const tValidation = useTranslations("validation");
  const router = useRouter();

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<TtsFormValues>({
    resolver: zodResolver(ttsFormSchema),
    defaultValues: {
      name: "",
      text: "",
      languageCode: "en-US",
    },
  });

  const { mutate: createTts, isPending } = useCreateTtsVoiceTag({
    mutationConfig: {
      onSuccess: (response) => {
        if (response.success) {
          toast.success(t("createSuccess"));
          onSuccess?.();
          router.push("/dashboard/voice-tags");
        }
      },
      onError: asApiError((err) => {
        const key = resolveErrorI18nKey(err);
        toast.error(key ? tErrors(key.split(".").pop() as never) : tCommon("error"));
      }),
    },
  });

  const onSubmit = handleSubmit((values) => {
    createTts({
      data: {
        name: values.name,
        text: values.text,
        languageCode: values.languageCode,
      },
    });
  });

  const renderError = (fieldKey: "name" | "text" | "languagecode") => {
    const map: Record<string, keyof TtsFormValues> = {
      "validation.name.required": "name",
      "validation.name.maxlength": "name",
      "validation.text.required": "text",
      "validation.text.maxlength": "text",
      "validation.languagecode.required": "languageCode",
      "validation.languagecode.pattern": "languageCode",
    };
    const error =
      errors[fieldKey === "languagecode" ? "languageCode" : fieldKey as keyof TtsFormValues]?.message;
    if (!error) return null;
    const i18nKey = (typeof error === "string" && map[error]) || error;
    return tValidation(i18nKey as never);
  };

  return (
    <form onSubmit={onSubmit} className="flex flex-col gap-4">
      <div className="flex flex-col gap-2">
        <Label htmlFor="tts-name">{t("nameLabel")}</Label>
        <Input id="tts-name" {...register("name")} maxLength={128} />
        {errors.name && <p className="text-xs text-red-600 dark:text-red-400">{renderError("name")}</p>}
      </div>

      <div className="flex flex-col gap-2">
        <Label htmlFor="tts-text">{t("textLabel")}</Label>
        <textarea
          id="tts-text"
          {...register("text")}
          maxLength={4000}
          rows={4}
          className="w-full rounded-lg border border-input bg-transparent px-2.5 py-2 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 dark:bg-input/30"
        />
        {errors.text && <p className="text-xs text-red-600 dark:text-red-400">{renderError("text")}</p>}
      </div>

      <div className="flex flex-col gap-2">
        <Label htmlFor="tts-language">{t("languageLabel")}</Label>
        <select
          id="tts-language"
          {...register("languageCode")}
          className="h-8 w-full rounded-lg border border-input bg-transparent px-2.5 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 dark:bg-input/30"
        >
          {LANGUAGE_CODES.map((lang) => (
            <option key={lang.value} value={lang.value}>
              {lang.label}
            </option>
          ))}
        </select>
        {errors.languageCode && (
          <p className="text-xs text-red-600 dark:text-red-400">{renderError("languagecode")}</p>
        )}
      </div>

      <div className="flex justify-end gap-2">
        {onCancel && (
          <Button type="button" variant="ghost" onClick={onCancel} disabled={isPending}>
            {tActions("cancel")}
          </Button>
        )}
        <Button type="submit" disabled={isPending}>
          {t("submitButton")}
        </Button>
      </div>
    </form>
  );
}