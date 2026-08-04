import type { UseMutationOptions, UseQueryOptions } from "@tanstack/react-query";

/**
 * Type helpers for query/mutation config passed into our hooks.
 * The runtime QueryClient is configured in `providers/app-providers.tsx`
 * to keep SSR/CSR concerns in one place.
 */
export type ApiFnReturnType<FnType extends (...args: never[]) => Promise<unknown>> = Awaited<
  ReturnType<FnType>
>;

export type QueryConfig<QueryFnType extends (...args: never[]) => Promise<unknown>> = Omit<
  UseQueryOptions<ApiFnReturnType<QueryFnType>, Error>,
  "queryKey" | "queryFn"
>;

export type MutationConfig<MutationFnType extends (...args: never[]) => Promise<unknown>> =
  UseMutationOptions<ApiFnReturnType<MutationFnType>, Error, Parameters<MutationFnType>[0]>;
