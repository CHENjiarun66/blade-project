<template>
  <div class="min-h-screen bg-white">
    <!-- 侧边栏 -->
    <aside
      ref="sidebarRef"
      class="fixed left-0 top-0 h-screen bg-[#1a1c1e] z-40 flex flex-col shadow-[20px_0_40px_-15px_rgba(0,0,0,0.3)] transition-all duration-300 ease-in-out overflow-hidden"
      :style="{ width: isCollapsed ? '64px' : '220px' }"
    >
      <!-- Logo 区域 -->
      <div class="py-5 transition-all duration-300" :class="isCollapsed ? 'px-4' : 'px-6'">
        <div class="flex items-center gap-3">
          <div class="w-8 h-8 bg-[#408aee] rounded-lg flex items-center justify-center flex-shrink-0">
            <span class="material-symbols-outlined text-white text-sm">swords</span>
          </div>
          <div v-if="!isCollapsed" class="overflow-hidden whitespace-nowrap">
            <h1 class="text-lg font-black text-white tracking-tight">Blade 后台</h1>
            <p class="text-[10px] text-slate-500 font-medium tracking-widest uppercase">活力精准</p>
          </div>
        </div>
      </div>

      <!-- 导航菜单 -->
      <nav ref="navRef" class="flex flex-col gap-1 px-3">
        <div v-for="item in navItems" :key="item.path">
          <!-- 带子菜单的项 -->
          <div v-if="item.children && item.children.length">
            <div
              @click="toggleSubmenu(item.path)"
              :title="isCollapsed ? item.name : ''"
              class="rounded-xl flex items-center gap-3 py-3 transition-all cursor-pointer text-slate-400 hover:text-white hover:bg-white/5"
              :class="[
                isCollapsed ? 'justify-center px-0' : 'px-4',
                isActive(item.path) || hasActiveChild(item) ? 'bg-[#408aee]/20 text-white' : ''
              ]"
            >
              <span class="material-symbols-outlined flex-shrink-0">{{ item.icon }}</span>
              <span v-if="!isCollapsed" class="font-medium text-sm whitespace-nowrap flex-1">{{ item.name }}</span>
              <span v-if="!isCollapsed" class="material-symbols-outlined text-xs" :class="expandedMenus.has(item.path) ? 'rotate-90' : ''">chevron_right</span>
            </div>
            <!-- 子菜单 -->
            <div v-if="!isCollapsed && expandedMenus.has(item.path)" class="ml-4 mt-1 flex flex-col gap-0.5">
              <a
                v-for="child in item.children"
                :key="child.path"
                :href="child.path"
                @click.prevent="navigate(child.path)"
                class="rounded-lg flex items-center gap-3 py-2.5 px-4 transition-all text-slate-500 hover:text-white hover:bg-white/5 text-sm"
                :class="route.path === child.path ? 'bg-[#408aee] text-white' : ''"
              >
                <span class="text-xs">{{ child.name }}</span>
              </a>
            </div>
          </div>
          <!-- 普通项 -->
          <a
            v-else
            :href="item.path"
            @click.prevent="navigate(item.path)"
            :title="isCollapsed ? item.name : ''"
            class="rounded-xl flex items-center gap-3 py-3 transition-all text-slate-400 hover:text-white hover:bg-white/5"
            :class="[
              isCollapsed ? 'justify-center px-0' : 'px-4',
              isActive(item.path) ? 'bg-[#408aee] text-white shadow-lg shadow-[#408aee]/30' : ''
            ]"
          >
            <span class="material-symbols-outlined flex-shrink-0" :class="{ 'text-white': isActive(item.path) }">{{ item.icon }}</span>
            <span v-if="!isCollapsed" class="font-medium text-sm whitespace-nowrap">{{ item.name }}</span>
          </a>
        </div>
      </nav>

      <!-- 宽度切换按钮 -->
      <div class="mt-auto px-3 py-4">
        <button
          @click="toggleSidebar"
          class="flex items-center justify-center gap-2 text-slate-500 hover:text-white rounded-xl py-2 px-3 text-xs font-medium transition-all hover:bg-white/5"
          :class="isCollapsed ? 'w-full' : 'w-full'"
        >
          <span class="material-symbols-outlined text-sm">{{ isCollapsed ? 'chevron_right' : 'chevron_left' }}</span>
          <span v-if="!isCollapsed">收起</span>
        </button>
      </div>
    </aside>

    <!-- 顶部导航栏 -->
    <header
      class="fixed top-0 h-16 bg-white/80 backdrop-blur-md z-30 flex justify-between items-center shadow-sm shadow-slate-200/50 transition-all duration-300 ease-in-out"
      :style="{ left: isCollapsed ? '64px' : '220px', right: '0' }"
    >
      <!-- 左侧：页面路径 -->
      <div class="flex items-center gap-6 h-full px-6 flex-1">
        <div class="flex items-center gap-2 text-sm">
          <span class="text-slate-400">页面</span>
          <span class="text-slate-300">/</span>
          <span class="text-slate-900 font-semibold">{{ pageTitle }}</span>
        </div>
        <!-- 全局搜索 -->
        <div class="relative w-64 max-w-md focus-within:ring-2 focus-within:ring-[#408aee]/20 rounded-full transition-all">
          <span class="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 text-lg">search</span>
          <input
            class="w-full bg-gray-100 border-none rounded-full py-2 pl-10 pr-4 text-xs focus:ring-0 placeholder:text-slate-400"
            placeholder="搜索..."
            type="text"
          />
        </div>
      </div>

      <!-- 右侧：通知、帮助、用户 -->
      <div class="flex items-center gap-4 px-6">
        <button class="hover:bg-gray-100 rounded-full p-2 text-slate-500 transition-colors">
          <span class="material-symbols-outlined">notifications</span>
        </button>
        <button class="hover:bg-gray-100 rounded-full p-2 text-slate-500 transition-colors">
          <span class="material-symbols-outlined">help_outline</span>
        </button>
        <div class="h-8 w-[1px] bg-slate-200 mx-2"></div>
        <div
          class="flex items-center gap-3 cursor-pointer hover:bg-gray-100 p-1 pr-3 rounded-full transition-colors"
          @click="handleUserClick"
        >
          <img
            v-if="authStore.userInfo?.avatar"
            class="w-8 h-8 rounded-full object-cover"
            :src="authStore.userInfo.avatar"
            alt="头像"
          />
          <div v-else class="w-8 h-8 rounded-full bg-[#408aee] flex items-center justify-center text-white font-bold text-sm">
            {{ userInitials }}
          </div>
          <span class="text-sm font-semibold text-slate-700">{{ authStore.userInfo?.realName || '管理员' }}</span>
        </div>
      </div>
    </header>

    <!-- 主内容区 -->
    <main
      class="min-h-screen pt-16 transition-all duration-300 ease-in-out"
      :style="{ paddingLeft: isCollapsed ? '64px' : '220px' }"
    >
      <div class="p-8 max-w-7xl mx-auto">
        <router-view />
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

// 检查用户是否有指定权限
function hasPermission(required: string | undefined): boolean {
  if (!required) return true
  const permissions = authStore.permissions || []
  return permissions.includes(required)
}

// 是否收起侧边栏
const isCollapsed = ref(false)

// 展开的子菜单
const expandedMenus = ref(new Set<string>(['/products']))

function toggleSidebar() {
  isCollapsed.value = !isCollapsed.value
}

interface NavItem {
  name: string
  path: string
  icon?: string
  children?: NavItem[]
  permission?: string  // required permission code to show this item
}

// 过滤后的导航菜单
const navItems = computed<NavItem[]>(() => {
  const allItems: NavItem[] = [
    { name: '仪表盘', path: '/dashboard', icon: 'dashboard', permission: 'menu:dashboard' },
    { name: '订单管理', path: '/orders', icon: 'shopping_bag', permission: 'menu:order' },
    { name: '库存', path: '/inventory', icon: 'inventory_2', permission: 'menu:inventory' },
    { name: '商品', path: '/products', icon: 'checkroom', permission: 'menu:product', children: [
      { name: '商品列表', path: '/products' },
      { name: '颜色列表', path: '/products/colors' },
      { name: '尺码列表', path: '/products/sizes' },
      { name: '商品分类', path: '/products/categories' },
    ]},
    { name: '客户管理', path: '/clients', icon: 'group', permission: 'menu:customer' },
    { name: '系统管理', path: '/system', icon: 'settings', permission: 'menu:system' },
  ]

  // 递归过滤函数
  function filterItems(items: NavItem[]): NavItem[] {
    return items.filter(item => {
      if (!hasPermission(item.permission)) return false
      if (item.children && item.children.length > 0) {
        item.children = filterItems(item.children)
      }
      return true
    })
  }

  return filterItems(allItems)
})

const pageTitle = computed(() => {
  const titleMap: Record<string, string> = {
    '/dashboard': '仪表盘',
    '/orders': '订单',
    '/inventory': '库存',
    '/products': '商品',
    '/products/colors': '颜色列表',
    '/products/sizes': '尺码列表',
    '/products/categories': '商品分类',
    '/clients': '客户',
    '/system': '系统管理',
  }
  return titleMap[route.path] || 'Blade Admin'
})

const userInitials = computed(() => {
  const name = authStore.userInfo?.realName || '管理员'
  return name.slice(0, 1)
})

function isActive(path: string): boolean {
  // Exact match for leaf nodes, or parent path + slash for child routes
  return route.path === path || route.path.startsWith(path + '/')
}

function hasActiveChild(item: NavItem): boolean {
  if (!item.children) return false
  // Check if any child path matches exactly (not prefix match)
  return item.children.some(child => route.path === child.path || route.path.startsWith(child.path + '/'))
}

function toggleSubmenu(path: string) {
  if (expandedMenus.value.has(path)) {
    expandedMenus.value.delete(path)
  } else {
    expandedMenus.value.add(path)
  }
}

function navigate(path: string) {
  router.push(path)
}

function handleUserClick() {
  ElMessageBox.confirm('确定要退出登录吗？', '提示', {
    type: 'warning',
  }).then(() => {
    authStore.logout()
    router.push('/login')
  })
}
</script>

<style scoped>
/* 移除默认的 body margin */
:global(body) {
  margin: 0;
  padding: 0;
}
</style>
