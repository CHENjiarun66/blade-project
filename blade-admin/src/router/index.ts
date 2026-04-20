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
        path: 'system',
        name: 'System',
        component: () => import('@/views/system/index.vue'),
        meta: { title: '系统管理', permission: 'menu:system' },
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 路由守卫
router.beforeEach((to, _from) => {
  const authStore = useAuthStore()
  const isLoginPage = to.path === '/login'

  // 如果没有 token 且不是访问登录页，跳转到登录页
  if (!authStore.token && !isLoginPage) {
    console.log('[Router Guard] No token, redirect to login')
    return '/login'
  }

  // 如果已登录且访问登录页，跳转到有权限的第一个页面
  if (isLoginPage && authStore.token) {
    console.log('[Router Guard] Already logged in, check permissions')
    // 检查是否有任何可访问的页面
    const permissions = authStore.permissions || []
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
    const hasPermission = authStore.permissions.includes(requiredPermission)
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

// 根据权限获取第一个可访问的页面
function getFirstAccessiblePage(permissions: string[]): string {
  const pagePermissionMap: Record<string, string> = {
    '/dashboard': 'menu:dashboard',
    '/orders': 'menu:order',
    '/inventory': 'menu:inventory',
    '/products': 'menu:product',
    '/clients': 'menu:customer',
    '/system': 'menu:system',
  }

  const priorityPages = ['/dashboard', '/orders', '/inventory', '/products', '/clients', '/system']
  for (const page of priorityPages) {
    const requiredPermission = pagePermissionMap[page]
    if (permissions.includes(requiredPermission)) {
      return page
    }
  }

  return '/login'  // 没有可访问的页面
}

export default router
