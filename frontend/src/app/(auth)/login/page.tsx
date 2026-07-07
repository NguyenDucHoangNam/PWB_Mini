import { LoginForm } from "@/features/auth/components/login-form";

export const metadata = {
  title: "Đăng nhập | PWB MiNi",
  description: "Đăng nhập vào tài khoản Play With Beats MiNi",
};

export default function LoginPage() {
  return <LoginForm />;
}
