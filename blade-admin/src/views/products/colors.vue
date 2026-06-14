<template>
  <div class="colors-page">
    <!-- 页面标题区 -->
    <div class="flex justify-between items-end mb-8">
      <div>
        <h2 class="text-2xl font-bold text-gray-900 tracking-tight mb-1">颜色列表</h2>
        <p class="text-gray-500 text-sm">管理商品的颜色选项。</p>
      </div>
      <div class="flex">
        <el-button class="!bg-gray-100 !text-gray-700 !border-none !px-5 !py-2.5 !rounded-xl !font-bold !h-auto hover:!bg-gray-200 mr-3" @click="loadData">
          <span class="material-symbols-outlined text-sm mr-1">refresh</span>
          刷新
        </el-button>
        <el-button type="primary" class="!bg-[#408aee] !border-none !px-6 !py-2.5 !rounded-xl !font-bold !h-auto hover:!bg-[#2d7be0] shadow-lg shadow-primary/20" @click="handleCreate">
          <span class="material-symbols-outlined text-sm mr-1">add_circle</span>
          新建颜色
        </el-button>
      </div>
    </div>

    <!-- 颜色表格 -->
    <div class="bg-white rounded-xl overflow-hidden shadow-sm mb-6">
      <el-table :data="tableData" class="color-table" v-loading="loading" empty-text="暂无颜色数据">
        <el-table-column label="颜色" min-width="200">
          <template #default="{ row }">
            <div class="flex items-center gap-3">
              <div class="w-8 h-8 rounded-lg border border-gray-200" :style="{ backgroundColor: row.colorCode }"></div>
              <div>
                <div class="text-sm font-semibold text-gray-900">{{ row.colorName }}</div>
                <div class="text-xs text-gray-400">{{ row.colorCode }}</div>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="色值" min-width="120">
          <template #default="{ row }">
            <div class="flex items-center gap-2">
              <div class="w-6 h-6 rounded border border-gray-200" :style="{ backgroundColor: row.colorCode }"></div>
              <span class="text-sm font-mono text-gray-600">{{ row.colorCode }}</span>
            </div>
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

    <!-- 颜色编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑颜色' : '新建颜色'"
      width="500px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item label="颜色名称" prop="colorName">
          <el-input v-model="form.colorName" placeholder="如：藏青" />
        </el-form-item>
        <el-form-item label="颜色编码" prop="colorCode">
          <el-input v-model="form.colorCode" placeholder="如：#1A3C5E">
            <template #append>
              <input type="color" v-model="form.colorCode" class="w-8 h-8 p-0 border-0 cursor-pointer" />
            </template>
          </el-input>
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
import { getAllColors, createColor, updateColor, deleteColor, type ProductColor } from '@/api/product'

const loading = ref(false)
const tableData = ref<any[]>([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitLoading = ref(false)
const formRef = ref()

const form = ref({
  id: null as number | null,
  colorName: '',
  colorCode: '#000000',
  status: 1
})

const formRules = {
  colorName: [{ required: true, message: '请输入颜色名称', trigger: 'blur' }],
  colorCode: [{ required: true, message: '请输入颜色编码', trigger: 'blur' }]
}

async function loadData() {
  loading.value = true
  try {
    const res = await getAllColors()
    if (res.code === 200) {
      tableData.value = res.data.map((c: ProductColor) => ({
        ...c,
        productCount: 0
      }))
    }
  } catch (error) {
    ElMessage.error('加载颜色列表失败')
  } finally {
    loading.value = false
  }
}

function handleCreate() {
  isEdit.value = false
  form.value = { id: null, colorName: '', colorCode: '#000000', status: 1 }
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
      `确定要删除颜色「${row.colorName}」吗？若颜色已被商品引用则无法删除，建议改为禁用。`,
      '删除确认',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    const res = await deleteColor(row.id)
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
      const res = await updateColor({
        id: form.value.id!,
        colorName: form.value.colorName,
        colorCode: form.value.colorCode,
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
      const res = await createColor({
        colorName: form.value.colorName,
        colorCode: form.value.colorCode,
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
.colors-page { padding: 0; }

.color-table :deep(.el-table__header th) {
  background-color: rgb(249 250 251 / 50%) !important;
  font-size: 11px !important;
  font-weight: 900 !important;
  text-transform: uppercase !important;
  letter-spacing: 0.05em !important;
  color: rgb(107 114 128) !important;
  padding: 20px 24px !important;
}

.color-table :deep(.el-table__body td) {
  padding: 16px 24px !important;
}
</style>
