package lbzf

import (
	"crypto/md5"
	"encoding/hex"
	"sort"
	"strings"
)

// Sign 对参与签名的键值对计算 sign（与平台文档：字典序、空值不参与、sign 不参与、拼接 &key=私钥 后 MD5 大写）。
func Sign(pairs map[string]string, privateKey string) string {
	keys := make([]string, 0, len(pairs))
	for k, v := range pairs {
		if k == "sign" || v == "" {
			continue
		}
		keys = append(keys, k)
	}
	sort.Strings(keys)
	parts := make([]string, 0, len(keys))
	for _, k := range keys {
		parts = append(parts, k+"="+pairs[k])
	}
	stringA := strings.Join(parts, "&")
	stringSignTemp := stringA + "&key=" + privateKey
	sum := md5.Sum([]byte(stringSignTemp))
	return strings.ToUpper(hex.EncodeToString(sum[:]))
}

// FormToPairs 从 Gin / net/http 的 url.Values 转为 map，值取单元素。
func FormToPairs(values map[string][]string) map[string]string {
	out := make(map[string]string, len(values))
	for k, vv := range values {
		if len(vv) == 0 {
			continue
		}
		out[k] = vv[0]
	}
	return out
}

// Verify 校验表单签名；pairs 需含 sign 字段，privateKey 为商户私钥。
func Verify(pairs map[string]string, privateKey string) bool {
	sign, ok := pairs["sign"]
	if !ok || sign == "" {
		return false
	}
	expect := Sign(pairs, privateKey)
	return strings.EqualFold(expect, sign)
}
