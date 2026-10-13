import { request } from '@/utils/request'
import type { StocktakeCreateDTO, StocktakeQuery, StocktakeVO, PageResult } from '@/types/api'

export const getStocktakePage = (params: StocktakeQuery) => request<PageResult<StocktakeVO>>({ url: '/stocktakes', method: 'GET', params })
export const getStocktake = (id: number) => request<StocktakeVO>({ url: `/stocktakes/${id}`, method: 'GET' })
export const createStocktake = (data: StocktakeCreateDTO) => request<StocktakeVO>({ url: '/stocktakes', method: 'POST', data })
export const confirmStocktake = (id: number) => request<StocktakeVO>({ url: `/stocktakes/${id}/confirm`, method: 'POST' })
export const cancelStocktake = (id: number) => request<StocktakeVO>({ url: `/stocktakes/${id}/cancel`, method: 'POST' })
