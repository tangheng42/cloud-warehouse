export type Role = 'ADMIN' | 'WAREHOUSE_MANAGER' | 'WAREHOUSE_OPERATOR'

export interface ApiResponse<T> {
  code: string
  message: string
  data: T | null
  traceId: string
}

export interface PageResult<T> {
  items: T[]
  page: number
  size: number
  total: number
}

export interface PageQuery {
  page?: number
  size?: number
}

export interface UserVO {
  id: number
  username: string
  role: Role
  enabled: boolean
}

export interface LoginDTO { username: string; password: string }
export interface LoginVO { token: string; expiresAt: string; user: UserVO }

export interface WarehouseCreateDTO { code: string; name: string; address?: string }
export interface WarehouseUpdateDTO { name: string; address?: string }
export interface WarehouseVO {
  id: number; code: string; name: string; address?: string; enabled: boolean
  createdAt: string; updatedAt: string
}
export interface WarehouseQuery extends PageQuery {
  code?: string; name?: string; enabled?: boolean
}

export interface CategoryCreateDTO { code: string; name: string }
export interface CategoryUpdateDTO { name: string }
export interface CategoryVO {
  id: number; code: string; name: string; enabled: boolean
  createdAt: string; updatedAt: string
}
export interface CategoryQuery extends PageQuery { name?: string; enabled?: boolean }

export interface SkuCreateDTO {
  code: string; name: string; categoryId: number; unit: string; safetyStock: number
}
export interface SkuUpdateDTO {
  name: string; categoryId: number; unit: string; safetyStock: number
}
export interface SkuVO {
  id: number; code: string; name: string; categoryId: number; unit: string
  safetyStock: number; enabled: boolean; createdAt: string; updatedAt: string
}
export interface SkuQuery extends PageQuery {
  code?: string; name?: string; categoryId?: number; enabled?: boolean
}

export interface StockVO {
  warehouseId: number; skuId: number; exists: boolean
  physicalStock: number; availableStock: number; lockedStock: number; safetyStock: number
}
export interface StockQuery extends PageQuery { warehouseId?: number; skuId?: number }

export type InboundStatus = 'PENDING' | 'COMPLETED' | 'CANCELLED'
export type OutboundStatus = 'CREATED' | 'RESERVED' | 'COMPLETED' | 'CANCELLED'
export type TransferStatus = 'CREATED' | 'RESERVED' | 'IN_TRANSIT' | 'COMPLETED' | 'CANCELLED'
export type StocktakeStatus = 'DRAFT' | 'COMPLETED' | 'CANCELLED'
export type StockAlertStatus = 'ACTIVE' | 'RESOLVED'
export type StockFlowBizType = 'INBOUND' | 'OUTBOUND' | 'TRANSFER' | 'STOCKTAKE'
export type StockFlowType = 'INBOUND' | 'OUTBOUND_LOCK' | 'OUTBOUND_RELEASE' | 'OUTBOUND' | 'TRANSFER_LOCK' | 'TRANSFER_RELEASE' | 'TRANSFER_OUT' | 'TRANSFER_IN' | 'STOCKTAKE_ADJUST'

export interface OrderItemDTO { skuId: number; quantity: number }
export interface OrderLineDraft { skuId: number | null; quantity: number; countedPhysical: number }
export interface OrderItemVO { skuId: number; quantity: number }
export interface InboundCreateDTO { warehouseId: number; items: OrderItemDTO[]; remark?: string }
export interface OutboundCreateDTO { warehouseId: number; items: OrderItemDTO[]; remark?: string }
export interface TransferCreateDTO { sourceWarehouseId: number; targetWarehouseId: number; items: OrderItemDTO[]; remark?: string }
export interface StocktakeItemDTO { skuId: number; countedPhysical: number }
export interface StocktakeCreateDTO { warehouseId: number; items: StocktakeItemDTO[]; remark?: string }

export interface OrderBaseVO<S extends string, I> {
  id: number; orderNo: string; status: S; items: I[]; remark?: string
  createdBy: number; createdAt: string; updatedAt: string
  cancelledBy?: number; cancelledAt?: string
}
export interface InboundVO extends OrderBaseVO<InboundStatus, OrderItemVO> {
  warehouseId: number; confirmedBy?: number; confirmedAt?: string
}
export interface OutboundVO extends OrderBaseVO<OutboundStatus, OrderItemVO> {
  warehouseId: number; reservedBy?: number; reservedAt?: string
  releasedBy?: number; releasedAt?: string; confirmedBy?: number; confirmedAt?: string
}
export interface TransferVO extends OrderBaseVO<TransferStatus, OrderItemVO> {
  sourceWarehouseId: number; targetWarehouseId: number
  reservedBy?: number; reservedAt?: string; releasedBy?: number; releasedAt?: string
  dispatchedBy?: number; dispatchedAt?: string; receivedBy?: number; receivedAt?: string
}
export interface StocktakeItemVO {
  skuId: number; snapshotPhysical: number; snapshotAvailable: number
  snapshotLocked: number; countedPhysical: number
}
export interface StocktakeVO extends OrderBaseVO<StocktakeStatus, StocktakeItemVO> {
  warehouseId: number; confirmedBy?: number; confirmedAt?: string
}

export interface InboundQuery extends PageQuery { warehouseId?: number; status?: InboundStatus }
export interface OutboundQuery extends PageQuery { warehouseId?: number; status?: OutboundStatus }
export interface TransferQuery extends PageQuery { sourceWarehouseId?: number; targetWarehouseId?: number; status?: TransferStatus }
export interface StocktakeQuery extends PageQuery { warehouseId?: number; status?: StocktakeStatus }
export interface StockFlowQuery extends PageQuery {
  warehouseId?: number; skuId?: number; bizType?: StockFlowBizType; bizId?: number; flowType?: StockFlowType
}
export interface StockAlertQuery extends PageQuery { warehouseId?: number; skuId?: number; status?: StockAlertStatus }
export interface StockFlowVO {
  id: number; warehouseId: number; skuId: number; bizType: StockFlowBizType; bizId: number
  flowType: StockFlowType; deltaPhysical: number; deltaAvailable: number; deltaLocked: number
  beforePhysical: number; beforeAvailable: number; beforeLocked: number
  afterPhysical: number; afterAvailable: number; afterLocked: number
  operatorId: number; createdAt: string
}
export interface StockAlertVO {
  id: number; warehouseId: number; skuId: number; availableStock: number; safetyStock: number
  status: StockAlertStatus; triggeredAt: string; resolvedAt?: string
}
