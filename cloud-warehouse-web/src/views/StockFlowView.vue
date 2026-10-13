<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { DataAnalysis, Refresh, Search } from '@element-plus/icons-vue'
import CatalogSelect from '@/components/CatalogSelect.vue'
import { getStockFlow, getStockFlowPage } from '@/api/stockFlow'
import { stockFlowBizTypes, stockFlowTypes } from '@/constants/orderStatus'
import { ensureSkuLabels, ensureWarehouseLabels, skuLabel, warehouseLabel } from '@/utils/catalog'
import { formatTime } from '@/utils/format'
import type { StockFlowBizType, StockFlowType, StockFlowVO } from '@/types/api'

const loading = ref(false)
const rows = ref<StockFlowVO[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const errorText = ref('')
const detail = ref<StockFlowVO | null>(null)
const detailVisible = ref(false)
const filters = reactive<{ warehouseId: number | null; skuId: number | null; bizType: StockFlowBizType | ''; bizId: number | null; flowType: StockFlowType | '' }>(
  { warehouseId: null, skuId: null, bizType: '', bizId: null, flowType: '' },
)
async function load() {
  loading.value = true; errorText.value = ''
  try {
    const result = await getStockFlowPage({ page: page.value, size: size.value, warehouseId: filters.warehouseId || undefined, skuId: filters.skuId || undefined,
      bizType: filters.bizType || undefined, bizId: filters.bizId || undefined, flowType: filters.flowType || undefined })
    rows.value = result.items; total.value = result.total
    await Promise.all([ensureWarehouseLabels(result.items.map(item => item.warehouseId)), ensureSkuLabels(result.items.map(item => item.skuId))])
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : '加载失败'
    rows.value = []; total.value = 0
  } finally { loading.value = false }
}
function search() { page.value = 1; void load() }
function reset() { filters.warehouseId = null; filters.skuId = null; filters.bizType = ''; filters.bizId = null; filters.flowType = ''; search() }
async function openDetail(id: number) {
  try {
    detail.value = await getStockFlow(id)
    await Promise.all([ensureWarehouseLabels([detail.value.warehouseId]), ensureSkuLabels([detail.value.skuId])])
    detailVisible.value = true
  } catch { /* 请求层显示后端错误 */ }
}
function delta(value: number) { return value > 0 ? `+${value}` : String(value) }
onMounted(() => { void load() })
</script>

<template>
  <div class="page-wrap"><div class="page-heading"><div><span class="eyebrow">INVENTORY / FLOW</span><h1>库存流水</h1><p>追踪每次库存变化的业务来源、前后数量与操作人。</p></div><div class="read-only-badge"><el-icon><DataAnalysis /></el-icon>只读视图</div></div>
    <el-alert v-if="errorText" :title="errorText" type="warning" show-icon :closable="false" class="request-alert" />
    <section class="panel filter-panel"><el-form inline class="filter-form" @submit.prevent="search">
      <el-form-item label="仓库"><CatalogSelect v-model="filters.warehouseId" kind="warehouse" /></el-form-item><el-form-item label="SKU"><CatalogSelect v-model="filters.skuId" kind="sku" /></el-form-item>
      <el-form-item label="业务类型"><el-select v-model="filters.bizType" clearable placeholder="全部业务" class="filter-select"><el-option v-for="(label, value) in stockFlowBizTypes" :key="value" :label="label" :value="value" /></el-select></el-form-item>
      <el-form-item label="业务 ID"><el-input-number v-model="filters.bizId" :min="1" :precision="0" controls-position="right" placeholder="业务 ID" /></el-form-item>
      <el-form-item label="流水类型"><el-select v-model="filters.flowType" clearable placeholder="全部类型" class="filter-select"><el-option v-for="(label, value) in stockFlowTypes" :key="value" :label="label" :value="value" /></el-select></el-form-item>
      <div class="filter-actions"><el-button type="primary" :icon="Search" @click="search">查询</el-button><el-button :icon="Refresh" @click="reset">重置</el-button></div>
    </el-form></section>
    <section class="panel table-panel"><div class="table-heading"><div><h2>流水记录</h2><span>共 {{ total }} 条</span></div></div>
      <el-table v-loading="loading" :data="rows" stripe class="data-table" empty-text="暂无流水记录">
        <el-table-column label="流水类型" min-width="125"><template #default="{ row }"><span class="primary-cell">{{ stockFlowTypes[row.flowType as StockFlowType] }}</span></template></el-table-column>
        <el-table-column label="仓库 / SKU" min-width="200"><template #default="{ row }"><span class="primary-cell">{{ warehouseLabel(row.warehouseId) }}</span><small class="cell-sub">{{ skuLabel(row.skuId) }}</small></template></el-table-column>
        <el-table-column label="实际库存变化" min-width="115" align="right"><template #default="{ row }">{{ delta(row.deltaPhysical) }}</template></el-table-column><el-table-column label="可用库存变化" min-width="115" align="right"><template #default="{ row }">{{ delta(row.deltaAvailable) }}</template></el-table-column><el-table-column label="锁定库存变化" min-width="115" align="right"><template #default="{ row }">{{ delta(row.deltaLocked) }}</template></el-table-column>
        <el-table-column label="业务来源" min-width="140"><template #default="{ row }">{{ stockFlowBizTypes[row.bizType as StockFlowBizType] }} #{{ row.bizId }}</template></el-table-column>
        <el-table-column label="操作人" width="100"><template #default="{ row }">用户 #{{ row.operatorId }}</template></el-table-column><el-table-column label="创建时间" min-width="155"><template #default="{ row }">{{ formatTime(row.createdAt) }}</template></el-table-column>
        <el-table-column label="操作" width="80" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="openDetail(row.id)">详情</el-button></template></el-table-column>
      </el-table><div class="pagination-bar"><el-pagination v-model:current-page="page" v-model:page-size="size" :total="total" :page-sizes="[10, 20, 50, 100]" layout="total, sizes, prev, pager, next" @current-change="load" @size-change="search" /></div>
    </section>
    <el-drawer v-model="detailVisible" title="库存流水详情" size="520px"><el-descriptions v-if="detail" :column="1" border>
      <el-descriptions-item label="流水编号">{{ detail.id }}</el-descriptions-item><el-descriptions-item label="流水类型">{{ stockFlowTypes[detail.flowType] }}</el-descriptions-item>
      <el-descriptions-item label="仓库">{{ warehouseLabel(detail.warehouseId) }}</el-descriptions-item><el-descriptions-item label="SKU">{{ skuLabel(detail.skuId) }}</el-descriptions-item>
      <el-descriptions-item label="业务来源">{{ stockFlowBizTypes[detail.bizType] }} #{{ detail.bizId }}</el-descriptions-item>
      <el-descriptions-item label="实际库存">{{ detail.beforePhysical }} → {{ detail.afterPhysical }}（{{ delta(detail.deltaPhysical) }}）</el-descriptions-item>
      <el-descriptions-item label="可用库存">{{ detail.beforeAvailable }} → {{ detail.afterAvailable }}（{{ delta(detail.deltaAvailable) }}）</el-descriptions-item>
      <el-descriptions-item label="锁定库存">{{ detail.beforeLocked }} → {{ detail.afterLocked }}（{{ delta(detail.deltaLocked) }}）</el-descriptions-item>
      <el-descriptions-item label="操作人">用户 #{{ detail.operatorId }}</el-descriptions-item><el-descriptions-item label="时间">{{ formatTime(detail.createdAt) }}</el-descriptions-item>
    </el-descriptions></el-drawer>
  </div>
</template>
