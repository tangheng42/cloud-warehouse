import { createRouter, createWebHistory } from 'vue-router'
import { pinia } from '@/store'
import { useAuthStore } from '@/store/auth'
import { getCurrentUser } from '@/api/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue'), meta: { title: '登录' } },
    {
      path: '/',
      component: () => import('@/layouts/AppLayout.vue'),
      redirect: '/dashboard',
      children: [
        { path: 'dashboard', component: () => import('@/views/DashboardView.vue'), meta: { title: '首页' } },
        { path: 'warehouses', component: () => import('@/views/WarehouseView.vue'), meta: { title: '仓库管理' } },
        { path: 'sku-categories', component: () => import('@/views/CategoryView.vue'), meta: { title: 'SKU 分类' } },
        { path: 'skus', component: () => import('@/views/SkuView.vue'), meta: { title: 'SKU 管理' } },
        { path: 'stocks', component: () => import('@/views/StockView.vue'), meta: { title: '库存查询' } },
        { path: 'stock-flows', component: () => import('@/views/StockFlowView.vue'), meta: { title: '库存流水' } },
        { path: 'stock-alerts', component: () => import('@/views/StockAlertView.vue'), meta: { title: '库存预警' } },
        { path: 'inbounds', component: () => import('@/views/OrderView.vue'), props: { kind: 'inbound' }, meta: { title: '入库管理' } },
        { path: 'inbounds/:id', component: () => import('@/views/OrderView.vue'), props: { kind: 'inbound' }, meta: { title: '入库单详情' } },
        { path: 'outbounds', component: () => import('@/views/OrderView.vue'), props: { kind: 'outbound' }, meta: { title: '出库管理' } },
        { path: 'outbounds/:id', component: () => import('@/views/OrderView.vue'), props: { kind: 'outbound' }, meta: { title: '出库单详情' } },
        { path: 'transfers', component: () => import('@/views/OrderView.vue'), props: { kind: 'transfer' }, meta: { title: '调拨管理' } },
        { path: 'transfers/:id', component: () => import('@/views/OrderView.vue'), props: { kind: 'transfer' }, meta: { title: '调拨单详情' } },
        { path: 'stocktakes', component: () => import('@/views/StocktakeView.vue'), meta: { title: '库存盘点' } },
        { path: 'stocktakes/:id', component: () => import('@/views/StocktakeView.vue'), meta: { title: '盘点单详情' } },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/dashboard' },
  ],
})

router.beforeEach(async (to) => {
  document.title = `${String(to.meta.title || '管理后台')} · 云仓智配`
  const auth = useAuthStore(pinia)
  if (!auth.token) return to.path === '/login' ? true : { path: '/login', query: { redirect: to.fullPath } }
  if (!auth.user) {
    try {
      auth.setUser(await getCurrentUser())
    } catch {
      auth.clearSession()
      return to.path === '/login' ? true : { path: '/login', query: { redirect: to.fullPath } }
    }
  }
  if (to.path === '/login') return '/dashboard'
  return true
})

export default router
