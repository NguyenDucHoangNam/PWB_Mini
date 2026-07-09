import type { UseMutationOptions, DefaultOptions, UseQueryOptions } from "@tanstack/react-query";

/**
 * Default query configuration.
 *
 * Notes:
 * - `retry: false` by default. 401 responses are handled by the axios interceptor
 *   (silent refresh + retry) and should not be retried at the React Query layer.
 * - `refetchOnWindowFocus: false` to avoid surprising refetches while the user
 *   is interacting with stale data.
 * - `staleTime` is 60 seconds. Long enough to feel snappy, short enough to
 *   pick up server-side changes.
 */
export const queryConfig = {
  queries: {
    refetchOnWindowFocus: false,
    retry: false as const,
    staleTime: 1000 * 60,
  },
} satisfies DefaultOptions;

export type ApiFnReturnType<FnType extends (...args: never[]) => Promise<unknown>> =
  Awaited<ReturnType<FnType>>;

export type QueryConfig<
  QueryFnType extends (...args: never[]) => Promise<unknown>,
> = Omit<
  UseQueryOptions<ApiFnReturnType<QueryFnType>, Error>,
  "queryKey" | "queryFn"
>;

export type MutationConfig<
  MutationFnType extends (...args: never[]) => Promise<unknown>,
> = UseMutationOptions<
  ApiFnReturnType<MutationFnType>,
  Error,
  Parameters<MutationFnType>[0]
>;