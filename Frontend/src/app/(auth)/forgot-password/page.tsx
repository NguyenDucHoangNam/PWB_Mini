import { ForgotPasswordForm } from "@/features/auth/components/forgot-password-form";

export const metadata = {
  title: "Quên mật khẩu | PWB",
  description: "Yêu cầu khôi phục mật khẩu tài khoản Play With Beats",
};

export default function ForgotPasswordPage() {
  return <ForgotPasswordForm />;
}
