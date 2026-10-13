import type { InboundStatus, OutboundStatus, StockAlertStatus, StockFlowBizType, StockFlowType, StocktakeStatus, TransferStatus } from '@/types/api'

export const inboundStatuses: Record<InboundStatus, string> = { PENDING: '待确认', COMPLETED: '已完成', CANCELLED: '已取消' }
export const outboundStatuses: Record<OutboundStatus, string> = { CREATED: '待预占', RESERVED: '已预占', COMPLETED: '已完成', CANCELLED: '已取消' }
export const transferStatuses: Record<TransferStatus, string> = { CREATED: '待预占', RESERVED: '已预占', IN_TRANSIT: '运输中', COMPLETED: '已完成', CANCELLED: '已取消' }
export const stocktakeStatuses: Record<StocktakeStatus, string> = { DRAFT: '草稿', COMPLETED: '已完成', CANCELLED: '已取消' }
export const stockAlertStatuses: Record<StockAlertStatus, string> = { ACTIVE: '预警中', RESOLVED: '已解决' }
export const stockFlowBizTypes: Record<StockFlowBizType, string> = { INBOUND: '入库', OUTBOUND: '出库', TRANSFER: '调拨', STOCKTAKE: '盘点' }
export const stockFlowTypes: Record<StockFlowType, string> = {
  INBOUND: '入库', OUTBOUND_LOCK: '出库预占', OUTBOUND_RELEASE: '出库释放', OUTBOUND: '确认出库',
  TRANSFER_LOCK: '调拨预占', TRANSFER_RELEASE: '调拨释放', TRANSFER_OUT: '调拨发运',
  TRANSFER_IN: '调拨收货', STOCKTAKE_ADJUST: '盘点调整',
}

export const inboundActions = { PENDING: ['confirm', 'cancel'], COMPLETED: [], CANCELLED: [] } as const
export const outboundActions = { CREATED: ['reserve', 'cancel'], RESERVED: ['release', 'confirm', 'cancel'], COMPLETED: [], CANCELLED: [] } as const
export const transferActions = { CREATED: ['reserve', 'cancel'], RESERVED: ['release', 'dispatch', 'cancel'], IN_TRANSIT: ['receive'], COMPLETED: [], CANCELLED: [] } as const
export const stocktakeActions = { DRAFT: ['confirm', 'cancel'], COMPLETED: [], CANCELLED: [] } as const

export const actionLabels: Record<string, string> = {
  reserve: '预占库存', release: '释放库存', confirm: '确认', cancel: '取消单据', dispatch: '发运', receive: '收货',
}
export function statusTagType(status: string): 'success' | 'warning' | 'info' | 'primary' {
  if (status === 'COMPLETED' || status === 'RESOLVED') return 'success'
  if (status === 'CANCELLED') return 'info'
  if (status === 'RESERVED' || status === 'IN_TRANSIT' || status === 'ACTIVE') return 'warning'
  return 'primary'
}
