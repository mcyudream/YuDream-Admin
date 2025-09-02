<script setup lang="ts">
import DepartmentTable from "#/views/organizationalStructure/department/department-table.vue";
import {ref} from "vue";
import type {Department} from "#/types/organizationalStructure/department";



const departments = ref<Department[]>([])
</script>

<template>
  <div id="department" class="p-5 w-full">
    <department-table v-model:department="departments"  />

  </div>
</template>

<style scoped>

</style>
