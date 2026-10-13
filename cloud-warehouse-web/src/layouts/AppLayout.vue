<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { Box, DataBoard, Files, Grid, House, Tickets, User, Document, Bell, Download, Upload, Switch, Memo } from '@element-plus/icons-vue'
import { useAuthStore } from '@/store/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const activePath = computed(() => route.path.replace(/\/(inbounds|outbounds|transfers|stocktakes)\/\d+$/, '/$1'))
const pageTitle = computed(() => String(route.meta.title || '管理后台'))

async function logout() {
  try {
    await ElMessageBox.confirm('确定退出当前账号？', '退出登录', { type: 'warning', confirmButtonText: '退出', cancelButtonText: '取消' })
  } catch { return }
  auth.clearSession()
  router.replace('/login')
}
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar">
      <div class="brand">
        <span class="brand-mark"><Box :size="22" /></span>
        <div><strong>云仓智配</strong><small>仓储协同管理平台</small></div>
      </div>
      <div class="sidebar-scroll">
        <div class="nav-caption">工作台</div>
        <el-menu :default-active="activePath" router class="side-menu">
          <el-menu-item index="/dashboard"><el-icon><House /></el-icon><span>首页</span></el-menu-item>
          <div class="nav-caption">基础资料</div>
          <el-menu-item index="/warehouses"><el-icon><Grid /></el-icon><span>仓库管理</span></el-menu-item>
          <el-menu-item index="/sku-categories"><el-icon><Files /></el-icon><span>SKU 分类</span></el-menu-item>
          <el-menu-item index="/skus"><el-icon><Tickets /></el-icon><span>SKU 管理</span></el-menu-item>
          <div class="nav-caption">库存管理</div>
          <el-menu-item index="/stocks"><el-icon><DataBoard /></el-icon><span>库存查询</span></el-menu-item>
          <el-menu-item index="/stock-flows"><el-icon><Document /></el-icon><span>库存流水</span></el-menu-item>
          <el-menu-item index="/stock-alerts"><el-icon><Bell /></el-icon><span>库存预警</span></el-menu-item>
          <div class="nav-caption">仓储作业</div>
          <el-menu-item index="/inbounds"><el-icon><Download /></el-icon><span>入库管理</span></el-menu-item>
          <el-menu-item index="/outbounds"><el-icon><Upload /></el-icon><span>出库管理</span></el-menu-item>
          <el-menu-item index="/transfers"><el-icon><Switch /></el-icon><span>调拨管理</span></el-menu-item>
          <el-menu-item index="/stocktakes"><el-icon><Memo /></el-icon><span>库存盘点</span></el-menu-item>
        </el-menu>
      </div>
      <div class="sidebar-foot"><span class="online-dot" /> V1.0 · 业务页面</div>
    </aside>

    <div class="workspace">
      <header class="topbar">
        <div class="topbar-heading"><span class="topbar-crumb">云仓智配 /</span> {{ pageTitle }}</div>
        <el-dropdown trigger="click" @command="logout">
          <div class="user-pill"><span class="user-avatar"><el-icon><User /></el-icon></span>
            <span class="user-name">{{ auth.user?.username || '用户' }}</span>
            <span class="user-role">{{ auth.user?.role === 'ADMIN' ? '管理员' : '仓库成员' }}</span>
          </div>
          <template #dropdown><el-dropdown-menu><el-dropdown-item command="logout">退出登录</el-dropdown-item></el-dropdown-menu></template>
        </el-dropdown>
      </header>
      <main class="main-content"><router-view /></main>
    </div>
  </div>
</template>
