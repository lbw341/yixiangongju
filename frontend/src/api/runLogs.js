import { request } from './request'

export async function listRunLogs(params = {}) {
  const qs = new URLSearchParams()
  Object.entries(params).forEach(([k, v]) => {
    if (v !== '' && v != null) qs.append(k, v)
  })
  const suffix = qs.toString() ? `?${qs.toString()}` : ''
  return request(`/api/run-logs${suffix}`)
}

export async function getRunLog(id) {
  return request(`/api/run-logs/${id}`)
}
