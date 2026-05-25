import axios from 'axios'

const http = axios.create({
  baseURL: '/api',
  timeout: 30000,
})

// 响应拦截器：统一提取 data
http.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && body.code !== undefined) {
      if (body.code !== 0) {
        return Promise.reject(new Error(body.message || '请求失败'))
      }
      return body
    }
    return response
  },
  (error) => {
    return Promise.reject(error)
  },
)

export default http
