import {requestClient} from "#/api/request";
import type {EntityDefinition} from "#/types/codegen";

/**
 * 代码生成
 */
export async function codegenApi(entityDef: EntityDefinition) {
  return requestClient.post<String>('/dev/gencode', entityDef, {
    responseReturn: "body"
  });
}
