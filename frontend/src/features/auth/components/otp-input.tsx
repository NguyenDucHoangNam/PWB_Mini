"use client";

import { useState, useRef, useEffect } from "react";

interface OtpInputProps {
  disabled?: boolean;
  onChange: (otp: string) => void;
}

export function OtpInput({ disabled = false, onChange }: OtpInputProps) {
  const [otp, setOtp] = useState<string[]>(Array(6).fill(""));
  const inputRefs = useRef<(HTMLInputElement | null)[]>([]);

  // Focus the first input on mount
  useEffect(() => {
    if (inputRefs.current[0]) {
      inputRefs.current[0].focus();
    }
  }, []);

  const handleChange = (value: string, index: number) => {
    // Only accept numeric inputs
    if (value && !/^\d$/.test(value)) return;

    const newOtp = [...otp];
    newOtp[index] = value;
    setOtp(newOtp);

    const otpString = newOtp.join("");
    onChange(otpString);

    // Auto-focus next input if value is filled
    if (value && index < 5) {
      inputRefs.current[index + 1]?.focus();
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>, index: number) => {
    if (e.key === "Backspace") {
      if (!otp[index] && index > 0) {
        // Current field is empty, delete previous field and focus it
        const newOtp = [...otp];
        newOtp[index - 1] = "";
        setOtp(newOtp);
        onChange(newOtp.join(""));
        inputRefs.current[index - 1]?.focus();
      } else if (otp[index]) {
        // Clear current field
        const newOtp = [...otp];
        newOtp[index] = "";
        setOtp(newOtp);
        onChange(newOtp.join(""));
      }
    }
  };

  const handlePaste = (e: React.ClipboardEvent<HTMLInputElement>) => {
    e.preventDefault();
    const pastedData = e.clipboardData.getData("text").trim();

    // Check if pasted data is exactly 6 digits
    if (!/^\d{6}$/.test(pastedData)) return;

    const newOtp = pastedData.split("");
    setOtp(newOtp);
    onChange(pastedData);

    // Focus last input or blur
    inputRefs.current[5]?.focus();
  };

  return (
    <div
      role="group"
      aria-label="OTP code input - 6 digits"
      className="flex justify-between gap-2 md:gap-4 w-full max-w-[320px] mx-auto font-sans"
    >
      {Array(6)
        .fill(null)
        .map((_, index) => (
          <input
            key={index}
            type="text"
            inputMode="numeric"
            pattern="[0-9]*"
            maxLength={1}
            value={otp[index]}
            disabled={disabled}
            aria-label={`OTP digit ${index + 1} of 6`}
            aria-describedby="otp-instructions"
            ref={(el) => {
              inputRefs.current[index] = el;
            }}
            onChange={(e) => handleChange(e.target.value, index)}
            onKeyDown={(e) => handleKeyDown(e, index)}
            onPaste={index === 0 ? handlePaste : undefined}
            className="size-11 border border-neutral-200 text-center text-lg font-bold rounded-lg outline-none transition-colors focus:border-black focus:ring-3 focus:ring-black/10 disabled:bg-neutral-100 disabled:opacity-50 dark:border-neutral-800 dark:bg-neutral-900 dark:focus:border-white dark:focus:ring-white/10"
          />
        ))}
    </div>
  );
}
