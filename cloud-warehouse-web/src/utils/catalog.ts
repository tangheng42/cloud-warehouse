import { reactive } from 'vue'
import { getWarehouse, getWarehousePage } from '@/api/warehouse'
import { getSku, getSkuPage } from '@/api/sku'
import type { SkuVO, WarehouseVO } from '@/types/api'

export const warehouseById = reactive<Record<number, WarehouseVO>>({})
export const skuById = reactive<Record<number, SkuVO>>({})

export async function searchWarehouses(name = '', enabledOnly = false) {
  const result = await getWarehousePage({ page: 1, size: 100, name: name.trim() || undefined, enabled: enabledOnly ? true : undefined })
  result.items.forEach(item => { warehouseById[item.id] = item })
  return result.items
}
export async function searchSkus(name = '', enabledOnly = false) {
  const result = await getSkuPage({ page: 1, size: 100, name: name.trim() || undefined, enabled: enabledOnly ? true : undefined })
  result.items.forEach(item => { skuById[item.id] = item })
  return result.items
}
export async function ensureWarehouseLabels(ids: number[]) {
  await Promise.all([...new Set(ids)].filter(id => id && !warehouseById[id]).map(async id => {
    try { warehouseById[id] = await getWarehouse(id) } catch { /* 保留 ID 显示 */ }
  }))
}
export async function ensureSkuLabels(ids: number[]) {
  await Promise.all([...new Set(ids)].filter(id => id && !skuById[id]).map(async id => {
    try { skuById[id] = await getSku(id) } catch { /* 保留 ID 显示 */ }
  }))
}
export function warehouseLabel(id: number) { return warehouseById[id]?.name || `仓库 #${id}` }
export function skuLabel(id: number) { return skuById[id]?.name || `SKU #${id}` }
