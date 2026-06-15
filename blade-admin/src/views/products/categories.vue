<template>
  <div class="categories-page">
    <!-- 页面标题区 -->
    <div class="flex justify-between items-end mb-8">
      <div>
        <h2 class="text-2xl font-bold text-gray-900 tracking-tight mb-1">商品分类</h2>
        <p class="text-gray-500 text-sm">管理商品的分类目录。</p>
      </div>
      <div class="flex">
        <el-button class="!bg-gray-100 !text-gray-700 !border-none !px-5 !py-2.5 !rounded-xl !font-bold !h-auto hover:!bg-gray-200 mr-3" @click="loadData">
          <span class="material-symbols-outlined text-sm mr-1">refresh</span>
          刷新
        </el-button>
        <el-button type="primary" class="!bg-[#408aee] !border-none !px-6 !py-2.5 !rounded-xl !font-bold !h-auto hover:!bg-[#2d7be0] shadow-lg shadow-primary/20" @click="handleCreate">
          <span class="material-symbols-outlined text-sm mr-1">add_circle</span>
          新建分类
        </el-button>
      </div>
    </div>

    <!-- 分类表格 -->
    <div class="bg-white rounded-xl overflow-hidden shadow-sm mb-6">
      <el-table :data="tableData" class="category-table" v-loading="loading" empty-text="暂无分类数据">
        <el-table-column label="分类名称" min-width="200">
          <template #default="{ row }">
            <div class="flex items-center gap-3">
              <div class="w-10 h-10 rounded-lg bg-[#408aee]/10 flex items-center justify-center">
                <span class="material-symbols-outlined text-[#408aee]">category</span>
              </div>
              <div>
                <div class="text-sm font-semibold text-gray-900">{{ row.categoryName || row.name }}</div>
                <div class="text-xs text-gray-400">{{ row.code }}</div>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="分类编码" min-width="150">
          <template #default="{ row }">
            <span class="text-sm font-mono text-gray-600">{{ row.code }}</span>
          </template>
        </el-table-column>

        <el-table-column label="商品数量" min-width="100" align="center">
          <template #default="{ row }">
            <span class="text-sm font-bold text-[#408aee]">{{ row.productCount || 0 }}</span>
          </template>
        </el-table-column>

        <el-table-column label="排序" min-width="80" align="center">
          <template #default="{ row }">
            <span class="text-sm text-gray-600">{{ row.sort }}</span>
          </template>
        </el-table-column>

        <el-table-column label="状态" min-width="80">
          <template #default="{ row }">
            <span :class="row.status === 1 ? 'bg-green-100 text-green-600' : 'bg-gray-100 text-gray-500'" class="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-[10px] font-bold uppercase tracking-wider">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </span>
          </template>
        </el-table-column>

        <el-table-column label="操作" min-width="160" align="center">
          <template #default="{ row }">
            <div class="flex items-center justify-center gap-3">
              <el-button type="default" link size="small" class="!text-gray-500 hover:!text-[#408aee]" @click="handleEdit(row)">编辑</el-button>
              <el-button type="default" link size="small" class="!text-gray-500 hover:!text-red-500" @click="handleDelete(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 分类编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑分类' : '新建分类'"
      width="500px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item label="分类名称" prop="name">
          <el-input v-model="form.name" placeholder="如：上衣、裤子、裙子" />
        </el-form-item>
        <el-form-item label="分类编码" prop="code">
          <el-input v-model="form.code" placeholder="如：CAT001" />
        </el-form-item>
        <el-form-item label="排序" prop="sort">
          <el-input-number v-model="form.sort" :min="0" :max="999" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitLoading">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAllCategories, createCategory, updateCategory, deleteCategory, type ProductCategory } from '@/api/product'

const loading = ref(false)
const tableData = ref<any[]>([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitLoading = ref(false)
const formRef = ref()

const form = ref({
  id: null as number | null,
  name: '',
  code: '',
  sort: 0,
  status: 1
})

const formRules = {
  name: [{ required: true, message: '请输入分类名称', trigger: 'blur' }],
  code: [{ required: true, message: '请输入分类编码', trigger: 'blur' }]
}

async function loadData() {
  loading.value = true
  try {
    const res = await getAllCategories()
    if (res.code === 200) {
      tableData.value = res.data.map((c: ProductCategory) => ({
        id: c.id,
        name: c.categoryName,
        categoryName: c.categoryName,
        code: `CAT${String(c.id).padStart(3, '0')}`,
        sort: c.sort,
        status: c.status,
        productCount: 0
      }))
    }
  } catch (error) {
    ElMessage.error('加载分类列表失败')
  } finally {
    loading.value = false
  }
}

function handleCreate() {
  isEdit.value = false
  form.value = {
    id: null,
    name: '',
    code: `CAT${String(tableData.value.length + 1).padStart(3, '0')}`,
    sort: tableData.value.length + 1,
    status: 1
  }
  dialogVisible.value = true
}

function handleEdit(row: any) {
  isEdit.value = true
  form.value = { ...row }
  dialogVisible.value = true
}

async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm(
      `确定要删除分类「${row.categoryName || row.name}」吗？若分类已被商品引用则无法删除，建议改为禁用。`,
      '删除确认',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    const res = await deleteCategory(row.id)
    if (res.code === 200) {
      ElMessage.success('删除成功')
      loadData()
    } else {
      ElMessageBox.alert(
        res.message || '删除失败，可能存在商品引用',
        '无法删除',
        { confirmButtonText: '知道了', type: 'warning' }
      )
    }
  } catch (error: any) {
    // only silently ignore cancel/close
    if (error !== 'cancel' && error !== 'close') {
      ElMessageBox.alert(
        error?.message || error?.response?.data?.message || '删除失败，可能存在商品引用',
        '无法删除',
        { confirmButtonText: '知道了', type: 'warning' }
      )
    }
  }
}

async function handleSubmit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  submitLoading.value = true
  try {
    if (isEdit.value) {
      const res = await updateCategory({
        id: form.value.id!,
        categoryName: form.value.name,
        sort: form.value.sort,
        status: form.value.status
      })
      if (res.code === 200) {
        ElMessage.success('更新成功')
        dialogVisible.value = false
        loadData()
      } else {
        ElMessage.error(res.message || '更新失败')
      }
    } else {
      const res = await createCategory({
        categoryName: form.value.name,
        sort: form.value.sort,
        status: form.value.status
      })
      if (res.code === 200) {
        ElMessage.success('创建成功')
        dialogVisible.value = false
        loadData()
      } else {
        ElMessage.error(res.message || '创建失败')
      }
    }
  } catch (error: any) {
    ElMessage.error(error?.message || '操作失败')
  } finally {
    submitLoading.value = false
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.categories-page { padding: 0; }

.category-table :deep(.el-table__header th) {
  background-color: rgb(249 250 251 / 50%) !important;
  font-size: 11px !important;
  font-weight: 900 !important;
  text-transform: uppercase !important;
  letter-spacing: 0.05em !important;
  color: rgb(107 114 128) !important;
  padding: 20px 24px !important;
}

.category-table :deep(.el-table__body td) {
  padding: 16px 24px !important;
}
</style>
