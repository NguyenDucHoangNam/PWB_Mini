export default function Home() {
  return (
    <div className="flex flex-1 flex-col items-center justify-center bg-white font-sans dark:bg-black">
      <main className="flex w-full max-w-2xl flex-col items-center gap-8 px-6 py-24 text-center">
        <h1 className="text-4xl font-bold tracking-tight text-black dark:text-white">
          PWB MiNi
        </h1>
        <p className="max-w-md text-lg leading-relaxed text-neutral-500 dark:text-neutral-400">
          Real-time audio collaboration and secure demo sharing platform for music producers.
        </p>
        <div className="h-px w-16 bg-neutral-300 dark:bg-neutral-700" />
        <p className="text-sm text-neutral-400 dark:text-neutral-500">
          Phase 1 — Project Bootstrap Complete
        </p>
      </main>
    </div>
  );
}
