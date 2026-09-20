<template>
  <div class="page-container">
    <el-card>
      <div class="flex flex-wrap items-center gap-3 mb-4">
        <el-input
          v-model="keyword"
          placeholder="搜索档口编码/名称"
          clearable
          class="w-64"
          @clear="reload"
          @keyup.enter="reload"
        >
          <template #prefix><span class="material-symbols-outlined text-gray-400">search</span></template>
        </el-input>
        <el-select v-model="statusFilter" placeholder="状态" clearable class="w-32" @change="reload">
          <el-option label="启用" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>
        <el-button type="primary" class="touch-btn" :loading="loading" @click="loadOutlets">
          <span class="material-symbols-outlined text-sm mr-1">search</span>搜索
        </el-button>
        <el-button v-if="can('btn:outlet:create')" type="success" class="touch-btn" @click="openDialog('create')">
          <span class="material-symbols-outlined text-sm mr-1">add</span>新建档口
        </el-button>
      </div>

      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="outletCode" label="编码" width="120" />
        <el-table-column prop="outletName" label="名称" min-width="140" show-overflow-tooltip />
        <el-table-column prop="outletType" label="类型" width="100" />
        <el-table-column label="默认" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.isTenantDefault === 1" type="warning" size="small">默认</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="boundUserCount" label="绑定用户" width="90" align="right" />
        <el-table-column prop="orderCount" label="订单" width="80" align="right" />
        <el-table-column prop="draftCount" label="草稿" width="80" align="right" />
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="can('btn:outlet:edit')"
              link type="primary" size="small"
              @click="openDialog('edit', row)"
            >编辑</el-button>
            <el-button
              v-if="can('btn:outlet:edit') && row.isTenantDefault !== 1 && row.status === 1"
              link type="warning" size="small"
              :loading="rowActionId === row.id"
              @click="setDefault(row)"
            >设为默认</el-button>
            <el-button
              v-if="can('btn:outlet:disable')"
              link :type="row.status === 1 ? 'danger' : 'success'" size="small"
              :loading="rowActionId === row.id"
              @click="toggleStatus(row)"
            >{{ row.status === 1 ? '禁用' : '启用' }}</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="mt-4 flex justify-end">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="loadOutlets"
          @current-change="loadOutlets"
        />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogMode === 'create' ? '新建档口' : '编辑档口'" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="档口编码" prop="outletCode">
          <el-input v-model="form.outletCode" :disabled="dialogMode === 'edit'" placeholder="稳定编码，如 YL" />
          <div v-if="dialogMode === 'edit'" class="text-xs text-gray-400 mt-1">档口编码创建后不可修改</div>
        </el-form-item>
        <el-form-item label="档口名称" prop="outletName">
          <el-input v-model="form.outletName" placeholder="档口名称" />
        </el-form-item>
        <el-form-item label="类型">
          <el-input v-model="form.outletType" placeholder="如 STORE" />
        </el-form-item>
        <el-form-item label="联系人">
          <el-input v-model="form.contactName" />
        </el-form-item>
        <el-form-item label="电话">
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-form-item label="地址">
          <el-input v-model="form.address" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" class="w-full" />
        </el-form-item>
        <el-form-item label="默认档口">
          <el-switch v-model="form.isTenantDefault" :active-value="1" :inactive-value="0" />
          <span class="text-xs text-gray-400 ml-2">设为默认会清除原默认档口</span>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button class="touch-btn" @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" class="touch-btn" :loading="submitting" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { getOutletPage, createOutlet, updateOutlet, updateOutletStatus, type OutletVO } from '@/api/outlet'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
function can(code: string) {
  return authStore.permissions.includes(code)
}

const list = ref<OutletVO[]>([])
const loading = ref(false)
const keyword = ref('')
const statusFilter = ref<number | undefined>(undefined)
const page = ref(1)
const size = ref(20)
const total = ref(0)
const rowActionId = ref<number | null>(null)

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const submitting = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({
  id: undefined as number | undefined,
  outletCode: '',
  outletName: '',
  outletType: '',
  contactName: '',
  phone: '',
  address: '',
  sort: 0,
  isTenantDefault: 0,
  remark: '',
})

const rules: FormRules = {
  outletCode: [{ required: true, message: '请输入档口编码', trigger: 'blur' }],
  outletName: [{ required: true, message: '请输入档口名称', trigger: 'blur' }],
}

async function loadOutlets() {
  loading.value = true
  try {
    const res = await getOutletPage({
      current: page.value,
      size: size.value,
      keyword: keyword.value || undefined,
      status: statusFilter.value,
    })
    list.value = res.data.records
    total.value = res.data.total
  } catch (e: any) {
    ElMessage.error(e?.message || e?.response?.data?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function reload() {
  page.value = 1
  loadOutlets()
}

function openDialog(mode: 'create' | 'edit', row?: OutletVO) {
  dialogMode.value = mode
  if (mode === 'create') {
    Object.assign(form, {
      id: undefined, outletCode: '', outletName: '', outletType: '', contactName: '',
      phone: '', address: '', sort: 0, isTenantDefault: 0, remark: '',
    })
  } else if (row) {
    Object.assign(form, {
      id: row.id,
      outletCode: row.outletCode,
      outletName: row.outletName,
      outletType: row.outletType || '',
      contactName: row.contactName || '',
      phone: row.phone || '',
      address: row.address || '',
      sort: row.sort ?? 0,
      isTenantDefault: row.isTenantDefault ?? 0,
      remark: row.remark || '',
    })
  }
  dialogVisible.value = true
}

async function submitForm() {
  if (!formRef.value) return
  await formRef.value.validate()
  submitting.value = true
  try {
    const payload = {
      outletCode: form.outletCode.trim(),
      outletName: form.outletName.trim(),
      outletType: form.outletType || undefined,
      contactName: form.contactName || undefined,
      phone: form.phone || undefined,
      address: form.address || undefined,
      sort: form.sort,
      isTenantDefault: form.isTenantDefault,
      remark: form.remark || undefined,
    }
    if (dialogMode.value === 'create') {
      await createOutlet(payload)
      ElMessage.success('创建成功')
    } else {
      await updateOutlet(form.id!, payload)
      ElMessage.success('更新成功')
    }
    dialogVisible.value = false
    loadOutlets()
  } catch (e: any) {
    ElMessage.error(e?.message || e?.response?.data?.message || '操作失败')
  } finally {
    submitting.value = false
  }
}

async function setDefault(row: OutletVO) {
  await ElMessageBox.confirm(`确定将「${row.outletName}」设为租户默认档口吗？原默认档口将被清除。`, '提示', { type: 'warning' })
  rowActionId.value = row.id
  try {
    await updateOutlet(row.id, {
      outletCode: row.outletCode,
      outletName: row.outletName,
      outletType: row.outletType,
      contactName: row.contactName,
      phone: row.phone,
      address: row.address,
      sort: row.sort ?? 0,
      isTenantDefault: 1,
      remark: row.remark,
    })
    ElMessage.success('已设为默认')
    loadOutlets()
  } catch (e: any) {
    ElMessage.error(e?.message || e?.response?.data?.message || '操作失败')
  } finally {
    rowActionId.value = null
  }
}

async function toggleStatus(row: OutletVO) {
  const disabling = row.status === 1
  const refHint = `当前绑定 ${row.boundUserCount ?? 0} 个用户、${row.orderCount ?? 0} 张订单、${row.draftCount ?? 0} 张草稿。`
  const defaultHint = row.isTenantDefault === 1 ? '该档口是租户默认档口，禁用后将清除默认标记。' : ''
  await ElMessageBox.confirm(
    disabling
      ? `禁用后「${row.outletName}」不再出现在新建订单选项，历史数据仍保留。${refHint}${defaultHint}确定禁用？`
      : `确定启用「${row.outletName}」吗？`,
    '提示',
    { type: 'warning' },
  )
  rowActionId.value = row.id
  try {
    await updateOutletStatus(row.id, disabling ? 0 : 1)
    ElMessage.success(disabling ? '已禁用' : '已启用')
    loadOutlets()
  } catch (e: any) {
    ElMessage.error(e?.message || e?.response?.data?.message || '操作失败')
  } finally {
    rowActionId.value = null
  }
}

onMounted(loadOutlets)
</script>

<style scoped>
.touch-btn {
  min-height: 44px;
}
</style>
