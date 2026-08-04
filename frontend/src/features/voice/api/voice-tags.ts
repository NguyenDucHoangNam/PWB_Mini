import {
  useQuery,
  useMutation,
  useQueryClient,
  keepPreviousData,
} from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import type { QueryConfig, MutationConfig } from "@/lib/react-query";
import type { ApiResponse, PaginatedResponse } from "@/types/api";
import type {
  AudioUrl,
  CreateTtsVoiceTagRequest,
  ListVoiceTagsParams,
  PreviewTtsRequest,
  TtsVoice,
  UpdateVoiceTagRequest,
  VoiceTag,
  VoiceTagType,
} from "../types";

export const VOICE_TAGS_KEY = "voice-voice-tags" as const;
export const TTS_VOICES_KEY = "voice-tts-voices" as const;
export const voiceTagKey = (voiceTagId: string) =>
  ["voice-voice-tags", voiceTagId] as const;
export const voiceTagAudioKey = (voiceTagId: string) =>
  ["voice-voice-tags", voiceTagId, "audio"] as const;

export const createTtsVoiceTag = ({
  data,
}: {
  data: CreateTtsVoiceTagRequest;
}): Promise<ApiResponse<VoiceTag>> =>
  apiClient.post("/voice-tags/tts", data).then((res) => res.data);

export const listVoiceTags = ({
  page,
  size,
}: ListVoiceTagsParams): Promise<ApiResponse<PaginatedResponse<VoiceTag>>> =>
  apiClient
    .get("/voice-tags", { params: { page, size } })
    .then((res) => res.data);

export const updateVoiceTag = ({
  voiceTagId,
  data,
}: {
  voiceTagId: string;
  data: UpdateVoiceTagRequest;
}): Promise<ApiResponse<VoiceTag>> =>
  apiClient
    .patch(`/voice-tags/${voiceTagId}`, data)
    .then((res) => res.data);

/** Answers 204 No Content — there is no envelope to unwrap, so callers get nothing back. */
export const deleteVoiceTag = ({
  voiceTagId,
}: {
  voiceTagId: string;
}): Promise<void> =>
  apiClient.delete(`/voice-tags/${voiceTagId}`).then(() => undefined);

export const getVoiceTagAudioUrl = ({
  voiceTagId,
}: {
  voiceTagId: string;
}): Promise<ApiResponse<AudioUrl>> =>
  apiClient
    .get(`/voice-tags/${voiceTagId}/audio-url`)
    .then((res) => res.data);

export const listTtsVoices = (): Promise<ApiResponse<TtsVoice[]>> =>
  apiClient.get("/voice-tags/tts/voices").then((res) => res.data);

/**
 * Answers with raw audio rather than a JSON envelope — nothing is stored, so there is no URL to hand
 * back. Callers own the returned Blob and must revoke any object URL they derive from it.
 */
export const previewTtsVoiceTag = ({
  data,
}: {
  data: PreviewTtsRequest;
}): Promise<Blob> =>
  apiClient
    .post("/voice-tags/tts/preview", data, { responseType: "blob" })
    .then((res) => res.data as Blob);

type UseTtsVoicesOptions = {
  queryConfig?: QueryConfig<typeof listTtsVoices>;
};

/** The catalog is a fixed server-side list, so it never needs refetching within a session. */
export const useTtsVoices = ({ queryConfig }: UseTtsVoicesOptions = {}) =>
  useQuery({
    queryKey: [TTS_VOICES_KEY],
    queryFn: listTtsVoices,
    staleTime: Infinity,
    ...queryConfig,
  });

type UseCreateTtsVoiceTagOptions = {
  mutationConfig?: MutationConfig<typeof createTtsVoiceTag>;
};

export const useCreateTtsVoiceTag = ({
  mutationConfig,
}: UseCreateTtsVoiceTagOptions = {}) => {
  const queryClient = useQueryClient();
  const { onSuccess, ...restMutationConfig } = mutationConfig ?? {};
  return useMutation({
    ...restMutationConfig,
    onSuccess: (response, variables, onMutateResult, context) => {
      if (response.success) {
        queryClient.invalidateQueries({ queryKey: [VOICE_TAGS_KEY] });
      }
      return onSuccess?.(response, variables, onMutateResult, context);
    },
    mutationFn: createTtsVoiceTag,
  });
};

type UseListVoiceTagsOptions = {
  queryConfig?: QueryConfig<typeof listVoiceTags>;
};

export const useListVoiceTags = ({
  page,
  size,
  queryConfig,
}: ListVoiceTagsParams & UseListVoiceTagsOptions) =>
  useQuery({
    queryKey: [VOICE_TAGS_KEY, { page, size }],
    queryFn: () => listVoiceTags({ page, size }),
    placeholderData: keepPreviousData,
    ...queryConfig,
  });

type UseUpdateVoiceTagOptions = {
  mutationConfig?: MutationConfig<typeof updateVoiceTag>;
};

export const useUpdateVoiceTag = ({
  mutationConfig,
}: UseUpdateVoiceTagOptions = {}) => {
  const queryClient = useQueryClient();
  const { onSuccess, ...restMutationConfig } = mutationConfig ?? {};
  return useMutation({
    ...restMutationConfig,
    onSuccess: (response, variables, onMutateResult, context) => {
      if (response.success) {
        queryClient.invalidateQueries({ queryKey: [VOICE_TAGS_KEY] });
        queryClient.invalidateQueries({
          queryKey: voiceTagKey(variables.voiceTagId),
        });
      }
      return onSuccess?.(response, variables, onMutateResult, context);
    },
    mutationFn: updateVoiceTag,
  });
};

type UseDeleteVoiceTagOptions = {
  mutationConfig?: MutationConfig<typeof deleteVoiceTag>;
};

export const useDeleteVoiceTag = ({
  mutationConfig,
}: UseDeleteVoiceTagOptions = {}) => {
  const queryClient = useQueryClient();
  const { onSuccess, ...restMutationConfig } = mutationConfig ?? {};
  return useMutation({
    ...restMutationConfig,
    onSuccess: (response, variables, onMutateResult, context) => {
      queryClient.invalidateQueries({ queryKey: [VOICE_TAGS_KEY] });
      queryClient.invalidateQueries({ queryKey: ["voice-songs"] });
      queryClient.removeQueries({ queryKey: voiceTagAudioKey(variables.voiceTagId) });
      return onSuccess?.(response, variables, onMutateResult, context);
    },
    mutationFn: deleteVoiceTag,
  });
};

type UseVoiceTagAudioUrlOptions = {
  queryConfig?: QueryConfig<typeof getVoiceTagAudioUrl>;
};

export const useVoiceTagAudioUrl = ({
  voiceTagId,
  enabled = true,
  queryConfig,
}: {
  voiceTagId: string;
  enabled?: boolean;
  queryConfig?: QueryConfig<typeof getVoiceTagAudioUrl>;
}) =>
  useQuery({
    queryKey: voiceTagAudioKey(voiceTagId),
    queryFn: () => getVoiceTagAudioUrl({ voiceTagId }),
    enabled: enabled && Boolean(voiceTagId),
    ...queryConfig,
  });

export type { VoiceTag, VoiceTagType };
