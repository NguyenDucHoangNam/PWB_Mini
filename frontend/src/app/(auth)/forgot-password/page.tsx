import { ForgotPasswordForm } from "@/features/auth/components/forgot-password-form";

export const metadata = {
  title: "Quên mật khẩu | PWB MiNi",
  description: "Yêu cầu khôi phục mật khẩu tài khoản Play With Beats MiNi",
};

export default function ForgotPasswordPage() {
  return <ForgotPasswordForm />;
}
