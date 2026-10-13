<script setup lang="ts">
import CatalogSelect from '@/components/CatalogSelect.vue'
import type { OrderLineDraft } from '@/types/api'

const props = defineProps<{ modelValue: OrderLineDraft[]; stocktake?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: OrderLineDraft[]] }>()
function add() { emit('update:modelValue', [...props.modelValue, { skuId: null, quantity: 1, countedPhysical: 0 }]) }
function remove(index: number) { emit('update:modelValue', props.modelValue.filter((_, i) => i !== index)) }
function update(index: number, patch: Partial<OrderLineDraft>) { emit('update:modelValue', props.modelValue.map((row, i) => i === index ? { ...row, ...patch } : row)) }
</script>

<template>
  <div class="order-lines">
    <div class="line-head"><span>商品明细</span><el-button type="primary" plain size="small" @click="add">添加商品</el-button></div>
    <div v-for="(line, index) in modelValue" :key="index" class="line-row">
      <span class="line-index">{{ index + 1 }}</span>
      <CatalogSelect :model-value="line.skuId" kind="sku" enabled-only placeholder="搜索并选择 SKU" @update:model-value="update(index, { skuId: $event })" />
      <el-input-number v-if="stocktake" :model-value="line.countedPhysical" :min="0" :precision="0" controls-position="right" @update:model-value="update(index, { countedPhysical: $event ?? 0 })" />
      <el-input-number v-else :model-value="line.quantity" :min="1" :precision="0" controls-position="right" @update:model-value="update(index, { quantity: $event ?? 1 })" />
      <el-button link type="danger" :disabled="modelValue.length === 1" @click="remove(index)">删除</el-button>
    </div>
    <p class="form-tip">{{ stocktake ? '填写实盘数量，系统库存快照由后端在创建时保存。' : '数量须为正整数，不能重复选择同一 SKU。' }}</p>
  </div>
</template>
