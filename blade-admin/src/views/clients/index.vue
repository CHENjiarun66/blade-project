<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>客户列表</span>
          <el-button type="primary">新建客户</el-button>
        </div>
      </template>

      <el-table :data="tableData" stripe style="width: 100%">
        <el-table-column prop="name" label="客户名称" />
        <el-table-column prop="contact" label="联系人" width="100" />
        <el-table-column prop="phone" label="联系电话" width="140" />
        <el-table-column prop="address" label="地址" />
        <el-table-column prop="orderCount" label="订单数" width="80" />
        <el-table-column label="操作" fixed="right" width="150">
          <template #default>
            <el-button link type="primary" size="small">编辑</el-button>
            <el-button link type="primary" size="small">查看订单</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const tableData = ref([
  { name: '张三服装店', contact: '张三', phone: '13800138000', address: '北京市朝阳区XX街', orderCount: 25 },
  { name: '李四服饰', contact: '李四', phone: '13900139000', address: '上海市浦东新区XX路', orderCount: 18 },
])
</script>

<style scoped>
.page-container {
  padding: 0;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
