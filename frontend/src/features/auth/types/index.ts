export interface RegisterRequest {
  email: string;
  password: string;
  confirmPassword: string;
  displayName: string;
}

export interface RegisterResponse {
  userId: string;
  email: string;
}

export interface VerifyOtpRequest {
  email: string;
  otpCode: string;
}
