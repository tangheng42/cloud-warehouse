import { request } from '@/utils/request'
import type { LoginDTO, LoginVO, UserVO } from '@/types/api'

export const login = (data: LoginDTO) => request<LoginVO>({ url: '/auth/login', method: 'POST', data })
export const getCurrentUser = () => request<UserVO>({ url: '/auth/me', method: 'GET' })
