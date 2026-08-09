"use client";

import Link from "next/link";
import { memo } from "react";
import { ChevronDown, LogOut, Crown } from "lucide-react";
import { useDropdownMenu, type DropdownItem } from "@/hooks/use-dropdown-menu";
import { useAvatarUrl } from "@/features/profile/api/use-avatar-url";
import type { AuthUser } from "@/features/auth/stores/use-auth-store";
import { cn } from "@/lib/utils";

interface UserDropdownProps {
  user: AuthUser | null;
  isPro?: boolean;
  labels: {
    logout: string;
    account: string;
    profile: string;
  };
  items: DropdownItem[];
}

function getInitials(value: string | null | undefined): string {
  if (!value) return "?";
  const trimmed = value.trim();
  if (!trimmed) return "?";
  return trimmed.charAt(0).toUpperCase();
}

function UserDropdownImpl({ user, isPro = false, labels, items }: UserDropdownProps) {
  const { isOpen, focusedIndex, triggerRef, containerRef, onKeyDown, toggle, close } =
    useDropdownMenu(items);

  // Read from the profile query rather than the auth store: the URL is presigned and expires.
  const { avatarUrl, onImageError } = useAvatarUrl();

  const initials = getInitials(user?.fullName || user?.email);
  const fullName = user?.fullName?.trim();
  const showFullName = !!fullName;
  const resolvedName = fullName || user?.email || labels.account;

  return (
    <div className="relative" ref={containerRef} onKeyDown={onKeyDown}>
      <button
        ref={triggerRef}
        onClick={toggle}
        type="button"
        className={cn(
          "neu-button group relative flex items-center gap-2 rounded-full p-1.5 transition-all bg-[#e0e5ec] dark:bg-[#1e222b] border-none focus-visible:outline-2 focus-visible:outline-indigo-600 focus-visible:outline-offset-2",
          isOpen && "neu-pressed",
        )}
        aria-label="User menu"
        aria-haspopup="menu"
        aria-expanded={isOpen}
      >
        <div
          className={cn(
            "neu-pressed relative flex size-9 items-center justify-center rounded-full p-0.5 transition-all bg-[#e0e5ec] dark:bg-[#1e222b]",
            isPro && "ring-2 ring-indigo-500/80 dark:ring-indigo-400/80",
          )}
        >
          <div className="flex size-full items-center justify-center overflow-hidden rounded-full bg-[#e0e5ec] text-xs font-bold text-slate-800 dark:bg-[#1e222b] dark:text-slate-100">
            {avatarUrl ? (
              <img
                src={avatarUrl}
                alt={resolvedName}
                onError={onImageError}
                className="h-full w-full object-cover"
              />
            ) : (
              initials
            )}
          </div>
          {isPro && (
            <span className="neu-raised absolute -top-1 -right-1 z-10 flex size-4 items-center justify-center rounded-full bg-[#e0e5ec] text-indigo-600 dark:bg-[#1e222b] dark:text-indigo-400">
              <Crown className="size-2.5" aria-hidden="true" />
            </span>
          )}
        </div>
        <ChevronDown
          className={cn(
            "size-3.5 text-slate-500 transition-transform duration-200 group-hover:text-slate-800 dark:text-slate-400 dark:group-hover:text-slate-200 ml-0.5 pr-0.5",
            isOpen && "rotate-180 text-indigo-600 dark:text-indigo-400",
          )}
          aria-hidden="true"
        />
      </button>

      {isOpen && (
        <div
          role="menu"
          className="neu-raised absolute right-0 z-50 mt-3 w-64 origin-top-right rounded-3xl bg-[#e0e5ec] p-3 dark:bg-[#1e222b] border-none shadow-neu-raised animate-in fade-in zoom-in-95 duration-150"
        >
          <div className="neu-pressed flex items-center gap-3 rounded-2xl bg-[#e0e5ec] p-3 dark:bg-[#1e222b] border-none">
            <div
              className={cn(
                "relative flex size-9 shrink-0 items-center justify-center overflow-hidden rounded-full bg-[#e0e5ec] text-sm font-bold text-slate-700 dark:bg-[#1e222b] dark:text-slate-200",
                isPro && "ring-2 ring-indigo-500/80",
              )}
            >
              {avatarUrl ? (
                <img
                  src={avatarUrl}
                  alt={resolvedName}
                  onError={onImageError}
                  className="h-full w-full object-cover"
                />
              ) : (
                initials
              )}
            </div>
            <div className="min-w-0 flex-1">
              <div className="flex items-center gap-1.5">
                {showFullName && (
                  <p className="truncate text-sm font-bold text-slate-900 dark:text-slate-50">
                    {fullName}
                  </p>
                )}
                {isPro && (
                  <span className="neu-raised-sm inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-bold text-indigo-600 dark:text-indigo-400">
                    <Crown className="size-2.5" aria-hidden="true" /> PRO
                  </span>
                )}
              </div>
              <p
                className={cn(
                  "truncate text-xs font-medium text-slate-500 dark:text-slate-400",
                  showFullName && "mt-0.5",
                )}
              >
                {user?.email || labels.account}
              </p>
            </div>
          </div>

          <div className="my-2 flex flex-col gap-1.5">
            {items.map((item, idx) => {
              const isFocused = idx === focusedIndex;
              const isDestructive = item.variant === "destructive";
              const isDisabled = item.disabled === true;
              const baseClass = cn(
                "neu-raised-sm flex w-full items-center gap-2.5 rounded-xl p-2.5 text-sm font-semibold transition-all focus-visible:outline-2 focus-visible:outline-indigo-600 focus-visible:outline-offset-2 bg-[#e0e5ec] dark:bg-[#1e222b]",
                isDestructive
                  ? "text-rose-600 dark:text-rose-400"
                  : "text-slate-700 dark:text-slate-200 hover:text-indigo-600 dark:hover:text-indigo-400",
                isFocused && "neu-pressed text-indigo-600 dark:text-indigo-400",
                isDisabled && "pointer-events-none opacity-50",
              );

              if (item.href) {
                return (
                  <Link
                    key={`${item.href}-${item.label}`}
                    href={item.href}
                    role="menuitem"
                    tabIndex={isDisabled ? -1 : 0}
                    aria-disabled={isDisabled}
                    className={baseClass}
                    onClick={() => {
                      item.onSelect?.();
                      close();
                    }}
                  >
                    {item.icon && (
                      <span className="flex size-4 shrink-0 items-center justify-center [&_svg]:size-4">
                        {item.icon}
                      </span>
                    )}
                    <span className="flex-1 truncate">{item.label}</span>
                  </Link>
                );
              }

              return (
                <button
                  key={item.label}
                  onClick={() => {
                    if (isDisabled) return;
                    item.onSelect?.();
                    close();
                  }}
                  type="button"
                  role="menuitem"
                  tabIndex={isDisabled ? -1 : 0}
                  aria-disabled={isDisabled}
                  className={baseClass}
                >
                  {item.icon && (
                    <span className="flex size-4 shrink-0 items-center justify-center [&_svg]:size-4">
                      {item.icon}
                    </span>
                  )}
                  <span className="flex-1 truncate text-left">{item.label}</span>
                </button>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}

export const UserDropdown = memo(UserDropdownImpl);

export { LogOut };
