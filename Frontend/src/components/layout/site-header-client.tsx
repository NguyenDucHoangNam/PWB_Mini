"use client";

import { useState, useEffect, useCallback, useSyncExternalStore } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { useQueryClient } from "@tanstack/react-query";
import { MobileDrawer } from "./mobile-drawer";
import { UserDropdown, LogOut } from "./user-dropdown";
import { LocaleSwitcher } from "./locale-switcher";
import { User as UserIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useAuthStore } from "@/features/auth/stores/use-auth-store";
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
  const tLiveroom = useTranslations("liveroom.nav");
  const [isOpen, setIsOpen] = useState(false);
  const isMounted = useSyncExternalStore(subscribeMounted, getMountedSnapshot, getServerSnapshot);
  const pathname = usePathname();
  const router = useRouter();

  const user = useAuthStore((state) => state.user);
  const accessToken = useAuthStore((state) => state.accessToken);
  const isLoggedIn = !!accessToken;

  const { mutate: logoutMutate } = useLogout();
  const queryClient = useQueryClient();

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

  const dropdownItems: Array<import("@/hooks/use-dropdown-menu").DropdownItem> = [
    {
      label: t("profile"),
      href: "/dashboard/profile",
      icon: <UserIcon aria-hidden="true" />,
    },
    {
      label: t("logout"),
      onSelect: handleLogout,
      icon: <LogOut aria-hidden="true" />,
      variant: "destructive",
    },
  ];

  return (
    <header className="sticky top-0 z-40 w-full bg-[#e0e5ec] dark:bg-[#1e222b] neu-raised border-b border-slate-300/60 dark:border-slate-800/80 shadow-neu-raised transition-colors">
      <div className="mx-auto flex h-20 w-full max-w-7xl items-center justify-between gap-3 px-4 sm:px-6 md:px-8 relative">
        <div className="flex items-center gap-4 sm:gap-6 lg:gap-8 min-w-0">
          <Link href="/" className="shrink-0 px-3 py-0.5 border-2 border-slate-900 dark:border-slate-100 focus-visible:outline-2 focus-visible:outline-indigo-600 focus-visible:outline-offset-2">
            <span className="text-2xl sm:text-3xl font-bold tracking-tight text-slate-900 dark:text-slate-100">
              PWB
            </span>
          </Link>
          <nav className="hidden items-center gap-4 md:flex">
            {isMounted && <DesktopNav items={publicItems} pathname={pathname} />}
          </nav>
        </div>

        <HeaderSignature />

        <div className="flex shrink-0 items-center gap-3 sm:gap-6">
          <div className="hidden items-center gap-4 sm:gap-6 lg:flex">
            {isMounted && isLoggedIn && (
              <nav className="flex items-center gap-4 mr-2">
                <DesktopNav
                  items={[
                    { label: t("dashboard"), href: "/dashboard/songs" },
                    { label: tLiveroom("liveroom"), href: "/dashboard/liveroom" },
                  ]}
                  pathname={pathname}
                />
              </nav>
            )}
            <ThemeToggle />
            <LocaleSwitcher />
            {isMounted &&
              (isLoggedIn ? (
                <UserDropdown
                  user={user}
                  labels={{
                    logout: t("logout"),
                    account: t("account"),
                    profile: t("profile"),
                  }}
                  items={dropdownItems}
                />
              ) : (
                <GuestActions loginLabel={t("login")} registerLabel={t("register")} />
              ))}
          </div>

          <div className="flex items-center gap-2 lg:hidden">
            <ThemeToggle />
            <LocaleSwitcher />
          </div>

          <button
            onClick={() => setIsOpen(true)}
            type="button"
            aria-label="Open menu"
            className="neu-button flex size-11 items-center justify-center rounded-2xl text-slate-700 focus-visible:outline-2 focus-visible:outline-indigo-600 focus-visible:outline-offset-2 lg:hidden dark:text-slate-200"
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
            className="px-3 py-1 border-2 border-slate-900 dark:border-slate-100 inline-block w-fit focus-visible:outline-2 focus-visible:outline-indigo-600 focus-visible:outline-offset-2"
          >
            <span className="text-lg font-bold tracking-tight text-slate-900 dark:text-slate-100">PWB</span>
          </Link>
          {isMounted &&
            (isLoggedIn ? (
              <MobileAuthenticated
                user={user}
                publicItems={publicItems}
                labels={{
                  dashboard: t("dashboard"),
                  profile: t("profile"),
                  logout: t("logout"),
                  account: t("account"),
                  liveroom: tLiveroom("liveroom"),
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
            ))}
        </div>
      </MobileDrawer>
    </header>
  );
}

const PLAYLIST_PREFIXES = ["/dashboard/songs", "/dashboard/voice-tags"];

function DesktopNav({ items, pathname }: { items: NavItem[]; pathname: string }) {
  return (
    <>
      {items.map((item) => {
        const isPlaylistLink = PLAYLIST_PREFIXES.some((p) => item.href.startsWith(p));
        const isActive = isPlaylistLink
          ? PLAYLIST_PREFIXES.some((p) => pathname.startsWith(p))
          : item.href === "/"
            ? pathname === "/"
            : pathname.startsWith(item.href);
        return (
          <Link
            key={item.href}
            href={item.href}
            className={`flex items-center gap-1.5 px-3.5 py-2 rounded-xl text-sm font-semibold transition-all focus-visible:outline-2 focus-visible:outline-indigo-600 focus-visible:outline-offset-2 ${
              isActive
                ? "neu-raised-sm text-indigo-600 dark:text-indigo-400 bg-[#e0e5ec] dark:bg-[#1e222b] font-bold"
                : "text-slate-600 hover:text-slate-900 dark:text-slate-300 dark:hover:text-slate-100"
            }`}
          >
            <span>{item.label}</span>
          </Link>
        );
      })}
    </>
  );
}

function HeaderSignature() {
  return (
    <div className="hidden min-w-0 flex-1 items-center justify-center gap-2 sm:gap-4 overflow-hidden pointer-events-none select-none xl:flex">
      <div className="h-[1px] flex-1 max-w-12 bg-gradient-to-r from-transparent to-slate-400/40 dark:to-slate-400/30" />
      <span className="shrink-0 whitespace-nowrap font-mono text-[11px] uppercase tracking-[0.35em] text-slate-500 opacity-60 dark:text-slate-400 dark:opacity-50 transition-colors">
        NAM IN THE MIX
      </span>
      <div className="h-[1px] flex-1 max-w-12 bg-gradient-to-r from-slate-400/40 dark:from-slate-400/30 to-transparent" />
    </div>
  );
}

function GuestActions({
  loginLabel,
  registerLabel,
}: {
  loginLabel: string;
  registerLabel: string;
}) {
  return (
    <div className="flex items-center gap-3">
      <Link href="/login">
        <button
          type="button"
          className="neu-button inline-flex h-10 items-center justify-center rounded-2xl px-5 text-sm font-semibold text-slate-700 focus-visible:outline-2 focus-visible:outline-indigo-600 focus-visible:outline-offset-2 dark:text-slate-200"
        >
          {loginLabel}
        </button>
      </Link>
      <Link href="/register">
        <button
          type="button"
          className="neu-button-primary inline-flex h-10 items-center justify-center rounded-2xl bg-indigo-600 hover:bg-indigo-700 text-white font-bold px-6 text-sm shadow-neu-raised-sm focus-visible:outline-2 focus-visible:outline-indigo-600 focus-visible:outline-offset-2 transition-all"
        >
          {registerLabel}
        </button>
      </Link>
    </div>
  );
}

interface MobileMenuLabels {
  dashboard: string;
  profile: string;
  logout: string;
  account: string;
  liveroom: string;
}

function MobileAuthenticated({
  user,
  publicItems,
  labels,
  onLogout,
  onNavigate,
}: {
  user: ReturnType<typeof useAuthStore.getState>["user"];
  publicItems: NavItem[];
  labels: MobileMenuLabels;
  onLogout: () => void;
  onNavigate: () => void;
}) {
  const linkClass =
    "neu-raised-sm block rounded-2xl bg-[#e0e5ec] px-4 py-3 text-base font-semibold text-slate-800 dark:bg-[#1e222b] dark:text-slate-200 hover:text-indigo-600 dark:hover:text-indigo-400 transition-all";
  return (
    <div className="flex flex-col gap-3">
      <div className="neu-pressed rounded-2xl bg-[#e0e5ec] px-4 py-3 dark:bg-[#1e222b]">
        <p className="text-sm font-bold text-slate-900 dark:text-slate-50 truncate">
          {user?.email || labels.account}
        </p>
      </div>
      {publicItems.map((item) => (
        <Link key={item.href} href={item.href} onClick={onNavigate} className={linkClass}>
          {item.label}
        </Link>
      ))}
      <Link href="/dashboard/songs" onClick={onNavigate} className={linkClass}>
        {labels.dashboard}
      </Link>
      <Link href="/dashboard/liveroom" onClick={onNavigate} className={linkClass}>
        {labels.liveroom}
      </Link>
      <Link href="/dashboard/profile" onClick={onNavigate} className={linkClass}>
        {labels.profile}
      </Link>
      <button
        type="button"
        onClick={onLogout}
        className="neu-button-primary mt-2 flex h-12 w-full items-center justify-center rounded-2xl bg-indigo-600 hover:bg-indigo-700 text-white font-bold text-base shadow-neu-raised-sm"
      >
        {labels.logout}
      </button>
    </div>
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
    "neu-raised-sm block rounded-2xl bg-[#e0e5ec] px-4 py-3 text-base font-semibold text-slate-800 dark:bg-[#1e222b] dark:text-slate-200 hover:text-indigo-600 dark:hover:text-indigo-400 transition-all";
  return (
    <div className="flex flex-col gap-3">
      {items.map((item) => (
        <Link key={item.href} href={item.href} onClick={onNavigate} className={linkClass}>
          {item.label}
        </Link>
      ))}
      <div className="mt-2 flex flex-col gap-3">
        <Link href="/login" onClick={onNavigate}>
          <button type="button" className="neu-button h-12 w-full rounded-2xl font-semibold text-slate-700 dark:text-slate-200">
            {labels.login}
          </button>
        </Link>
        <Link href="/register" onClick={onNavigate}>
          <button type="button" className="neu-button-primary h-12 w-full rounded-2xl bg-indigo-600 hover:bg-indigo-700 text-white font-bold shadow-neu-raised-sm">
            {labels.register}
          </button>
        </Link>
      </div>
    </div>
  );
}
