export function JoinStepShell({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex h-[min(34rem,calc(100dvh-10rem))] w-full max-w-xl flex-col">
      {children}
    </div>
  );
}
