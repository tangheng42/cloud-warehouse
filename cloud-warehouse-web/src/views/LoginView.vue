<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ArrowRight, Box, Lock, User } from '@element-plus/icons-vue'
import { login, getCurrentUser } from '@/api/auth'
import { useAuthStore } from '@/store/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({ username: '', password: '' })
const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function submit() {
  if (!await formRef.value?.validate().catch(() => false)) return
  loading.value = true
  try {
    const result = await login({ username: form.username.trim(), password: form.password })
    auth.setToken(result.token)
    auth.setUser(await getCurrentUser())
    const redirect = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/')
      ? route.query.redirect : '/dashboard'
    await router.replace(redirect)
  } catch {
    auth.clearSession()
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-story">
      <div class="login-story-inner">
        <div class="login-brand"><span class="login-brand-mark"><Box :size="23" /></span><strong>云仓智配</strong></div>
        <div class="login-story-copy">
          <span class="eyebrow light">CLOUD WAREHOUSE PLATFORM</span>
          <h1>让多仓协作<br />清晰有序</h1>
          <p>统一管理仓库、SKU 与库存信息，为后续入库、出库和调拨协同打好基础。</p>
        </div>
        <div class="warehouse-art" aria-hidden="true">
          <div class="art-glow" />
          <div class="art-building art-building-back"><i /><i /><i /></div>
          <div class="art-building art-building-front"><i /><i /><i /><i /></div>
          <div class="art-box art-box-one" /><div class="art-box art-box-two" /><div class="art-box art-box-three" />
          <div class="art-floor" />
        </div>
        <div class="story-foot">多仓库存与调拨协同平台 · V1.0</div>
      </div>
    </div>
    <div class="login-form-side">
      <div class="login-card">
        <span class="eyebrow">WELCOME BACK</span>
        <h2>登录管理后台</h2>
        <p class="login-subtitle">请输入您的账号信息以继续</p>
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
          <el-form-item label="用户名" prop="username">
            <el-input v-model="form.username" :prefix-icon="User" size="large" placeholder="请输入用户名" autocomplete="username" />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input v-model="form.password" :prefix-icon="Lock" size="large" type="password" show-password placeholder="请输入密码" autocomplete="current-password" @keyup.enter="submit" />
          </el-form-item>
          <el-button class="login-submit" type="primary" size="large" native-type="submit" :loading="loading">
            登录 <el-icon class="button-arrow"><ArrowRight /></el-icon>
          </el-button>
        </el-form>
        <p class="login-note">仅供已授权的仓储业务人员使用</p>
      </div>
    </div>
  </div>
</template>
