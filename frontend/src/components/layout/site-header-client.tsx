"use client";

import { useState, useEffect, useRef } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { MobileDrawer } from "./mobile-drawer";
import { Button } from "@/components/ui/button";
import { useAuthStore } from "@/features/auth/stores/use-auth-store";
import { useLogout } from "@/features/auth/api/account";
import { abortRefresh } from "@/lib/auth-refresh";
import { useAuthChannelSync, broadcastAuthMessage } from "@/lib/use-auth-channel";
import { isPublicPath } from "@/lib/config";
import { LocaleSwitcher } from "./locale-switcher";
import { ThemeToggle } from "@/components/theme-toggle";
import { toast } from "sonner";

export function SiteHeaderClient() {
  const t = useTranslations("header");
  const [isOpen, setIsOpen] = useState(false);
  const [isMounted, setIsMounted] = useState(false);
  const [showDropdown, setShowDropdown] = useState(false);
  const [focusedIndex, setFocusedIndex] = useState(-1);
  const dropdownRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const pathname = usePathname();
  const router = useRouter();

  const user = useAuthStore((state) => state.user);
  const accessToken = useAuthStore((state) => state.accessToken);
  const isLoggedIn = !!accessToken;

  const { mutate: logoutMutate } = useLogout();

  // Cross-tab auth sync (singleton BroadcastChannel).
  useAuthChannelSync();

  // Handle Hydration mismatch
  useEffect(() => {
    setIsMounted(true);
  }, []);

  // On cross-tab LOGOUT, redirect to /login (useAuthChannelSync already
  // clears the auth store). Skip redirect if on a public path.
  useEffect(() => {
    if (typeof window === "undefined") return;
    if (!isLoggedIn && !isPublicPath(pathname)) {
      router.push("/login");
      toast.info(t("sessionExpired"));
    }
    // We intentionally only react to isLoggedIn flipping to false.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isLoggedIn]);

  // Close dropdown on click outside
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setShowDropdown(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  const handleLogout = () => {
    abortRefresh();

    logoutMutate(undefined, {
      onSuccess: () => {
        broadcastAuthMessage({ type: "LOGOUT" });
        setShowDropdown(false);
        setIsOpen(false);
        router.push("/login");
      },
      onError: () => {
        // Force clear auth even if API fails.
        useAuthStore.getState().clearAuth();
        broadcastAuthMessage({ type: "LOGOUT" });
        toast.success(t("logout"));
        setShowDropdown(false);
        setIsOpen(false);
        router.push("/login");
      },
    });
  };

  const menuItems = [
    { label: t("home"), href: "/" },
    { label: t("features"), href: "/features" },
    { label: t("contact"), href: "/contact" },
  ];

  const loggedInMenuItems = [
    { label: t("dashboard"), href: "/dashboard" },
    { label: t("liveRooms"), href: "/rooms" },
  ];

  const dropdownMenuItems = [
    { label: t("profile"), href: "/profile" },
    { label: t("sessions"), href: "/sessions" },
    { label: t("logout"), onClick: handleLogout },
  ];

  // Keyboard navigation handler for accessibility
  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (!showDropdown) return;

    if (e.key === "Escape") {
      setShowDropdown(false);
      triggerRef.current?.focus();
    } else if (e.key === "ArrowDown") {
      e.preventDefault();
      setFocusedIndex((prev) => (prev + 1) % dropdownMenuItems.length);
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      setFocusedIndex((prev) => (prev - 1 + dropdownMenuItems.length) % dropdownMenuItems.length);
    } else if (e.key === "Enter") {
      e.preventDefault();
      if (focusedIndex >= 0 && focusedIndex < dropdownMenuItems.length) {
        const activeItem = dropdownMenuItems[focusedIndex];
        if (activeItem.href) {
          router.push(activeItem.href);
          setShowDropdown(false);
        } else if (activeItem.onClick) {
          activeItem.onClick();
        }
      }
    }
  };

  // Reset focus index when dropdown opens/closes
  useEffect(() => {
    if (showDropdown) {
      setFocusedIndex(-1);
    }
  }, [showDropdown]);

  const renderNavLinks = () => {
    const items = isLoggedIn ? loggedInMenuItems : menuItems;
    return items.map((item) => {
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
    });
  };

  const getInitials = () => {
    if (!user?.fullName) return "?";
    return user.fullName.charAt(0).toUpperCase();
  };

  return (
    <header className="sticky top-0 z-40 w-full border-b border-neutral-200 bg-white/80 backdrop-blur-md dark:border-neutral-800 dark:bg-black/80">
      <div className="mx-auto flex h-16 w-full max-w-7xl items-center justify-between gap-3 px-4 sm:px-6 md:px-8 relative">
        {/* Left: Logo & Navigation */}
        <div className="flex items-center gap-4 sm:gap-6 lg:gap-8 min-w-0">
          <Link href="/" className="shrink-0 px-3 py-0.5 border-2 border-black dark:border-white">
            <span className="text-xl sm:text-2xl font-bold tracking-tight text-black dark:text-white">
              PWB
            </span>
          </Link>
          <nav className="hidden items-center gap-6 xl:flex">{isMounted && renderNavLinks()}</nav>
        </div>

        {/* Center: Signature (Desktop) */}
        <div className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 hidden xl:flex items-center gap-4 pointer-events-none select-none">
          {/* Left Line */}
          <div className="h-[1px] w-12 bg-gradient-to-r from-transparent to-neutral-400/40 dark:to-neutral-400/30" />

          {/* Text */}
          <span className="font-mono text-[11px] uppercase tracking-[0.35em] text-neutral-400 opacity-45 dark:text-neutral-400 dark:opacity-30 transition-colors">
            NAM IN THE MIX
          </span>

          {/* Right Line */}
          <div className="h-[1px] w-12 bg-gradient-to-r from-neutral-400/40 dark:from-neutral-400/30 to-transparent" />
        </div>

        {/* Right: Actions (Desktop) & Hamburger (Mobile) */}
        <div className="flex shrink-0 items-center gap-3 sm:gap-6">
          <div className="hidden items-center gap-4 sm:gap-6 xl:flex">
            <ThemeToggle />
            <LocaleSwitcher />
            {isMounted && (
              <>
                {isLoggedIn ? (
                  <div className="relative" ref={dropdownRef} onKeyDown={handleKeyDown}>
                    <button
                      ref={triggerRef}
                      onClick={() => setShowDropdown(!showDropdown)}
                      type="button"
                      className="flex size-9 items-center justify-center rounded-full bg-neutral-100 border border-neutral-200 text-sm font-bold text-neutral-800 hover:bg-neutral-200 dark:bg-neutral-800 dark:border-neutral-700 dark:text-neutral-200 dark:hover:bg-neutral-700 focus:outline-none focus:ring-2 focus:ring-black dark:focus:ring-white focus:ring-offset-2"
                      aria-label="User menu"
                      aria-haspopup="menu"
                      aria-expanded={showDropdown}
                    >
                      {getInitials()}
                    </button>

                    {/* Dropdown Menu */}
                    {showDropdown && (
                      <div
                        role="menu"
                        className="absolute right-0 mt-2 w-52 rounded-lg border border-neutral-200 bg-white py-1 shadow-lg dark:border-neutral-800 dark:bg-neutral-950 animate-in fade-in slide-in-from-top-2 duration-100"
                      >
                        <div className="px-4 py-2 border-b border-neutral-100 dark:border-neutral-800">
                          <p className="text-xs text-neutral-400 font-bold truncate">
                            {user?.fullName || t("account")}
                          </p>
                          <p className="truncate text-xs text-neutral-500 mt-0.5">
                            {user?.email || ""}
                          </p>
                        </div>

                        {dropdownMenuItems.map((item, idx) => {
                          const isFocused = idx === focusedIndex;
                          if (item.href) {
                            return (
                              <Link
                                key={item.href}
                                href={item.href}
                                role="menuitem"
                                className={`block px-4 py-2 text-sm text-neutral-700 dark:text-neutral-300 hover:bg-neutral-100 dark:hover:bg-neutral-800 focus:outline-none ${
                                  isFocused ? "bg-neutral-100 dark:bg-neutral-800" : ""
                                }`}
                                onClick={() => setShowDropdown(false)}
                              >
                                {item.label}
                              </Link>
                            );
                          } else {
                            return (
                              <button
                                key={item.label}
                                onClick={item.onClick}
                                type="button"
                                role="menuitem"
                                className={`w-full text-left block px-4 py-2 text-sm text-black dark:text-white hover:bg-neutral-100 dark:hover:bg-neutral-800 border-t border-neutral-100 dark:border-neutral-800 font-semibold focus:outline-none ${
                                  isFocused ? "bg-neutral-100 dark:bg-neutral-800" : ""
                                }`}
                              >
                                {item.label}
                              </button>
                            );
                          }
                        })}
                      </div>
                    )}
                  </div>
                ) : (
                  <>
                    <Link href="/login">
                      <Button variant="ghost" className="h-9 text-sm px-4">
                        {t("login")}
                      </Button>
                    </Link>
                    <Link href="/register">
                      <Button variant="default" className="h-9 text-sm px-4">
                        {t("register")}
                      </Button>
                    </Link>
                  </>
                )}
              </>
            )}
          </div>

          {/* Tablet & Mobile: Compact actions (theme & lang) + Hamburger */}
          <div className="flex items-center gap-1 xl:hidden">
            <ThemeToggle />
            <LocaleSwitcher />
          </div>

          {/* Hamburger (Mobile + Tablet) */}
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

      {/* Mobile Drawer */}
      <MobileDrawer isOpen={isOpen} onClose={() => setIsOpen(false)}>
        <div className="flex flex-col gap-4">
          <div className="flex items-center justify-between">
            <Link
              href="/"
              onClick={() => setIsOpen(false)}
              className="px-3 py-1 border-2 border-black dark:border-white"
            >
              <span className="text-lg font-bold tracking-tight text-black dark:text-white">
                PWB
              </span>
            </Link>
          </div>
          <hr className="border-neutral-200 dark:border-neutral-800" />
          {isMounted &&
            (isLoggedIn ? (
              <>
                <div className="px-2 py-1">
                  <p className="text-sm font-bold text-black dark:text-white truncate">
                    {user?.fullName || t("account")}
                  </p>
                  <p className="text-xs text-neutral-500 truncate mt-0.5">
                    {user?.email || ""}
                  </p>
                </div>
                <hr className="border-neutral-200 dark:border-neutral-800" />
                <Link
                  href="/dashboard"
                  onClick={() => setIsOpen(false)}
                  className="rounded-lg px-3 py-2.5 text-base text-neutral-700 hover:bg-neutral-100 hover:text-black dark:text-neutral-300 dark:hover:bg-neutral-800 dark:hover:text-white"
                >
                  {t("dashboard")}
                </Link>
                <Link
                  href="/rooms"
                  onClick={() => setIsOpen(false)}
                  className="rounded-lg px-3 py-2.5 text-base text-neutral-700 hover:bg-neutral-100 hover:text-black dark:text-neutral-300 dark:hover:bg-neutral-800 dark:hover:text-white"
                >
                  {t("liveRooms")}
                </Link>
                <hr className="border-neutral-200 dark:border-neutral-800" />
                <Link
                  href="/profile"
                  onClick={() => setIsOpen(false)}
                  className="rounded-lg px-3 py-2.5 text-base text-neutral-700 hover:bg-neutral-100 hover:text-black dark:text-neutral-300 dark:hover:bg-neutral-800 dark:hover:text-white"
                >
                  {t("profile")}
                </Link>
                <Link
                  href="/sessions"
                  onClick={() => setIsOpen(false)}
                  className="rounded-lg px-3 py-2.5 text-base text-neutral-700 hover:bg-neutral-100 hover:text-black dark:text-neutral-300 dark:hover:bg-neutral-800 dark:hover:text-white"
                >
                  {t("sessions")}
                </Link>
                <hr className="border-neutral-200 dark:border-neutral-800" />
                <Button
                  onClick={handleLogout}
                  variant="default"
                  size="sm"
                  className="w-full justify-center"
                >
                  {t("logout")}
                </Button>
              </>
            ) : (
              <>
                <Link
                  href="/"
                  onClick={() => setIsOpen(false)}
                  className="rounded-lg px-3 py-2.5 text-base text-neutral-700 hover:bg-neutral-100 hover:text-black dark:text-neutral-300 dark:hover:bg-neutral-800 dark:hover:text-white"
                >
                  {t("home")}
                </Link>
                <Link
                  href="/features"
                  onClick={() => setIsOpen(false)}
                  className="rounded-lg px-3 py-2.5 text-base text-neutral-700 hover:bg-neutral-100 hover:text-black dark:text-neutral-300 dark:hover:bg-neutral-800 dark:hover:text-white"
                >
                  {t("features")}
                </Link>
                <Link
                  href="/contact"
                  onClick={() => setIsOpen(false)}
                  className="rounded-lg px-3 py-2.5 text-base text-neutral-700 hover:bg-neutral-100 hover:text-black dark:text-neutral-300 dark:hover:bg-neutral-800 dark:hover:text-white"
                >
                  {t("contact")}
                </Link>
                <hr className="border-neutral-200 dark:border-neutral-800" />
                <Link href="/login" onClick={() => setIsOpen(false)}>
                  <Button variant="outline" size="sm" className="w-full justify-center">
                    {t("login")}
                  </Button>
                </Link>
                <Link href="/register" onClick={() => setIsOpen(false)}>
                  <Button variant="default" size="sm" className="w-full justify-center">
                    {t("register")}
                  </Button>
                </Link>
              </>
            ))}
        </div>
      </MobileDrawer>
    </header>
  );
}
