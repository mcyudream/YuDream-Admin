export interface Department {
  id?: string;
  name?: string;
  parentId?: string;
  description?: string;
  createdTime?: Date;
  updatedTime?: Date;
}

export interface CreateDepartment {
  name?: string;
  parentId?: string;
  description?: string;
}


