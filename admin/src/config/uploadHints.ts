/**
 * 与后端 handlers.UploadImage / utils 一致：单图 ≤15MB，栅格图最长边缩至 1280px 并转 JPG。
 * 视频上传后端未做大小校验，提示运营自控体积。
 */

export const IMAGE_HINT_COVER =
  '建议图片高∶宽 ≈ 16∶9（竖屏全屏常见比例）。格式：JPG、PNG；单张不超过 15MB；超长边将缩至 1280px 并转为 JPG。'

export const IMAGE_HINT_BANNER =
  '轮播建议横版约 16∶9。格式：JPG、PNG；单张不超过 15MB；超长边将缩至 1280px 并转为 JPG。'

/** 分集正片、剧集管理内分集上传 */
export const VIDEO_HINT_EPISODE =
  '建议画面高∶宽 ≈ 16∶9（竖屏全屏）。格式：MP4 等；建议 H.264 视频 + AAC 音频，分辨率 720p 及以上。请控制单文件体积，避免上传超时或播放卡顿。'

/** 解锁类广告视频 */
export const VIDEO_HINT_AD =
  '宜短；建议画面高∶宽 ≈ 16∶9（竖屏全屏）。格式：MP4 等，建议 H.264 + AAC。请控制体积，避免上传超时。'

/** 解锁类广告图片 */
export const IMAGE_HINT_AD =
  '建议图片高∶宽 ≈ 16∶9（竖屏全屏）。格式：JPG、PNG；单张不超过 15MB；超长边将缩至 1280px 并转为 JPG。'

/** App 用户头像（管理端代用户上传） */
export const IMAGE_HINT_USER_AVATAR =
  '建议正方形或圆形裁切用图（如 1∶1）。格式：JPG、PNG；单张不超过 15MB；超长边将缩至 1280px 并转为 JPG。'
