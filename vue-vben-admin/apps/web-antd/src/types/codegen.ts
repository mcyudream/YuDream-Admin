export interface FieldDefinition {
  name: string;
  type: string;
  indexed: string;
}

export interface EntityDefinition {
  className?: string;
  collectionName?: string;
  fields: FieldDefinition[];
  moduleEntity?: string;
  moduleRepository?: string;
  moduleService?: string;
  moduleServiceImpl?: string;
  packageEntity?: string;
  packageRepository?: string;
  packageService?: string;
  packageServiceImpl?: string;
  outputDir?: string;
}

