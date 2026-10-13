import { request } from '@/utils/request'
import type { PageResult, SkuCreateDTO, SkuQuery, SkuUpdateDTO, SkuVO } from '@/types/api'

export const getSkuPage = (params: SkuQuery) =>
  request<PageResult<SkuVO>>({ url: '/skus', method: 'GET', params })
export const getSku = (id: number) => request<SkuVO>({ url: `/skus/${id}`, method: 'GET' })
export const createSku = (data: SkuCreateDTO) => request<SkuVO>({ url: '/skus', method: 'POST', data })
export const updateSku = (id: number, data: SkuUpdateDTO) =>
  request<SkuVO>({ url: `/skus/${id}`, method: 'PUT', data })
export const enableSku = (id: number) => request<SkuVO>({ url: `/skus/${id}/enable`, method: 'POST' })
export const disableSku = (id: number) => request<SkuVO>({ url: `/skus/${id}/disable`, method: 'POST' })
