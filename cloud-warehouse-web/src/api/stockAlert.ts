import { request } from '@/utils/request'
import type { PageResult, StockAlertQuery, StockAlertVO } from '@/types/api'

export const getStockAlertPage = (params: StockAlertQuery) => request<PageResult<StockAlertVO>>({ url: '/stock-alerts', method: 'GET', params })
