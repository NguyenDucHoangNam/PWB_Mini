import Link from "next/link";
import { useTranslations } from "next-intl";
import { Button } from "@/components/ui/button";
import { LandingRedirectGuard } from "@/features/auth/components/landing-redirect-guard";

export const metadata = {
  title: "PWB MiNi — Real-time Audio Collaboration Platform",
  description:
    "A secure real-time audio collaboration and demo sharing platform for music producers.",
};

export default function LandingPage() {
  const t = useTranslations("landing");

  return (
    <div className="flex w-full flex-col font-sans">
      <LandingRedirectGuard />
      {/* Hero Section */}
      <section className="flex min-h-[calc(100vh-64px)] w-full flex-col items-center justify-center border-b border-neutral-200 bg-white px-6 py-24 text-center dark:border-neutral-800 dark:bg-black">
        <div className="mx-auto flex w-full max-w-3xl flex-col items-center gap-8">
          <span className="rounded-full bg-neutral-100 px-3 py-1 text-xs font-semibold uppercase tracking-wider text-neutral-800 dark:bg-neutral-800 dark:text-neutral-200">
            {t("phase")}
          </span>
          <h1 className="text-5xl font-extrabold tracking-tight text-black sm:text-6xl dark:text-white">
            {t("title")}
          </h1>
          <p className="text-xl font-bold leading-normal text-neutral-800 dark:text-neutral-200">
            {t("subtitle")}
          </p>
          <p className="max-w-xl text-base leading-relaxed text-neutral-500 dark:text-neutral-400">
            {t("description")}
          </p>

          <div className="mt-4 flex flex-col gap-4 sm:flex-row">
            <Link href="/register">
              <Button variant="default" size="lg" className="w-full sm:w-auto h-12 px-8 text-base">
                {t("ctaStart")}
              </Button>
            </Link>
            <a href="#features">
              <Button variant="outline" size="lg" className="w-full sm:w-auto h-12 px-8 text-base">
                {t("ctaLearn")}
              </Button>
            </a>
          </div>
        </div>
      </section>

      {/* Feature Highlights Section */}
      <section
        id="features"
        className="w-full bg-neutral-50 px-6 py-24 dark:bg-neutral-950 scroll-mt-16"
      >
        <div className="mx-auto w-full max-w-7xl">
          <div className="mb-16 text-center">
            <h2 className="text-3xl font-bold tracking-tight text-black dark:text-white sm:text-4xl">
              {t("whyTitle")}
            </h2>
            <p className="mt-4 text-neutral-500 dark:text-neutral-400">
              {t("whySubtitle")}
            </p>
          </div>

          <div className="grid grid-cols-1 gap-8 md:grid-cols-3">
            {/* Card 1 */}
            <div className="flex flex-col gap-4 rounded-xl border border-neutral-200 bg-white p-8 shadow-sm dark:border-neutral-800 dark:bg-black">
              <div className="flex size-12 items-center justify-center rounded-lg bg-neutral-100 dark:bg-neutral-800">
                <svg
                  className="size-6 text-black dark:text-white"
                  fill="none"
                  viewBox="0 0 24 24"
                  stroke="currentColor"
                  strokeWidth="2"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z"
                  />
                </svg>
              </div>
              <h3 className="text-xl font-bold text-black dark:text-white">
                {t("feature1Title")}
              </h3>
              <p className="text-sm leading-relaxed text-neutral-500 dark:text-neutral-400">
                {t("feature1LongDesc")}
              </p>
            </div>

            {/* Card 2 */}
            <div className="flex flex-col gap-4 rounded-xl border border-neutral-200 bg-white p-8 shadow-sm dark:border-neutral-800 dark:bg-black">
              <div className="flex size-12 items-center justify-center rounded-lg bg-neutral-100 dark:bg-neutral-800">
                <svg
                  className="size-6 text-black dark:text-white"
                  fill="none"
                  viewBox="0 0 24 24"
                  stroke="currentColor"
                  strokeWidth="2"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9"
                  />
                </svg>
              </div>
              <h3 className="text-xl font-bold text-black dark:text-white">
                {t("feature2Title")}
              </h3>
              <p className="text-sm leading-relaxed text-neutral-500 dark:text-neutral-400">
                {t("feature2LongDesc")}
              </p>
            </div>

            {/* Card 3 */}
            <div className="flex flex-col gap-4 rounded-xl border border-neutral-200 bg-white p-8 shadow-sm dark:border-neutral-800 dark:bg-black">
              <div className="flex size-12 items-center justify-center rounded-lg bg-neutral-100 dark:bg-neutral-800">
                <svg
                  className="size-6 text-black dark:text-white"
                  fill="none"
                  viewBox="0 0 24 24"
                  stroke="currentColor"
                  strokeWidth="2"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z"
                  />
                </svg>
              </div>
              <h3 className="text-xl font-bold text-black dark:text-white">
                {t("feature3Title")}
              </h3>
              <p className="text-sm leading-relaxed text-neutral-500 dark:text-neutral-400">
                {t("feature3LongDesc")}
              </p>
            </div>
          </div>
        </div>
      </section>
    </div>
  );
}
