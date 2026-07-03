export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T | null;
  errors: ErrorDetail[] | null;
  timestamp: string;
}

export interface ErrorDetail {
  code: string;
  field: string | null;
  message: string;
}

export interface PaginatedResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}
