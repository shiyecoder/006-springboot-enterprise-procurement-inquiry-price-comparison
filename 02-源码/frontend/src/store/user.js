import { reactive, computed } from 'vue'

/**
 * 轻量用户状态：保存登录态、用户信息、按角色下发的菜单树。
 * 使用 reactive 单例，避免为了一个状态引入额外依赖。
 */
const state = reactive({
  token: localStorage.getItem('pi_token') || '',
  user: JSON.parse(localStorage.getItem('pi_user') || 'null'),
  menus: JSON.parse(localStorage.getItem('pi_menus') || '[]')
})

export function useUserStore() {
  const isLogin = computed(() => !!state.token)
  const isRequester = computed(() => state.user?.roleCode === 'REQUESTER')

  function restore() {
    state.token = localStorage.getItem('pi_token') || ''
    state.user = JSON.parse(localStorage.getItem('pi_user') || 'null')
    state.menus = JSON.parse(localStorage.getItem('pi_menus') || '[]')
  }

  function setLogin(data) {
    state.token = data.token
    state.user = data.user
    state.menus = data.menus || []
    localStorage.setItem('pi_token', data.token)
    localStorage.setItem('pi_user', JSON.stringify(data.user))
    localStorage.setItem('pi_menus', JSON.stringify(state.menus))
  }

  function setUser(user) {
    state.user = user
    localStorage.setItem('pi_user', JSON.stringify(user))
  }

  function logout() {
    state.token = ''
    state.user = null
    state.menus = []
    localStorage.removeItem('pi_token')
    localStorage.removeItem('pi_user')
    localStorage.removeItem('pi_menus')
  }

  return { state, isLogin, isRequester, restore, setLogin, setUser, logout }
}
