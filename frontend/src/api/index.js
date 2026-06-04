import axios from 'axios'

function unwrapCommonResult(response) {
  const body = response.data
  if (body && body.code !== undefined) {
    if (body.code !== 0) {
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return body
  }
  return response
}

const http = axios.create({
  baseURL: '/api',
  timeout: 30000,
})

http.interceptors.response.use(unwrapCommonResult, (error) => Promise.reject(error))

export const v1Http = axios.create({
  baseURL: '/v1',
  timeout: 30000,
})

export function setV1ApiKey(apiKey) {
  if (apiKey) {
    v1Http.defaults.headers.common.Authorization = `Bearer ${apiKey}`
  } else {
    delete v1Http.defaults.headers.common.Authorization
  }
}

v1Http.interceptors.response.use(unwrapCommonResult, (error) => Promise.reject(error))

export default http
