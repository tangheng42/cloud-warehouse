<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { createCategory, disableCategory, enableCategory, getCategory, getCategoryPage, updateCategory } from '@/api/skuCategory'
import { useAuthStore } from '@/store/auth'
import type { CategoryVO } from '@/types/api'
import { formatTime } from '@/utils/format'

const auth = useAuthStore()
const loading = ref(false)
const saving = ref(false)
const rows = ref<CategoryVO[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const filters = reactive<{ name: string; enabled: boolean | null }>({ name: '', enabled: null })
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const detailVisible = ref(false)
const detail = ref<CategoryVO | null>(null)
const formRef = ref<FormInstance>()
const form = reactive({ code: '', name: '' })
const rules: FormRules = {
  code: [{ required: true, whitespace: true, message: '请输入分类编码', trigger: 'blur' }, { max: 32, message: '最多 32 个字符', trigger: 'blur' }],
  name: [{ required: true, whitespace: true, message: '请输入分类名称', trigger: 'blur' }, { max: 100, message: '最多 100 个字符', trigger: 'blur' }],
}

async function load() {
  loading.value = true
  try {
    const result = await getCategoryPage({ page: page.value, size: size.value,
      name: filters.name.trim() || undefined,
      enabled: typeof filters.enabled === 'boolean' ? filters.enabled : undefined })
    rows.value = result.items; total.value = result.total
  } catch { rows.value = []; total.value = 0 }
  finally { loading.value = false }
}
function search() { page.value = 1; void load() }
function reset() { filters.name = ''; filters.enabled = null; search() }
function openCreate() {
  dialogMode.value = 'create'; editingId.value = null
  Object.assign(form, { code: '', name: '' })
  dialogVisible.value = true; formRef.value?.clearValidate()
}
async function openEdit(row: Pick<CategoryVO, 'id'>) {
  try {
    const current = await getCategory(row.id)
    dialogMode.value = 'edit'; editingId.value = current.id
    Object.assign(form, { code: current.code, name: current.name })
    dialogVisible.value = true; formRef.value?.clearValidate()
  } catch { /* 已提示 */ }
}
async function openDetail(row: Pick<CategoryVO, 'id'>) {
  try { detail.value = await getCategory(row.id); detailVisible.value = true } catch { /* 已提示 */ }
}
async function save() {
  if (!await formRef.value?.validate().catch(() => false)) return
  saving.value = true
  try {
    if (dialogMode.value === 'create') {
      await createCategory({ code: form.code.trim(), name: form.name.trim() }); page.value = 1
    } else if (editingId.value !== null) {
      await updateCategory(editingId.value, { name: form.name.trim() })
    }
    dialogVisible.value = false; ElMessage.success('保存成功'); await load()
  } catch { /* 已提示 */ }
  finally { saving.value = false }
}
async function toggle(row: Pick<CategoryVO, 'id' | 'name' | 'enabled'>) {
  const action = row.enabled ? '停用' : '启用'
  try { await ElMessageBox.confirm(`确定${action}分类「${row.name}」？`, `${action}分类`, { type: 'warning', confirmButtonText: action, cancelButtonText: '取消' }) }
  catch { return }
  try {
    if (row.enabled) await disableCategory(row.id)
    else await enableCategory(row.id)
    ElMessage.success(`${action}成功`); await load()
  } catch { /* 已提示 */ }
}
onMounted(load)
</script>

<template>
  <div class="page-wrap">
    <div class="page-heading"><div><span class="eyebrow">MASTER DATA / CATEGORIES</span><h1>SKU 分类</h1><p>建立清晰的 SKU 分类结构，停用前需确保没有启用的 SKU。</p></div>
      <el-button v-if="auth.isAdmin" type="primary" :icon="Plus" @click="openCreate">新增分类</el-button>
    </div>
    <section class="panel filter-panel"><el-form inline class="filter-form" @submit.prevent="search">
      <el-form-item label="分类名称"><el-input v-model="filters.name" placeholder="输入名称" clearable @keyup.enter="search" /></el-form-item>
      <el-form-item label="状态"><el-select v-model="filters.enabled" placeholder="全部状态" clearable :value-on-clear="null" class="status-select"><el-option label="启用" :value="true" /><el-option label="停用" :value="false" /></el-select></el-form-item>
      <div class="filter-actions"><el-button type="primary" :icon="Search" @click="search">查询</el-button><el-button :icon="Refresh" @click="reset">重置</el-button></div>
    </el-form></section>
    <section class="panel table-panel"><div class="table-heading"><div><h2>分类列表</h2><span>共 {{ total }} 条记录</span></div></div>
      <el-table v-loading="loading" :data="rows" stripe class="data-table" empty-text="暂无分类数据">
        <el-table-column prop="code" label="分类编码" min-width="170" /><el-table-column prop="name" label="分类名称" min-width="200" />
        <el-table-column label="状态" width="110"><template #default="{ row }"><el-tag :type="row.enabled ? 'success' : 'info'" effect="light" round>{{ row.enabled ? '启用' : '停用' }}</el-tag></template></el-table-column>
        <el-table-column label="创建时间" min-width="190"><template #default="{ row }">{{ formatTime(row.createdAt) }}</template></el-table-column>
        <el-table-column label="操作" width="190" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="openDetail(row as CategoryVO)">详情</el-button><el-button v-if="auth.isAdmin" link type="primary" @click="openEdit(row as CategoryVO)">编辑</el-button><el-button v-if="auth.isAdmin" link :type="row.enabled ? 'warning' : 'success'" @click="toggle(row as CategoryVO)">{{ row.enabled ? '停用' : '启用' }}</el-button></template></el-table-column>
      </el-table>
      <div class="pagination-bar"><el-pagination v-model:current-page="page" v-model:page-size="size" :total="total" :page-sizes="[10, 20, 50, 100]" layout="total, sizes, prev, pager, next" @current-change="load" @size-change="search" /></div>
    </section>
    <el-dialog v-model="dialogVisible" :title="dialogMode === 'create' ? '新增分类' : '编辑分类'" width="500px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top"><el-form-item label="分类编码" prop="code"><el-input v-model="form.code" :disabled="dialogMode === 'edit'" maxlength="32" placeholder="例如 CAT-FOOD" /></el-form-item><el-form-item label="分类名称" prop="name"><el-input v-model="form.name" maxlength="100" placeholder="请输入分类名称" /></el-form-item></el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>
    <el-dialog v-model="detailVisible" title="分类详情" width="500px"><el-descriptions v-if="detail" :column="1" border><el-descriptions-item label="分类编码">{{ detail.code }}</el-descriptions-item><el-descriptions-item label="分类名称">{{ detail.name }}</el-descriptions-item><el-descriptions-item label="状态">{{ detail.enabled ? '启用' : '停用' }}</el-descriptions-item><el-descriptions-item label="创建时间">{{ formatTime(detail.createdAt) }}</el-descriptions-item><el-descriptions-item label="更新时间">{{ formatTime(detail.updatedAt) }}</el-descriptions-item></el-descriptions></el-dialog>
  </div>
</template>
