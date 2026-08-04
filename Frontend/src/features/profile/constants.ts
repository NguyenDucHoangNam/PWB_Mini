export const ALLOWED_AVATAR_TYPES: readonly string[] = ["image/jpeg", "image/png", "image/webp"];

export const MAX_AVATAR_SIZE = 5 * 1024 * 1024;

export const ROLE_LABEL_KEYS: Record<string, string> = {
  ADMIN: "roleValue.ADMIN",
  PRO: "roleValue.PRO",
  USER: "roleValue.USER",
};
