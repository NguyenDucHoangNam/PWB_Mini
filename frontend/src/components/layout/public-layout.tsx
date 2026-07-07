import { SiteHeader } from "./site-header";
import { SiteFooter } from "./site-footer";

interface PublicLayoutProps {
  children: React.ReactNode;
}

export function PublicLayout({ children }: PublicLayoutProps) {
  return (
    <div className="flex min-h-screen flex-col bg-white dark:bg-black font-sans">
      <SiteHeader />
      <main className="flex w-full flex-1 flex-col">{children}</main>
      <SiteFooter />
    </div>
  );
}
