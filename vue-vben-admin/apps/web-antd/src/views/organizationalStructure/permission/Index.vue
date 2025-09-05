<script setup lang="ts">
import RoleTable from "#/views/organizationalStructure/permission/role-table.vue";
import type {Role} from "@vben/stores";
import {ref} from "vue";
import PermissionList from "#/views/organizationalStructure/permission/permission-list.vue";
const selected = ref<Role>({})
</script>

<template>
  <div class="p-5 flex flex-col gap-5">
    <role-table class="flex-1" v-model:role="selected" />
    <permission-list class="flex-1" :role="selected"/>
  </div>
</template>

<style scoped>

</style>
