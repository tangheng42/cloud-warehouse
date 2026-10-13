import { request } from '@/utils/request'
import type { OutboundCreateDTO, OutboundQuery, OutboundVO, PageResult } from '@/types/api'

export const getOutboundPage = (params: OutboundQuery) => request<PageResult<OutboundVO>>({ url: '/outbounds', method: 'GET', params })
export const getOutbound = (id: number) => request<OutboundVO>({ url: `/outbounds/${id}`, method: 'GET' })
export const createOutbound = (data: OutboundCreateDTO) => request<OutboundVO>({ url: '/outbounds', method: 'POST', data })
export const reserveOutbound = (id: number) => request<OutboundVO>({ url: `/outbounds/${id}/reserve`, method: 'POST' })
export const releaseOutbound = (id: number) => request<OutboundVO>({ url: `/outbounds/${id}/release`, method: 'POST' })
export const confirmOutbound = (id: number) => request<OutboundVO>({ url: `/outbounds/${id}/confirm`, method: 'POST' })
export const cancelOutbound = (id: number) => request<OutboundVO>({ url: `/outbounds/${id}/cancel`, method: 'POST' })
