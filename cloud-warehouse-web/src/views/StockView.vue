<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { DataAnalysis, Refresh, Search } from '@element-plus/icons-vue'
import { getStock, getStockPage } from '@/api/stock'
import { getWarehouse, getWarehousePage } from '@/api/warehouse'
import { getSku, getSkuPage } from '@/api/sku'
import type { SkuVO, StockVO, WarehouseVO } from '@/types/api'

const loading = ref(false)
const optionLoading = ref(false)
const rows = ref<StockVO[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const filters = reactive<{ warehouseId: number | null; skuId: number | null }>({ warehouseId: null, skuId: null })
const warehouses = ref<WarehouseVO[]>([])
const skus = ref<SkuVO[]>([])
const warehouseById = reactive<Record<number, WarehouseVO>>({})
const skuById = reactive<Record<number, SkuVO>>({})
const detailVisible = ref(false)
const detail = ref<StockVO | null>(null)

async function loadWarehouseOptions(keyword = '') {
  optionLoading.value = true
  try {
    const result = await getWarehousePage({ page: 1, size: 100, name: keyword.trim() || undefined })
    warehouses.value = result.items
    result.items.forEach(item => { warehouseById[item.id] = item })
  } catch { warehouses.value = [] }
  finally { optionLoading.value = false }
}
async function loadSkuOptions(keyword = '') {
  optionLoading.value = true
  try {
    const result = await getSkuPage({ page: 1, size: 100, name: keyword.trim() || undefined })
    skus.value = result.items
    result.items.forEach(item => { skuById[item.id] = item })
  } catch { skus.value = [] }
  finally { optionLoading.value = false }
}
async function ensureLabels(items: StockVO[]) {
  await Promise.all([
    ...[...new Set(items.map(item => item.warehouseId))].filter(id => !warehouseById[id]).map(async id => {
      try { warehouseById[id] = await getWarehouse(id) } catch { /* 仍显示 ID */ }
    }),
    ...[...new Set(items.map(item => item.skuId))].filter(id => !skuById[id]).map(async id => {
      try { skuById[id] = await getSku(id) } catch { /* 仍显示 ID */ }
    }),
  ])
}
async function load() {
  loading.value = true
  try {
    const result = await getStockPage({ page: page.value, size: size.value,
      warehouseId: typeof filters.warehouseId === 'number' ? filters.warehouseId : undefined,
      skuId: typeof filters.skuId === 'number' ? filters.skuId : undefined })
    rows.value = result.items; total.value = result.total
    await ensureLabels(result.items)
  } catch { rows.value = []; total.value = 0 }
  finally { loading.value = false }
}
function search() { page.value = 1; void load() }
function reset() { filters.warehouseId = null; filters.skuId = null; search() }
async function openDetail(warehouseId: number, skuId: number) {
  try {
    detail.value = await getStock(warehouseId, skuId)
    await ensureLabels([detail.value])
    detailVisible.value = true
  } catch { /* 已提示 */ }
}
onMounted(() => { void loadWarehouseOptions(); void loadSkuOptions(); void load() })
</script>

<template>
  <div class="page-wrap">
    <div class="page-heading"><div><span class="eyebrow">INVENTORY / OVERVIEW</span><h1>库存查询</h1><p>按仓库与 SKU 查看真实库存，所有数量均由后端查询返回。</p></div>
      <div class="read-only-badge"><el-icon><DataAnalysis /></el-icon> 只读视图</div>
    </div>
    <section class="panel filter-panel"><el-form inline class="filter-form" @submit.prevent="search">
      <el-form-item label="仓库"><el-select v-model="filters.warehouseId" filterable remote clearable :value-on-clear="null" :remote-method="loadWarehouseOptions" :loading="optionLoading" placeholder="输入仓库名称" class="filter-select"><el-option v-for="item in warehouses" :key="item.id" :label="`${item.name} · ${item.code}`" :value="item.id" /></el-select></el-form-item>
      <el-form-item label="SKU"><el-select v-model="filters.skuId" filterable remote clearable :value-on-clear="null" :remote-method="loadSkuOptions" :loading="optionLoading" placeholder="输入 SKU 名称" class="filter-select"><el-option v-for="item in skus" :key="item.id" :label="`${item.name} · ${item.code}`" :value="item.id" /></el-select></el-form-item>
      <div class="filter-actions"><el-button type="primary" :icon="Search" @click="search">查询</el-button><el-button :icon="Refresh" @click="reset">重置</el-button><el-button v-if="filters.warehouseId && filters.skuId" @click="openDetail(filters.warehouseId, filters.skuId)">查询指定库存</el-button></div>
    </el-form></section>
    <section class="panel table-panel"><div class="table-heading"><div><h2>仓库库存</h2><span>仅展示已存在的库存记录 · 共 {{ total }} 条</span></div></div>
      <el-table v-loading="loading" :data="rows" stripe class="data-table stock-table" empty-text="暂无库存记录">
        <el-table-column label="仓库" min-width="145"><template #default="{ row }"><span class="primary-cell">{{ warehouseById[row.warehouseId]?.name || `仓库 #${row.warehouseId}` }}</span><small class="cell-sub">{{ warehouseById[row.warehouseId]?.code || '' }}</small></template></el-table-column>
        <el-table-column label="SKU" min-width="180"><template #default="{ row }"><span class="primary-cell">{{ skuById[row.skuId]?.name || `SKU #${row.skuId}` }}</span><small class="cell-sub">{{ skuById[row.skuId]?.code || '' }}</small></template></el-table-column>
        <el-table-column label="实际库存" min-width="110" align="right"><template #header><el-tooltip content="仓库中实际存在的数量" placement="top"><span class="tooltip-label">实际库存 ⓘ</span></el-tooltip></template><template #default="{ row }"><strong class="stock-number">{{ row.physicalStock }}</strong></template></el-table-column>
        <el-table-column label="可用库存" min-width="110" align="right"><template #header><el-tooltip content="当前还能被新业务使用的数量" placement="top"><span class="tooltip-label">可用库存 ⓘ</span></el-tooltip></template><template #default="{ row }"><strong class="stock-number available">{{ row.availableStock }}</strong></template></el-table-column>
        <el-table-column label="锁定库存" min-width="110" align="right"><template #header><el-tooltip content="已被业务预占但尚未真正出库的数量" placement="top"><span class="tooltip-label">锁定库存 ⓘ</span></el-tooltip></template><template #default="{ row }"><span class="stock-number locked">{{ row.lockedStock }}</span></template></el-table-column>
        <el-table-column prop="safetyStock" label="安全库存" width="100" align="right" />
        <el-table-column label="操作" width="84" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="openDetail(row.warehouseId, row.skuId)">详情</el-button></template></el-table-column>
      </el-table>
      <div class="pagination-bar"><el-pagination v-model:current-page="page" v-model:page-size="size" :total="total" :page-sizes="[10, 20, 50, 100]" layout="total, sizes, prev, pager, next" @current-change="load" @size-change="search" /></div>
    </section>
    <div class="stock-hint"><span class="online-dot" /> 库存数量只由明确的入库、出库、调拨或盘点业务动作改变。</div>
    <el-dialog v-model="detailVisible" title="库存详情" width="520px"><el-descriptions v-if="detail" :column="1" border>
      <el-descriptions-item label="仓库">{{ warehouseById[detail.warehouseId]?.name || `仓库 #${detail.warehouseId}` }}</el-descriptions-item>
      <el-descriptions-item label="SKU">{{ skuById[detail.skuId]?.name || `SKU #${detail.skuId}` }} <span class="muted">{{ skuById[detail.skuId]?.code || '' }}</span></el-descriptions-item>
      <el-descriptions-item label="库存记录">{{ detail.exists ? '已建立' : '尚未建立，按 0 展示' }}</el-descriptions-item>
      <el-descriptions-item label="实际库存">{{ detail.physicalStock }}</el-descriptions-item><el-descriptions-item label="可用库存">{{ detail.availableStock }}</el-descriptions-item>
      <el-descriptions-item label="锁定库存">{{ detail.lockedStock }}</el-descriptions-item><el-descriptions-item label="安全库存">{{ detail.safetyStock }}</el-descriptions-item>
    </el-descriptions></el-dialog>
  </div>
</template>
