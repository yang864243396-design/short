package cron

import (
	"fmt"
	"time"

	"short-drama-backend/database"
	"short-drama-backend/models"
	"short-drama-backend/utils"
)

func StartRankingCron() {
	go func() {
		for {
			now := time.Now()
			next := time.Date(now.Year(), now.Month(), now.Day()+1, 4, 0, 0, 0, now.Location())
			duration := next.Sub(now)

			fmt.Printf("[Cron] Next ranking refresh at %s (in %s)\n", next.Format("2006-01-02 15:04:05"), duration)
			time.Sleep(duration)

			RefreshAllRankings()
		}
	}()

	go RefreshAllRankings()
}

func RefreshAllRankings() {
	start := time.Now()
	fmt.Println("[Cron] Refreshing all rankings...")

	ensureAllDramasHaveStats()
	snapshotDailyHeat()
	backfillHeatEngagementFromTotals()
	RefreshDramaStatsAggregates()
	computeHotRanking()
	computeRisingRanking()
	computeRatingRanking()

	utils.CacheDelete("home:page")

	fmt.Printf("[Cron] All rankings refreshed in %s\n", time.Since(start))
}

// snapshotDailyHeat 写入当日各剧 dramas.heat 快照，供飙升榜计算「近30日热度增量」
func snapshotDailyHeat() {
	today := time.Now().Format("2006-01-02")
	res := database.DB.Exec(`
		INSERT INTO daily_snapshots (drama_id, date, heat, views, likes, collects, created_at)
		SELECT d.id, ?, d.heat, 0, 0, 0, NOW()
		FROM dramas d
		WHERE d.enabled = 1
		ON DUPLICATE KEY UPDATE heat = VALUES(heat)
	`, today)
	if res.Error != nil {
		fmt.Printf("[Cron] snapshotDailyHeat: %v\n", res.Error)
	}
}

// backfillHeatEngagementFromTotals 将历史 total_* 按每条 +100 规则回填好评榜用字段（对应列为 0 时才填，避免覆盖已增量写入）
func backfillHeatEngagementFromTotals() {
	database.DB.Exec(`
		UPDATE drama_stats
		SET heat_from_comments = COALESCE(total_comments, 0) * 100
		WHERE COALESCE(heat_from_comments, 0) = 0 AND COALESCE(total_comments, 0) > 0
	`)
	database.DB.Exec(`
		UPDATE drama_stats
		SET heat_from_collects = COALESCE(total_collects, 0) * 100
		WHERE COALESCE(heat_from_collects, 0) = 0 AND COALESCE(total_collects, 0) > 0
	`)
}

func ensureAllDramasHaveStats() {
	oneMonthAgo := time.Now().AddDate(0, -1, 0)

	var dramas []models.Drama
	database.DB.Where("created_at >= ?", oneMonthAgo).Find(&dramas)

	for _, d := range dramas {
		var count int64
		database.DB.Model(&models.DramaStats{}).Where("drama_id = ?", d.ID).Count(&count)
		if count == 0 {
			database.DB.Create(&models.DramaStats{DramaID: d.ID, TotalViews: 0})
		}
	}
	fmt.Printf("[Cron] Ensured stats for %d dramas\n", len(dramas))
}

// RefreshDramaStatsAggregates 从 daily_snapshots 汇总近 7 日/前 7 日播放量（预留统计用）
func RefreshDramaStatsAggregates() {
	sevenDaysAgo := time.Now().AddDate(0, 0, -7).Format("2006-01-02")
	fourteenDaysAgo := time.Now().AddDate(0, 0, -14).Format("2006-01-02")
	oneMonthAgo := time.Now().AddDate(0, -1, 0)

	database.DB.Exec(`
		UPDATE drama_stats ds
		INNER JOIN dramas d ON d.id = ds.drama_id
		SET
			ds.views_7day = COALESCE((
				SELECT SUM(snap.views) FROM daily_snapshots snap
				WHERE snap.drama_id = ds.drama_id AND snap.date >= ?
			), 0),
			ds.views_prev_7day = COALESCE((
				SELECT SUM(snap.views) FROM daily_snapshots snap
				WHERE snap.drama_id = ds.drama_id AND snap.date >= ? AND snap.date < ?
			), 0),
			ds.updated_at = NOW()
		WHERE d.created_at >= ?
	`, sevenDaysAgo, fourteenDaysAgo, sevenDaysAgo, oneMonthAgo)
}

// BuildHotRankingList 与定时任务同源的热播榜（最多 30 条），供 Redis 未命中时降级。
func BuildHotRankingList() []map[string]interface{} {
	oneMonthAgo := time.Now().AddDate(0, -1, 0)

	var dramas []models.Drama
	database.DB.Raw(`
		SELECT d.*
		FROM dramas d
		WHERE d.created_at >= ? AND d.enabled = 1
		ORDER BY d.heat DESC
		LIMIT 30
	`, oneMonthAgo).Scan(&dramas)

	return dramasToRankList(dramas)
}

func computeHotRanking() {
	result := BuildHotRankingList()
	utils.CacheSet("ranking:hot:full", result, 25*time.Hour)
	fmt.Printf("[Cron] Hot ranking: %d dramas\n", len(result))
}

// BuildRisingRankingList 飙升榜：近一个月内上架的剧，按「最近约30天内 dramas.heat 增量」降序（依赖 daily_snapshots.heat 日快照）
func BuildRisingRankingList() []map[string]interface{} {
	oneMonthAgo := time.Now().AddDate(0, -1, 0)

	var dramas []models.Drama
	database.DB.Raw(`
		SELECT d.*
		FROM dramas d
		WHERE d.created_at >= ? AND d.enabled = 1
		ORDER BY (
			d.heat - COALESCE(
				(SELECT s2.heat FROM daily_snapshots s2
				 WHERE s2.drama_id = d.id AND s2.date <= DATE_SUB(CURDATE(), INTERVAL 30 DAY)
				 ORDER BY s2.date DESC LIMIT 1),
				0
			)
		) DESC, d.heat DESC
		LIMIT 30
	`, oneMonthAgo).Scan(&dramas)

	return dramasToRankList(dramas)
}

func computeRisingRanking() {
	result := BuildRisingRankingList()
	utils.CacheSet("ranking:rising:full", result, 25*time.Hour)
	fmt.Printf("[Cron] Rising ranking: %d dramas\n", len(result))
}

// BuildRatingRankingList 好评榜：近一个月内上架；按「评论+收藏贡献的热度」（heat_from_comments + heat_from_collects）降序
func BuildRatingRankingList() []map[string]interface{} {
	oneMonthAgo := time.Now().AddDate(0, -1, 0)

	var dramas []models.Drama
	database.DB.Raw(`
		SELECT d.*
		FROM dramas d
		LEFT JOIN drama_stats s ON s.drama_id = d.id
		WHERE d.created_at >= ? AND d.enabled = 1
		ORDER BY (COALESCE(s.heat_from_comments, 0) + COALESCE(s.heat_from_collects, 0)) DESC, d.heat DESC
		LIMIT 30
	`, oneMonthAgo).Scan(&dramas)

	return dramasToRankList(dramas)
}

func computeRatingRanking() {
	result := BuildRatingRankingList()
	utils.CacheSet("ranking:rating:full", result, 25*time.Hour)
	fmt.Printf("[Cron] Rating ranking: %d dramas\n", len(result))
}

func dramasToRankList(dramas []models.Drama) []map[string]interface{} {
	result := make([]map[string]interface{}, 0, len(dramas))
	for i, d := range dramas {
		var totalLikes int64
		var stats models.DramaStats
		if database.DB.Where("drama_id = ?", d.ID).First(&stats).Error == nil {
			totalLikes = stats.TotalLikes
		}

		result = append(result, map[string]interface{}{
			"rank": i + 1,
			"drama": map[string]interface{}{
				"id":             d.ID,
				"title":          d.Title,
				"cover_url":      d.CoverURL,
				"description":    d.Description,
				"category":       d.Category,
				"category_list":  utils.ParseCategoryList(d.Category),
				"total_episodes": d.TotalEpisodes,
				"rating":         d.Rating,
				"heat":           d.Heat,
				"status":         d.Status,
			},
			"heat":        d.Heat,
			"total_likes": totalLikes,
		})
	}
	return result
}
