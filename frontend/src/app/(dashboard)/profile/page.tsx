"use client";

import { useState } from "react";
import { useTranslations } from "next-intl";
import { ProfileSettingsLayout } from "@/features/auth/components/profile-settings-layout";
import { DeleteAccountDialog } from "@/features/auth/components/delete-account-dialog";
import { Button } from "@/components/ui/button";

export default function ProfilePage() {
  const t = useTranslations("profile");
  const [isDeleteOpen, setIsDeleteOpen] = useState(false);

  return (
    <div className="flex flex-col gap-8 font-sans">
      <div className="flex flex-col gap-2">
        <h1 className="text-2xl font-bold tracking-tight text-black dark:text-white">
          {t("title")}
        </h1>
        <p className="text-sm text-neutral-500 dark:text-neutral-400">{t("subtitle")}</p>
      </div>

      {/* Main Settings Panel */}
      <ProfileSettingsLayout />

      {/* Danger Zone panel */}
      <div className="border border-neutral-200 dark:border-neutral-800 bg-white dark:bg-black rounded-xl p-6 sm:p-8 mt-4">
        <h2 className="text-xl font-bold tracking-tight text-black dark:text-white mb-2">
          {t("dangerZone.title")}
        </h2>
        <p className="text-sm text-neutral-500 dark:text-neutral-400 mb-6 max-w-2xl">
          {t("dangerZone.description")}
        </p>
        <Button
          onClick={() => setIsDeleteOpen(true)}
          variant="outline"
          className="border-neutral-200 text-black hover:bg-neutral-50 hover:text-black dark:border-neutral-800 dark:text-white dark:hover:bg-neutral-900"
        >
          {t("dangerZone.button")}
        </Button>
      </div>

      {/* Delete Confirmation Modal */}
      <DeleteAccountDialog isOpen={isDeleteOpen} onClose={() => setIsDeleteOpen(false)} />
    </div>
  );
}
