/** 后端统一响应信封（interfaces/common/Result）。 */
export interface ResultEnvelope<T> {
  code: number;
  message: string;
  data: T;
}
