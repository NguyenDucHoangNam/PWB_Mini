"use client";

import { useState, useEffect, useRef } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useTranslations } from "next-intl";
import { MobileDrawer } from "./mobile-drawer";
import { Button } from "@/components/ui/button";
import { useAuthStore } from "@/features/auth/stores/use-auth-store";
import { useLogout } from "@/features/auth/api/account";
import { LocaleSwitcher } from "./locale-switcher";
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

  // Handle Hydration mismatch
  useEffect(() => {
    setIsMounted(true);
  }, []);

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
    logoutMutate(undefined, {
      onSuccess: (res) => {
        if (res.success) {
          toast.success(t("logout") + " " + (res.message || "thành công"));
        }
        setShowDropdown(false);
        setIsOpen(false);
        router.push("/login");
      },
      onError: () => {
        useAuthStore.getState().clearAuth();
        toast.success(t("logout") + " thành công");
        setShowDropdown(false);
        setIsOpen(false);
        router.push("/login");
      },
    });
  };

  const menuItems = [
    { label: t("home"), href: "/" },
    { label: t("features"), href: "/#features" },
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
              ? "text-black dark:text-white border-b-2 border-black dark:border-white pb-1"
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
      <div className="mx-auto flex h-16 w-full max-w-7xl items-center justify-between px-6 md:px-8">
        {/* Left: Logo */}
        <Link href="/" className="text-xl font-bold tracking-tight text-black dark:text-white">
          PWB MiNi
        </Link>

        {/* Center: Navigation Links (Desktop) */}
        <nav className="hidden items-center gap-6 lg:flex">{isMounted && renderNavLinks()}</nav>

        {/* Right: Actions (Desktop) */}
        <div className="hidden items-center gap-4 lg:flex">
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
                    <Button variant="ghost" size="sm">
                      {t("login")}
                    </Button>
                  </Link>
                  <Link href="/register">
                    <Button variant="default" size="sm">
                      {t("register")}
                    </Button>
                  </Link>
                </>
              )}
            </>
          )}
        </div>

        {/* Right: Hamburger (Mobile) */}
        <button
          onClick={() => setIsOpen(true)}
          type="button"
          aria-label="Open menu"
          className="flex size-10 items-center justify-center rounded-lg border border-transparent text-neutral-500 hover:bg-neutral-100 hover:text-black lg:hidden dark:text-neutral-400 dark:hover:bg-neutral-800 dark:hover:text-white"
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

      {/* Mobile Drawer */}
      <MobileDrawer isOpen={isOpen} onClose={() => setIsOpen(false)}>
        <div className="flex flex-col gap-4">
          <div className="flex items-center justify-between">
            <Link
              href="/"
              onClick={() => setIsOpen(false)}
              className="text-lg font-bold tracking-tight text-black dark:text-white"
            >
              PWB MiNi
            </Link>
            <LocaleSwitcher />
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
                  className="text-base text-neutral-700 hover:text-black dark:text-neutral-300 dark:hover:text-white"
                >
                  {t("dashboard")}
                </Link>
                <Link
                  href="/rooms"
                  onClick={() => setIsOpen(false)}
                  className="text-base text-neutral-700 hover:text-black dark:text-neutral-300 dark:hover:text-white"
                >
                  {t("liveRooms")}
                </Link>
                <hr className="border-neutral-200 dark:border-neutral-800" />
                <Link
                  href="/profile"
                  onClick={() => setIsOpen(false)}
                  className="text-base text-neutral-700 hover:text-black dark:text-neutral-300 dark:hover:text-white"
                >
                  {t("profile")}
                </Link>
                <Link
                  href="/sessions"
                  onClick={() => setIsOpen(false)}
                  className="text-base text-neutral-700 hover:text-black dark:text-neutral-300 dark:hover:text-white"
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
                  className="text-base text-neutral-700 hover:text-black dark:text-neutral-300 dark:hover:text-white"
                >
                  {t("home")}
                </Link>
                <Link
                  href="/#features"
                  onClick={() => setIsOpen(false)}
                  className="text-base text-neutral-700 hover:text-black dark:text-neutral-300 dark:hover:text-white"
                >
                  {t("features")}
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
