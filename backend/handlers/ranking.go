package handlers

import (
	"time"

	"github.com/gin-gonic/gin"
	"short-drama-backend/utils"
)

func GetRankings(c *gin.Context) {
	rankType := c.DefaultQuery("type", "hot")
	cacheKey := "ranking:" + rankType + ":full"

	var cached []map[string]interface{}
	if err := utils.CacheGet(cacheKey, &cached); err == nil {
		if len(cached) > 30 {
			cached = cached[:30]
		}
		utils.Success(c, cached)
		return
	}

	utils.Success(c, []interface{}{})
}

func GetHomeHotRankingFromCache(limit int) []map[string]interface{} {
	var cached []map[string]interface{}
	if err := utils.CacheGet("ranking:hot:full", &cached); err == nil {
		if len(cached) > limit {
			return cached[:limit]
		}
		return cached
	}
	return []map[string]interface{}{}
}

func UpdateDramaInRankingCache(dramaID uint, updates map[string]interface{}) {
	keys := []string{"ranking:hot:full", "ranking:rising:full", "ranking:rating:full"}

	for _, key := range keys {
		var cached []map[string]interface{}
		if err := utils.CacheGet(key, &cached); err != nil {
			continue
		}

		changed := false
		for i, item := range cached {
			drama, ok := item["drama"].(map[string]interface{})
			if !ok {
				continue
			}

			var itemDramaID uint
			switch v := drama["id"].(type) {
			case float64:
				itemDramaID = uint(v)
			case uint:
				itemDramaID = v
			}

			if itemDramaID == dramaID {
				for k, v := range updates {
					drama[k] = v
				}
				cached[i]["drama"] = drama
				changed = true
			}
		}

		if changed {
			utils.CacheSet(key, cached, 25*time.Hour)
		}
	}

	utils.CacheDelete("home:page")
}
