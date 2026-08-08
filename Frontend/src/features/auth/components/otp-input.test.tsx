import { describe, it, expect, vi } from "vitest";
import { createRef, act } from "react";
import { OtpInput, type OtpInputHandle } from "./otp-input";
import { render, screen, fireEvent } from "@testing-library/react";

describe("OtpInput", () => {
  it("renders 6 individual inputs", () => {
    render(<OtpInput onChange={() => {}} />);
    expect(screen.getAllByRole("textbox")).toHaveLength(6);
  });

  it("calls onChange with combined digits on digit input", () => {
    const onChange = vi.fn();
    render(<OtpInput onChange={onChange} />);
    const inputs = screen.getAllByRole("textbox");
    fireEvent.change(inputs[0], { target: { value: "1" } });
    expect(onChange).toHaveBeenCalledWith("1");
  });

  it("only accepts digits", () => {
    const onChange = vi.fn();
    render(<OtpInput onChange={onChange} />);
    const inputs = screen.getAllByRole("textbox");
    fireEvent.change(inputs[0], { target: { value: "a" } });
    expect(onChange).not.toHaveBeenCalled();
  });

  it("exposes clear() via ref and resets internal state", () => {
    const onChange = vi.fn();
    const ref = createRef<OtpInputHandle>();
    render(<OtpInput ref={ref} onChange={onChange} />);
    const inputs = screen.getAllByRole("textbox");

    fireEvent.change(inputs[0], { target: { value: "1" } });
    fireEvent.change(inputs[1], { target: { value: "2" } });

    act(() => {
      ref.current?.clear();
    });

    const clearedInputs = screen.getAllByRole("textbox");
    clearedInputs.forEach((input) => {
      expect((input as HTMLInputElement).value).toBe("");
    });
  });
});