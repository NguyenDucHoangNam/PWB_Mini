"use client";

import { useState, useEffect, useCallback, useSyncExternalStore } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { useQueryClient } from "@tanstack/react-query";
import { MobileDrawer } from "./mobile-drawer";
import { UserDropdown } from "./user-dropdown";
import { LocaleSwitcher } from "./locale-switcher";
import { Button } from "@/components/ui/button";
import { useAuthStore } from "@/features/auth/stores/use-auth-store";
import { useProGuard } from "@/features/auth/hooks/use-pro-guard";
import { useLogout } from "@/features/auth/api/account";
import { abortRefresh } from "@/lib/auth-refresh";
import { useAuthChannelSync, broadcastAuthMessage } from "@/lib/use-auth-channel";
import { isPublicPath } from "@/lib/config";
import { ThemeToggle } from "@/components/theme-toggle";
import { toast } from "sonner";

const subscribeMounted = (callback: () => void) => {
  if (typeof window === "undefined") return () => {};
  window.addEventListener("load", callback);
  return () => window.removeEventListener("load", callback);
};

const getMountedSnapshot = () => true;
const getServerSnapshot = () => false;

interface NavItem {
  href: string;
  label: string;
}

export function SiteHeaderClient() {
  const t = useTranslations("header");
  const [isOpen, setIsOpen] = useState(false);
  const isMounted = useSyncExternalStore(subscribeMounted, getMountedSnapshot, getServerSnapshot);
  const pathname = usePathname();
  const router = useRouter();

  const user = useAuthStore((state) => state.user);
  const accessToken = useAuthStore((state) => state.accessToken);
  const isLoggedIn = !!accessToken;

  const { mutate: logoutMutate } = useLogout();
  const queryClient = useQueryClient();
  const { isPro } = useProGuard();

  useAuthChannelSync();

  useEffect(() => {
    if (typeof window === "undefined") return;

    if (!isLoggedIn) {
      const activeLogout = sessionStorage.getItem("active_logout") === "true";
      if (activeLogout) {
        sessionStorage.removeItem("active_logout");
        toast.success(t("logoutSuccess"));
        if (!isPublicPath(pathname)) {
          router.push("/login");
        }
      } else {
        if (!isPublicPath(pathname)) {
          router.push("/login");
          toast.info(t("sessionExpired"));
        }
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isLoggedIn]);

  const handleLogout = useCallback(() => {
    abortRefresh();
    if (typeof window !== "undefined") {
      sessionStorage.setItem("active_logout", "true");
    }

    const finalize = () => {
      queryClient.clear();
      broadcastAuthMessage({ type: "LOGOUT" });
      setIsOpen(false);
      router.push("/login");
    };

    logoutMutate(undefined, {
      onSuccess: () => {
        finalize();
      },
      onError: () => {
        finalize();
        useAuthStore.getState().clearAuth();
      },
    });
  }, [logoutMutate, queryClient, router]);

  const publicItems: NavItem[] = [
    { label: t("home"), href: "/" },
    { label: t("features"), href: "/features" },
    { label: t("contact"), href: "/contact" },
  ];

  const loggedInItems: NavItem[] = [
    { label: t("dashboard"), href: "/dashboard" },
    { label: t("liveRooms"), href: "/rooms" },
  ];

  const proItems: NavItem[] = [
    { label: t("voiceTags"), href: "/voice-tags" },
    { label: t("songs"), href: "/songs" },
  ];

  const dropdownItems = [
    { label: t("logout"), onSelect: handleLogout },
  ];

  return (
    <header className="sticky top-0 z-40 w-full border-b border-neutral-200 bg-white/80 backdrop-blur-md dark:border-neutral-800 dark:bg-black/80">
      <div className="mx-auto flex h-16 w-full max-w-7xl items-center justify-between gap-3 px-4 sm:px-6 md:px-8 relative">
        <div className="flex items-center gap-4 sm:gap-6 lg:gap-8 min-w-0">
          <Link href="/" className="shrink-0 px-3 py-0.5 border-2 border-black dark:border-white">
            <span className="text-xl sm:text-2xl font-bold tracking-tight text-black dark:text-white">
              PWB
            </span>
          </Link>
          <nav className="hidden items-center gap-6 xl:flex">
            {isMounted && <DesktopNav items={publicItems} pathname={pathname} />}
          </nav>
        </div>

        <HeaderSignature />

        <div className="flex shrink-0 items-center gap-3 sm:gap-6">
          <div className="hidden items-center gap-4 sm:gap-6 xl:flex">
            {isMounted && isLoggedIn && (
              <nav className="flex items-center gap-6 mr-4">
                <DesktopNav items={loggedInItems} pathname={pathname} />
                {isPro && <DesktopNav items={proItems} pathname={pathname} />}
              </nav>
            )}
            <ThemeToggle />
            <LocaleSwitcher />
            {isMounted && (
              isLoggedIn ? (
                <UserDropdown
                  user={user}
                  labels={{
                    logout: t("logout"),
                    account: t("account"),
                  }}
                  items={dropdownItems}
                />
              ) : (
                <GuestActions
                  loginLabel={t("login")}
                  registerLabel={t("register")}
                />
              )
            )}
          </div>

          <div className="flex items-center gap-1 xl:hidden">
            <ThemeToggle />
            <LocaleSwitcher />
          </div>

          <button
            onClick={() => setIsOpen(true)}
            type="button"
            aria-label="Open menu"
            className="flex size-11 items-center justify-center rounded-lg border border-transparent text-neutral-500 hover:bg-neutral-100 hover:text-black xl:hidden dark:text-neutral-400 dark:hover:bg-neutral-800 dark:hover:text-white"
          >
            <svg
              className="size-6"
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
              strokeWidth="2"
            >
              <path strokeLinecap="round" strokeLinejoin="round" d="M4 6h16M4 12h16M4 18h16" />
            </svg>
          </button>
        </div>
      </div>

      <MobileDrawer isOpen={isOpen} onClose={() => setIsOpen(false)}>
        <div className="flex flex-col gap-4">
          <Link
            href="/"
            onClick={() => setIsOpen(false)}
            className="px-3 py-1 border-2 border-black dark:border-white inline-block w-fit"
          >
            <span className="text-lg font-bold tracking-tight text-black dark:text-white">
              PWB
            </span>
          </Link>
          <hr className="border-neutral-200 dark:border-neutral-800" />
          {isMounted && (
            isLoggedIn ? (
              <MobileAuthenticated
                user={user}
                isPro={isPro}
                proLabels={{
                  voiceTags: t("voiceTags"),
                  songs: t("songs"),
                }}
                labels={{
                  dashboard: t("dashboard"),
                  liveRooms: t("liveRooms"),
                  logout: t("logout"),
                  account: t("account"),
                }}
                onLogout={handleLogout}
                onNavigate={() => setIsOpen(false)}
              />
            ) : (
              <MobileGuest
                labels={{
                  home: t("home"),
                  features: t("features"),
                  contact: t("contact"),
                  login: t("login"),
                  register: t("register"),
                }}
                items={publicItems}
                onNavigate={() => setIsOpen(false)}
              />
            )
          )}
        </div>
      </MobileDrawer>
    </header>
  );
}

function DesktopNav({ items, pathname }: { items: NavItem[]; pathname: string }) {
  return (
    <>
      {items.map((item) => {
        const isActive = pathname === item.href;
        return (
          <Link
            key={item.href}
            href={item.href}
            className={`text-sm font-medium transition-colors hover:text-black dark:hover:text-white ${
              isActive
                ? "text-black dark:text-white border-b-2 border-black dark:border-white pb-0.5"
                : "text-neutral-500 dark:text-neutral-400"
            }`}
          >
            {item.label}
          </Link>
        );
      })}
    </>
  );
}

function HeaderSignature() {
  return (
    <div className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 hidden xl:flex items-center gap-4 pointer-events-none select-none">
      <div className="h-[1px] w-12 bg-gradient-to-r from-transparent to-neutral-400/40 dark:to-neutral-400/30" />
      <span className="font-mono text-[11px] uppercase tracking-[0.35em] text-neutral-400 opacity-45 dark:text-neutral-400 dark:opacity-30 transition-colors">
        NAM IN THE MIX
      </span>
      <div className="h-[1px] w-12 bg-gradient-to-r from-neutral-400/40 dark:from-neutral-400/30 to-transparent" />
    </div>
  );
}

function GuestActions({ loginLabel, registerLabel }: { loginLabel: string; registerLabel: string }) {
  return (
    <>
      <Link href="/login">
        <Button variant="ghost" className="h-9 text-sm px-4">
          {loginLabel}
        </Button>
      </Link>
      <Link href="/register">
        <Button variant="default" className="h-9 text-sm px-4">
          {registerLabel}
        </Button>
      </Link>
    </>
  );
}

interface MobileMenuLabels {
  dashboard: string;
  liveRooms: string;
  logout: string;
  account: string;
}

interface ProLabels {
  voiceTags: string;
  songs: string;
}

function MobileAuthenticated({
  user,
  isPro,
  proLabels,
  labels,
  onLogout,
  onNavigate,
}: {
  user: ReturnType<typeof useAuthStore.getState>["user"];
  isPro: boolean;
  proLabels: ProLabels;
  labels: MobileMenuLabels;
  onLogout: () => void;
  onNavigate: () => void;
}) {
  const linkClass =
    "rounded-lg px-3 py-2.5 text-base text-neutral-700 hover:bg-neutral-100 hover:text-black dark:text-neutral-300 dark:hover:bg-neutral-800 dark:hover:text-white";
  return (
    <>
      <div className="px-2 py-1">
        <p className="text-sm font-bold text-black dark:text-white truncate">
          {user?.email || labels.account}
        </p>
      </div>
      <hr className="border-neutral-200 dark:border-neutral-800" />
      <Link href="/dashboard" onClick={onNavigate} className={linkClass}>
        {labels.dashboard}
      </Link>
      <Link href="/rooms" onClick={onNavigate} className={linkClass}>
        {labels.liveRooms}
      </Link>
      {isPro && (
        <>
          <Link href="/voice-tags" onClick={onNavigate} className={linkClass}>
            {proLabels.voiceTags}
          </Link>
          <Link href="/songs" onClick={onNavigate} className={linkClass}>
            {proLabels.songs}
          </Link>
        </>
      )}
      <hr className="border-neutral-200 dark:border-neutral-800" />
      <Button onClick={onLogout} variant="default" size="sm" className="w-full justify-center">
        {labels.logout}
      </Button>
    </>
  );
}

function MobileGuest({
  labels,
  items,
  onNavigate,
}: {
  labels: { home: string; features: string; contact: string; login: string; register: string };
  items: NavItem[];
  onNavigate: () => void;
}) {
  const linkClass =
    "rounded-lg px-3 py-2.5 text-base text-neutral-700 hover:bg-neutral-100 hover:text-black dark:text-neutral-300 dark:hover:bg-neutral-800 dark:hover:text-white";
  return (
    <>
      {items.map((item) => (
        <Link key={item.href} href={item.href} onClick={onNavigate} className={linkClass}>
          {item.label}
        </Link>
      ))}
      <hr className="border-neutral-200 dark:border-neutral-800" />
      <Link href="/login" onClick={onNavigate}>
        <Button variant="outline" size="sm" className="w-full justify-center">
          {labels.login}
        </Button>
      </Link>
      <Link href="/register" onClick={onNavigate}>
        <Button variant="default" size="sm" className="w-full justify-center">
          {labels.register}
        </Button>
      </Link>
    </>
  );
}
