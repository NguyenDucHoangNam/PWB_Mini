import { Suspense } from "react";
import { redirect } from "next/navigation";
import { getTranslations } from "next-intl/server";
import { OtpForm } from "@/features/auth/components/otp-form";

export async function generateMetadata({
  params,
}: {
  params: Promise<{ locale: string }>;
}) {
  const { locale } = await params;
  const t = await getTranslations({ locale, namespace: "auth.otp" });
  return {
    title: t("metaTitle"),
    description: t("metaDescription"),
  };
}

export default async function VerifyOtpPage({
  searchParams,
}: {
  searchParams: Promise<{ userId?: string }>;
}) {
  const { userId } = await searchParams;
  if (!userId || userId.trim().length === 0) {
    redirect("/register");
  }

  return (
    <Suspense
      fallback={
        <div className="flex justify-center py-12">
          <span className="size-6 animate-spin rounded-full border-4 border-neutral-200 border-t-neutral-500" />
        </div>
      }
    >
      <OtpForm />
    </Suspense>
  );
}
