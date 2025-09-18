<script lang="ts" setup>
import {useVbenDrawer, useVbenModal} from '@vben/common-ui';
import type {DepartmentRoleEntity, User} from "#/types/user";
import { useVbenForm } from '#/adapter/form';
import { $t } from '@vben/locales';
import { z } from '#/adapter/form';
import {message,List,ListItem,Button,Tag} from "ant-design-vue";
import {onMounted, ref, watch} from "vue";
import {addToDepartmentApi, deleteUserDepartmentApi, editUserApi} from "#/api";
import AddDepartmentModal from "#/views/organizationalStructure/user/add-department-modal.vue";
import type {Department} from "#/types/organizationalStructure/department";
import DataRangeSelectModal from "#/views/organizationalStructure/user/data-range-select-modal.vue";

const props = defineProps({
  initialValue: {
    type: Object as () => User,
    required: true,
  },
  departments: {
    type: Array as () => Department[],
    required: true,
  }
})

const [EditForm, formApi] = useVbenForm({
  commonConfig: {
    componentProps: {
      class: 'w-full',
    },
  },
  handleSubmit: async (values) => {
    const res = await editUserApi({
      ...props.initialValue,
      ...values
    })
    // @ts-ignore
    message.success(res.message)
  },
  layout: 'vertical',
  showDefaultActions: false,
  schema: [
    {
      component: 'Input',
      fieldName: 'nickname',
      label: $t('form.user.edit.nickname.title'),
      help: $t('form.user.edit.nickname.description'),
      componentProps: {
        placeholder: $t('form.user.edit.nickname.placeholder'),
      },
      rules: 'required',
      defaultValue: props.initialValue.nickname,
    },
    {
      component: 'Input',
      fieldName: 'email',
      label: $t('form.user.edit.email.title'),
      help: $t('form.user.edit.email.description'),
      componentProps: {
        placeholder: $t('form.user.edit.email.placeholder'),
      },
      rules: z.string().email(),
      defaultValue: props.initialValue.email,
    },
    {
      component: 'Input',
      fieldName: 'phone',
      label: $t('form.user.edit.phone.title'),
      help: $t('form.user.edit.phone.description'),
      componentProps: {
        placeholder: $t('form.user.edit.phone.placeholder'),
      },
      rules: z.string().min(11).max(11),
      defaultValue: props.initialValue.phone,
    }
  ],
});

watch(()=> props.initialValue, () => {
  formApi.setValues(props.initialValue)
})
const emit = defineEmits(['onSubmit','onDepartmentUpdate']);



const [Drawer, drawerApi] = useVbenDrawer({
  onConfirm: async () => {
    await formApi.validate().then(
       async () => {
          await formApi.submitForm()
          await drawerApi.close();
          emit('onSubmit');
        }
    ).catch(e => {
      message.error(e.message)
    })
  }
});

const [SelectDataRangeModal, selectDataRangeApi] = useVbenModal({
  connectedComponent: DataRangeSelectModal
})

const [AddDepartment, addDepartmentModalApi] = useVbenModal({
  connectedComponent: AddDepartmentModal,
})
const departments = ref<DepartmentRoleEntity[]>([]);
const onAddDepartment = async (id: string) => {
  const res = await addToDepartmentApi(props.initialValue, id)
  emit('onDepartmentUpdate')
  //@ts-ignore
  message.success(res.message)
  //@ts-ignore
  departments.value = res.data.departmentRoles
}

const onDeleteDepartment = async (id: string) => {
  const res = await deleteUserDepartmentApi(props.initialValue, id)
  emit('onDepartmentUpdate')
  //@ts-ignore
  message.success(res.message)
  //@ts-ignore
  departments.value = res.data.departments
}

watch(()=>props.initialValue, () => {
  departments.value = props.initialValue.departmentRoles??[]
})

onMounted(()=>{
  departments.value = props.initialValue.departmentRoles??[]
})
</script>
<template>
  <Drawer title="编辑用户">
    <edit-form/>
    <div class="flex-col gap-2 flex">
      <span class="font-bold">{{$t('部门列表')}}</span>
      <div class="toolbar flex flex-warp w-full justify-end ">
        <Button type="primary" @click="addDepartmentModalApi.open()">添加到部门</Button>
      </div>
      <List>
        <ListItem :title="item.department?.name" v-for="item in departments">
          <div class="space-x-2">
            <span class="font-bold">{{item.department?.name}}</span>
            | {{item.department?.description}} |
            <Tag @click="selectDataRangeApi.open()" color="#108ee9" >{{item.dataRange}}</Tag>
          </div>
          <template #actions>
            <Button danger type="link" @click="onDeleteDepartment(item.department?.id??'')">移出</Button>
          </template>
        </ListItem>
      </List>
    </div>
    <AddDepartment @on-submit="onAddDepartment" />
    <SelectDataRangeModal/>
  </Drawer>
</template>
