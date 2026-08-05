"use client";

import { useEffect, useSyncExternalStore } from "react";
import { useRouter } from "next/navigation";
import { Spinner } from "@/components/ui/spinner";
import { useAuthStore } from "@/features/auth/stores/use-auth-store";
import { useAuthChannelSync } from "@/lib/use-auth-channel";
import { persistReturnTo } from "@/hooks/use-return-to";

const subscribeAuthStore = (callback: () => void) => useAuthStore.subscribe(callback);
const getAccessTokenSnapshot = () => useAuthStore.getState().accessToken;
const getServerSnapshot = () => null;


export default function LiveroomLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  useAuthChannelSync();

  const accessToken = useSyncExternalStore(
    subscribeAuthStore,
    getAccessTokenSnapshot,
    getServerSnapshot,
  );
  const bootstrapping = useAuthStore((state) => state.bootstrapping);

  useEffect(() => {
    if (bootstrapping || accessToken) return;
    persistReturnTo(window.location.pathname + window.location.search);
    router.replace("/login");
  }, [bootstrapping, accessToken, router]);

  if (bootstrapping || !accessToken) {
    return (
      <div className="flex h-dvh items-center justify-center bg-white dark:bg-black">
        <Spinner size="sm" />
      </div>
    );
  }

  return (
    <div className="h-dvh overflow-hidden bg-white font-sans dark:bg-black">{children}</div>
  );
}