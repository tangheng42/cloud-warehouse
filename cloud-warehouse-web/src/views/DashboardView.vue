<script setup lang="ts">
import { ArrowRight, Bell, DataBoard, Document, Download, Files, Grid, Memo, Switch, Tickets, Upload } from '@element-plus/icons-vue'
import { useAuthStore } from '@/store/auth'

const auth = useAuthStore()
const entries = [
  { title: '仓库管理', desc: '维护仓库档案与启停状态', path: '/warehouses', icon: Grid, index: '01', ready: true },
  { title: 'SKU 分类', desc: '整理库存商品的分类结构', path: '/sku-categories', icon: Files, index: '02', ready: true },
  { title: 'SKU 管理', desc: '维护 SKU 基础信息与安全库存', path: '/skus', icon: Tickets, index: '03', ready: true },
  { title: '库存查询', desc: '按仓库与 SKU 查看真实库存', path: '/stocks', icon: DataBoard, index: '04', ready: true },
  { title: '入库管理', desc: '创建、确认与取消入库单', path: '/inbounds', icon: Download, index: '05', ready: true },
  { title: '出库管理', desc: '库存预占、释放与出库', path: '/outbounds', icon: Upload, index: '06', ready: true },
  { title: '调拨管理', desc: '源仓发运与目标仓收货', path: '/transfers', icon: Switch, index: '07', ready: true },
  { title: '库存盘点', desc: '实盘登记与差异确认', path: '/stocktakes', icon: Memo, index: '08', ready: true },
  { title: '库存流水', desc: '查看库存数量变化明细', path: '/stock-flows', icon: Document, index: '09', ready: true },
  { title: '库存预警', desc: '关注低于安全库存的商品', path: '/stock-alerts', icon: Bell, index: '10', ready: true },
]
</script>

<template>
  <div class="dashboard-page">
    <div class="dashboard-hero">
      <div>
        <span class="eyebrow light">WORKSPACE / V1.0</span>
        <h1>你好，{{ auth.user?.username || '欢迎回来' }}</h1>
        <p>统一管理基础资料、仓储作业与库存追踪。</p>
      </div>
      <div class="hero-signal" aria-hidden="true"><span /><span /><span /><span /></div>
    </div>
    <div class="section-heading"><div><span class="eyebrow">QUICK ACCESS</span><h2>功能入口</h2></div><span>V1.0 功能已接入后端接口</span></div>
    <div class="dashboard-grid">
      <router-link v-for="item in entries" :key="item.path" :to="item.path" class="feature-card">
        <div class="feature-top"><span class="feature-icon"><component :is="item.icon" /></span><small>{{ item.ready ? '已联调' : '待后端' }}</small></div>
        <h3>{{ item.title }}</h3><p>{{ item.desc }}</p>
        <div class="feature-link">进入模块 <el-icon><ArrowRight /></el-icon></div>
      </router-link>
    </div>
    <div class="dashboard-note"><span class="online-dot" /><span>库存与单据结果来自后端；遇到业务冲突时请根据提示核对单据状态。</span></div>
  </div>
</template>
