export interface Role {
  id?: string;
  name?: string;
  description?: string;
  enabled?: boolean;
  level?: number;
  permissionId?: string[];
}
