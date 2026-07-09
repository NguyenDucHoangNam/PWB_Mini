import { describe, it, expect, vi } from "vitest";
import { OtpInput } from "./otp-input";
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
    // onChange should not be called for invalid input
    expect(onChange).not.toHaveBeenCalled();
  });
});