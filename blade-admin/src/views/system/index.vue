<template>
  <div>
    <el-card class="mb-4">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="用户管理" name="users" />
        <el-tab-pane label="角色管理" name="roles" />
        <el-tab-pane label="权限配置" name="permissions" />
      </el-tabs>
    </el-card>

    <el-card v-show="activeTab === 'users'">
      <!-- 用户搜索筛选 -->
      <div class="flex gap-4 mb-4">
        <el-input v-model="userSearch" placeholder="搜索用户名/昵称" clearable class="w-64" @clear="loadUsers" @keyup.enter="loadUsers">
          <template #prefix><span class="material-symbols-outlined text-gray-400">search</span></template>
        </el-input>
        <el-button type="primary" @click="loadUsers">
          <span class="material-symbols-outlined text-sm mr-1">search</span>搜索
        </el-button>
        <el-button type="success" @click="openUserDialog('create')">
          <span class="material-symbols-outlined text-sm mr-1">add</span>新建用户
        </el-button>
      </div>

      <!-- 用户表格 -->
      <el-table :data="userList" v-loading="userLoading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="用户名" />
        <el-table-column prop="nickname" label="昵称" />
        <el-table-column prop="email" label="邮箱" />
        <el-table-column prop="phone" label="手机号" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="角色" min-width="180">
          <template #default="{ row }">
            <el-tag v-for="role in row.roles" :key="role.id" class="mr-1" size="small">
              {{ role.roleName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="180">
          <template #default="{ row }">
            <span class="text-sm text-gray-500">{{ formatDate(row.createTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openUserDialog('edit', row)">编辑</el-button>
            <el-button link type="warning" size="small" @click="openResetPwdDialog(row)">重置密码</el-button>
            <el-button link type="danger" size="small" @click="handleDeleteUser(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 用户分页 -->
      <div class="mt-4 flex justify-end">
        <el-pagination
          v-model:current-page="userPage"
          v-model:page-size="userSize"
          :total="userTotal"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="loadUsers"
          @current-change="loadUsers"
        />
      </div>
    </el-card>

    <el-card v-show="activeTab === 'roles'">
      <!-- 角色搜索筛选 -->
      <div class="flex gap-4 mb-4">
        <el-input v-model="roleSearch" placeholder="搜索角色名称/编码" clearable class="w-64" @clear="loadRoles" @keyup.enter="loadRoles">
          <template #prefix><span class="material-symbols-outlined text-gray-400">search</span></template>
        </el-input>
        <el-button type="primary" @click="loadRoles">
          <span class="material-symbols-outlined text-sm mr-1">search</span>搜索
        </el-button>
        <el-button type="success" @click="openRoleDialog('create')">
          <span class="material-symbols-outlined text-sm mr-1">add</span>新建角色
        </el-button>
      </div>

      <!-- 角色表格 -->
      <el-table :data="roleList" v-loading="roleLoading" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="roleName" label="角色名称" />
        <el-table-column prop="roleCode" label="角色编码" />
        <el-table-column prop="description" label="描述" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="180">
          <template #default="{ row }">
            <span class="text-sm text-gray-500">{{ formatDate(row.createTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openRoleDialog('edit', row)">编辑</el-button>
            <el-button link type="warning" size="small" @click="openRoleDialog('permission', row)">分配权限</el-button>
            <el-button link type="danger" size="small" @click="handleDeleteRole(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 角色分页 -->
      <div class="mt-4 flex justify-end">
        <el-pagination
          v-model:current-page="rolePage"
          v-model:page-size="roleSize"
          :total="roleTotal"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="loadRoles"
          @current-change="loadRoles"
        />
      </div>
    </el-card>

    <el-card v-if="activeTab === 'permissions'">
      <!-- 权限树 -->
      <div class="mb-4 flex justify-between items-center">
        <span class="text-sm text-gray-500">权限树结构</span>
        <el-button type="success" size="small" @click="openPermissionDialog('create')">
          <span class="material-symbols-outlined text-sm mr-1">add</span>新建权限
        </el-button>
      </div>

      <el-tree
        :data="permissionTree"
        :props="{ label: 'name', children: 'children' }"
        node-key="id"
        :expand-on-click-node="false"
        :default-expanded-keys="expandedPermissionIds"
        class="permission-tree"
      >
        <template #default="{ node, data }">
          <span class="flex items-center gap-2">
            <span class="text-sm">{{ node.label }}</span>
            <el-tag size="small" :type="getPermissionTypeTag(data.type)">
              {{ getPermissionTypeName(data.type) }}
            </el-tag>
            <span v-if="data.code" class="text-xs text-gray-400">{{ data.code }}</span>
            <el-tag v-if="data.maskType === 1" size="small" type="warning">脱敏</el-tag>
            <span class="ml-auto flex gap-1">
              <el-button link type="primary" size="small" @click.stop="openPermissionDialog('edit', data)">编辑</el-button>
              <el-button link type="danger" size="small" @click.stop="handleDeletePermission(data)">删除</el-button>
            </span>
          </span>
        </template>
      </el-tree>
    </el-card>

    <!-- 用户对话框 -->
    <el-dialog v-model="userDialogVisible" :title="userDialogTitle" width="500px">
      <el-form ref="userFormRef" :model="userForm" :rules="userRules" label-width="80px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="userForm.username" :disabled="userDialogMode === 'edit'" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item v-if="userDialogMode === 'create'" label="密码" prop="password">
          <el-input v-model="userForm.password" type="password" show-password placeholder="请输入密码" />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="userForm.nickname" placeholder="请输入昵称" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="userForm.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="userForm.phone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="角色" prop="roleIds">
          <el-select v-model="userForm.roleIds" multiple placeholder="请选择角色" class="w-full">
            <el-option v-for="role in allRoles" :key="role.id" :label="role.roleName" :value="role.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="userDialogMode === 'edit'" label="状态" prop="status">
          <el-radio-group v-model="userForm.status">
            <el-radio :label="1">启用</el-radio>
            <el-radio :label="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="userDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitUserForm">确定</el-button>
      </template>
    </el-dialog>

    <!-- 重置密码对话框 -->
    <el-dialog v-model="resetPwdDialogVisible" title="重置密码" width="400px">
      <el-form ref="resetPwdFormRef" :model="resetPwdForm" :rules="resetPwdRules" label-width="80px">
        <el-form-item label="用户">
          <span class="text-gray-600">{{ resetPwdForm.username }}</span>
        </el-form-item>
        <el-form-item label="新密码" prop="password">
          <el-input v-model="resetPwdForm.password" type="password" show-password placeholder="请输入新密码" />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="resetPwdForm.confirmPassword" type="password" show-password placeholder="请确认新密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetPwdDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitResetPwd">确定重置</el-button>
      </template>
    </el-dialog>

    <!-- 角色对话框 -->
    <el-dialog v-model="roleDialogVisible" :title="roleDialogTitle" width="500px">
      <el-form ref="roleFormRef" :model="roleForm" :rules="roleRules" label-width="80px">
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="roleForm.roleName" placeholder="请输入角色名称" />
        </el-form-item>
        <el-form-item label="角色编码" prop="roleCode">
          <el-input v-model="roleForm.roleCode" placeholder="请输入角色编码，如 ROLE_ADMIN" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="roleForm.description" type="textarea" rows="3" placeholder="请输入描述" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="roleForm.status">
            <el-radio :label="1">启用</el-radio>
            <el-radio :label="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitRoleForm">确定</el-button>
      </template>
    </el-dialog>

    <!-- 分配权限对话框 -->
    <el-dialog v-model="permDialogVisible" title="分配权限" width="600px">
      <div class="mb-4 text-sm text-gray-500">
        角色：<span class="font-semibold text-gray-700">{{ currentRole?.roleName }}</span>
      </div>
      <el-tree
        ref="permTreeRef"
        :data="permissionTree"
        :props="{ label: 'name', children: 'children' }"
        node-key="id"
        show-checkbox
        :check-strictly="false"
        :expand-on-click-node="false"
        :default-expanded-keys="expandedPermissionIds"
        :default-checked-keys="defaultCheckedPermissions"
        class="permission-tree"
      >
        <template #default="{ node, data }">
          <span class="flex items-center gap-2">
            <span class="text-sm">{{ node.label }}</span>
            <el-tag size="small" :type="getPermissionTypeTag(data.type)">
              {{ getPermissionTypeName(data.type) }}
            </el-tag>
          </span>
        </template>
      </el-tree>
      <template #footer>
        <el-button @click="permDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitRolePermissions">确定分配</el-button>
      </template>
    </el-dialog>

    <!-- 权限对话框 -->
    <el-dialog v-model="permissionDialogVisible" :title="permissionDialogTitle" width="500px">
      <el-form ref="permissionFormRef" :model="permissionForm" :rules="permissionRules" label-width="80px">
        <el-form-item label="权限名称" prop="name">
          <el-input v-model="permissionForm.name" placeholder="请输入权限名称" />
        </el-form-item>
        <el-form-item label="权限编码" prop="code">
          <el-input v-model="permissionForm.code" placeholder="请输入权限编码，如 btn:user:create" />
        </el-form-item>
        <el-form-item label="权限类型" prop="type">
          <el-select v-model="permissionForm.type" class="w-full">
            <el-option label="菜单" :value="1" />
            <el-option label="按钮" :value="2" />
            <el-option label="字段" :value="3" />
            <el-option label="API" :value="4" />
          </el-select>
        </el-form-item>
        <el-form-item label="所属模块" prop="module">
          <el-select v-model="permissionForm.module" clearable class="w-full">
            <el-option label="仪表盘" value="dashboard" />
            <el-option label="数据分析" value="analytics" />
            <el-option label="订单" value="order" />
            <el-option label="库存" value="inventory" />
            <el-option label="商品" value="product" />
            <el-option label="客户" value="customer" />
            <el-option label="系统" value="system" />
          </el-select>
        </el-form-item>
        <el-form-item label="路由路径" prop="path">
          <el-input v-model="permissionForm.path" placeholder="请输入路由路径" />
        </el-form-item>
        <el-form-item label="请求方法" prop="method">
          <el-select v-model="permissionForm.method" clearable class="w-full">
            <el-option label="GET" value="GET" />
            <el-option label="POST" value="POST" />
            <el-option label="PUT" value="PUT" />
            <el-option label="DELETE" value="DELETE" />
          </el-select>
        </el-form-item>
        <el-form-item label="图标" prop="icon">
          <el-input v-model="permissionForm.icon" placeholder="Material Icons 图标名" />
        </el-form-item>
        <el-form-item label="排序" prop="sort">
          <el-input-number v-model="permissionForm.sort" :min="0" class="w-full" />
        </el-form-item>
        <el-form-item label="脱敏类型" prop="maskType">
          <el-select v-model="permissionForm.maskType" class="w-full">
            <el-option label="不脱敏" :value="0" />
            <el-option label="返回null" :value="1" />
            <el-option label="返回***" :value="2" />
            <el-option label="自定义值" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="permissionForm.description" type="textarea" rows="2" placeholder="请输入描述" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="permissionDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitPermissionForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { getUserPage, getAllRoles, createUser, updateUser, deleteUser, resetUserPassword, type UserVO } from '@/api/user'
import { getRolePage, createRole, updateRole, deleteRole, getRolePermissions, type RoleVO } from '@/api/role'
import { getPermissionTree, createPermission, updatePermission, deletePermission, assignRolePermissions, type PermissionVO } from '@/api/permission'
import { formatDate } from '@/utils/format'

const activeTab = ref('users')

// ==================== 用户管理 ====================
const userList = ref<UserVO[]>([])
const userLoading = ref(false)
const userSearch = ref('')
const userPage = ref(1)
const userSize = ref(20)
const userTotal = ref(0)
const allRoles = ref<{ id: number; roleName: string }[]>([])

const userDialogVisible = ref(false)
const userDialogMode = ref<'create' | 'edit'>('create')
const userDialogTitle = computed(() => userDialogMode.value === 'create' ? '新建用户' : '编辑用户')
const userFormRef = ref<FormInstance>()
const userForm = reactive({
  id: undefined as number | undefined,
  username: '',
  password: '',
  nickname: '',
  email: '',
  phone: '',
  roleIds: [] as number[],
  status: 1,
})

const userRules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

// 重置密码相关
const resetPwdDialogVisible = ref(false)
const resetPwdFormRef = ref<FormInstance>()
const resetPwdForm = reactive({
  id: undefined as number | undefined,
  username: '',
  password: '',
  confirmPassword: '',
})
const resetPwdRules: FormRules = {
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度为6-20位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    {
      validator: (_rule: any, value: string, callback: any) => {
        if (value !== resetPwdForm.password) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
}

function openResetPwdDialog(row: UserVO) {
  resetPwdForm.id = row.id
  resetPwdForm.username = row.username
  resetPwdForm.password = ''
  resetPwdForm.confirmPassword = ''
  resetPwdDialogVisible.value = true
}

async function submitResetPwd() {
  if (!resetPwdFormRef.value) return
  await resetPwdFormRef.value.validate()
  try {
    await resetUserPassword(resetPwdForm.id!, resetPwdForm.password)
    ElMessage.success('密码重置成功')
    resetPwdDialogVisible.value = false
  } catch (e: any) {
    ElMessage.error(e.message || '重置失败')
  }
}

async function loadUsers() {
  userLoading.value = true
  try {
    const res = await getUserPage({ current: userPage.value, size: userSize.value, keyword: userSearch.value })
    userList.value = res.data.records
    userTotal.value = res.data.total
  } finally {
    userLoading.value = false
  }
}

async function loadAllRoles() {
  const res = await getAllRoles()
  allRoles.value = res.data
}

function openUserDialog(mode: 'create' | 'edit', row?: UserVO) {
  userDialogMode.value = mode
  if (mode === 'create') {
    Object.assign(userForm, { username: '', password: '', nickname: '', email: '', phone: '', roleIds: [], status: 1 })
  } else if (row) {
    Object.assign(userForm, {
      id: row.id,
      username: row.username || '',
      nickname: row.nickname || '',
      email: row.email || '',
      phone: row.phone || '',
      roleIds: row.roles?.map(r => r.id) || [],
      status: row.status,
    })
  }
  userDialogVisible.value = true
}

async function submitUserForm() {
  if (!userFormRef.value) return
  await userFormRef.value.validate()

  try {
    if (userDialogMode.value === 'create') {
      await createUser({
        username: userForm.username,
        password: userForm.password,
        nickname: userForm.nickname,
        email: userForm.email,
        phone: userForm.phone,
        roleIds: userForm.roleIds,
      })
      ElMessage.success('创建成功')
    } else {
      await updateUser({
        id: userForm.id!,
        nickname: userForm.nickname,
        email: userForm.email,
        phone: userForm.phone,
        status: userForm.status,
        roleIds: userForm.roleIds,
      })
      ElMessage.success('更新成功')
    }
    userDialogVisible.value = false
    loadUsers()
  } catch (e: any) {
    const msg = e?.message || e?.response?.data?.message || '操作失败'
    ElMessage.error(msg)
  }
}

async function handleDeleteUser(row: UserVO) {
  await ElMessageBox.confirm(`确定删除用户「${row.username}」吗？`, '提示', { type: 'warning' })
  await deleteUser(row.id)
  ElMessage.success('删除成功')
  loadUsers()
}

// ==================== 角色管理 ====================
const roleList = ref<RoleVO[]>([])
const roleLoading = ref(false)
const roleSearch = ref('')
const rolePage = ref(1)
const roleSize = ref(20)
const roleTotal = ref(0)

const roleDialogVisible = ref(false)
const roleDialogMode = ref<'create' | 'edit' | 'permission'>('create')
const roleDialogTitle = computed(() => roleDialogMode.value === 'create' ? '新建角色' : roleDialogMode.value === 'edit' ? '编辑角色' : '分配权限')
const roleFormRef = ref<FormInstance>()
const roleForm = reactive({
  id: undefined as number | undefined,
  roleName: '',
  roleCode: '',
  description: '',
  status: 1,
})

const roleRules: FormRules = {
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  roleCode: [{ required: true, message: '请输入角色编码', trigger: 'blur' }],
}

async function loadRoles() {
  roleLoading.value = true
  try {
    const res = await getRolePage({ current: rolePage.value, size: roleSize.value, keyword: roleSearch.value })
    roleList.value = res.data.records
    roleTotal.value = res.data.total
  } finally {
    roleLoading.value = false
  }
}

async function openRoleDialog(mode: 'create' | 'edit' | 'permission', row?: RoleVO) {
  roleDialogMode.value = mode
  if (mode === 'create' || mode === 'edit') {
    if (mode === 'create') {
      Object.assign(roleForm, { id: undefined, roleName: '', roleCode: '', description: '', status: 1 })
    } else if (row) {
      Object.assign(roleForm, { id: row.id, roleName: row.roleName, roleCode: row.roleCode, description: row.description || '', status: row.status })
    }
    roleDialogVisible.value = true
  } else if (mode === 'permission' && row) {
    currentRole.value = row
    defaultCheckedPermissions.value = []
    // 确保权限树数据已加载
    if (permissionTree.value.length === 0) {
      await loadPermissionTree()
      // 权限会在 loadPermissionTree 中自动加载
    } else {
      // 树已加载，直接获取角色权限
      const res = await getRolePermissions(row.id)
      defaultCheckedPermissions.value = res.data || []
    }
    permDialogVisible.value = true
  }
}

async function submitRoleForm() {
  if (!roleFormRef.value) return
  await roleFormRef.value.validate()

  try {
    if (roleDialogMode.value === 'create') {
      await createRole({
        roleName: roleForm.roleName,
        roleCode: roleForm.roleCode,
        description: roleForm.description,
        status: roleForm.status,
      })
      ElMessage.success('创建成功')
    } else {
      await updateRole({
        id: roleForm.id!,
        roleName: roleForm.roleName,
        roleCode: roleForm.roleCode,
        description: roleForm.description,
        status: roleForm.status,
      })
      ElMessage.success('更新成功')
    }
    roleDialogVisible.value = false
    loadRoles()
  } catch (e: any) {
    ElMessage.error(e.message || '操作失败')
  }
}

async function handleDeleteRole(row: RoleVO) {
  await ElMessageBox.confirm(`确定删除角色「${row.roleName}」吗？`, '提示', { type: 'warning' })
  await deleteRole(row.id)
  ElMessage.success('删除成功')
  loadRoles()
}

// ==================== 分配权限 ====================
const permDialogVisible = ref(false)
const permTreeRef = ref()
const currentRole = ref<RoleVO | null>(null)

async function loadRolePermissions(roleId: number) {
  const res = await getRolePermissions(roleId)
  defaultCheckedPermissions.value = res.data || []
}

async function submitRolePermissions() {
  if (!currentRole.value) return
  const checkedKeys = permTreeRef.value?.getCheckedKeys() || []
  try {
    await assignRolePermissions({ roleId: currentRole.value.id, permissionIds: checkedKeys })
    ElMessage.success('权限分配成功')
    permDialogVisible.value = false
  } catch (e: any) {
    ElMessage.error(e.message || '权限分配失败')
  }
}

// ==================== 权限配置 ====================
const permissionTree = ref<PermissionVO[]>([])
const expandedPermissionIds = ref<number[]>([])
const defaultCheckedPermissions = ref<number[]>([])
let pendingRoleId: number | null = null

function getPermissionTypeName(type: number) {
  const map: Record<number, string> = { 1: '菜单', 2: '按钮', 3: '字段', 4: 'API' }
  return map[type] || '未知'
}

function getPermissionTypeTag(type: number) {
  const map: Record<number, string> = { 1: '', 2: 'success', 3: 'warning', 4: 'info' }
  return map[type] || ''
}

async function loadPermissionTree() {
  const res = await getPermissionTree()
  permissionTree.value = res.data || []
  // 默认展开系统管理菜单（id=6），使其子节点（API权限）也能被正确渲染
  expandedPermissionIds.value = [6]
  // 如果有待加载的角色权限，则加载
  if (pendingRoleId !== null) {
    await loadRolePermissions(pendingRoleId)
    pendingRoleId = null
  }
}

const permissionDialogVisible = ref(false)
const permissionDialogMode = ref<'create' | 'edit'>('create')
const permissionDialogTitle = computed(() => permissionDialogMode.value === 'create' ? '新建权限' : '编辑权限')
const permissionFormRef = ref<FormInstance>()
const permissionForm = reactive({
  id: undefined as number | undefined,
  name: '',
  code: '',
  type: 2,
  module: '',
  parentId: undefined as number | undefined,
  path: '',
  method: '',
  icon: '',
  sort: 0,
  status: 1,
  maskType: 0,
  description: '',
})

const permissionRules: FormRules = {
  name: [{ required: true, message: '请输入权限名称', trigger: 'blur' }],
  code: [{ required: true, message: '请输入权限编码', trigger: 'blur' }],
  type: [{ required: true, message: '请选择权限类型', trigger: 'change' }],
}

function openPermissionDialog(mode: 'create' | 'edit', row?: PermissionVO) {
  permissionDialogMode.value = mode
  if (mode === 'create') {
    Object.assign(permissionForm, { id: undefined, name: '', code: '', type: 2, module: '', parentId: undefined, path: '', method: '', icon: '', sort: 0, status: 1, maskType: 0, description: '' })
  } else if (row) {
    Object.assign(permissionForm, {
      id: row.id,
      name: row.name,
      code: row.code,
      type: row.type,
      module: row.module || '',
      parentId: row.parentId,
      path: row.path || '',
      method: row.method || '',
      icon: row.icon || '',
      sort: row.sort,
      status: row.status,
      maskType: row.maskType || 0,
      description: row.description || '',
    })
  }
  permissionDialogVisible.value = true
}

async function submitPermissionForm() {
  if (!permissionFormRef.value) return
  await permissionFormRef.value.validate()

  try {
    if (permissionDialogMode.value === 'create') {
      await createPermission({
        name: permissionForm.name,
        code: permissionForm.code,
        type: permissionForm.type,
        module: permissionForm.module,
        parentId: permissionForm.parentId,
        path: permissionForm.path,
        method: permissionForm.method,
        icon: permissionForm.icon,
        sort: permissionForm.sort,
        status: permissionForm.status,
        maskType: permissionForm.maskType,
        description: permissionForm.description,
      })
      ElMessage.success('创建成功')
    } else {
      await updatePermission({
        id: permissionForm.id!,
        name: permissionForm.name,
        code: permissionForm.code,
        type: permissionForm.type,
        module: permissionForm.module,
        parentId: permissionForm.parentId,
        path: permissionForm.path,
        method: permissionForm.method,
        icon: permissionForm.icon,
        sort: permissionForm.sort,
        status: permissionForm.status,
        maskType: permissionForm.maskType,
        description: permissionForm.description,
      })
      ElMessage.success('更新成功')
    }
    permissionDialogVisible.value = false
    loadPermissionTree()
  } catch (e: any) {
    ElMessage.error(e.message || '操作失败')
  }
}

async function handleDeletePermission(row: PermissionVO) {
  await ElMessageBox.confirm(`确定删除权限「${row.name}」吗？`, '提示', { type: 'warning' })
  await deletePermission(row.id)
  ElMessage.success('删除成功')
  loadPermissionTree()
}

// ==================== 通用 ====================
function handleTabChange(tab: string) {
  if (tab === 'users') loadUsers()
  else if (tab === 'roles') loadRoles()
  else if (tab === 'permissions') loadPermissionTree()
}

onMounted(() => {
  loadUsers()
  loadAllRoles()
  // 预先加载权限树，避免打开分配权限对话框时卡顿
  loadPermissionTree()
})
</script>

<style scoped>
.permission-tree :deep(.el-tree-node__content) {
  height: auto;
  padding: 4px 0;
}
</style>
