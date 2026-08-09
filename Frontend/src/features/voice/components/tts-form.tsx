"use client";

import { useState, useRef, useEffect, useCallback } from "react";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { standardSchemaResolver as zodResolver } from "@hookform/resolvers/standard-schema";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { ChevronDown, Check, Loader2, Volume2 } from "lucide-react";
import {
  NEU_ERROR_TEXT,
  NEU_FOCUS,
  NEU_INPUT,
  NEU_LABEL,
  NEU_TEXT,
  NEU_TEXT_MUTED,
  NeuButton,
  NeuPanel,
} from "@/components/ui/neu";
import { asApiError, type ApiError } from "@/lib/api-client";
import { useCreateTtsVoiceTag, useTtsVoices, previewTtsVoiceTag } from "../api/voice-tags";
import { resolveVoiceErrorMessage } from "../lib/resolve-voice-error-message";
import { ttsFormSchema, LANGUAGE_CODE_VALUES, type TtsFormValues } from "../schemas/voice-tag-schema";
import { LanguageFlagIcon, GenderIcon } from "./language-flag";

interface TtsFormProps {
  onCancel?: () => void;
  onSuccess?: () => void;
}

/** Closed reads as a raised control; open sinks in and turns accent. */
function dropdownTrigger(open: boolean) {
  return [
    "flex h-12 w-full items-center justify-between gap-2 rounded-2xl border-none px-4 text-sm font-semibold",
    NEU_FOCUS,
    open ? "neu-pressed text-indigo-600 dark:text-indigo-400" : "neu-button",
  ].join(" ");
}

const DROPDOWN_POPUP =
  "neu-raised absolute top-[calc(100%+0.625rem)] left-0 z-50 w-full rounded-2xl border-none p-2 animate-in fade-in-0 zoom-in-95 motion-reduce:animate-none";

/** Selection is carried by the tick and the accent colour, not by the inset alone. */
function dropdownOption(selected: boolean) {
  return [
    "flex w-full items-center justify-between gap-2 rounded-xl border-none px-3 py-2.5 text-sm transition-all",
    selected
      ? "neu-pressed-sm font-bold text-indigo-600 dark:text-indigo-400"
      : "font-medium text-slate-600 hover:text-slate-900 dark:text-slate-300 dark:hover:text-slate-100",
  ].join(" ");
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
  const [voiceDropdownOpen, setVoiceDropdownOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);
  const voiceDropdownRef = useRef<HTMLDivElement>(null);
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
      if (voiceDropdownRef.current && !voiceDropdownRef.current.contains(event.target as Node)) {
        setVoiceDropdownOpen(false);
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
    <form onSubmit={onSubmit} className="flex flex-1 flex-col gap-4">
      <div className="grid gap-5 sm:grid-cols-2 sm:gap-6">
        <div className="flex flex-col gap-2">
          <label htmlFor="tts-name" className={NEU_LABEL}>
            {t("nameLabel")}
          </label>
          <input
            id="tts-name"
            {...register("name")}
            maxLength={100}
            className={`${NEU_INPUT} h-12`}
          />
          {errors.name && (
            <p role="alert" className={NEU_ERROR_TEXT}>
              {renderError("name")}
            </p>
          )}
        </div>

        <div className="flex flex-col gap-2">
          <label htmlFor="tts-text" className={NEU_LABEL}>
            {t("textLabel")}
          </label>
          <textarea
            id="tts-text"
            {...register("text")}
            maxLength={2000}
            rows={1}
            className={`${NEU_INPUT} h-12 resize-none py-3.5`}
          />
          {errors.text && (
            <p role="alert" className={NEU_ERROR_TEXT}>
              {renderError("text")}
            </p>
          )}
        </div>
      </div>

      <div className="grid gap-5 sm:grid-cols-2 sm:gap-6">
        <div className="flex flex-col gap-2" ref={dropdownRef}>
          <label htmlFor="tts-language" className={NEU_LABEL}>
            {t("languageLabel")}
          </label>
          <div className="relative">
            <button
              id="tts-language"
              type="button"
              onClick={() => setLangDropdownOpen((prev) => !prev)}
              aria-expanded={langDropdownOpen}
              className={dropdownTrigger(langDropdownOpen)}
            >
              <span className="flex min-w-0 items-center gap-2.5">
                <LanguageFlagIcon langCode={selectedLang} className="h-3.5 w-5 shrink-0 rounded-xs" />
                <span className="truncate">{tLanguageCodes(selectedLang)}</span>
              </span>
              <ChevronDown
                className={`size-4 shrink-0 beat-16th transition-transform ease-hammer motion-reduce:transition-none ${
                  langDropdownOpen ? "rotate-180" : "text-slate-400 dark:text-slate-500"
                }`}
                aria-hidden="true"
              />
            </button>

            {langDropdownOpen && (
              <div className={DROPDOWN_POPUP}>
                {LANGUAGE_CODE_VALUES.map((lang) => {
                  const isSelected = selectedLang === lang;
                  return (
                    <button
                      key={lang}
                      type="button"
                      onClick={() => {
                        setValue("languageCode", lang, { shouldValidate: true });
                        setValue("voiceName", "");
                        clearPreview();
                        setLangDropdownOpen(false);
                      }}
                      className={dropdownOption(isSelected)}
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
          </div>

          {errors.languageCode && (
            <p role="alert" className={NEU_ERROR_TEXT}>
              {renderError("languagecode")}
            </p>
          )}
        </div>

        <div className="flex flex-col gap-2" ref={voiceDropdownRef}>
          <label htmlFor="tts-voice" className={NEU_LABEL}>
            {t("voiceLabel")}
          </label>
          <div className="relative">
            <button
              id="tts-voice"
              type="button"
              onClick={() => setVoiceDropdownOpen((prev) => !prev)}
              aria-expanded={voiceDropdownOpen}
              className={dropdownTrigger(voiceDropdownOpen)}
            >
              <span className="flex min-w-0 items-center gap-2.5">
                <GenderIcon
                  gender={voicesForLanguage.find((v) => v.name === watch("voiceName"))?.gender}
                  className="size-4 shrink-0"
                />
                <span className="truncate">
                  {watch("voiceName")
                    ? `${tVoiceGender(voicesForLanguage.find((v) => v.name === watch("voiceName"))?.gender ?? "MALE")} — ${watch("voiceName")}`
                    : t("voiceDefaultOption")}
                </span>
              </span>
              <ChevronDown
                className={`size-4 shrink-0 beat-16th transition-transform ease-hammer motion-reduce:transition-none ${
                  voiceDropdownOpen ? "rotate-180" : "text-slate-400 dark:text-slate-500"
                }`}
                aria-hidden="true"
              />
            </button>

            {voiceDropdownOpen && (
              <div className={DROPDOWN_POPUP}>
                <button
                  type="button"
                  onClick={() => {
                    setValue("voiceName", "");
                    clearPreview();
                    setVoiceDropdownOpen(false);
                  }}
                  className={dropdownOption(!watch("voiceName"))}
                >
                <span className="flex min-w-0 items-center gap-2.5">
                  <GenderIcon gender={null} className="size-4 shrink-0" />
                  <span className="truncate">{t("voiceDefaultOption")}</span>
                </span>
                  {!watch("voiceName") && <Check className="size-4 shrink-0" aria-hidden="true" />}
                </button>
                {voicesForLanguage.map((voice) => {
                  const isSelected = watch("voiceName") === voice.name;
                  return (
                    <button
                      key={voice.name}
                      type="button"
                      onClick={() => {
                        setValue("voiceName", voice.name, { shouldValidate: true });
                        clearPreview();
                        setVoiceDropdownOpen(false);
                      }}
                      className={dropdownOption(isSelected)}
                    >
                    <span className="flex min-w-0 items-center gap-2.5">
                      <GenderIcon gender={voice.gender} className="size-4 shrink-0" />
                      <span className="truncate">{tVoiceGender(voice.gender)} — {voice.name}</span>
                    </span>
                      {isSelected && <Check className="size-4 shrink-0" aria-hidden="true" />}
                    </button>
                  );
                })}
              </div>
            )}
          </div>
        </div>
      </div>

      <NeuPanel tone="pressed" className="flex flex-col gap-4 rounded-2xl p-5">
        <div className="flex items-start justify-between gap-3">
          <div className="flex min-w-0 flex-col gap-1">
            <span className={`text-sm font-bold ${NEU_TEXT}`}>{t("previewTitle")}</span>
            <span className={`text-xs leading-relaxed ${NEU_TEXT_MUTED}`}>{t("previewHint")}</span>
          </div>
          <NeuButton
            type="button"
            onClick={handlePreview}
            disabled={isPreviewing || isPending}
            className="shrink-0"
          >
            {isPreviewing ? (
              <Loader2 className="size-4 animate-spin" aria-hidden="true" />
            ) : (
              <Volume2 className="size-4" aria-hidden="true" />
            )}
            {t("previewButton")}
          </NeuButton>
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
      </NeuPanel>

      <NeuPanel tone="pressed" className="flex flex-1 flex-col justify-center rounded-2xl p-5">
        <p className={`mb-3 text-sm font-bold ${NEU_TEXT}`}>{t("guideTitle")}</p>
        <ol className={`flex flex-col gap-2.5 text-xs leading-relaxed ${NEU_TEXT_MUTED}`}>
          {[t("guideStep1"), t("guideStep2"), t("guideStep3")].map((step, index) => (
            <li key={index} className="flex items-start gap-2.5">
              <span className="neu-raised-sm flex size-5 shrink-0 items-center justify-center rounded-full border-none text-[10px] font-bold text-indigo-600 dark:text-indigo-400">
                {index + 1}
              </span>
              {step}
            </li>
          ))}
        </ol>
      </NeuPanel>

      <div className="flex justify-end gap-3">
        {onCancel && (
          <NeuButton type="button" onClick={onCancel} disabled={isPending}>
            {tActions("cancel")}
          </NeuButton>
        )}
        <NeuButton type="submit" variant="primary" disabled={isPending} className="min-w-36">
          {isPending && <Loader2 className="size-4 animate-spin" aria-hidden="true" />}
          {t("submitButton")}
        </NeuButton>
      </div>
    </form>
  );
}
