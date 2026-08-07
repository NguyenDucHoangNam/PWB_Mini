import { SiteHeader } from "./site-header";
import { SiteFooter } from "./site-footer";

interface PublicLayoutProps {
  children: React.ReactNode;
}

export function PublicLayout({ children }: PublicLayoutProps) {
  return (
    <div className="flex min-h-screen flex-col bg-white dark:bg-black font-sans">
      <SiteHeader />
      {/* min-h keeps the content area a full viewport tall (minus the h-18 header) so a short
          page never lets the footer creep up into the fold. */}
      <main className="flex min-h-[calc(100dvh-4.5rem)] w-full flex-1 flex-col">{children}</main>
      <SiteFooter />
    </div>
  );
}
