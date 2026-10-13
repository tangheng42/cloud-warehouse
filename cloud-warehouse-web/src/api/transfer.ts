import { request } from '@/utils/request'
import type { TransferCreateDTO, TransferQuery, TransferVO, PageResult } from '@/types/api'

export const getTransferPage = (params: TransferQuery) => request<PageResult<TransferVO>>({ url: '/transfers', method: 'GET', params })
export const getTransfer = (id: number) => request<TransferVO>({ url: `/transfers/${id}`, method: 'GET' })
export const createTransfer = (data: TransferCreateDTO) => request<TransferVO>({ url: '/transfers', method: 'POST', data })
export const reserveTransfer = (id: number) => request<TransferVO>({ url: `/transfers/${id}/reserve`, method: 'POST' })
export const releaseTransfer = (id: number) => request<TransferVO>({ url: `/transfers/${id}/release`, method: 'POST' })
export const dispatchTransfer = (id: number) => request<TransferVO>({ url: `/transfers/${id}/dispatch`, method: 'POST' })
export const receiveTransfer = (id: number) => request<TransferVO>({ url: `/transfers/${id}/receive`, method: 'POST' })
export const cancelTransfer = (id: number) => request<TransferVO>({ url: `/transfers/${id}/cancel`, method: 'POST' })
