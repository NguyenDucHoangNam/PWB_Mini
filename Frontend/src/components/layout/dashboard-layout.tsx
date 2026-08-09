import { SiteHeader } from "./site-header";
import { SiteFooter } from "./site-footer";

interface DashboardLayoutProps {
  children: React.ReactNode;
}

export function DashboardLayout({ children }: DashboardLayoutProps) {
  return (
    // The matte surface belongs to the whole shell, not to a card floating on white:
    // soft UI only reads when the plane behind a control is the same colour as the control.
    <div className="flex min-h-dvh flex-col bg-[#e0e5ec] dark:bg-[#1e222b] font-sans transition-colors">
      <SiteHeader />
      {/* min-h keeps the content area a full viewport tall (minus the h-18 header) so a short
          page never lets the footer creep up into the fold. */}
      <main className="flex min-h-[calc(100dvh-4.5rem)] w-full flex-1 flex-col py-6 sm:py-8">
        <div className="mx-auto flex w-full max-w-7xl flex-1 flex-col px-4 sm:px-6 md:px-8">
          {children}
        </div>
      </main>
      <SiteFooter />
    </div>
  );
}
