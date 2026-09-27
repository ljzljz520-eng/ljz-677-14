import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import router from '@/router'

const baseURL = import.meta.env.VITE_API_BASE_URL || '/api'

const request = axios.create({
  baseURL,
  timeout: 60000,
  headers: {
    'Content-Type': 'application/json'
  }
})

request.interceptors.request.use(
  (config) => {
    const userStore = useUserStore()
    if (userStore.token) {
      config.headers.Authorization = `Bearer ${userStore.token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

request.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.code !== 200) {
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return res
  },
  (error) => {
    if (error.response) {
      const { status, data } = error.response
      if (status === 401) {
        ElMessage.error('登录已过期，请重新登录')
        const userStore = useUserStore()
        userStore.logout()
        router.push('/login')
      } else {
        ElMessage.error(data?.message || '请求失败')
      }
    } else {
      ElMessage.error('网络错误，请检查网络连接')
    }
    return Promise.reject(error)
  }
)

export const authApi = {
  login: (data) => request.post('/auth/login', data)
}

export const excelApi = {
  import: (file, onProgress) => {
    const formData = new FormData()
    formData.append('file', file)
    return request.post('/excel/import', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      },
      timeout: 300000,
      onUploadProgress: onProgress
    })
  },

  getRecords: (params) => request.get('/excel/records', { params }),

  getDataByBatch: (batchNo, params) => request.get(`/excel/data/${batchNo}`, { params }),

  getBatchStats: (batchNo) => request.get(`/excel/stats/${batchNo}`),

  getRow: (id) => request.get(`/excel/row/${id}`),

  correctData: (id, data) => request.put(`/excel/data/correct/${id}`, data),

  reportData: (batchNo) => request.post(`/excel/report/${batchNo}`),

  getFailedData: (batchNo) => request.get(`/excel/report/failed/${batchNo}`),

  retryReport: (batchNo) => request.post(`/excel/report/retry/${batchNo}`),

  // 修正后只重送勾选的异常行
  retryRows: (batchNo, ids) => request.post(`/excel/report/retry-rows/${batchNo}`, { ids }),

  // 结构化异常明细分页，可按错误码筛选
  getReportErrors: (batchNo, params) => request.get(`/excel/report/errors/${batchNo}`, { params }),

  // 错误码聚合（筛选用）
  getErrorCodeStats: (batchNo) => request.get(`/excel/report/error-codes/${batchNo}`),

  // 错误码字典（错误码/描述/处理建议）
  getErrorCodeDict: () => request.get('/excel/error-code-dict'),

  downloadTemplate: () => {
    return `${baseURL}/excel/template`
  },

  exportErrors: (batchNo, errorCode) => {
    const query = errorCode ? `?errorCode=${encodeURIComponent(errorCode)}` : ''
    return `${baseURL}/excel/export/errors/${batchNo}${query}`
  }
}

export default request
