/**
 * 生产构建：在 .env.production 中设置 VITE_API_ORIGIN（如 http://api2.h6ign.com）
 * 本地开发：不设置或留空，请求走 Vite 代理到本机后端
 */
const raw = (import.meta.env.VITE_API_ORIGIN as string | undefined) || ''
const origin = raw.replace(/\/$/, '')

/** 管理端接口根路径，含 /api/v1/admin */
export const adminApiBase = origin ? `${origin}/api/v1/admin` : '/api/v1/admin'

export const adminUploadImageUrl = `${adminApiBase}/upload/image`
export const adminUploadVideoUrl = `${adminApiBase}/upload/video`

/**
 * 后台存的是相对路径（如 /uploads/images/xxx.jpg），线上管理端与 API 不同域时必须拼成 API 公网地址才能回显。
 * 开发环境 origin 为空时仍用相对路径，走 Vite 对 /uploads 的代理。
 */
export function resolveMediaUrl(path: string | null | undefined): string {
  if (path == null || String(path).trim() === '') return ''
  const p = String(path).trim()
  if (p.startsWith('http://') || p.startsWith('https://')) return p
  if (p.startsWith('//')) {
    return (typeof window !== 'undefined' && window.location.protocol === 'https:' ? 'https:' : 'http:') + p
  }
  const normalized = p.startsWith('/') ? p : `/${p}`
  if (!origin) return normalized
  return `${origin}${normalized}`
}
