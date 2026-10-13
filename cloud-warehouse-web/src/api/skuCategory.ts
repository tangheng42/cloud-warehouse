import { request } from '@/utils/request'
import type { CategoryCreateDTO, CategoryQuery, CategoryUpdateDTO, CategoryVO, PageResult } from '@/types/api'

export const getCategoryPage = (params: CategoryQuery) =>
  request<PageResult<CategoryVO>>({ url: '/sku-categories', method: 'GET', params })
export const getCategory = (id: number) => request<CategoryVO>({ url: `/sku-categories/${id}`, method: 'GET' })
export const createCategory = (data: CategoryCreateDTO) =>
  request<CategoryVO>({ url: '/sku-categories', method: 'POST', data })
export const updateCategory = (id: number, data: CategoryUpdateDTO) =>
  request<CategoryVO>({ url: `/sku-categories/${id}`, method: 'PUT', data })
export const enableCategory = (id: number) =>
  request<CategoryVO>({ url: `/sku-categories/${id}/enable`, method: 'POST' })
export const disableCategory = (id: number) =>
  request<CategoryVO>({ url: `/sku-categories/${id}/disable`, method: 'POST' })
