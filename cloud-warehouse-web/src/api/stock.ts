import { request } from '@/utils/request'
import type { PageResult, StockQuery, StockVO } from '@/types/api'

export const getStockPage = (params: StockQuery) =>
  request<PageResult<StockVO>>({ url: '/stocks', method: 'GET', params })
export const getStock = (warehouseId: number, skuId: number) =>
  request<StockVO>({ url: `/stocks/warehouses/${warehouseId}/skus/${skuId}`, method: 'GET' })
