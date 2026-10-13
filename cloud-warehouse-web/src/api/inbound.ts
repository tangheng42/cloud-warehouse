import { request } from '@/utils/request'
import type { InboundCreateDTO, InboundQuery, InboundVO, PageResult } from '@/types/api'

export const getInboundPage = (params: InboundQuery) => request<PageResult<InboundVO>>({ url: '/inbounds', method: 'GET', params })
export const getInbound = (id: number) => request<InboundVO>({ url: `/inbounds/${id}`, method: 'GET' })
export const createInbound = (data: InboundCreateDTO) => request<InboundVO>({ url: '/inbounds', method: 'POST', data })
export const confirmInbound = (id: number) => request<InboundVO>({ url: `/inbounds/${id}/confirm`, method: 'POST' })
export const cancelInbound = (id: number) => request<InboundVO>({ url: `/inbounds/${id}/cancel`, method: 'POST' })
