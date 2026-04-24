package utils

import (
	"regexp"
	"strings"
)

var dramaCategorySplitRe = regexp.MustCompile(`[,，、;|]+`)

// ParseCategoryList 将库内 category 字段（多选分类以逗号等拼接）解析为去重后的有序列表。
func ParseCategoryList(category string) []string {
	category = strings.TrimSpace(category)
	if category == "" {
		return nil
	}
	seen := make(map[string]struct{})
	out := make([]string, 0, 8)
	for _, part := range dramaCategorySplitRe.Split(category, -1) {
		s := strings.TrimSpace(part)
		if s == "" {
			continue
		}
		if _, ok := seen[s]; ok {
			continue
		}
		seen[s] = struct{}{}
		out = append(out, s)
	}
	if len(out) == 0 {
		return nil
	}
	return out
}
