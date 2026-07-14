import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import type { QueryConfig, MutationConfig } from "@/lib/react-query";
import type { ApiResponse } from "@/types/api";
import type {
  CreateVoiceTagRequest,
  VoiceTagPreviewResponse,
  VoiceTagResponse,
  VoiceWhitelistResponse,
} from "../types";

export const VOICE_TAGS_KEY = "audio-voice-tags" as const;
export const VOICE_WHITELIST_KEY = "audio-voice-whitelist" as const;

export const getVoiceTags = (): Promise<ApiResponse<VoiceTagResponse[]>> => {
  return apiClient.get("/voice-tags").then((res) => res.data);
};

export const getVoiceWhitelist = ({
  languageCode,
}: {
  languageCode: string;
}): Promise<ApiResponse<VoiceWhitelistResponse>> => {
  return apiClient
    .get("/voice-tags/whitelist", { params: { languageCode } })
    .then((res) => res.data);
};

export const createVoiceTag = ({
  data,
}: {
  data: CreateVoiceTagRequest;
}): Promise<ApiResponse<VoiceTagResponse>> => {
  return apiClient.post("/voice-tags", data).then((res) => res.data);
};

export const setDefaultVoiceTag = ({
  tagId,
}: {
  tagId: string;
}): Promise<ApiResponse<void>> => {
  return apiClient
    .post(`/voice-tags/${tagId}/default`)
    .then((res) => res.data);
};

export const deleteVoiceTag = ({
  tagId,
}: {
  tagId: string;
}): Promise<ApiResponse<void>> => {
  return apiClient.delete(`/voice-tags/${tagId}`).then((res) => res.data);
};

export const restoreVoiceTag = ({
  tagId,
}: {
  tagId: string;
}): Promise<ApiResponse<void>> => {
  return apiClient
    .post(`/voice-tags/${tagId}/restore`)
    .then((res) => res.data);
};

export const previewVoiceTag = ({
  tagId,
}: {
  tagId: string;
}): Promise<ApiResponse<VoiceTagPreviewResponse>> => {
  return apiClient
    .get(`/voice-tags/${tagId}/preview`)
    .then((res) => res.data);
};

export function useVoiceTags({
  queryConfig,
}: { queryConfig?: QueryConfig<typeof getVoiceTags> } = {}) {
  return useQuery({
    queryKey: [VOICE_TAGS_KEY],
    queryFn: getVoiceTags,
    ...queryConfig,
  });
}

export function useVoiceWhitelist({
  languageCode,
  queryConfig,
}: {
  languageCode: string;
  queryConfig?: QueryConfig<typeof getVoiceWhitelist>;
} = { languageCode: "" }) {
  return useQuery({
    queryKey: [VOICE_WHITELIST_KEY, languageCode],
    queryFn: () => getVoiceWhitelist({ languageCode }),
    enabled: Boolean(languageCode) && (queryConfig?.enabled ?? true),
    ...queryConfig,
  });
}

type UseCreateVoiceTagOptions = {
  mutationConfig?: MutationConfig<typeof createVoiceTag>;
};

export const useCreateVoiceTag = ({
  mutationConfig,
}: UseCreateVoiceTagOptions = {}) => {
  const queryClient = useQueryClient();
  return useMutation({
    onSuccess: (response) => {
      if (response.success) {
        queryClient.invalidateQueries({ queryKey: [VOICE_TAGS_KEY] });
      }
    },
    ...mutationConfig,
    mutationFn: createVoiceTag,
  });
};

type UseSetDefaultVoiceTagOptions = {
  mutationConfig?: MutationConfig<typeof setDefaultVoiceTag>;
};

export const useSetDefaultVoiceTag = ({
  mutationConfig,
}: UseSetDefaultVoiceTagOptions = {}) => {
  const queryClient = useQueryClient();
  return useMutation({
    onSuccess: (response) => {
      if (response.success) {
        queryClient.invalidateQueries({ queryKey: [VOICE_TAGS_KEY] });
      }
    },
    ...mutationConfig,
    mutationFn: setDefaultVoiceTag,
  });
};

type UseDeleteVoiceTagOptions = {
  mutationConfig?: MutationConfig<typeof deleteVoiceTag>;
};

export const useDeleteVoiceTag = ({
  mutationConfig,
}: UseDeleteVoiceTagOptions = {}) => {
  const queryClient = useQueryClient();
  return useMutation({
    onSuccess: (response) => {
      if (response.success) {
        queryClient.invalidateQueries({ queryKey: [VOICE_TAGS_KEY] });
      }
    },
    ...mutationConfig,
    mutationFn: deleteVoiceTag,
  });
};

type UseRestoreVoiceTagOptions = {
  mutationConfig?: MutationConfig<typeof restoreVoiceTag>;
};

export const useRestoreVoiceTag = ({
  mutationConfig,
}: UseRestoreVoiceTagOptions = {}) => {
  const queryClient = useQueryClient();
  return useMutation({
    onSuccess: (response) => {
      if (response.success) {
        queryClient.invalidateQueries({ queryKey: [VOICE_TAGS_KEY] });
      }
    },
    ...mutationConfig,
    mutationFn: restoreVoiceTag,
  });
};
