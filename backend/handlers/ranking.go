package handlers

import (
	"time"

	"github.com/gin-gonic/gin"
	"short-drama-backend/cron"
	"short-drama-backend/models"
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

	live := rankingListFromDB(rankType)
	if len(live) > 30 {
		live = live[:30]
	}
	if len(live) > 0 {
		_ = utils.CacheSet(cacheKey, live, 25*time.Hour)
	}
	utils.Success(c, live)
}

func rankingListFromDB(rankType string) []map[string]interface{} {
	switch rankType {
	case "rising":
		return cron.BuildRisingRankingList()
	case "rating":
		return cron.BuildRatingRankingList()
	default:
		return cron.BuildHotRankingList()
	}
}

func GetHomeHotRankingFromCache(limit int) []map[string]interface{} {
	var cached []map[string]interface{}
	if err := utils.CacheGet("ranking:hot:full", &cached); err == nil {
		if len(cached) > limit {
			return cached[:limit]
		}
		return cached
	}
	live := cron.BuildHotRankingList()
	if len(live) == 0 {
		return []map[string]interface{}{}
	}
	_ = utils.CacheSet("ranking:hot:full", live, 25*time.Hour)
	if len(live) > limit {
		return live[:limit]
	}
	return live
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

func rankFromRankingItem(item map[string]interface{}) (int, bool) {
	v, ok := item["rank"]
	if !ok {
		return 0, false
	}
	switch x := v.(type) {
	case float64:
		return int(x), true
	case int:
		return x, true
	case int64:
		return int(x), true
	default:
		return 0, false
	}
}

func dramaIDFromRankingItem(item map[string]interface{}) (uint, bool) {
	d, ok := item["drama"].(map[string]interface{})
	if !ok {
		return 0, false
	}
	return uintFromJSONNumber(d["id"])
}

func uintFromJSONNumber(v interface{}) (uint, bool) {
	switch x := v.(type) {
	case float64:
		return uint(x), true
	case int:
		return uint(x), true
	case int64:
		return uint(x), true
	case uint:
		return x, true
	default:
		return 0, false
	}
}

func findRankInCachedList(list []map[string]interface{}, dramaID uint) (int, bool) {
	for _, item := range list {
		id, ok := dramaIDFromRankingItem(item)
		if !ok || id != dramaID {
			continue
		}
		return rankFromRankingItem(item)
	}
	return 0, false
}

// AttachDramaRanking 按热播 > 飙升 > 好评 仅附着一条榜单信息（与客户端展示顺序一致）
func AttachDramaRanking(d *models.Drama) {
	if d == nil || d.ID == 0 {
		return
	}
	var hot, rising, rating []map[string]interface{}
	_ = utils.CacheGet("ranking:hot:full", &hot)
	_ = utils.CacheGet("ranking:rising:full", &rising)
	_ = utils.CacheGet("ranking:rating:full", &rating)
	id := d.ID
	if r, ok := findRankInCachedList(hot, id); ok && r > 0 {
		d.Ranking = &models.DramaRankingInfo{List: "hot", Rank: r}
		return
	}
	if r, ok := findRankInCachedList(rising, id); ok && r > 0 {
		d.Ranking = &models.DramaRankingInfo{List: "rising", Rank: r}
		return
	}
	if r, ok := findRankInCachedList(rating, id); ok && r > 0 {
		d.Ranking = &models.DramaRankingInfo{List: "rating", Rank: r}
		return
	}
	d.Ranking = nil
}
