"use client";

import Link from "next/link";
import { memo } from "react";
import { ChevronDown, LogOut } from "lucide-react";
import { useDropdownMenu, type DropdownItem } from "@/hooks/use-dropdown-menu";
import type { AuthUser } from "@/features/auth/stores/use-auth-store";
import { cn } from "@/lib/utils";

interface UserDropdownProps {
  user: AuthUser | null;
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

function UserDropdownImpl({ user, labels, items }: UserDropdownProps) {
  const { isOpen, focusedIndex, triggerRef, containerRef, onKeyDown, toggle, close } =
    useDropdownMenu(items);

  const initials = getInitials(user?.fullName || user?.email);
  const fullName = user?.fullName?.trim();
  const showFullName = !!fullName;
  const avatarUrl = user?.avatarUrl ?? null;
  const resolvedName = fullName || user?.email || labels.account;

  return (
    <div className="relative" ref={containerRef} onKeyDown={onKeyDown}>
      <button
        ref={triggerRef}
        onClick={toggle}
        type="button"
        className={cn(
          "group flex h-9 items-center gap-1.5 rounded-full border border-neutral-200 bg-white pl-0.5 pr-2 text-sm font-medium text-neutral-800 transition-all",
          "hover:border-neutral-300 hover:bg-neutral-50",
          "dark:border-neutral-800 dark:bg-neutral-950 dark:text-neutral-200 dark:hover:border-neutral-700 dark:hover:bg-neutral-900",
          "focus:outline-none focus-visible:ring-2 focus-visible:ring-black focus-visible:ring-offset-2 dark:focus-visible:ring-white",
          isOpen && "border-neutral-300 bg-neutral-50 dark:border-neutral-700 dark:bg-neutral-900",
        )}
        aria-label="User menu"
        aria-haspopup="menu"
        aria-expanded={isOpen}
      >
        <span className="flex size-8 items-center justify-center overflow-hidden rounded-full bg-neutral-100 text-xs font-bold text-neutral-700 dark:bg-neutral-800 dark:text-neutral-200">
          {avatarUrl ? (
            <img
              src={avatarUrl}
              alt={resolvedName}
              className="h-full w-full object-cover"
            />
          ) : (
            initials
          )}
        </span>
        <ChevronDown
          className={cn(
            "size-3.5 text-neutral-500 transition-transform duration-200",
            isOpen && "rotate-180 text-neutral-700 dark:text-neutral-300",
          )}
          aria-hidden="true"
        />
      </button>

      {isOpen && (
        <div
          role="menu"
          className="absolute right-0 z-50 mt-2 w-64 origin-top-right rounded-xl border border-neutral-200 bg-white p-1.5 shadow-xl shadow-neutral-900/5 ring-1 ring-black/5 dark:border-neutral-800 dark:bg-neutral-950 dark:shadow-black/40 animate-in fade-in zoom-in-95 slide-in-from-top-2 duration-150"
        >
          <div className="flex items-center gap-3 rounded-lg bg-neutral-50 px-3 py-2.5 dark:bg-neutral-900/60">
            <div className="flex size-9 shrink-0 items-center justify-center overflow-hidden rounded-full bg-white text-sm font-bold text-neutral-700 ring-1 ring-neutral-200 dark:bg-neutral-800 dark:text-neutral-200 dark:ring-neutral-700">
              {avatarUrl ? (
                <img
                  src={avatarUrl}
                  alt={resolvedName}
                  className="h-full w-full object-cover"
                />
              ) : (
                initials
              )}
            </div>
            <div className="min-w-0 flex-1">
              {showFullName && (
                <p className="truncate text-sm font-semibold text-neutral-900 dark:text-neutral-50">
                  {fullName}
                </p>
              )}
              <p
                className={cn(
                  "truncate text-xs text-neutral-500 dark:text-neutral-400",
                  showFullName && "mt-0.5",
                )}
              >
                {user?.email || labels.account}
              </p>
            </div>
          </div>

          <div className="my-1 h-px bg-neutral-100 dark:bg-neutral-800" />

          <div className="flex flex-col">
            {items.map((item, idx) => {
              const isFocused = idx === focusedIndex;
              const isDestructive = item.variant === "destructive";
              const isDisabled = item.disabled === true;
              const baseClass = cn(
                "flex w-full items-center gap-2.5 rounded-md px-2.5 py-2 text-sm transition-colors",
                "focus:outline-none",
                isDestructive
                  ? "text-neutral-700 hover:bg-red-50 hover:text-red-600 dark:text-neutral-300 dark:hover:bg-red-950/40 dark:hover:text-red-400"
                  : "text-neutral-700 hover:bg-neutral-100 hover:text-neutral-900 dark:text-neutral-200 dark:hover:bg-neutral-800 dark:hover:text-neutral-50",
                isFocused &&
                  !isDestructive &&
                  "bg-neutral-100 text-neutral-900 dark:bg-neutral-800 dark:text-neutral-50",
                isFocused &&
                  isDestructive &&
                  "bg-red-50 text-red-600 dark:bg-red-950/40 dark:text-red-400",
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
