import { LandingRedirectGuard } from "@/features/auth/components/landing-redirect-guard";
import { LandingContent } from "@/features/landing/components/landing-content";

export default function LandingPage() {
  return (
    <div className="flex w-full flex-col font-sans">
      <LandingRedirectGuard />
      <LandingContent />
    </div>
  );
}
