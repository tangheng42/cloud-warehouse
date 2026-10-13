<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { searchSkus, searchWarehouses, skuById, warehouseById } from '@/utils/catalog'
import type { SkuVO, WarehouseVO } from '@/types/api'

const props = withDefaults(defineProps<{ modelValue: number | null; kind: 'warehouse' | 'sku'; enabledOnly?: boolean; placeholder?: string }>(), { enabledOnly: false })
const emit = defineEmits<{ 'update:modelValue': [value: number | null] }>()
const options = ref<(WarehouseVO | SkuVO)[]>([])
const loading = ref(false)
let serial = 0
async function load(keyword = '') {
  const current = ++serial
  loading.value = true
  try {
    const result = props.kind === 'warehouse' ? await searchWarehouses(keyword, props.enabledOnly) : await searchSkus(keyword, props.enabledOnly)
    if (current === serial) options.value = result
  } catch { if (current === serial) options.value = [] }
  finally { if (current === serial) loading.value = false }
}
watch(() => [props.kind, props.enabledOnly], () => { void load() })
onMounted(() => { void load() })
</script>

<template>
  <el-select :model-value="modelValue" filterable remote clearable :value-on-clear="null" :remote-method="load" :loading="loading"
    :placeholder="placeholder || (kind === 'warehouse' ? '搜索仓库名称' : '搜索 SKU 名称')" class="filter-select" @update:model-value="emit('update:modelValue', $event)">
    <el-option v-if="modelValue && !options.some(item => item.id === modelValue) && (kind === 'warehouse' ? warehouseById[modelValue] : skuById[modelValue])"
      :label="kind === 'warehouse' ? `${warehouseById[modelValue]?.name} · ${warehouseById[modelValue]?.code}` : `${skuById[modelValue]?.name} · ${skuById[modelValue]?.code}`" :value="modelValue" />
    <el-option v-for="item in options" :key="item.id" :label="`${item.name} · ${item.code}`" :value="item.id" />
  </el-select>
</template>
