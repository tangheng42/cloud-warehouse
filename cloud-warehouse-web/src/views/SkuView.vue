<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { createSku, disableSku, enableSku, getSku, getSkuPage, updateSku } from '@/api/sku'
import { getCategory, getCategoryPage } from '@/api/skuCategory'
import { useAuthStore } from '@/store/auth'
import type { CategoryVO, SkuVO } from '@/types/api'
import { formatTime } from '@/utils/format'

const auth = useAuthStore()
const loading = ref(false)
const saving = ref(false)
const rows = ref<SkuVO[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const filters = reactive<{ code: string; name: string; categoryId: number | null; enabled: boolean | null }>({ code: '', name: '', categoryId: null, enabled: null })
const categoryNames = reactive<Record<number, string>>({})
const filterCategories = ref<CategoryVO[]>([])
const formCategories = ref<CategoryVO[]>([])
const categoryLoading = ref(false)
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const detailVisible = ref(false)
const detail = ref<SkuVO | null>(null)
const formRef = ref<FormInstance>()
const form = reactive<{ code: string; name: string; categoryId: number | null; unit: string; safetyStock: number }>({ code: '', name: '', categoryId: null, unit: '件', safetyStock: 0 })
const rules: FormRules = {
  code: [{ required: true, whitespace: true, message: '请输入 SKU 编码', trigger: 'blur' }, { max: 64, message: '最多 64 个字符', trigger: 'blur' }],
  name: [{ required: true, whitespace: true, message: '请输入 SKU 名称', trigger: 'blur' }, { max: 100, message: '最多 100 个字符', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择分类', trigger: 'change' }],
  unit: [{ required: true, whitespace: true, message: '请输入单位', trigger: 'blur' }, { max: 16, message: '最多 16 个字符', trigger: 'blur' }],
  safetyStock: [{ required: true, message: '请输入安全库存', trigger: 'change' }],
}

async function loadFilterCategories(keyword = '') {
  try {
    const result = await getCategoryPage({ page: 1, size: 100, name: keyword.trim() || undefined })
    filterCategories.value = result.items
    result.items.forEach(item => { categoryNames[item.id] = item.name })
  } catch { filterCategories.value = [] }
}
async function loadFormCategories(keyword = '') {
  categoryLoading.value = true
  try {
    const result = await getCategoryPage({ page: 1, size: 100, name: keyword.trim() || undefined, enabled: true })
    formCategories.value = result.items
    result.items.forEach(item => { categoryNames[item.id] = item.name })
  } catch { formCategories.value = [] }
  finally { categoryLoading.value = false }
}
async function ensureCategoryName(id: number) {
  if (categoryNames[id]) return
  try { categoryNames[id] = (await getCategory(id)).name } catch { categoryNames[id] = `分类 #${id}` }
}
async function load() {
  loading.value = true
  try {
    const result = await getSkuPage({ page: page.value, size: size.value,
      code: filters.code.trim() || undefined, name: filters.name.trim() || undefined,
      categoryId: typeof filters.categoryId === 'number' ? filters.categoryId : undefined,
      enabled: typeof filters.enabled === 'boolean' ? filters.enabled : undefined })
    rows.value = result.items; total.value = result.total
    await Promise.all([...new Set(result.items.map(item => item.categoryId))].map(ensureCategoryName))
  } catch { rows.value = []; total.value = 0 }
  finally { loading.value = false }
}
function search() { page.value = 1; void load() }
function reset() { Object.assign(filters, { code: '', name: '', categoryId: null, enabled: null }); search() }
async function openCreate() {
  dialogMode.value = 'create'; editingId.value = null
  Object.assign(form, { code: '', name: '', categoryId: null, unit: '件', safetyStock: 0 })
  await loadFormCategories()
  dialogVisible.value = true; formRef.value?.clearValidate()
}
async function openEdit(row: Pick<SkuVO, 'id'>) {
  try {
    const current = await getSku(row.id)
    await loadFormCategories()
    if (!formCategories.value.some(item => item.id === current.categoryId)) {
      const selected = await getCategory(current.categoryId)
      formCategories.value.push(selected)
      categoryNames[selected.id] = selected.name
    }
    dialogMode.value = 'edit'; editingId.value = current.id
    Object.assign(form, { code: current.code, name: current.name, categoryId: current.categoryId, unit: current.unit, safetyStock: current.safetyStock })
    dialogVisible.value = true; formRef.value?.clearValidate()
  } catch { /* 已提示 */ }
}
async function openDetail(row: Pick<SkuVO, 'id'>) {
  try { detail.value = await getSku(row.id); await ensureCategoryName(detail.value.categoryId); detailVisible.value = true } catch { /* 已提示 */ }
}
async function save() {
  if (!await formRef.value?.validate().catch(() => false) || form.categoryId === null) return
  saving.value = true
  try {
    const data = { name: form.name.trim(), categoryId: form.categoryId, unit: form.unit.trim(), safetyStock: form.safetyStock }
    if (dialogMode.value === 'create') {
      await createSku({ code: form.code.trim(), ...data }); page.value = 1
    } else if (editingId.value !== null) {
      await updateSku(editingId.value, data)
    }
    dialogVisible.value = false; ElMessage.success('保存成功'); await load()
  } catch { /* 已提示 */ }
  finally { saving.value = false }
}
async function toggle(row: Pick<SkuVO, 'id' | 'name' | 'enabled'>) {
  const action = row.enabled ? '停用' : '启用'
  try { await ElMessageBox.confirm(`确定${action} SKU「${row.name}」？`, `${action} SKU`, { type: 'warning', confirmButtonText: action, cancelButtonText: '取消' }) }
  catch { return }
  try {
    if (row.enabled) await disableSku(row.id)
    else await enableSku(row.id)
    ElMessage.success(`${action}成功`); await load()
  } catch { /* 已提示 */ }
}
onMounted(() => { void loadFilterCategories(); void load() })
</script>

<template>
  <div class="page-wrap">
    <div class="page-heading"><div><span class="eyebrow">MASTER DATA / SKU</span><h1>SKU 管理</h1><p>管理 SKU 基础属性、安全库存与启停状态。</p></div>
      <el-button v-if="auth.isAdmin" type="primary" :icon="Plus" @click="openCreate">新增 SKU</el-button>
    </div>
    <section class="panel filter-panel"><el-form inline class="filter-form" @submit.prevent="search">
      <el-form-item label="SKU 编码"><el-input v-model="filters.code" placeholder="精确查询" clearable @keyup.enter="search" /></el-form-item>
      <el-form-item label="SKU 名称"><el-input v-model="filters.name" placeholder="输入名称" clearable @keyup.enter="search" /></el-form-item>
      <el-form-item label="分类"><el-select v-model="filters.categoryId" filterable remote clearable :value-on-clear="null" :remote-method="loadFilterCategories" placeholder="输入分类名称" class="filter-select"><el-option v-for="item in filterCategories" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item>
      <el-form-item label="状态"><el-select v-model="filters.enabled" placeholder="全部状态" clearable :value-on-clear="null" class="status-select"><el-option label="启用" :value="true" /><el-option label="停用" :value="false" /></el-select></el-form-item>
      <div class="filter-actions"><el-button type="primary" :icon="Search" @click="search">查询</el-button><el-button :icon="Refresh" @click="reset">重置</el-button></div>
    </el-form></section>
    <section class="panel table-panel"><div class="table-heading"><div><h2>SKU 列表</h2><span>共 {{ total }} 条记录</span></div></div>
      <el-table v-loading="loading" :data="rows" stripe class="data-table" empty-text="暂无 SKU 数据">
        <el-table-column prop="code" label="SKU 编码" min-width="145" /><el-table-column prop="name" label="SKU 名称" min-width="160" />
        <el-table-column label="分类" min-width="130"><template #default="{ row }">{{ categoryNames[row.categoryId] || `分类 #${row.categoryId}` }}</template></el-table-column>
        <el-table-column prop="unit" label="单位" width="82" /><el-table-column prop="safetyStock" label="安全库存" width="110" />
        <el-table-column label="状态" width="92"><template #default="{ row }"><el-tag :type="row.enabled ? 'success' : 'info'" effect="light" round>{{ row.enabled ? '启用' : '停用' }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="190" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="openDetail(row as SkuVO)">详情</el-button><el-button v-if="auth.isAdmin" link type="primary" @click="openEdit(row as SkuVO)">编辑</el-button><el-button v-if="auth.isAdmin" link :type="row.enabled ? 'warning' : 'success'" @click="toggle(row as SkuVO)">{{ row.enabled ? '停用' : '启用' }}</el-button></template></el-table-column>
      </el-table>
      <div class="pagination-bar"><el-pagination v-model:current-page="page" v-model:page-size="size" :total="total" :page-sizes="[10, 20, 50, 100]" layout="total, sizes, prev, pager, next" @current-change="load" @size-change="search" /></div>
    </section>
    <el-dialog v-model="dialogVisible" :title="dialogMode === 'create' ? '新增 SKU' : '编辑 SKU'" width="540px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="SKU 编码" prop="code"><el-input v-model="form.code" :disabled="dialogMode === 'edit'" maxlength="64" placeholder="例如 SKU-RICE" /></el-form-item>
        <el-form-item label="SKU 名称" prop="name"><el-input v-model="form.name" maxlength="100" placeholder="请输入 SKU 名称" /></el-form-item>
        <el-form-item label="分类" prop="categoryId"><el-select v-model="form.categoryId" filterable remote :remote-method="loadFormCategories" :loading="categoryLoading" placeholder="输入分类名称搜索" class="full-width"><el-option v-for="item in formCategories" :key="item.id" :label="`${item.name} · ${item.code}`" :value="item.id" /></el-select></el-form-item>
        <div class="form-row"><el-form-item label="单位" prop="unit"><el-input v-model="form.unit" maxlength="16" placeholder="件 / 箱 / 袋" /></el-form-item><el-form-item label="安全库存" prop="safetyStock"><el-input-number v-model="form.safetyStock" :min="0" :precision="0" controls-position="right" class="full-width" /></el-form-item></div>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>
    <el-dialog v-model="detailVisible" title="SKU 详情" width="520px"><el-descriptions v-if="detail" :column="1" border>
      <el-descriptions-item label="SKU 编码">{{ detail.code }}</el-descriptions-item><el-descriptions-item label="SKU 名称">{{ detail.name }}</el-descriptions-item>
      <el-descriptions-item label="分类">{{ categoryNames[detail.categoryId] || `分类 #${detail.categoryId}` }}</el-descriptions-item><el-descriptions-item label="单位">{{ detail.unit }}</el-descriptions-item>
      <el-descriptions-item label="安全库存">{{ detail.safetyStock }}</el-descriptions-item><el-descriptions-item label="状态">{{ detail.enabled ? '启用' : '停用' }}</el-descriptions-item>
      <el-descriptions-item label="创建时间">{{ formatTime(detail.createdAt) }}</el-descriptions-item><el-descriptions-item label="更新时间">{{ formatTime(detail.updatedAt) }}</el-descriptions-item>
    </el-descriptions></el-dialog>
  </div>
</template>
