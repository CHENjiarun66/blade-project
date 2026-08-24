import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录' },
  },
  {
    path: '/catalog',
    name: 'Catalog',
    component: () => import('@/views/catalog/index.vue'),
    meta: { title: '现货选款', permission: 'data:catalog:view' },
  },
  {
    path: '/',
    component: () => import('@/views/layout/index.vue'),
    children: [
      {
        path: '',
        redirect: '/dashboard',
      },
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '仪表盘', permission: 'menu:dashboard' },
      },
      {
        path: 'analytics',
        name: 'Analytics',
        component: () => import('@/views/analytics/index.vue'),
        meta: { title: '数据分析', permission: 'menu:analytics' },
      },
      {
        path: 'orders',
        name: 'Orders',
        component: () => import('@/views/orders/index.vue'),
        meta: { title: '订单管理', permission: 'menu:order' },
      },
      {
        path: 'orders/new',
        name: 'OrderNew',
        component: () => import('@/views/orders/new.vue'),
        meta: { title: '新建订单', permission: 'menu:order' },
      },
      {
        path: 'orders/quick',
        name: 'OrderQuick',
        component: () => import('@/views/orders/quick.vue'),
        meta: { title: '快速录单', permission: 'menu:order' },
      },
      {
        path: 'orders/:id',
        name: 'OrderDetail',
        component: () => import('@/views/orders/detail.vue'),
        meta: { title: '订单详情', permission: 'menu:order' },
      },
      {
        path: 'inventory',
        name: 'Inventory',
        component: () => import('@/views/inventory/index.vue'),
        meta: { title: '库存管理', permission: 'menu:inventory' },
      },
      {
        path: 'products',
        name: 'Products',
        component: () => import('@/views/products/index.vue'),
        meta: { title: '商品管理', permission: 'menu:product' },
      },
      {
        path: 'products/colors',
        name: 'ProductColors',
        component: () => import('@/views/products/colors.vue'),
        meta: { title: '颜色列表', permission: 'menu:product' },
      },
      {
        path: 'products/sizes',
        name: 'ProductSizes',
        component: () => import('@/views/products/sizes.vue'),
        meta: { title: '尺码列表', permission: 'menu:product' },
      },
      {
        path: 'products/categories',
        name: 'ProductCategories',
        component: () => import('@/views/products/categories.vue'),
        meta: { title: '商品分类', permission: 'menu:product' },
      },
      {
        path: 'clients',
        name: 'Clients',
        component: () => import('@/views/clients/index.vue'),
        meta: { title: '客户管理', permission: 'menu:customer' },
      },
      {
        path: 'customers/:id',
        name: 'CustomerDetail',
        component: () => import('@/views/customers/detail.vue'),
        meta: { title: '客户详情', permission: 'menu:customer' },
      },
      {
        path: 'system',
        name: 'System',
        component: () => import('@/views/system/index.vue'),
        meta: { title: '系统管理', permission: 'menu:system' },
      },
      {
        path: 'files',
        name: 'Files',
        component: () => import('@/views/files/index.vue'),
        meta: { title: '文件中心', permission: 'menu:file' },
      },
      {
        path: 'whatsapp',
        name: 'WhatsappArchive',
        component: () => import('@/views/whatsapp/index.vue'),
        meta: { title: 'WhatsApp 归档', permission: 'menu:whatsapp' },
      },
      {
        path: 'personal',
        name: 'Personal',
        component: () => import('@/views/personal/index.vue'),
        meta: { title: '个人中心' },
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 路由守卫
router.beforeEach(async (to, _from) => {
  const authStore = useAuthStore()
  const isLoginPage = to.path === '/login'

  // 如果没有 token 且不是访问登录页，跳转到登录页
  if (!authStore.token && !isLoginPage) {
    console.log('[Router Guard] No token, redirect to login')
    return {
      path: '/login',
      query: { redirect: to.fullPath },
    }
  }

  // 刷新页面后 Pinia 内存会清空；如果 token 还在但权限/用户信息缺失，先尝试恢复登录态。
  if (authStore.token && (authStore.permissions.length === 0 || !authStore.userInfo)) {
    try {
      const { getAuthCodes, getUserInfo } = await import('@/api/auth')
      const [userInfoRes, codesRes] = await Promise.all([
        authStore.userInfo ? Promise.resolve(authStore.userInfo) : getUserInfo(),
        authStore.permissions.length > 0 ? Promise.resolve(authStore.permissions) : getAuthCodes(),
      ])
      if (!authStore.userInfo) {
        authStore.setUserInfo({
          userId: String((userInfoRes as any).userId || '1'),
          username: (userInfoRes as any).username || '',
          realName: (userInfoRes as any).realName || '管理员',
          avatar: (userInfoRes as any).avatar,
          roles: (userInfoRes as any).roles,
        })
      }
      if (authStore.permissions.length === 0) {
        authStore.setPermissions(codesRes as unknown as string[])
      }
    } catch (error) {
      console.warn('[Router Guard] Failed to restore auth state, redirect to login', error)
      authStore.logout()
      if (!isLoginPage) {
        return {
          path: '/login',
          query: { redirect: to.fullPath },
        }
      }
    }
  }

  // 如果已登录且访问登录页，跳转到有权限的第一个页面
  if (isLoginPage && authStore.token) {
    console.log('[Router Guard] Already logged in, check permissions')
    // 检查是否有任何可访问的页面
    const permissions = authStore.permissions || []
    const redirect = getSafeRedirect(to.query.redirect)
    if (redirect && canAccessPath(redirect, permissions)) {
      return redirect
    }
    const firstPage = getFirstAccessiblePage(permissions)
    if (firstPage !== '/login') {
      return firstPage
    } else {
      // 没有可访问的页面，清除token重新登录
      authStore.logout()
      return true // 继续当前导航到登录页
    }
  }

  // 检查路由权限
  const requiredPermission = to.meta.permission as string | undefined
  if (requiredPermission) {
    const roles = authStore.userInfo?.roles || []
    const hasRoleFallback = requiredPermission === 'menu:analytics' && (roles.includes('ROLE_ADMIN') || roles.includes('ROLE_OWNER'))
    const hasPermission = hasRoleFallback || authStore.permissions.includes(requiredPermission)
    if (!hasPermission) {
      console.log('[Router Guard] No permission for:', requiredPermission, 'user permissions:', authStore.permissions)
      // 没有权限，跳转到有权限的第一个页面
      const permissions = authStore.permissions || []
      const firstPage = getFirstAccessiblePage(permissions)
      if (firstPage !== '/login') {
        return firstPage
      } else {
        // 没有可访问的页面，清除token重新登录
        authStore.logout()
        return '/login'
      }
    }
  }

  return true
})

function getSafeRedirect(redirect: unknown): string {
  if (typeof redirect !== 'string') return ''
  if (!redirect.startsWith('/') || redirect.startsWith('//')) return ''
  if (redirect.startsWith('/login')) return ''
  return redirect
}

function canAccessPath(path: string, permissions: string[]): boolean {
  const pathname = path.split('?')[0].split('#')[0]
  const pagePermissionMap: Record<string, string> = {
    '/dashboard': 'menu:dashboard',
    '/analytics': 'menu:analytics',
    '/orders': 'menu:order',
    '/inventory': 'menu:inventory',
    '/products': 'menu:product',
    '/clients': 'menu:customer',
    '/files': 'menu:file',
    '/whatsapp': 'menu:whatsapp',
    '/system': 'menu:system',
    '/catalog': 'data:catalog:view',
  }

  const match = Object.keys(pagePermissionMap)
    .sort((a, b) => b.length - a.length)
    .find((prefix) => pathname === prefix || pathname.startsWith(`${prefix}/`))

  if (!match) return false
  return permissions.includes(pagePermissionMap[match])
}

// 根据权限获取第一个可访问的页面
function getFirstAccessiblePage(permissions: string[]): string {
  const pagePermissionMap: Record<string, string> = {
    '/dashboard': 'menu:dashboard',
    '/analytics': 'menu:analytics',
    '/orders': 'menu:order',
    '/inventory': 'menu:inventory',
    '/products': 'menu:product',
    '/clients': 'menu:customer',
    '/files': 'menu:file',
    '/whatsapp': 'menu:whatsapp',
    '/system': 'menu:system',
    '/catalog': 'data:catalog:view',
  }

  const priorityPages = ['/dashboard', '/analytics', '/orders', '/inventory', '/products', '/clients', '/whatsapp', '/files', '/system', '/catalog']
  for (const page of priorityPages) {
    const requiredPermission = pagePermissionMap[page]
    if (permissions.includes(requiredPermission)) {
      return page
    }
  }

  return '/login'  // 没有可访问的页面
}

export default router
