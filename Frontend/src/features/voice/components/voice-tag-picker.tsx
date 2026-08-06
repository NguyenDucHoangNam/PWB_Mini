"use client";

import { useMemo, useState } from "react";
import { useTranslations } from "next-intl";
import { SuggestCombobox } from "@/components/ui/suggest-combobox";
import { useDebouncedValue } from "@/hooks/use-debounced-value";
import { LanguageFlagIcon } from "@/features/voice/components/language-flag";
import { useListVoiceTags, useSuggestVoiceTags } from "@/features/voice/api/voice-tags";
import type { VoiceTagSuggestion } from "@/features/voice/types";

const SEARCH_DEBOUNCE_MS = 250;
const SUGGEST_MIN_CHARS = 2;
const LOCAL_PAGE_SIZE = 50;

interface VoiceTagPickerProps {
  id?: string;
  onSelect: (voiceTagId: string) => void;
  disabled?: boolean;
}

/**
 * Search-and-pick for the voice tag a song is uploaded with.
 *
 * Two sources feed the same list. Below two characters it filters the first page of the user's tags
 * that the form already holds, so the very first keystroke shows something; from two characters on it
 * asks the server, which matches without diacritics and tolerates typos. Whichever is showing, each row
 * carries the voice and language so a name like "intro" is still distinguishable from another "intro".
 */
export function VoiceTagPicker({ id = "voice-tag-picker", onSelect, disabled }: VoiceTagPickerProps) {
  const tList = useTranslations("voice.list");

  const [keyword, setKeyword] = useState("");
  const debouncedKeyword = useDebouncedValue(keyword, SEARCH_DEBOUNCE_MS);
  const useServer = debouncedKeyword.trim().length >= SUGGEST_MIN_CHARS;

  const { data: localData } = useListVoiceTags({ page: 0, size: LOCAL_PAGE_SIZE });
  const { data: suggestData, isFetching } = useSuggestVoiceTags({ q: debouncedKeyword });

  const items = useMemo<VoiceTagSuggestion[]>(() => {
    if (useServer) return suggestData?.data ?? [];

    const term = keyword.trim().toLowerCase();
    return (localData?.data?.content ?? [])
      .filter((tag) => tag.name.toLowerCase().includes(term))
      .slice(0, 8)
      .map((tag) => ({
        id: tag.id,
        name: tag.name,
        voiceName: tag.voiceName ?? null,
        languageCode: tag.languageCode,
        tagType: tag.tagType,
      }));
  }, [useServer, suggestData, localData, keyword]);

  return (
    <SuggestCombobox
      id={id}
      value={keyword}
      onValueChange={setKeyword}
      items={disabled ? [] : items}
      loading={useServer && isFetching}
      placeholder={tList("searchVoiceTagsPlaceholder")}
      emptyLabel={tList("noResults")}
      clearLabel={tList("clearSearch")}
      getKey={(item) => item.id}
      onSelect={(item) => {
        onSelect(item.id);
        setKeyword(item.name);
      }}
      renderItem={(item) => (
        <span className="flex min-w-0 items-center gap-2">
          <span className="min-w-0 flex-1 truncate font-medium">{item.name}</span>
          {item.languageCode ? <LanguageFlagIcon langCode={item.languageCode} /> : null}
          {item.voiceName ? (
            <span className="truncate text-xs text-neutral-500 dark:text-neutral-400">
              {item.voiceName}
            </span>
          ) : null}
          <span className="shrink-0 rounded-full border border-neutral-200 px-1.5 py-0.5 text-[10px] font-semibold text-neutral-500 dark:border-neutral-700 dark:text-neutral-400">
            {item.tagType}
          </span>
        </span>
      )}
    />
  );
}
