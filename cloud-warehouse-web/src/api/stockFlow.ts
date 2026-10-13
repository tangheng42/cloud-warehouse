import { request } from '@/utils/request'
import type { PageResult, StockFlowQuery, StockFlowVO } from '@/types/api'

export const getStockFlowPage = (params: StockFlowQuery) => request<PageResult<StockFlowVO>>({ url: '/stock-flows', method: 'GET', params })
export const getStockFlow = (id: number) => request<StockFlowVO>({ url: `/stock-flows/${id}`, method: 'GET' })
