<script setup lang="ts">
import { FaButton, FaToast, useFaToast } from '@yudream/components'

const toast = useFaToast()

function showLoading() {
  const id = toast.loading('正在处理...', { duration: Infinity })
  setTimeout(() => {
    toast.dismiss(id)
    toast.success('处理完成')
  }, 1000)
}

function showPromise() {
  toast.promise(new Promise(resolve => setTimeout(resolve, 1000)), {
    loading: '正在加载本地数据',
    success: '本地数据加载完成',
    error: '本地数据加载失败',
  })
}
</script>

<template>
  <FaToast />
  <div class="demo-row">
    <FaButton @click="toast('默认提示', { description: '这是本地演示反馈' })">默认</FaButton>
    <FaButton @click="toast.success('保存成功', { description: '内容已在本地更新' })">成功</FaButton>
    <FaButton @click="toast.error('保存失败', { description: '无需网络即可重试' })">错误</FaButton>
    <FaButton @click="toast.info('系统通知', { description: '这是一条本地通知' })">信息</FaButton>
    <FaButton @click="toast.warning('注意事项', { description: '请确认内容已保存' })">警告</FaButton>
    <FaButton variant="outline" @click="toast('文件已移入回收站', { action: { label: '撤销', onClick: () => toast.success('已撤销删除') } })">操作按钮</FaButton>
    <FaButton variant="outline" @click="showLoading">加载状态</FaButton>
    <FaButton variant="outline" @click="showPromise">Promise 状态</FaButton>
  </div>
</template>
