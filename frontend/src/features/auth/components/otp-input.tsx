"use client";

import { useState, useRef, useEffect } from "react";

interface OtpInputProps {
  disabled?: boolean;
  invalid?: boolean;
  onChange: (otp: string) => void;
}

export function OtpInput({ disabled = false, invalid = false, onChange }: OtpInputProps) {
  const [otp, setOtp] = useState<string[]>(Array(6).fill(""));
  const inputRefs = useRef<(HTMLInputElement | null)[]>([]);

  useEffect(() => {
    if (inputRefs.current[0]) {
      inputRefs.current[0].focus();
    }
  }, []);

  const handleChange = (value: string, index: number) => {
    if (value && !/^\d$/.test(value)) return;

    const newOtp = [...otp];
    newOtp[index] = value;
    setOtp(newOtp);

    const otpString = newOtp.join("");
    onChange(otpString);

    if (value && index < 5) {
      inputRefs.current[index + 1]?.focus();
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>, index: number) => {
    if (e.key === "Backspace") {
      if (!otp[index] && index > 0) {
        const newOtp = [...otp];
        newOtp[index - 1] = "";
        setOtp(newOtp);
        onChange(newOtp.join(""));
        inputRefs.current[index - 1]?.focus();
      } else if (otp[index]) {
        const newOtp = [...otp];
        newOtp[index] = "";
        setOtp(newOtp);
        onChange(newOtp.join(""));
      }
      return;
    }

    if (e.key === "ArrowLeft" && index > 0) {
      e.preventDefault();
      inputRefs.current[index - 1]?.focus();
      return;
    }

    if (e.key === "ArrowRight" && index < 5) {
      e.preventDefault();
      inputRefs.current[index + 1]?.focus();
      return;
    }
  };

  const handlePaste = (e: React.ClipboardEvent<HTMLInputElement>) => {
    e.preventDefault();
    const pastedData = e.clipboardData.getData("text").replace(/\D/g, "").slice(0, 6);
    if (pastedData.length !== 6) return;

    const newOtp = pastedData.split("");
    setOtp(newOtp);
    onChange(pastedData);

    inputRefs.current[5]?.blur();
  };

  return (
    <div
      role="group"
      aria-label="OTP code input - 6 digits"
      className="flex justify-between gap-1.5 sm:gap-2 md:gap-3 w-full max-w-[360px] mx-auto font-sans"
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
            aria-invalid={invalid}
            aria-label={`OTP digit ${index + 1} of 6`}
            aria-describedby="otp-instructions"
            ref={(el) => {
              inputRefs.current[index] = el;
            }}
            onChange={(e) => handleChange(e.target.value, index)}
            onKeyDown={(e) => handleKeyDown(e, index)}
            onPaste={index === 0 ? handlePaste : undefined}
            className={`size-10 sm:size-11 md:size-12 border text-center text-lg font-bold rounded-lg outline-none transition-colors focus:border-black focus:ring-3 focus:ring-black/10 disabled:bg-neutral-100 disabled:opacity-50 dark:bg-neutral-900 dark:focus:border-white dark:focus:ring-white/10 ${
              invalid
                ? "border-red-500 focus:border-red-500 focus:ring-red-500/20 dark:border-red-500"
                : "border-neutral-200 dark:border-neutral-800"
            }`}
          />
        ))}
    </div>
  );
}