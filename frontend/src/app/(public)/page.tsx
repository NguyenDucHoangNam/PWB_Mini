import { LandingRedirectGuard } from "@/features/auth/components/landing-redirect-guard";
import { LandingContentClient } from "@/features/landing/components/landing-content-client";

export default function LandingPage() {
  return (
    <div className="flex w-full flex-col font-sans">
      <LandingRedirectGuard />
      <LandingContentClient />
    </div>
  );
}
