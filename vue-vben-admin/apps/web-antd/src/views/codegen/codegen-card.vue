<script setup lang="ts">
import {$t} from "@vben/locales";
import {ref} from "vue";
import {useVbenForm} from "#/adapter/form";
import CodeGenFieldTable from "#/views/codegen/code-gen-field-table.vue";
import type {EntityDefinition, FieldDefinition} from "#/types/codegen";
import {codegenApi} from "#/api";
const onSubmitCodeGen = async (values: Record<string, any>) => {
  let entityDef: EntityDefinition = {
    ...values,
    fields: fieldList.value
  }

  let res = await codegenApi(entityDef);
  console.log(res)
}
const fieldList = ref<FieldDefinition[]>([]);


const [CodeGenForm] = useVbenForm({
  commonConfig: {
    componentProps: {
      class: 'w-full'
    }
  },
  handleSubmit: onSubmitCodeGen,
  layout: 'vertical',
  schema: [
    {
      component: "Input",
      componentProps: {
        placeholder: $t("form.codegen.className.placeholder"),
      },
      fieldName: "className",
      label: $t("form.codegen.className.label"),
      help: $t("form.codegen.className.description"),
    },
    {
      component: "Input",
      componentProps: {
        placeholder: $t("form.codegen.collectionName.placeholder"),
      },
      fieldName: "collectionName",
      label: $t("form.codegen.collectionName.label"),
      help: $t("form.codegen.collectionName.description"),
    },
    {
      component: "Input",
      componentProps: {
        placeholder: $t("form.codegen.packageName.placeholder"),
      },
      fieldName: "packageName",
      label: $t("form.codegen.packageName.label"),
      help: $t("form.codegen.packageName.description"),
      defaultValue: "online.yudream.spring"
    },{
      component: "Input",
      componentProps: {
        placeholder: $t("form.codegen.moduleEntity.placeholder"),
      },
      fieldName: "moduleEntity",
      label: $t("form.codegen.moduleEntity.label"),
      help: $t("form.codegen.moduleEntity.description"),
      defaultValue: "entity"
    },{
      component: "Input",
      componentProps: {
        placeholder: $t("form.codegen.moduleRepository.placeholder"),
      },
      fieldName: "moduleRepository",
      label: $t("form.codegen.moduleRepository.label"),
      help: $t("form.codegen.moduleRepository.description"),
      defaultValue: "entity"
    },{
      component: "Input",
      componentProps: {
        placeholder: $t("form.codegen.moduleService.placeholder"),
      },
      fieldName: "moduleService",
      label: $t("form.codegen.moduleService.label"),
      help: $t("form.codegen.moduleService.description"),
      defaultValue: "admin"
    },{
      component: "Input",
      componentProps: {
        placeholder: $t("form.codegen.moduleServiceImpl.placeholder"),
      },
      fieldName: "moduleServiceImpl",
      label: $t("form.codegen.moduleServiceImpl.label"),
      help: $t("form.codegen.moduleServiceImpl.description"),
      defaultValue: "admin"
    },{
      component: "Input",
      componentProps: {
        placeholder: $t("form.codegen.packageEntity.placeholder"),
      },
      fieldName: "packageEntity",
      label: $t("form.codegen.packageEntity.label"),
      help: $t("form.codegen.packageEntity.description"),
      defaultValue: ".entity.entity"
    },{
      component: "Input",
      componentProps: {
        placeholder: $t("form.codegen.packageRepository.placeholder"),
      },
      fieldName: "packageRepository",
      label: $t("form.codegen.packageRepository.label"),
      help: $t("form.codegen.packageRepository.description"),
      defaultValue: ".entity.mapper"
    },{
      component: "Input",
      componentProps: {
        placeholder: $t("form.codegen.packageService.placeholder"),
      },
      fieldName: "packageService",
      label: $t("form.codegen.packageService.label"),
      help: $t("form.codegen.packageService.description"),
      defaultValue: ".admin.service"
    },{
      component: "Input",
      componentProps: {
        placeholder: $t("form.codegen.packageServiceImpl.placeholder"),
      },
      fieldName: "packageServiceImpl",
      label: $t("form.codegen.packageServiceImpl.label"),
      help: $t("form.codegen.packageServiceImpl.description"),
      defaultValue: ".admin.service.impl"
    },{
      component: "Input",
      componentProps: {
        placeholder: $t("form.codegen.outputDir.placeholder"),
      },
      fieldName: "outputDir",
      label: $t("form.codegen.outputDir.label"),
      help: $t("form.codegen.outputDir.description"),
      defaultValue: "src/main/java"
    },
  ],
  wrapperClass: 'grid-cols-1 md:grid-cols-2 lg:grid-cols-3',
})
</script>

<template>
  <div class="card-box p-4 py-6 lg:flex flex-col space-y-4">
    <div>
      <h1 class="text-md font-semibold md:text-xl">{{$t("page.codegen.card.title")}}</h1>
      <span class="text-foreground/80 mt-1">{{$t("page.codegen.card.description")}}</span>
    </div>
    <div class="space-y-6">
      <code-gen-field-table v-model="fieldList"/>
      <code-gen-form></code-gen-form>

    </div>
  </div>
</template>

<style scoped>

</style>
