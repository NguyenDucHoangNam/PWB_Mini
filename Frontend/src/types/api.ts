export interface ApiResponse<T> {
  success: boolean;
  status?: number;
  message: string;
  data: T | null;
  errors: ErrorDetail[] | null;
  code?: string;
  timestamp: string;
  traceId?: string | null;
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
