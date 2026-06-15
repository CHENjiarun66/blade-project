<template>
  <div class="clients-page page-container">
    <!-- 页面标题区 -->
    <div class="flex justify-between items-end mb-8">
      <div>
        <h2 class="text-2xl font-bold text-gray-900 tracking-tight mb-1">客户管理</h2>
        <p class="text-gray-500 text-sm">管理您的服装店客户信息。</p>
      </div>
      <div class="flex">
        <el-button class="!bg-gray-100 !text-gray-700 !border-none !px-5 !py-2.5 !rounded-xl !font-bold !h-auto hover:!bg-gray-200 mr-3" @click="loadData">
          <span class="material-symbols-outlined text-sm mr-1">refresh</span>
          刷新
        </el-button>
        <el-button type="primary" class="!bg-[#408aee] !border-none !px-6 !py-2.5 !rounded-xl !font-bold !h-auto hover:!bg-[#2d7be0] shadow-lg shadow-primary/20" @click="handleAdd">
          <span class="material-symbols-outlined text-sm mr-1">add_circle</span>
          新建客户
        </el-button>
      </div>
    </div>

    <!-- 筛选区域 -->
    <div class="bg-white rounded-xl p-6 mb-6 shadow-sm flex flex-wrap items-center gap-6">
      <div class="w-[280px]">
        <label class="block text-[10px] font-bold uppercase tracking-wider text-gray-500 mb-2 ml-1">关键字搜索</label>
        <el-input
          v-model="searchQuery"
          placeholder="搜索客户名称或电话"
          class="client-search-input"
          clearable
          @clear="handleSearch"
          @keyup.enter="handleSearch"
        >
          <template #prefix>
            <span class="material-symbols-outlined text-gray-400 text-sm">search</span>
          </template>
        </el-input>
      </div>

      <div class="ml-auto flex items-end gap-3">
        <el-button class="!bg-gray-100 !text-gray-700 !border-none !px-5 !py-2.5 !rounded-xl !font-bold !h-auto hover:!bg-gray-200" @click="handleReset">
          <span class="material-symbols-outlined text-sm mr-1">filter_list</span>
          重置筛选
        </el-button>
        <el-button type="primary" class="!bg-[#408aee] !border-none !px-5 !py-2.5 !rounded-xl !font-bold !h-auto hover:!bg-[#2d7be0]" @click="handleSearch">
          <span class="material-symbols-outlined text-sm mr-1">search</span>
          搜索
        </el-button>
      </div>
    </div>

    <!-- 客户表格 -->
    <div class="bg-white rounded-xl overflow-hidden shadow-sm mb-6">
      <el-table v-loading="loading" :data="tableData" class="client-table" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" />
        <el-table-column label="客户名称" min-width="200">
          <template #default="{ row }">
            <div class="flex items-center gap-3">
              <div class="w-9 h-9 rounded-full bg-[#408aee]/10 flex items-center justify-center text-[#408aee] font-bold text-xs">
                {{ row.name.slice(0, 2).toUpperCase() }}
              </div>
              <div>
                <div class="text-sm font-semibold text-gray-900">{{ row.name }}</div>
                <div class="text-xs text-gray-400">{{ formatDate(row.createTime) }}</div>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="联系电话" min-width="160">
          <template #default="{ row }">
            <div class="text-sm text-gray-500">
              <span v-if="row.countryCode" class="mr-0.5">{{ row.countryCode }}</span>
              {{ row.phones && row.phones.length > 0 ? row.phones.join(', ') : '-' }}
            </div>
          </template>
        </el-table-column>

        <el-table-column label="国家" min-width="100">
          <template #default="{ row }">
            <span v-if="row.countryName" class="text-sm">
              {{ row.countryName }}
            </span>
            <span v-else-if="row.countryCode" class="text-sm text-gray-600">
              {{ getCountryNameZh(row.countryCode) }}
            </span>
            <span v-else class="text-sm text-gray-300">-</span>
          </template>
        </el-table-column>

        <el-table-column label="地址" min-width="180">
          <template #default="{ row }">
            <span class="text-sm text-gray-500 truncate block max-w-[180px]" :title="row.address">{{ row.address || '-' }}</span>
          </template>
        </el-table-column>

        <el-table-column label="备注" min-width="150">
          <template #default="{ row }">
            <span class="text-sm text-gray-500 truncate block max-w-[150px]" :title="row.remark">{{ row.remark || '-' }}</span>
          </template>
        </el-table-column>

        <el-table-column label="订单数" min-width="100" align="center">
          <template #default="{ row }">
            <span class="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-[10px] font-bold uppercase tracking-wider bg-[#408aee]/10 text-[#408aee]">
              {{ row.orderCount || 0 }} 单
            </span>
          </template>
        </el-table-column>

        <el-table-column label="操作" min-width="220" align="center">
          <template #default="{ row }">
            <div class="flex items-center justify-center gap-2">
              <el-button type="default" link size="small" class="!text-gray-500 hover:!text-[#408aee]" @click="handleDetail(row)">详情</el-button>
              <el-button type="default" link size="small" class="!text-gray-500 hover:!text-[#408aee]" @click="handleEdit(row)">编辑</el-button>
              <el-button type="danger" link size="small" class="!text-gray-400 hover:!text-red-500" @click="handleDelete(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="flex items-center justify-between px-6 py-4 bg-gray-50/50">
        <p class="text-xs text-gray-500 font-medium">
          显示第 <span class="text-gray-900">{{ (pagination.current - 1) * pagination.size + 1 }}</span>-<span class="text-gray-900">{{ Math.min(pagination.current * pagination.size, pagination.total) }}</span> 条，共 <span class="text-gray-900">{{ pagination.total }}</span> 条客户
        </p>
        <el-pagination
          v-model:current-page="pagination.current"
          :page-size="pagination.size"
          :total="pagination.total"
          layout="prev, pager, next"
          background
          @current-change="loadData"
        />
      </div>
    </div>

    <!-- 新建/编辑客户弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogMode === 'add' ? '新建客户' : '编辑客户'" width="560px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="80px" class="customer-form">
        <el-form-item label="客户名称" prop="name">
          <el-input v-model="formData.name" placeholder="请输入客户名称" />
        </el-form-item>

        <!-- 国家区号 -->
        <el-form-item label="国家区号">
          <CountryCodeSelect v-model="formData.countryCode" class="w-full" />
        </el-form-item>

        <!-- 多电话输入 -->
        <el-form-item label="联系电话">
          <div class="w-full">
            <div v-for="(_, index) in formData.phones" :key="index" class="flex items-center gap-2 mb-2">
              <el-input
                v-model="formData.phones[index]"
                :placeholder="`请输入本地号码 ${index + 1}`"
                class="flex-1"
              >
                <template #prefix v-if="formData.countryCode">
                  <span class="text-xs text-gray-400">{{ formData.countryCode }}</span>
                </template>
              </el-input>
              <el-button
                v-if="formData.phones.length > 1"
                type="danger"
                plain
                size="small"
                @click="removePhone(index)"
              >
                删除
              </el-button>
            </div>
            <el-button type="default" plain size="small" @click="addPhone">
              <span class="material-symbols-outlined text-sm mr-1">add</span>
              添加电话
            </el-button>
          </div>
        </el-form-item>

        <el-form-item label="地址" prop="address">
          <el-input v-model="formData.address" placeholder="请输入客户地址" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="formData.remark" type="textarea" :rows="3" placeholder="请输入备注信息" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getCustomerPage, createCustomer, updateCustomer, deleteCustomer } from '@/api/customer'
import type { CustomerVO, CustomerPageDTO } from '@/api/customer'
import CountryCodeSelect from '@/components/CountryCodeSelect.vue'
import { getCountryNameZh } from '@/data/countries'
import { formatDate } from '@/utils/format'

const router = useRouter()

const loading = ref(false)
const submitLoading = ref(false)
const searchQuery = ref('')
const tableData = ref<CustomerVO[]>([])
const dialogVisible = ref(false)
const dialogMode = ref<'add' | 'edit'>('add')
const formRef = ref()

const pagination = reactive({
  current: 1,
  size: 20,
  total: 0
})

const formData = reactive({
  id: 0,
  name: '',
  phones: [''] as string[],  // 电话列表，至少有一个空输入框
  address: '',
  remark: '',
  countryCode: ''
})

const formRules = {
  name: [{ required: true, message: '请输入客户名称', trigger: 'blur' }]
}

// 添加一个电话输入框
function addPhone() {
  formData.phones.push('')
}

// 移除一个电话输入框
function removePhone(index: number) {
  if (formData.phones.length > 1) {
    formData.phones.splice(index, 1)
  }
}

function handleSelectionChange() {
  // TODO: 批量操作
}

async function loadData() {
  loading.value = true
  try {
    const params: CustomerPageDTO = {
      current: pagination.current,
      size: pagination.size,
      keyword: searchQuery.value || undefined
    }
    const res = await getCustomerPage(params)
    tableData.value = res.data.records
    pagination.total = res.data.total
  } catch (error) {
    console.error('加载客户列表失败:', error)
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.current = 1
  loadData()
}

function handleReset() {
  searchQuery.value = ''
  pagination.current = 1
  loadData()
}

function handleAdd() {
  dialogMode.value = 'add'
  Object.assign(formData, { id: 0, name: '', phones: [''], address: '', remark: '', countryCode: '' })
  dialogVisible.value = true
}

function handleEdit(row: CustomerVO) {
  dialogMode.value = 'edit'
  formData.id = row.id
  formData.name = row.name
  formData.phones = row.phones && row.phones.length > 0 ? [...row.phones] : ['']
  formData.address = row.address || ''
  formData.remark = row.remark || ''
  formData.countryCode = row.countryCode || ''
  dialogVisible.value = true
}

function handleDetail(row: CustomerVO) {
  router.push(`/customers/${row.id}`)
}

async function handleDelete(row: CustomerVO) {
  try {
    await ElMessageBox.confirm(`确定要删除客户「${row.name}」吗？删除后不可恢复。`, '删除客户', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteCustomer(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除客户失败:', error)
    }
  }
}

async function handleSubmit() {
  if (!formRef.value) return

  try {
    await formRef.value.validate()
    submitLoading.value = true

    // 过滤掉空电话，保留非空的
    const validPhones = formData.phones.filter(p => p && p.trim() !== '')

    if (dialogMode.value === 'add') {
      if (validPhones.length === 0) {
        ElMessage.error('请至少输入一个联系电话')
        return
      }
      await createCustomer({
        name: formData.name,
        phones: validPhones,
        address: formData.address || undefined,
        remark: formData.remark || undefined,
        countryCode: formData.countryCode || undefined
      })
      ElMessage.success('创建成功')
    } else {
      await updateCustomer({
        id: formData.id,
        name: formData.name,
        phones: validPhones,
        address: formData.address || undefined,
        remark: formData.remark || undefined,
        countryCode: formData.countryCode || undefined
      })
      ElMessage.success('更新成功')
    }

    dialogVisible.value = false
    loadData()
  } catch (error) {
    console.error('提交失败:', error)
  } finally {
    submitLoading.value = false
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.clients-page {
  padding: 0;
}

.client-search-input :deep(.el-input__wrapper) {
  background-color: rgb(255 255 255) !important;
  border-radius: 12px !important;
  border: 1px solid #e5e7eb !important;
  height: 44px !important;
}

.client-search-input :deep(.el-input__inner) {
  font-size: 14px !important;
  color: #1a1a2e !important;
}

.client-table :deep(.el-table__header th) {
  background-color: rgb(249 250 251 / 50%) !important;
  font-size: 11px !important;
  font-weight: 900 !important;
  text-transform: uppercase !important;
  letter-spacing: 0.05em !important;
  color: rgb(107 114 128) !important;
  padding: 20px 24px !important;
}

.client-table :deep(.el-table__body td) {
  padding: 20px 24px !important;
}

.client-table :deep(.el-table__row:hover > td) {
  background-color: rgb(249 250 251 / 30%) !important;
}

.customer-form :deep(.el-form-item__label) {
  font-weight: 500;
}

/* 分页样式 */
.clients-page :deep(.el-pagination.is-background .el-pager li.is-active) {
  background-color: #408aee !important;
}
</style>
