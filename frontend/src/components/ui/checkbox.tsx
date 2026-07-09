"use client";

import * as React from "react";
import { Checkbox } from "@base-ui/react/checkbox";

import { cn } from "@/lib/utils";

const CheckboxRoot = Checkbox.Root;
const CheckboxIndicator = Checkbox.Indicator;

const CheckboxComponent = React.forwardRef<
  React.ComponentRef<typeof CheckboxRoot>,
  React.ComponentPropsWithoutRef<typeof CheckboxRoot>
>(({ className, ...props }, ref) => (
  <CheckboxRoot
    ref={ref}
    className={cn(
      "peer h-4 w-4 shrink-0 rounded-[4px] border border-neutral-300 dark:border-neutral-600 shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 data-[checked]:bg-black dark:data-[checked]:bg-white data-[checked]:text-white dark:data-[checked]:text-black data-[checked]:border-transparent data-[disabled]:cursor-not-allowed data-[disabled]:opacity-50 transition-colors",
      className
    )}
    {...props}
  >
    <CheckboxIndicator className="flex items-center justify-center text-current">
      <svg
        className="size-3"
        fill="none"
        viewBox="0 0 24 24"
        stroke="currentColor"
        strokeWidth={3}
      >
        <path
          strokeLinecap="round"
          strokeLinejoin="round"
          d="M5 13l4 4L19 7"
        />
      </svg>
    </CheckboxIndicator>
  </CheckboxRoot>
));
CheckboxComponent.displayName = "Checkbox";

export { CheckboxComponent as Checkbox };
