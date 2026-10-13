<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { DataAnalysis, Refresh, Search } from '@element-plus/icons-vue'
import CatalogSelect from '@/components/CatalogSelect.vue'
import { getStockAlertPage } from '@/api/stockAlert'
import { statusTagType, stockAlertStatuses } from '@/constants/orderStatus'
import { ensureSkuLabels, ensureWarehouseLabels, skuLabel, warehouseLabel } from '@/utils/catalog'
import { formatTime } from '@/utils/format'
import type { StockAlertStatus, StockAlertVO } from '@/types/api'

const loading = ref(false)
const rows = ref<StockAlertVO[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const errorText = ref('')
const filters = reactive<{ warehouseId: number | null; skuId: number | null; status: StockAlertStatus }>(
  { warehouseId: null, skuId: null, status: 'ACTIVE' },
)
async function load() {
  loading.value = true; errorText.value = ''
  try {
    const result = await getStockAlertPage({ page: page.value, size: size.value, warehouseId: filters.warehouseId || undefined, skuId: filters.skuId || undefined, status: filters.status })
    rows.value = result.items; total.value = result.total
    await Promise.all([ensureWarehouseLabels(result.items.map(item => item.warehouseId)), ensureSkuLabels(result.items.map(item => item.skuId))])
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : '加载失败'
    rows.value = []; total.value = 0
  } finally { loading.value = false }
}
function search() { page.value = 1; void load() }
function reset() { filters.warehouseId = null; filters.skuId = null; filters.status = 'ACTIVE'; search() }
onMounted(() => { void load() })
</script>

<template>
  <div class="page-wrap"><div class="page-heading"><div><span class="eyebrow">INVENTORY / ALERTS</span><h1>库存预警</h1><p>默认查看预警中的 SKU；预警状态由服务端维护。</p></div><div class="read-only-badge"><el-icon><DataAnalysis /></el-icon>只读视图</div></div>
    <el-alert v-if="errorText" :title="errorText" type="warning" show-icon :closable="false" class="request-alert" />
    <section class="panel filter-panel"><el-form inline class="filter-form" @submit.prevent="search"><el-form-item label="仓库"><CatalogSelect v-model="filters.warehouseId" kind="warehouse" /></el-form-item><el-form-item label="SKU"><CatalogSelect v-model="filters.skuId" kind="sku" /></el-form-item><el-form-item label="状态"><el-select v-model="filters.status" class="status-select"><el-option v-for="(label, value) in stockAlertStatuses" :key="value" :label="label" :value="value" /></el-select></el-form-item><div class="filter-actions"><el-button type="primary" :icon="Search" @click="search">查询</el-button><el-button :icon="Refresh" @click="reset">重置</el-button></div></el-form></section>
    <section class="panel table-panel"><div class="table-heading"><div><h2>预警记录</h2><span>共 {{ total }} 条</span></div></div>
      <el-table v-loading="loading" :data="rows" stripe class="data-table" empty-text="暂无预警记录">
        <el-table-column label="仓库" min-width="140"><template #default="{ row }">{{ warehouseLabel(row.warehouseId) }}</template></el-table-column><el-table-column label="SKU" min-width="170"><template #default="{ row }">{{ skuLabel(row.skuId) }}</template></el-table-column>
        <el-table-column prop="availableStock" label="当前可用库存" min-width="115" align="right" /><el-table-column prop="safetyStock" label="安全库存" min-width="100" align="right" />
        <el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="statusTagType(row.status)">{{ stockAlertStatuses[row.status as StockAlertStatus] }}</el-tag></template></el-table-column>
        <el-table-column label="触发时间" min-width="160"><template #default="{ row }">{{ formatTime(row.triggeredAt) }}</template></el-table-column><el-table-column label="解决时间" min-width="160"><template #default="{ row }">{{ formatTime(row.resolvedAt) }}</template></el-table-column>
      </el-table><div class="pagination-bar"><el-pagination v-model:current-page="page" v-model:page-size="size" :total="total" :page-sizes="[10, 20, 50, 100]" layout="total, sizes, prev, pager, next" @current-change="load" @size-change="search" /></div>
    </section>
  </div>
</template>
