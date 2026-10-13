<script setup lang="ts">
import type { PermissionOverview } from '@/api/modules/system-permission'
import { permissionCategoryOf, permissionResourceLabels, permissionResourceOf } from '@/utils/permission-catalog'
import { permissionDetails } from './permission-operations'

const props = defineProps<{ overview: PermissionOverview, code: string }>()
const emit = defineEmits<{
  grant: [code: string]
  revoke: [roleId: string, code: string]
  editRole: [roleId: string]
}>()
const { auth } = useAppAuth()
const details = computed(() => permissionDetails(props.overview, props.code))
const resourceLabels = computed(() => permissionResourceLabels(props.overview.permissions))
const resource = computed(() => details.value.permission ? permissionResourceOf(details.value.permission, resourceLabels.value) : undefined)
const category = computed(() => permissionCategoryOf(props.code))
</script>

<template>
  <section class="flex flex-col gap-5" aria-label="权限细则">
    <div class="flex flex-wrap gap-3 items-start justify-between">
      <div class="min-w-0">
        <h3 class="text-lg font-semibold">
          {{ details.permission?.name || '历史或未登记权限' }}
        </h3>
        <p class="text-sm text-secondary-foreground/70 mt-1 break-all">
          {{ code }}
        </p>
      </div>
      <YdTag :variant="category.variant">
        {{ category.label }}
      </YdTag>
    </div>
    <dl class="text-sm gap-x-4 gap-y-2 grid grid-cols-[auto_minmax(0,1fr)]">
      <dt class="text-secondary-foreground/70">
        业务资源
      </dt><dd>{{ resource ? `${resource.domainLabel} / ${resource.label}` : '目录中已不可用' }}</dd>
      <dt class="text-secondary-foreground/70">
        登记模块
      </dt><dd>{{ details.permission?.module || '未登记' }}</dd>
      <dt class="text-secondary-foreground/70">
        操作说明
      </dt><dd class="break-words">
        {{ details.permission?.desc || '登记信息未提供补充说明。' }}
      </dd>
      <dt class="text-secondary-foreground/70">
        目录状态
      </dt><dd>{{ details.permission?.status === 'ACTIVE' ? '可新增授权' : '已停用或未登记；原配置保留，不能新增授权' }}</dd>
    </dl>
    <div class="text-sm leading-relaxed p-4 border rounded-lg">
      <h4 class="font-semibold">
        生效细则
      </h4>
      <ol class="mt-2 pl-5 list-decimal space-y-1">
        <li>用户须已启用且验证邮箱，并在当前部门选择持有此权限的活动角色。</li>
        <li>用户已分配的多个角色是可切换身份；实际访问不会合并所有角色权限。</li>
        <li>SUPER_ADMIN 系统角色自动获得通配权限，普通角色的等级不产生通配授权。历史配置中的 * 也会产生通配，应通过原权限树处理，不能逐项撤销。</li>
        <li>菜单或按钮还受启停、插件和平台能力的运行状态控制；授予权限不会自动启用这些资源。</li>
      </ol>
    </div>
    <div>
      <div class="flex gap-3 items-center justify-between">
        <h4 class="font-semibold">
          配置来源角色 · {{ details.sources.length }}
        </h4>
        <YdButton v-if="auth('system:role:edit') && details.permission?.status === 'ACTIVE'" size="sm" @click="emit('grant', code)">
          <YdIcon name="morph:plus" />授予角色
        </YdButton>
      </div>
      <div v-if="details.sources.length" class="mt-3 flex flex-col gap-2 max-h-64 overflow-auto">
        <div v-for="source in details.sources" :key="source.role.id" class="p-3 border rounded-lg flex flex-wrap gap-3 items-center justify-between">
          <div>
            <p>{{ source.role.name }} <span class="text-xs text-secondary-foreground/70">{{ source.role.deptName }}</span></p>
            <p class="text-xs text-secondary-foreground/70 mt-1">
              {{ source.wildcard ? '通配授权' : '显式配置' }} · {{ source.active ? '角色启用' : '角色停用，配置未生效' }}
            </p>
          </div>
          <div v-if="auth('system:role:edit')" class="flex gap-2">
            <YdButton variant="outline" size="sm" @click="emit('editRole', source.role.id)">
              原权限树
            </YdButton>
            <YdButton v-if="!source.wildcard && details.permission?.status === 'ACTIVE'" variant="outline" size="sm" @click="emit('revoke', source.role.id, code)">
              撤销此权限
            </YdButton>
          </div>
        </div>
      </div>
      <p v-else class="text-sm text-secondary-foreground/70 mt-2">
        没有角色配置此权限。
      </p>
    </div>
    <div>
      <h4 class="font-semibold">
        直接绑定的菜单与按钮
      </h4>
      <p v-if="!overview.canViewMenus" class="text-sm text-secondary-foreground/70 mt-2">
        没有菜单查看权限。
      </p>
      <ul v-else-if="details.menus.length" class="text-sm mt-3 max-h-40 overflow-auto space-y-2">
        <li v-for="menu in details.menus" :key="menu.code" class="p-3 border rounded-lg">
          {{ menu.name }} <span class="text-xs text-secondary-foreground/70">{{ menu.type }} · {{ menu.status === 'ACTIVE' && menu.runtimeAvailable ? '运行可用' : '停用或运行不可用' }}</span>
          <p class="text-xs text-secondary-foreground/70 mt-1 break-all">
            {{ menu.path || menu.code }}
          </p>
        </li>
      </ul>
      <p v-else class="text-sm text-secondary-foreground/70 mt-2">
        没有直接菜单绑定；此权限仍可能用于接口或按钮操作。结构祖先由后端菜单规则推导。
      </p>
    </div>
    <div class="text-sm text-secondary-foreground/70 p-3 border rounded-lg">
      <p v-if="overview.canViewUsers">
        当前载入的 {{ overview.users.length }} 名用户中，{{ details.users.length }} 人配置了相关角色；这里只是配置关系，并不表示正在使用该身份。
      </p>
      <p v-else>
        没有用户查看权限，未展示用户关系。
      </p>
      <p class="mt-2">
        细则来自现有权限登记和判权规则，未登记的额外数据范围或条件不会在这里推断。
      </p>
    </div>
  </section>
</template>
