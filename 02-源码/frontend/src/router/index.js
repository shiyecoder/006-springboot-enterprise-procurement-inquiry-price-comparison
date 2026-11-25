import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/login', component: () => import('../views/Login.vue'), meta: { public: true } },
  {
    path: '/',
    component: () => import('../layout/AppLayout.vue'),
    children: [
      { path: '', redirect: '/dashboard' },
      { path: 'dashboard', component: () => import('../views/Dashboard.vue'), meta: { title: '采购分析看板' } },
      { path: 'requests', component: () => import('../views/RequestList.vue'), meta: { title: '采购需求池' } },
      { path: 'requests/:id', component: () => import('../views/RequestDetail.vue'), meta: { title: '需求详情' } },
      { path: 'inquiry', component: () => import('../views/InquiryList.vue'), meta: { title: '询价比价单' } },
      { path: 'inquiry/:id', component: () => import('../views/InquiryDetail.vue'), meta: { title: '询价详情' } },
      { path: 'quote', component: () => import('../views/QuoteDesk.vue'), meta: { title: '供应商报价台' } },
      { path: 'comparison', component: () => import('../views/Comparison.vue'), meta: { title: '综合比价矩阵' } },
      { path: 'comparison/:id', component: () => import('../views/Comparison.vue'), meta: { title: '综合比价矩阵' } },
      { path: 'rules', component: () => import('../views/RuleConfig.vue'), meta: { title: '比价规则配置' } },
      { path: 'orders', component: () => import('../views/OrderList.vue'), meta: { title: '采购单管理' } },
      { path: 'orders/:id', component: () => import('../views/OrderDetail.vue'), meta: { title: '采购单详情' } },
      { path: 'receipt', component: () => import('../views/ReceiptEntry.vue'), meta: { title: '待到货验收' } },
      { path: 'receipts', component: () => import('../views/ReceiptList.vue'), meta: { title: '验收记录' } },
      { path: 'returns', component: () => import('../views/ReturnList.vue'), meta: { title: '退货登记' } },
      { path: 'materials', component: () => import('../views/MaterialList.vue'), meta: { title: '物料档案' } },
      { path: 'suppliers', component: () => import('../views/SupplierList.vue'), meta: { title: '供应商档案' } },
      { path: 'users', component: () => import('../views/UserList.vue'), meta: { title: '用户管理' } },
      { path: 'roles', component: () => import('../views/RoleList.vue'), meta: { title: '角色管理' } },
      { path: 'menus', component: () => import('../views/MenuList.vue'), meta: { title: '菜单管理' } },
      { path: 'logs', component: () => import('../views/LogList.vue'), meta: { title: '操作日志' } },
      { path: 'messages', component: () => import('../views/MessageList.vue'), meta: { title: '消息中心' } },
      { path: 'profile', component: () => import('../views/Profile.vue'), meta: { title: '个人中心' } },
      { path: 'portal', component: () => import('../views/PortalHome.vue'), meta: { title: '我的需求' } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach((to) => {
  const token = localStorage.getItem('pi_token')
  if (to.meta.public) return true
  if (!token) return { path: '/login', query: { redirect: to.fullPath } }
  return true
})

export default router
