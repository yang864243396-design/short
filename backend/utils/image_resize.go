package utils

import (
	"bytes"
	"image"
	"image/color"
	"image/jpeg"

	_ "image/jpeg"
	_ "image/png"

	"golang.org/x/image/draw"
)

// MaxImageUploadBytes 管理端单次图片上传体积分上限
const MaxImageUploadBytes = 15 << 20

// ImageUploadMaxSide 缩放后最长边（像素），适配手机轮播/封面，显著减小文件体积
const ImageUploadMaxSide = 1280

// NormalizeImageUpload 将支持的栅格图解码、按最长边缩小（不放大）、白底合成透明区后输出 JPEG。
// 若数据不是 JPEG/PNG 等可解码格式，返回 ok=false，由调用方按原文件保存。
func NormalizeImageUpload(data []byte, maxSide int) (out []byte, ok bool) {
	if maxSide < 16 {
		maxSide = ImageUploadMaxSide
	}
	img, _, err := image.Decode(bytes.NewReader(data))
	if err != nil {
		return nil, false
	}
	img = resizeDownIfNeeded(img, maxSide)
	var buf bytes.Buffer
	if err := jpeg.Encode(&buf, img, &jpeg.Options{Quality: 85}); err != nil {
		return nil, false
	}
	return buf.Bytes(), true
}

func resizeDownIfNeeded(src image.Image, maxSide int) image.Image {
	b := src.Bounds()
	w, h := b.Dx(), b.Dy()
	if w <= 0 || h <= 0 {
		return src
	}
	if w <= maxSide && h <= maxSide {
		return src
	}
	nw, nh := maxSide, maxSide*h/w
	if h > w {
		nh, nw = maxSide, maxSide*w/h
	}
	if nw < 1 {
		nw = 1
	}
	if nh < 1 {
		nh = 1
	}
	dst := image.NewRGBA(image.Rect(0, 0, nw, nh))
	draw.Draw(dst, dst.Bounds(), image.NewUniform(color.White), image.Point{}, draw.Src)
	draw.CatmullRom.Scale(dst, dst.Bounds(), src, b, draw.Over, nil)
	return dst
}
