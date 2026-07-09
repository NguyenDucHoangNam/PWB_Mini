import { SiteHeader } from "./site-header";
import { SiteFooter } from "./site-footer";

interface DashboardLayoutProps {
  children: React.ReactNode;
}

export function DashboardLayout({ children }: DashboardLayoutProps) {
  return (
    <div className="flex min-h-screen flex-col bg-white dark:bg-black font-sans">
      <SiteHeader />
      <main className="flex w-full flex-1 flex-col py-6 sm:py-8">
        <div className="mx-auto w-full max-w-7xl px-4 sm:px-6 md:px-8">{children}</div>
      </main>
      <SiteFooter />
    </div>
  );
}
