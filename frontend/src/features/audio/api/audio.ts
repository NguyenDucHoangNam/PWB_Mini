import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "@/lib/api-client";
import type { QueryConfig, MutationConfig } from "@/lib/react-query";
import type { ApiResponse, PaginatedResponse } from "@/types/api";
import type {
  ConfirmUploadRequest,
  ConfirmUploadResponse,
  DemoListItem,
  DemoStatusResponse,
  PresignedUrlRequest,
  PresignedUrlResponse,
  RotateKeyResponse,
} from "../types";

export const getDemos = ({
  page,
  size,
}: {
  page: number;
  size: number;
}): Promise<ApiResponse<PaginatedResponse<DemoListItem>>> => {
  return apiClient
    .get("/demos", { params: { page, size } })
    .then((res) => res.data);
};

export const getDemoStatus = ({
  demoId,
}: {
  demoId: string;
}): Promise<ApiResponse<DemoStatusResponse>> => {
  return apiClient.get(`/demos/${demoId}/status`).then((res) => res.data);
};

export const requestPresignedUrl = ({
  data,
}: {
  data: PresignedUrlRequest;
}): Promise<ApiResponse<PresignedUrlResponse>> => {
  return apiClient
    .post("/demos/presigned-upload-url", data)
    .then((res) => res.data);
};

export const confirmUpload = ({
  data,
}: {
  data: ConfirmUploadRequest;
}): Promise<ApiResponse<ConfirmUploadResponse>> => {
  return apiClient.post("/demos/confirm-upload", data).then((res) => res.data);
};

export const rotateDemoKey = ({
  demoId,
}: {
  demoId: string;
}): Promise<ApiResponse<RotateKeyResponse>> => {
  return apiClient
    .post(`/demos/${demoId}/rotate-key`)
    .then((res) => res.data);
};

export const DEMOS_KEY = "audio-demos" as const;
export const DEMO_STATUS_KEY = (demoId: string) =>
  ["audio-demos-status", demoId] as const;

type UseConfirmUploadOptions = {
  mutationConfig?: MutationConfig<typeof confirmUpload>;
};

export const useConfirmUpload = ({
  mutationConfig,
}: UseConfirmUploadOptions = {}) => {
  const queryClient = useQueryClient();
  return useMutation({
    onSuccess: (response) => {
      if (response.success) {
        queryClient.invalidateQueries({ queryKey: [DEMOS_KEY] });
      }
    },
    ...mutationConfig,
    mutationFn: confirmUpload,
  });
};

type UseRotateDemoKeyOptions = {
  mutationConfig?: MutationConfig<typeof rotateDemoKey>;
};

export const useRotateDemoKey = ({
  mutationConfig,
}: UseRotateDemoKeyOptions = {}) => {
  const queryClient = useQueryClient();
  return useMutation({
    onSuccess: (response) => {
      if (response.success) {
        queryClient.invalidateQueries({ queryKey: [DEMOS_KEY] });
        queryClient.invalidateQueries({ queryKey: ["audio-demos-status"] });
      }
    },
    ...mutationConfig,
    mutationFn: rotateDemoKey,
  });
};

export function useDemos({
  page,
  size,
  queryConfig,
}: {
  page: number;
  size: number;
  queryConfig?: QueryConfig<typeof getDemos>;
}) {
  return useQuery({
    queryKey: [DEMOS_KEY, { page, size }],
    queryFn: () => getDemos({ page, size }),
    ...queryConfig,
  });
}

export function useDemoStatus({
  demoId,
  queryConfig,
}: {
  demoId: string;
  queryConfig?: QueryConfig<typeof getDemoStatus>;
}) {
  return useQuery({
    queryKey: DEMO_STATUS_KEY(demoId),
    queryFn: () => getDemoStatus({ demoId }),
    enabled: Boolean(demoId),
    ...queryConfig,
  });
}
