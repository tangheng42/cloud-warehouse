<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import CatalogSelect from '@/components/CatalogSelect.vue'
import OrderLinesEditor from '@/components/OrderLinesEditor.vue'
import { getInboundPage, getInbound, createInbound, confirmInbound, cancelInbound } from '@/api/inbound'
import { getOutboundPage, getOutbound, createOutbound, reserveOutbound, releaseOutbound, confirmOutbound, cancelOutbound } from '@/api/outbound'
import { getTransferPage, getTransfer, createTransfer, reserveTransfer, releaseTransfer, dispatchTransfer, receiveTransfer, cancelTransfer } from '@/api/transfer'
import { inboundActions, inboundStatuses, outboundActions, outboundStatuses, statusTagType, transferActions, transferStatuses } from '@/constants/orderStatus'
import { ensureSkuLabels, ensureWarehouseLabels, skuLabel, warehouseLabel } from '@/utils/catalog'
import { formatTime } from '@/utils/format'
import type { InboundStatus, InboundVO, OrderLineDraft, OutboundStatus, OutboundVO, TransferStatus, TransferVO } from '@/types/api'

type Kind = 'inbound' | 'outbound' | 'transfer'
type Order = InboundVO | OutboundVO | TransferVO
type Action = 'reserve' | 'release' | 'confirm' | 'cancel' | 'dispatch' | 'receive'
const props = defineProps<{ kind: Kind }>()
const route = useRoute()
const router = useRouter()
const meta = { inbound: { title: '入库管理', singular: '入库单', path: '/inbounds', desc: '创建、确认与取消入库单' }, outbound: { title: '出库管理', singular: '出库单', path: '/outbounds', desc: '预占、释放与确认出库' }, transfer: { title: '调拨管理', singular: '调拨单', path: '/transfers', desc: '源仓预占、发运及目标仓收货' } }
const config = computed(() => meta[props.kind])
const detailId = computed(() => Number(route.params.id) || null)
const loading = ref(false)
const saving = ref(false)
const rows = ref<Order[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const errorText = ref('')
const detail = ref<Order | null>(null)
const dialogVisible = ref(false)
const filters = reactive<{ warehouseId: number | null; sourceWarehouseId: number | null; targetWarehouseId: number | null; status: string }>(
  { warehouseId: null, sourceWarehouseId: null, targetWarehouseId: null, status: '' },
)
const form = reactive<{ warehouseId: number | null; sourceWarehouseId: number | null; targetWarehouseId: number | null; remark: string; items: OrderLineDraft[] }>(
  { warehouseId: null, sourceWarehouseId: null, targetWarehouseId: null, remark: '', items: [{ skuId: null, quantity: 1, countedPhysical: 0 }] },
)
const statuses = computed(() => props.kind === 'inbound' ? inboundStatuses : props.kind === 'outbound' ? outboundStatuses : transferStatuses)
function statusLabel(status: string) { return statuses.value[status as keyof typeof statuses.value] || status }
function actions(order: Order): Action[] {
  if (props.kind === 'inbound') return [...(inboundActions[order.status as InboundStatus] || [])]
  if (props.kind === 'outbound') return [...(outboundActions[order.status as OutboundStatus] || [])]
  return [...(transferActions[order.status as TransferStatus] || [])]
}
// Element Plus 将表格插槽行标为 DefaultRow；实际数据由 rows: Order[] 提供。
function tableOrder(row: unknown): Order { return row as Order }
function actionLabel(action: Action) {
  if (action === 'confirm') return props.kind === 'inbound' ? '确认入库' : '确认出库'
  if (action === 'reserve') return props.kind === 'transfer' ? '调拨预占' : '预占库存'
  if (action === 'release') return props.kind === 'transfer' ? '调拨释放' : '释放库存'
  return { cancel: '取消单据', dispatch: '发运', receive: '收货' }[action]
}
function affectedWarehouseIds(orders: Order[]) { return orders.flatMap(order => 'warehouseId' in order ? [order.warehouseId] : [order.sourceWarehouseId, order.targetWarehouseId]) }
async function load() {
  loading.value = true; errorText.value = ''
  if (detailId.value) detail.value = null
  try {
    if (detailId.value) {
      const id = detailId.value
      detail.value = props.kind === 'inbound' ? await getInbound(id) : props.kind === 'outbound' ? await getOutbound(id) : await getTransfer(id)
      await Promise.all([ensureWarehouseLabels(affectedWarehouseIds([detail.value])), ensureSkuLabels(detail.value.items.map(item => item.skuId))])
    } else {
      const pagination = { page: page.value, size: size.value }
      const result = props.kind === 'inbound'
        ? await getInboundPage({ ...pagination, warehouseId: filters.warehouseId || undefined, status: (filters.status || undefined) as InboundStatus | undefined })
        : props.kind === 'outbound'
          ? await getOutboundPage({ ...pagination, warehouseId: filters.warehouseId || undefined, status: (filters.status || undefined) as OutboundStatus | undefined })
          : await getTransferPage({ ...pagination, sourceWarehouseId: filters.sourceWarehouseId || undefined, targetWarehouseId: filters.targetWarehouseId || undefined, status: (filters.status || undefined) as TransferStatus | undefined })
      rows.value = result.items; total.value = result.total
      await ensureWarehouseLabels(affectedWarehouseIds(result.items))
    }
  } catch (error) {
    errorText.value = error instanceof Error ? error.message : '加载失败'
    if (detailId.value) detail.value = null
    else { rows.value = []; total.value = 0 }
  } finally { loading.value = false }
}
function search() { page.value = 1; void load() }
function reset() { filters.warehouseId = null; filters.sourceWarehouseId = null; filters.targetWarehouseId = null; filters.status = ''; search() }
function openCreate() {
  form.warehouseId = null; form.sourceWarehouseId = null; form.targetWarehouseId = null; form.remark = ''
  form.items = [{ skuId: null, quantity: 1, countedPhysical: 0 }]
  dialogVisible.value = true
}
async function submitCreate() {
  if (props.kind === 'transfer') {
    if (!form.sourceWarehouseId || !form.targetWarehouseId) return ElMessage.warning('请选择源仓库和目标仓库')
    if (form.sourceWarehouseId === form.targetWarehouseId) return ElMessage.warning('源仓库和目标仓库不能相同')
  } else if (!form.warehouseId) return ElMessage.warning('请选择仓库')
  if (!form.items.length || form.items.some(item => !item.skuId || !Number.isInteger(item.quantity) || item.quantity <= 0)) return ElMessage.warning('请填写完整的 SKU 和正整数数量')
  const ids = form.items.map(item => item.skuId)
  if (new Set(ids).size !== ids.length) return ElMessage.warning('商品明细中不能重复选择同一 SKU')
  const items = form.items.map(item => ({ skuId: item.skuId!, quantity: item.quantity }))
  const remark = form.remark.trim() || undefined
  saving.value = true
  try {
    const created = props.kind === 'inbound' ? await createInbound({ warehouseId: form.warehouseId!, items, remark })
      : props.kind === 'outbound' ? await createOutbound({ warehouseId: form.warehouseId!, items, remark })
        : await createTransfer({ sourceWarehouseId: form.sourceWarehouseId!, targetWarehouseId: form.targetWarehouseId!, items, remark })
    dialogVisible.value = false
    ElMessage.success(`${config.value.singular}创建成功`)
    await router.push(`${config.value.path}/${created.id}`)
  } catch { /* 请求层显示后端错误，保留表单 */ }
  finally { saving.value = false }
}
async function runAction(order: Order, action: Action) {
  const label = actionLabel(action)
  try { await ElMessageBox.confirm(`确定对 ${order.orderNo} 执行“${label}”？`, '操作确认', { type: 'warning', confirmButtonText: label, cancelButtonText: '返回' }) }
  catch { return }
  try {
    if (props.kind === 'inbound') {
      if (action === 'confirm') await confirmInbound(order.id)
      else if (action === 'cancel') await cancelInbound(order.id)
    } else if (props.kind === 'outbound') {
      if (action === 'reserve') await reserveOutbound(order.id)
      else if (action === 'release') await releaseOutbound(order.id)
      else if (action === 'confirm') await confirmOutbound(order.id)
      else if (action === 'cancel') await cancelOutbound(order.id)
    } else {
      if (action === 'reserve') await reserveTransfer(order.id)
      else if (action === 'release') await releaseTransfer(order.id)
      else if (action === 'dispatch') await dispatchTransfer(order.id)
      else if (action === 'receive') await receiveTransfer(order.id)
      else if (action === 'cancel') await cancelTransfer(order.id)
    }
    ElMessage.success(`${label}成功`)
    await load()
  } catch { /* 请求层显示后端错误 */ }
}
const eventRows = computed(() => {
  const order = detail.value
  if (!order) return []
  const result: { label: string; by?: number; at?: string }[] = [{ label: '创建', by: order.createdBy, at: order.createdAt }]
  if ('reservedAt' in order && order.reservedAt) result.push({ label: '预占', by: order.reservedBy, at: order.reservedAt })
  if ('releasedAt' in order && order.releasedAt) result.push({ label: '释放', by: order.releasedBy, at: order.releasedAt })
  if ('dispatchedAt' in order && order.dispatchedAt) result.push({ label: '发运', by: order.dispatchedBy, at: order.dispatchedAt })
  if ('receivedAt' in order && order.receivedAt) result.push({ label: '收货', by: order.receivedBy, at: order.receivedAt })
  if ('confirmedAt' in order && order.confirmedAt) result.push({ label: '确认', by: order.confirmedBy, at: order.confirmedAt })
  if (order.cancelledAt) result.push({ label: '取消', by: order.cancelledBy, at: order.cancelledAt })
  return result
})
watch(() => [route.path, props.kind], () => { void load() })
onMounted(() => { void load() })
</script>

<template>
  <div class="page-wrap">
    <div class="page-heading"><div><span class="eyebrow">OPERATIONS / {{ kind.toUpperCase() }}</span><h1>{{ detailId ? `${config.singular}详情` : config.title }}</h1><p>{{ config.desc }}；实际结果以服务端返回为准。</p></div>
      <el-button v-if="detailId" @click="router.push(config.path)">返回列表</el-button>
      <el-button v-else type="primary" :icon="Plus" @click="openCreate">新建{{ config.singular }}</el-button>
    </div>
    <el-alert v-if="errorText" :title="errorText" type="warning" show-icon :closable="false" class="request-alert" />
    <template v-if="!detailId">
      <section class="panel filter-panel"><el-form inline class="filter-form" @submit.prevent="search">
        <template v-if="kind === 'transfer'">
          <el-form-item label="源仓库"><CatalogSelect v-model="filters.sourceWarehouseId" kind="warehouse" /></el-form-item>
          <el-form-item label="目标仓库"><CatalogSelect v-model="filters.targetWarehouseId" kind="warehouse" /></el-form-item>
        </template>
        <el-form-item v-else label="仓库"><CatalogSelect v-model="filters.warehouseId" kind="warehouse" /></el-form-item>
        <el-form-item label="状态"><el-select v-model="filters.status" clearable placeholder="全部状态" class="status-select"><el-option v-for="(label, value) in statuses" :key="value" :label="label" :value="value" /></el-select></el-form-item>
        <div class="filter-actions"><el-button type="primary" :icon="Search" @click="search">查询</el-button><el-button :icon="Refresh" @click="reset">重置</el-button></div>
      </el-form></section>
      <section class="panel table-panel"><div class="table-heading"><div><h2>{{ config.singular }}列表</h2><span>共 {{ total }} 条</span></div></div>
        <el-table v-loading="loading" :data="rows" stripe class="data-table" empty-text="暂无单据">
          <el-table-column prop="orderNo" label="单号" min-width="185"><template #default="{ row }"><span class="primary-cell">{{ row.orderNo }}</span></template></el-table-column>
          <el-table-column v-if="kind === 'transfer'" label="源仓 / 目标仓" min-width="210"><template #default="{ row }">{{ warehouseLabel(row.sourceWarehouseId) }} → {{ warehouseLabel(row.targetWarehouseId) }}</template></el-table-column>
          <el-table-column v-else label="仓库" min-width="130"><template #default="{ row }">{{ warehouseLabel(row.warehouseId) }}</template></el-table-column>
          <el-table-column label="状态" width="105"><template #default="{ row }"><el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
          <el-table-column label="商品行数" width="90"><template #default="{ row }">{{ row.items.length }}</template></el-table-column>
          <el-table-column label="创建时间" min-width="165"><template #default="{ row }">{{ formatTime(row.createdAt) }}</template></el-table-column>
          <el-table-column label="操作" min-width="240" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="router.push(`${config.path}/${row.id}`)">详情</el-button><el-button v-for="action in actions(tableOrder(row))" :key="action" link :type="action === 'cancel' ? 'danger' : 'primary'" @click="runAction(tableOrder(row), action)">{{ actionLabel(action) }}</el-button></template></el-table-column>
        </el-table><div class="pagination-bar"><el-pagination v-model:current-page="page" v-model:page-size="size" :total="total" :page-sizes="[10, 20, 50, 100]" layout="total, sizes, prev, pager, next" @current-change="load" @size-change="search" /></div>
      </section>
    </template>
    <template v-else-if="detail">
      <section class="panel detail-panel"><div class="detail-title"><div><span class="eyebrow">ORDER DETAIL</span><h2>{{ detail.orderNo }}</h2></div><el-tag :type="statusTagType(detail.status)" size="large">{{ statusLabel(detail.status) }}</el-tag></div>
        <el-descriptions title="基本信息与仓库" :column="2" border>
          <el-descriptions-item label="单据编号">{{ detail.orderNo }}</el-descriptions-item><el-descriptions-item label="状态">{{ statusLabel(detail.status) }}</el-descriptions-item>
          <template v-if="'sourceWarehouseId' in detail"><el-descriptions-item label="源仓库">{{ warehouseLabel(detail.sourceWarehouseId) }}</el-descriptions-item><el-descriptions-item label="目标仓库">{{ warehouseLabel(detail.targetWarehouseId) }}</el-descriptions-item></template>
          <el-descriptions-item v-else label="仓库">{{ warehouseLabel(detail.warehouseId) }}</el-descriptions-item>
          <el-descriptions-item label="备注">{{ detail.remark || '—' }}</el-descriptions-item>
          <el-descriptions-item label="创建人">用户 #{{ detail.createdBy }}</el-descriptions-item><el-descriptions-item label="更新时间">{{ formatTime(detail.updatedAt) }}</el-descriptions-item>
        </el-descriptions>
        <h3 class="detail-section-heading">商品明细</h3>
        <el-table :data="detail.items" stripe class="data-table"><el-table-column label="SKU" min-width="230"><template #default="{ row }">{{ skuLabel(row.skuId) }}</template></el-table-column><el-table-column prop="quantity" label="数量" width="130" align="right" /></el-table>
        <h3 class="detail-section-heading">操作与时间</h3>
        <el-timeline class="order-timeline"><el-timeline-item v-for="event in eventRows" :key="event.label + event.at" :timestamp="formatTime(event.at)">{{ event.label }} · 用户 #{{ event.by ?? '—' }}</el-timeline-item></el-timeline>
        <div v-if="actions(detail).length" class="detail-actions"><el-button v-for="action in actions(detail)" :key="action" :type="action === 'cancel' ? 'danger' : 'primary'" plain @click="runAction(detail, action)">{{ actionLabel(action) }}</el-button></div>
      </section>
    </template>
    <el-dialog v-model="dialogVisible" :title="`新建${config.singular}`" width="760px" destroy-on-close>
      <el-form label-position="top"><div v-if="kind === 'transfer'" class="form-row"><el-form-item label="源仓库" required><CatalogSelect v-model="form.sourceWarehouseId" kind="warehouse" enabled-only /></el-form-item><el-form-item label="目标仓库" required><CatalogSelect v-model="form.targetWarehouseId" kind="warehouse" enabled-only /></el-form-item></div>
        <el-form-item v-else label="仓库" required><CatalogSelect v-model="form.warehouseId" kind="warehouse" enabled-only /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit /></el-form-item>
        <OrderLinesEditor v-model="form.items" />
      </el-form><template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="submitCreate">创建{{ config.singular }}</el-button></template>
    </el-dialog>
  </div>
</template>
