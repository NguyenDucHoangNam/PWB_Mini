"use client";

import { useState, useRef, useEffect, useCallback } from "react";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { standardSchemaResolver as zodResolver } from "@hookform/resolvers/standard-schema";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { ChevronDown, Check, Loader2, Volume2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { asApiError, type ApiError } from "@/lib/api-client";
import { useCreateTtsVoiceTag, useTtsVoices, previewTtsVoiceTag } from "../api/voice-tags";
import { resolveVoiceErrorMessage } from "../lib/resolve-voice-error-message";
import { ttsFormSchema, LANGUAGE_CODE_VALUES, type TtsFormValues } from "../schemas/voice-tag-schema";
import { LanguageFlagIcon } from "./language-flag";

interface TtsFormProps {
  onCancel?: () => void;
  onSuccess?: () => void;
}

export function TtsForm({ onCancel, onSuccess }: TtsFormProps) {
  const t = useTranslations("voice.voiceTags.form");
  const tLanguageCodes = useTranslations("voice.voiceTags.languageCodes");
  const tVoiceGender = useTranslations("voice.voiceTags.voiceGender");
  const tActions = useTranslations("voice.actions");
  const tCommon = useTranslations("common");
  const tErrors = useTranslations("voice.errors");
  const tValidation = useTranslations("validation");
  const router = useRouter();

  const [langDropdownOpen, setLangDropdownOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [isPreviewing, setIsPreviewing] = useState(false);

  const {
    register,
    handleSubmit,
    watch,
    getValues,
    setValue,
    formState: { errors },
  } = useForm<TtsFormValues>({
    resolver: zodResolver(ttsFormSchema),
    defaultValues: {
      name: "",
      text: "",
      languageCode: "vi-VN",
      voiceName: "",
    },
  });

  const selectedLang = watch("languageCode");

  const { data: voicesRes } = useTtsVoices();
  const voicesForLanguage = (voicesRes?.data ?? []).filter(
    (voice) => voice.languageCode === selectedLang,
  );

  // An object URL keeps its blob alive until revoked, so the previous preview is released whenever a
  // new one replaces it and when the form unmounts.
  useEffect(
    () => () => {
      if (previewUrl) URL.revokeObjectURL(previewUrl);
    },
    [previewUrl],
  );

  const clearPreview = useCallback(() => setPreviewUrl(null), []);

  const handlePreview = useCallback(async () => {
    const { text, languageCode, voiceName } = getValues();
    if (!text.trim()) {
      toast.error(tValidation("text.required"));
      return;
    }

    setIsPreviewing(true);
    try {
      const blob = await previewTtsVoiceTag({
        data: { text, languageCode, voiceName: voiceName || null },
      });
      setPreviewUrl(URL.createObjectURL(blob));
    } catch (err) {
      // A blob request carries no JSON envelope to read a message out of, so the status is what we have.
      const status = (err as ApiError | undefined)?.status;
      const retryAfter = (err as ApiError | undefined)?.retryAfterSeconds;
      if (status === 429) {
        toast.error(t("previewRateLimited", { seconds: retryAfter ?? 60 }));
      } else {
        toast.error(t("previewFailed"));
      }
    } finally {
      setIsPreviewing(false);
    }
  }, [getValues, t, tValidation]);

  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setLangDropdownOpen(false);
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  const { mutate: createTts, isPending } = useCreateTtsVoiceTag({
    mutationConfig: {
      onSuccess: (response) => {
        if (response.success) {
          toast.success(t("createSuccess"));
          onSuccess?.();
          router.push("/dashboard/voice-tags");
        } else {
          toast.error(response.message || tCommon("error"));
        }
      },
      onError: asApiError((err) => {
        toast.error(resolveVoiceErrorMessage(err, tErrors, tCommon));
      }),
    },
  });

  const onSubmit = handleSubmit((values) => {
    createTts({
      data: {
        name: values.name,
        text: values.text,
        languageCode: values.languageCode,
        voiceName: values.voiceName || null,
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
      errors[fieldKey === "languagecode" ? "languageCode" : (fieldKey as keyof TtsFormValues)]
        ?.message;
    if (!error) return null;
    const i18nKey = (typeof error === "string" && map[error]) || error;
    return tValidation(i18nKey as never);
  };

  return (
    <form onSubmit={onSubmit} className="flex flex-col gap-5">
      <div className="grid gap-5 lg:grid-cols-2 lg:gap-6">
        <div className="flex flex-col gap-4">
          <div className="flex flex-col gap-2">
            <Label htmlFor="tts-name">{t("nameLabel")}</Label>
            <Input id="tts-name" {...register("name")} maxLength={100} className="h-10" />
            {errors.name && (
              <p role="alert" className="text-xs text-destructive">
                {renderError("name")}
              </p>
            )}
          </div>

          <div className="flex flex-1 flex-col gap-2">
            <Label htmlFor="tts-text">{t("textLabel")}</Label>
            <textarea
              id="tts-text"
              {...register("text")}
              maxLength={2000}
              rows={5}
              className="min-h-32 w-full flex-1 resize-y rounded-lg border border-input bg-transparent px-2.5 py-2 text-base outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 md:text-sm dark:bg-input/30"
            />
            {errors.text && (
              <p role="alert" className="text-xs text-destructive">
                {renderError("text")}
              </p>
            )}
          </div>
        </div>

        <div className="flex flex-col gap-4">
          <div className="relative flex flex-col gap-2" ref={dropdownRef}>
            <Label htmlFor="tts-language">{t("languageLabel")}</Label>
            <button
              id="tts-language"
              type="button"
              onClick={() => setLangDropdownOpen((prev) => !prev)}
              aria-expanded={langDropdownOpen}
              className="key-press flex h-10 w-full items-center justify-between rounded-lg border border-input bg-transparent px-3 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 dark:bg-input/30"
            >
              <span className="flex min-w-0 items-center gap-2.5">
                <LanguageFlagIcon langCode={selectedLang} className="h-3.5 w-5 shrink-0 rounded-xs" />
                <span className="truncate font-medium text-foreground">
                  {tLanguageCodes(selectedLang)}
                </span>
              </span>
              <ChevronDown
                className={`size-4 shrink-0 text-muted-foreground beat-16th transition-transform ease-hammer ${
                  langDropdownOpen ? "rotate-180" : ""
                }`}
                aria-hidden="true"
              />
            </button>

            {langDropdownOpen && (
              <div className="absolute top-[calc(100%+0.25rem)] left-0 z-50 w-full rounded-lg border border-border bg-popover p-1 shadow-lg animate-in fade-in-0 zoom-in-95">
                {LANGUAGE_CODE_VALUES.map((lang) => {
                  const isSelected = selectedLang === lang;
                  return (
                    <button
                      key={lang}
                      type="button"
                      onClick={() => {
                        setValue("languageCode", lang, { shouldValidate: true });
                        // Voices are language-specific, so the previous pick and its preview no longer apply.
                        setValue("voiceName", "");
                        clearPreview();
                        setLangDropdownOpen(false);
                      }}
                      className={`flex w-full items-center justify-between rounded-md px-2.5 py-2 text-sm beat-16th transition-colors ease-hammer ${
                        isSelected
                          ? "bg-secondary font-medium text-foreground"
                          : "text-muted-foreground hover:bg-muted hover:text-foreground"
                      }`}
                    >
                      <span className="flex min-w-0 items-center gap-2.5">
                        <LanguageFlagIcon langCode={lang} className="h-3.5 w-5 shrink-0 rounded-xs" />
                        <span className="truncate">{tLanguageCodes(lang)}</span>
                      </span>
                      {isSelected && <Check className="size-4 shrink-0" aria-hidden="true" />}
                    </button>
                  );
                })}
              </div>
            )}

            {errors.languageCode && (
              <p role="alert" className="text-xs text-destructive">
                {renderError("languagecode")}
              </p>
            )}
          </div>

          <div className="flex flex-col gap-2">
            <Label htmlFor="tts-voice">{t("voiceLabel")}</Label>
            <select
              id="tts-voice"
              className="h-10 w-full rounded-lg border border-input bg-transparent px-2.5 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 dark:bg-input/30"
              {...register("voiceName", { onChange: clearPreview })}
            >
              <option value="">{t("voiceDefaultOption")}</option>
              {voicesForLanguage.map((voice) => (
                <option key={voice.name} value={voice.name}>
                  {tVoiceGender(voice.gender)} — {voice.name}
                </option>
              ))}
            </select>
            <p className="text-xs text-muted-foreground">{t("voiceHint")}</p>
          </div>

          <div className="mt-auto flex flex-col gap-3 rounded-xl border border-border bg-muted/40 p-4">
            <div className="flex items-start justify-between gap-3">
              <div className="flex min-w-0 flex-col gap-0.5">
                <span className="text-sm font-medium text-foreground">{t("previewTitle")}</span>
                <span className="text-xs leading-relaxed text-muted-foreground">
                  {t("previewHint")}
                </span>
              </div>
              <Button
                type="button"
                variant="outline"
                size="lg"
                onClick={handlePreview}
                disabled={isPreviewing || isPending}
                className="h-10 shrink-0 sm:h-9"
              >
                {isPreviewing ? (
                  <Loader2 className="mr-1.5 size-3.5 animate-spin" aria-hidden="true" />
                ) : (
                  <Volume2 className="mr-1.5 size-3.5" aria-hidden="true" />
                )}
                {t("previewButton")}
              </Button>
            </div>

            {previewUrl && (
              <audio
                controls
                autoPlay
                src={previewUrl}
                className="w-full"
                aria-label={t("previewTitle")}
              />
            )}
          </div>
        </div>
      </div>

      <div className="flex justify-end gap-2">
        {onCancel && (
          <Button type="button" variant="ghost" size="lg" onClick={onCancel} disabled={isPending}>
            {tActions("cancel")}
          </Button>
        )}
        <Button type="submit" size="lg" disabled={isPending} className="h-11 min-w-36 sm:h-9">
          {t("submitButton")}
        </Button>
      </div>
    </form>
  );
}
