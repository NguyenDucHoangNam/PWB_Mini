const TONE_STYLES = {
  danger: "border-destructive/30 bg-destructive/10 text-destructive",
  warning: "border-border bg-muted/50 text-foreground",
  neutral: "border-border bg-card text-foreground",
} as const;

export function JoinStateCard({
  tone,
  icon,
  title,
  body,
  action,
}: {
  tone: keyof typeof TONE_STYLES;
  icon: React.ReactNode;
  title: string;
  body: string;
  action: React.ReactNode;
}) {
  return (
    <div
      className={`flex h-full flex-col items-center justify-center gap-4 rounded-xl border p-6 text-center shadow-xs md:p-8 ${TONE_STYLES[tone]}`}
    >
      {icon}
      <div className="flex flex-col gap-1">
        <p className="text-lg font-bold tracking-tight">{title}</p>
        <p className="max-w-sm text-sm opacity-80">{body}</p>
      </div>
      {action}
    </div>
  );
}
