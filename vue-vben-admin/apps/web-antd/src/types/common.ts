

export interface SearchPageParams {
  page?: number;
  size?: number;
  keywords?: Record<string, string>;
}

export interface SearchPageResponse<T> {
  totalElements: number;
  content: T []
}
