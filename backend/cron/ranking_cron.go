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
	refreshDailyAggregates()
	computeHotRanking()
	computeRisingRanking()
	computeRatingRanking()

	utils.CacheDelete("home:page")

	fmt.Printf("[Cron] All rankings refreshed in %s\n", time.Since(start))
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

func refreshDailyAggregates() {
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

func computeHotRanking() {
	oneMonthAgo := time.Now().AddDate(0, -1, 0)

	var dramas []models.Drama
	database.DB.Raw(`
		SELECT d.*
		FROM dramas d
		WHERE d.created_at >= ? AND d.enabled = 1
		ORDER BY d.heat DESC
		LIMIT 30
	`, oneMonthAgo).Scan(&dramas)

	result := dramasToRankList(dramas)
	utils.CacheSet("ranking:hot:full", result, 25*time.Hour)
	fmt.Printf("[Cron] Hot ranking: %d dramas\n", len(result))
}

func computeRisingRanking() {
	oneMonthAgo := time.Now().AddDate(0, -1, 0)

	database.DB.Exec(`
		UPDATE drama_stats ds
		INNER JOIN dramas d ON d.id = ds.drama_id
		SET ds.rising_score = CASE
			WHEN ds.views_prev_7day > 0 THEN (ds.views_7day - ds.views_prev_7day) * 100.0 / ds.views_prev_7day
			WHEN ds.views_7day > 0 THEN ds.views_7day
			ELSE 0
		END,
		ds.updated_at = NOW()
		WHERE d.created_at >= ?
	`, oneMonthAgo)

	var dramas []models.Drama
	database.DB.Raw(`
		SELECT d.*
		FROM dramas d
		LEFT JOIN drama_stats s ON s.drama_id = d.id
		WHERE d.created_at >= ? AND d.enabled = 1
		ORDER BY COALESCE(s.rising_score, 0) DESC, d.heat DESC
		LIMIT 30
	`, oneMonthAgo).Scan(&dramas)

	result := dramasToRankList(dramas)
	utils.CacheSet("ranking:rising:full", result, 25*time.Hour)
	fmt.Printf("[Cron] Rising ranking: %d dramas\n", len(result))
}

func computeRatingRanking() {
	oneMonthAgo := time.Now().AddDate(0, -1, 0)

	var dramas []models.Drama
	database.DB.Raw(`
		SELECT d.*
		FROM dramas d
		LEFT JOIN drama_stats s ON s.drama_id = d.id
		WHERE d.created_at >= ? AND d.enabled = 1
		ORDER BY COALESCE(s.avg_rating, d.rating) DESC, d.rating DESC
		LIMIT 30
	`, oneMonthAgo).Scan(&dramas)

	result := dramasToRankList(dramas)
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
				"tags":           d.Tags,
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
