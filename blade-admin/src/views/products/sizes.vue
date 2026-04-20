<template>
  <div class="sizes-page">
    <!-- 页面标题区 -->
    <div class="flex justify-between items-end mb-8">
      <div>
        <h2 class="text-2xl font-bold text-gray-900 tracking-tight mb-1">尺码列表</h2>
        <p class="text-gray-500 text-sm">管理商品的尺码选项。</p>
      </div>
      <div class="flex">
        <el-button class="!bg-gray-100 !text-gray-700 !border-none !px-5 !py-2.5 !rounded-xl !font-bold !h-auto hover:!bg-gray-200 mr-3" @click="loadData">
          <span class="material-symbols-outlined text-sm mr-1">refresh</span>
          刷新
        </el-button>
        <el-button type="primary" class="!bg-[#408aee] !border-none !px-6 !py-2.5 !rounded-xl !font-bold !h-auto hover:!bg-[#2d7be0] shadow-lg shadow-primary/20" @click="handleCreate">
          <span class="material-symbols-outlined text-sm mr-1">add_circle</span>
          新建尺码
        </el-button>
      </div>
    </div>

    <!-- 尺码表格 -->
    <div class="bg-white rounded-xl overflow-hidden shadow-sm mb-6">
      <el-table :data="tableData" class="size-table" v-loading="loading" empty-text="暂无尺码数据">
        <el-table-column label="尺码" min-width="200">
          <template #default="{ row }">
            <div class="flex items-center gap-3">
              <div class="w-10 h-10 rounded-lg bg-[#408aee]/10 flex items-center justify-center">
                <span class="text-[#408aee] font-bold">{{ row.sizeCode }}</span>
              </div>
              <div>
                <div class="text-sm font-semibold text-gray-900">{{ row.sizeCode }}</div>
                <div class="text-xs text-gray-400">排序: {{ row.sort }}</div>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="排序" min-width="120">
          <template #default="{ row }">
            <span class="text-sm font-bold text-gray-900">{{ row.sort }}</span>
          </template>
        </el-table-column>

        <el-table-column label="关联商品" min-width="100" align="center">
          <template #default="{ row }">
            <span class="text-sm font-bold text-[#408aee]">{{ row.productCount || 0 }}</span>
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

    <!-- 尺码编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑尺码' : '新建尺码'"
      width="500px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item label="尺码编码" prop="sizeCode">
          <el-input v-model="form.sizeCode" placeholder="如：M、L、XL" />
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
import { getAllSizes, createSize, updateSize, deleteSize, type ProductSize } from '@/api/product'

const loading = ref(false)
const tableData = ref<any[]>([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitLoading = ref(false)
const formRef = ref()

const form = ref({
  id: null as number | null,
  sizeCode: '',
  sort: 0,
  status: 1
})

const formRules = {
  sizeCode: [{ required: true, message: '请输入尺码编码', trigger: 'blur' }]
}

async function loadData() {
  loading.value = true
  try {
    const res = await getAllSizes()
    if (res.code === 200) {
      tableData.value = res.data.map((s: ProductSize) => ({
        ...s,
        productCount: 0
      }))
    }
  } catch (error) {
    ElMessage.error('加载尺码列表失败')
  } finally {
    loading.value = false
  }
}

function handleCreate() {
  isEdit.value = false
  form.value = { id: null, sizeCode: '', sort: tableData.value.length + 1, status: 1 }
  dialogVisible.value = true
}

function handleEdit(row: any) {
  isEdit.value = true
  form.value = { ...row }
  dialogVisible.value = true
}

async function handleDelete(row: any) {
  try {
    await ElMessageBox.confirm(`确定要删除尺码「${row.sizeCode}」吗？`, '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    const res = await deleteSize(row.id)
    if (res.code === 200) {
      ElMessage.success('删除成功')
      loadData()
    } else {
      ElMessage.error(res.message || '删除失败')
    }
  } catch {
    // 用户取消
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
      const res = await updateSize({
        id: form.value.id!,
        sizeCode: form.value.sizeCode,
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
      const res = await createSize({
        sizeCode: form.value.sizeCode,
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
.sizes-page { padding: 0; }

.size-table :deep(.el-table__header th) {
  background-color: rgb(249 250 251 / 50%) !important;
  font-size: 11px !important;
  font-weight: 900 !important;
  text-transform: uppercase !important;
  letter-spacing: 0.05em !important;
  color: rgb(107 114 128) !important;
  padding: 20px 24px !important;
}

.size-table :deep(.el-table__body td) {
  padding: 16px 24px !important;
}
</style>
