"use client";

import { useState, useRef, useEffect } from "react";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { standardSchemaResolver as zodResolver } from "@hookform/resolvers/standard-schema";
import { useTranslations } from "next-intl";
import { toast } from "sonner";
import { ChevronDown, Check } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { asApiError } from "@/lib/api-client";
import { useCreateTtsVoiceTag } from "../api/voice-tags";
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
  const tActions = useTranslations("voice.actions");
  const tCommon = useTranslations("common");
  const tErrors = useTranslations("voice.errors");
  const tValidation = useTranslations("validation");
  const router = useRouter();

  const [langDropdownOpen, setLangDropdownOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);

  const {
    register,
    handleSubmit,
    watch,
    setValue,
    formState: { errors },
  } = useForm<TtsFormValues>({
    resolver: zodResolver(ttsFormSchema),
    defaultValues: {
      name: "",
      text: "",
      languageCode: "vi-VN",
    },
  });

  const selectedLang = watch("languageCode");

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
    <form onSubmit={onSubmit} className="flex flex-col gap-4">
      <div className="flex flex-col gap-2">
        <Label htmlFor="tts-name">{t("nameLabel")}</Label>
        <Input id="tts-name" {...register("name")} maxLength={100} />
        {errors.name && (
          <p className="text-xs text-red-600 dark:text-red-400">{renderError("name")}</p>
        )}
      </div>

      <div className="flex flex-col gap-2">
        <Label htmlFor="tts-text">{t("textLabel")}</Label>
        <textarea
          id="tts-text"
          {...register("text")}
          maxLength={2000}
          rows={4}
          className="w-full rounded-lg border border-input bg-transparent px-2.5 py-2 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 dark:bg-input/30"
        />
        {errors.text && (
          <p className="text-xs text-red-600 dark:text-red-400">{renderError("text")}</p>
        )}
      </div>

      <div className="flex flex-col gap-2 relative" ref={dropdownRef}>
        <Label htmlFor="tts-language">{t("languageLabel")}</Label>
        <button
          id="tts-language"
          type="button"
          onClick={() => setLangDropdownOpen((prev) => !prev)}
          className="h-9 w-full rounded-lg border border-input bg-transparent px-3 text-sm flex items-center justify-between outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 dark:bg-neutral-950"
        >
          <div className="flex items-center gap-2.5 min-w-0">
            <LanguageFlagIcon langCode={selectedLang} className="h-3.5 w-[20px] rounded-[1px] shrink-0" />
            <span className="truncate text-neutral-900 dark:text-neutral-100 font-medium">
              {tLanguageCodes(selectedLang)}
            </span>
          </div>
          <ChevronDown
            className={`size-4 text-neutral-500 transition-transform duration-200 shrink-0 ${
              langDropdownOpen ? "rotate-180" : ""
            }`}
          />
        </button>

        {langDropdownOpen && (
          <div className="absolute top-[calc(100%+4px)] left-0 z-50 w-full rounded-lg border border-neutral-200 bg-white p-1 shadow-lg dark:border-neutral-800 dark:bg-neutral-950 animate-in fade-in-0 zoom-in-95">
            {LANGUAGE_CODE_VALUES.map((lang) => {
              const isSelected = selectedLang === lang;
              return (
                <button
                  key={lang}
                  type="button"
                  onClick={() => {
                    setValue("languageCode", lang, { shouldValidate: true });
                    setLangDropdownOpen(false);
                  }}
                  className={`flex w-full items-center justify-between rounded-md px-2.5 py-2 text-sm transition-colors ${
                    isSelected
                      ? "bg-neutral-100 font-semibold text-neutral-900 dark:bg-neutral-900 dark:text-neutral-50"
                      : "text-neutral-700 hover:bg-neutral-50 dark:text-neutral-300 dark:hover:bg-neutral-900/60"
                  }`}
                >
                  <div className="flex items-center gap-2.5 min-w-0">
                    <LanguageFlagIcon langCode={lang} className="h-3.5 w-[20px] rounded-[1px] shrink-0" />
                    <span className="truncate">{tLanguageCodes(lang)}</span>
                  </div>
                  {isSelected && <Check className="size-4 text-neutral-900 dark:text-neutral-100 shrink-0" />}
                </button>
              );
            })}
          </div>
        )}

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
