import { request } from '@/utils/request'
import type { PageResult, WarehouseCreateDTO, WarehouseQuery, WarehouseUpdateDTO, WarehouseVO } from '@/types/api'

export const getWarehousePage = (params: WarehouseQuery) =>
  request<PageResult<WarehouseVO>>({ url: '/warehouses', method: 'GET', params })
export const getWarehouse = (id: number) => request<WarehouseVO>({ url: `/warehouses/${id}`, method: 'GET' })
export const createWarehouse = (data: WarehouseCreateDTO) =>
  request<WarehouseVO>({ url: '/warehouses', method: 'POST', data })
export const updateWarehouse = (id: number, data: WarehouseUpdateDTO) =>
  request<WarehouseVO>({ url: `/warehouses/${id}`, method: 'PUT', data })
export const enableWarehouse = (id: number) =>
  request<WarehouseVO>({ url: `/warehouses/${id}/enable`, method: 'POST' })
export const disableWarehouse = (id: number) =>
  request<WarehouseVO>({ url: `/warehouses/${id}/disable`, method: 'POST' })
