import Link from "next/link";

interface MinimalLayoutProps {
  children: React.ReactNode;
}

export function MinimalLayout({ children }: MinimalLayoutProps) {
  return (
    <div className="flex min-h-dvh flex-col items-center justify-center bg-white px-6 py-12 dark:bg-black font-sans">
      <header className="mb-8">
        <Link
          href="/"
          className="text-2xl font-bold tracking-tight text-black dark:text-white hover:opacity-85"
        >
          PWB MiNi
        </Link>
      </header>
      <main className="w-full max-w-md flex-1 flex flex-col justify-center">{children}</main>
    </div>
  );
}
