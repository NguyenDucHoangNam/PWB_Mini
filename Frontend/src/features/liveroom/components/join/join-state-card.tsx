const TONE_STYLES = {
  danger: "text-rose-700 dark:text-rose-400",
  warning: "text-amber-800 dark:text-amber-400",
  neutral: "text-slate-900 dark:text-slate-100",
} as const;

const ICON_TONE_STYLES = {
  danger: "text-rose-700 dark:text-rose-400",
  warning: "text-amber-800 dark:text-amber-400",
  neutral: "text-indigo-600 dark:text-indigo-400",
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
    <div className="neu-pressed flex h-full flex-col items-center justify-center gap-5 rounded-3xl border-none p-6 text-center md:p-8">
      <span
        className={`neu-raised grid size-16 place-items-center rounded-full border-none ${ICON_TONE_STYLES[tone]}`}
      >
        {icon}
      </span>
      <div className="flex flex-col gap-1.5">
        <p className={`text-lg font-bold tracking-tight ${TONE_STYLES[tone]}`}>{title}</p>
        <p className="max-w-sm text-sm leading-relaxed font-medium text-slate-600 dark:text-slate-400">
          {body}
        </p>
      </div>
      {action}
    </div>
  );
}
