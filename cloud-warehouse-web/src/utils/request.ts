import axios, { AxiosError, type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/store/auth'
import { pinia } from '@/store'
import type { ApiResponse } from '@/types/api'

export class ApiError extends Error {
  constructor(public code: string, message: string, public status?: number) {
    super(message)
    this.name = 'ApiError'
  }
}

const client = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  timeout: 12000,
})

client.interceptors.request.use((config) => {
  const token = useAuthStore(pinia).token
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

function showError(error: ApiError, isLoginRequest: boolean) {
  const auth = useAuthStore(pinia)
  if (!isLoginRequest && (error.status === 401 || error.code === 'UNAUTHENTICATED')) {
    auth.clearSession()
    ElMessage.warning(error.message || '登录已失效，请重新登录')
    if (location.pathname !== '/login') location.replace('/login')
  } else {
    ElMessage.error(error.message)
  }
}

client.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResponse<unknown>
    if (body && body.code === 'OK' && body.data !== null && body.data !== undefined) {
      response.data = body.data
      return response
    }
    const error = new ApiError(body?.code || 'INVALID_RESPONSE', body?.message || '服务响应异常', response.status)
    showError(error, response.config.url?.endsWith('/auth/login') ?? false)
    return Promise.reject(error)
  },
  (error: AxiosError<ApiResponse<unknown>>) => {
    const body = error.response?.data
    const apiError = new ApiError(
      body?.code || 'NETWORK_ERROR',
      body?.message || (error.response ? `请求失败（${error.response.status}）` : '网络连接失败，请稍后重试'),
      error.response?.status,
    )
    showError(apiError, error.config?.url?.endsWith('/auth/login') ?? false)
    return Promise.reject(apiError)
  },
)

export function request<T>(config: AxiosRequestConfig): Promise<T> {
  return client.request<T>(config).then(response => response.data)
}
