<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import CatalogSelect from '@/components/CatalogSelect.vue'
import OrderLinesEditor from '@/components/OrderLinesEditor.vue'
import { cancelStocktake, confirmStocktake, createStocktake, getStocktake, getStocktakePage } from '@/api/stocktake'
import { statusTagType, stocktakeActions, stocktakeStatuses } from '@/constants/orderStatus'
import { ensureSkuLabels, ensureWarehouseLabels, skuLabel, warehouseLabel } from '@/utils/catalog'
import { formatTime } from '@/utils/format'
import { useAuthStore } from '@/store/auth'
import type { OrderLineDraft, StocktakeStatus, StocktakeVO } from '@/types/api'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const canConfirm = computed(() => auth.user?.role === 'ADMIN' || auth.user?.role === 'WAREHOUSE_MANAGER')
const detailId = computed(() => Number(route.params.id) || null)
const loading = ref(false)
const saving = ref(false)
const errorText = ref('')
const rows = ref<StocktakeVO[]>([])
const detail = ref<StocktakeVO | null>(null)
const total = ref(0)
const page = ref(1)
const size = ref(20)
const dialogVisible = ref(false)
const filters = reactive<{ warehouseId: number | null; status: StocktakeStatus | '' }>({ warehouseId: null, status: '' })
const form = reactive<{ warehouseId: number | null; remark: string; items: OrderLineDraft[] }>({ warehouseId: null, remark: '', items: [{ skuId: null, quantity: 1, countedPhysical: 0 }] })
function actions(order: StocktakeVO) { return stocktakeActions[order.status].filter(action => action !== 'confirm' || canConfirm.value) }
function tableStocktake(row: unknown): StocktakeVO { return row as StocktakeVO }
async function load() {
  loading.value = true; errorText.value = ''
  try {
    if (detailId.value) {
      detail.value = await getStocktake(detailId.value)
      await Promise.all([ensureWarehouseLabels([detail.value.warehouseId]), ensureSkuLabels(detail.value.items.map(item => item.skuId))])
    } else {
      const result = await getStocktakePage({ page: page.value, size: size.value, warehouseId: filters.warehouseId || undefined, status: filters.status || undefined })
      rows.value = result.items; total.value = result.total
      await ensureWarehouseLabels(result.items.map(item => item.warehouseId))
    }
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : '加载失败'
    if (detailId.value) detail.value = null
    else { rows.value = []; total.value = 0 }
  } finally { loading.value = false }
}
function search() { page.value = 1; void load() }
function reset() { filters.warehouseId = null; filters.status = ''; search() }
function openCreate() { form.warehouseId = null; form.remark = ''; form.items = [{ skuId: null, quantity: 1, countedPhysical: 0 }]; dialogVisible.value = true }
async function submitCreate() {
  if (!form.warehouseId) return ElMessage.warning('请选择盘点仓库')
  if (!form.items.length || form.items.some(item => !item.skuId || !Number.isInteger(item.countedPhysical) || item.countedPhysical < 0)) return ElMessage.warning('请填写完整的 SKU 和非负整数实盘数量')
  const ids = form.items.map(item => item.skuId)
  if (new Set(ids).size !== ids.length) return ElMessage.warning('盘点明细中不能重复选择同一 SKU')
  saving.value = true
  try {
    const created = await createStocktake({ warehouseId: form.warehouseId, items: form.items.map(item => ({ skuId: item.skuId!, countedPhysical: item.countedPhysical })), remark: form.remark.trim() || undefined })
    dialogVisible.value = false; ElMessage.success('盘点单创建成功')
    await router.push(`/stocktakes/${created.id}`)
  } catch { /* 请求层显示后端错误，保留表单 */ }
  finally { saving.value = false }
}
async function runAction(order: StocktakeVO, action: 'confirm' | 'cancel') {
  if (action === 'confirm' && !canConfirm.value) return
  const label = action === 'confirm' ? '确认盘点' : '取消盘点单'
  try { await ElMessageBox.confirm(`确定对 ${order.orderNo} 执行“${label}”？`, '操作确认', { type: 'warning', confirmButtonText: label, cancelButtonText: '返回' }) }
  catch { return }
  try {
    if (action === 'confirm') await confirmStocktake(order.id)
    else await cancelStocktake(order.id)
    ElMessage.success(`${label}成功`); await load()
  } catch { /* 请求层显示后端错误 */ }
}
watch(() => route.path, () => { void load() })
onMounted(() => { void load() })
</script>

<template>
  <div class="page-wrap">
    <div class="page-heading"><div><span class="eyebrow">OPERATIONS / STOCKTAKE</span><h1>{{ detailId ? '盘点单详情' : '库存盘点' }}</h1><p>记录实盘数量；系统库存快照由服务端保存。</p></div>
      <el-button v-if="detailId" @click="router.push('/stocktakes')">返回列表</el-button><el-button v-else type="primary" :icon="Plus" @click="openCreate">新建盘点单</el-button>
    </div>
    <el-alert v-if="errorText" :title="errorText" type="warning" show-icon :closable="false" class="request-alert" />
    <template v-if="!detailId">
      <section class="panel filter-panel"><el-form inline class="filter-form" @submit.prevent="search">
        <el-form-item label="仓库"><CatalogSelect v-model="filters.warehouseId" kind="warehouse" /></el-form-item>
        <el-form-item label="状态"><el-select v-model="filters.status" clearable placeholder="全部状态" class="status-select"><el-option v-for="(label, value) in stocktakeStatuses" :key="value" :label="label" :value="value" /></el-select></el-form-item>
        <div class="filter-actions"><el-button type="primary" :icon="Search" @click="search">查询</el-button><el-button :icon="Refresh" @click="reset">重置</el-button></div>
      </el-form></section>
      <section class="panel table-panel"><div class="table-heading"><div><h2>盘点单列表</h2><span>共 {{ total }} 条</span></div></div>
        <el-table v-loading="loading" :data="rows" stripe class="data-table" empty-text="暂无盘点单">
          <el-table-column prop="orderNo" label="单号" min-width="185"><template #default="{ row }"><span class="primary-cell">{{ row.orderNo }}</span></template></el-table-column>
          <el-table-column label="仓库" min-width="145"><template #default="{ row }">{{ warehouseLabel(row.warehouseId) }}</template></el-table-column>
          <el-table-column label="状态" width="105"><template #default="{ row }"><el-tag :type="statusTagType(row.status)">{{ stocktakeStatuses[row.status as StocktakeStatus] }}</el-tag></template></el-table-column>
          <el-table-column label="商品行数" width="90"><template #default="{ row }">{{ row.items.length }}</template></el-table-column>
          <el-table-column label="创建时间" min-width="165"><template #default="{ row }">{{ formatTime(row.createdAt) }}</template></el-table-column>
          <el-table-column label="操作" min-width="190" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="router.push(`/stocktakes/${row.id}`)">详情</el-button><el-button v-for="action in actions(tableStocktake(row))" :key="action" link :type="action === 'cancel' ? 'danger' : 'primary'" @click="runAction(tableStocktake(row), action)">{{ action === 'confirm' ? '确认盘点' : '取消盘点' }}</el-button></template></el-table-column>
        </el-table><div class="pagination-bar"><el-pagination v-model:current-page="page" v-model:page-size="size" :total="total" :page-sizes="[10, 20, 50, 100]" layout="total, sizes, prev, pager, next" @current-change="load" @size-change="search" /></div>
      </section>
    </template>
    <section v-else-if="detail" class="panel detail-panel"><div class="detail-title"><div><span class="eyebrow">STOCKTAKE DETAIL</span><h2>{{ detail.orderNo }}</h2></div><el-tag :type="statusTagType(detail.status)" size="large">{{ stocktakeStatuses[detail.status] }}</el-tag></div>
      <el-descriptions title="基本信息与仓库" :column="2" border>
        <el-descriptions-item label="单据编号">{{ detail.orderNo }}</el-descriptions-item><el-descriptions-item label="状态">{{ stocktakeStatuses[detail.status] }}</el-descriptions-item>
        <el-descriptions-item label="盘点仓库">{{ warehouseLabel(detail.warehouseId) }}</el-descriptions-item><el-descriptions-item label="备注">{{ detail.remark || '—' }}</el-descriptions-item>
        <el-descriptions-item label="创建人">用户 #{{ detail.createdBy }}</el-descriptions-item><el-descriptions-item label="创建时间">{{ formatTime(detail.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="确认人">{{ detail.confirmedBy ? `用户 #${detail.confirmedBy}` : '—' }}</el-descriptions-item><el-descriptions-item label="确认时间">{{ formatTime(detail.confirmedAt) }}</el-descriptions-item>
        <el-descriptions-item label="取消人">{{ detail.cancelledBy ? `用户 #${detail.cancelledBy}` : '—' }}</el-descriptions-item><el-descriptions-item label="取消时间">{{ formatTime(detail.cancelledAt) }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{ formatTime(detail.updatedAt) }}</el-descriptions-item>
      </el-descriptions>
      <h3 class="detail-section-heading">商品明细与服务端快照</h3>
      <el-table :data="detail.items" stripe class="data-table"><el-table-column label="SKU" min-width="180"><template #default="{ row }">{{ skuLabel(row.skuId) }}</template></el-table-column><el-table-column prop="snapshotPhysical" label="快照实际库存" min-width="120" align="right" /><el-table-column prop="snapshotAvailable" label="快照可用库存" min-width="120" align="right" /><el-table-column prop="snapshotLocked" label="快照锁定库存" min-width="120" align="right" /><el-table-column prop="countedPhysical" label="实盘数量" min-width="110" align="right" /></el-table>
      <div v-if="actions(detail).length" class="detail-actions"><el-button v-for="action in actions(detail)" :key="action" :type="action === 'cancel' ? 'danger' : 'primary'" plain @click="runAction(detail, action)">{{ action === 'confirm' ? '确认盘点' : '取消盘点' }}</el-button></div>
    </section>
    <el-dialog v-model="dialogVisible" title="新建盘点单" width="760px" destroy-on-close><el-form label-position="top"><el-form-item label="盘点仓库" required><CatalogSelect v-model="form.warehouseId" kind="warehouse" enabled-only /></el-form-item><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit /></el-form-item><OrderLinesEditor v-model="form.items" stocktake /></el-form><template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="submitCreate">创建盘点单</el-button></template></el-dialog>
  </div>
</template>
