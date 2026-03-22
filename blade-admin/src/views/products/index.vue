<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>商品列表</span>
          <el-button type="primary">新建商品</el-button>
        </div>
      </template>

      <el-table :data="tableData" stripe style="width: 100%">
        <el-table-column prop="name" label="商品名称" />
        <el-table-column prop="category" label="分类" width="100" />
        <el-table-column prop="price" label="单价" width="100" />
        <el-table-column prop="skuCount" label="SKU数量" width="100" />
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" fixed="right" width="150">
          <template #default>
            <el-button link type="primary" size="small">编辑</el-button>
            <el-button link type="danger" size="small">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const tableData = ref([
  { name: 'T恤', category: '上衣', price: '¥99.00', skuCount: 12, createTime: '2026-03-20 10:00:00' },
  { name: '牛仔裤', category: '裤子', price: '¥199.00', skuCount: 8, createTime: '2026-03-20 11:00:00' },
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
