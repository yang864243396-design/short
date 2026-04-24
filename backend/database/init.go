package database

import (
	"fmt"
	"log"

	"golang.org/x/crypto/bcrypt"
	"gorm.io/driver/mysql"
	"gorm.io/gorm"
	"short-drama-backend/config"
	"short-drama-backend/models"
)

var DB *gorm.DB

func Init(cfg config.DBConfig) {
	var err error
	DB, err = gorm.Open(mysql.Open(cfg.DSN()), &gorm.Config{})
	if err != nil {
		log.Fatalf("Failed to connect database: %v", err)
	}

	sqlDB, err := DB.DB()
	if err != nil {
		log.Fatalf("Failed to get sql.DB: %v", err)
	}
	sqlDB.SetMaxOpenConns(100)
	sqlDB.SetMaxIdleConns(20)
	sqlDB.SetConnMaxLifetime(30 * 60 * 1e9) // 30 minutes

	fmt.Println("Database connected successfully")

	err = DB.AutoMigrate(
		&models.Drama{},
		&models.Episode{},
		&models.Admin{},
		&models.AppUser{},
		&models.Comment{},
		&models.Category{},
		&models.UserLike{},
		&models.UserCollect{},
		&models.UserHistory{},
		&models.CommentLike{},
		&models.HotSearch{},
		&models.SearchHistory{},
		&models.FeedRecommend{},
		&models.Banner{},
		&models.DramaStats{},
		&models.DailySnapshot{},
		&models.UserRating{},
		&models.AdminRole{},
		&models.AdVideo{},
		&models.WalletTransaction{},
		&models.UserEpisodeCoinUnlock{},
		&models.DataFixMarker{},
		&models.RechargePackage{},
		&models.RechargeOrder{},
		&models.PayProductConfig{},
		&models.RechargeMchSeq{},
		&models.AdSkipConfig{},
	)
	if err != nil {
		log.Fatalf("Failed to migrate: %v", err)
	}
	fmt.Println("Database migrated successfully")

	dropAppUserVipLevelColumnIfExists()

	backfillAppUserRegisteredEmail()

	runOneTimeDataFixes()

	seedCommerceDefaults()
	backfillAdSkipV2Data()

	cleanDuplicateEpisodes()
	syncAllDramaEpisodeCounts()
}

// dropAppUserVipLevelColumnIfExists removes legacy vip_level column if present (GORM AutoMigrate won't drop columns).
func dropAppUserVipLevelColumnIfExists() {
	var cnt int64
	if err := DB.Raw(`
		SELECT COUNT(*) FROM information_schema.COLUMNS
		WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'app_users' AND COLUMN_NAME = 'vip_level'
	`).Scan(&cnt).Error; err != nil {
		log.Printf("vip_level column check failed: %v", err)
		return
	}
	if cnt == 0 {
		return
	}
	if err := DB.Exec("ALTER TABLE app_users DROP COLUMN vip_level").Error; err != nil {
		log.Printf("drop vip_level column failed: %v", err)
		return
	}
	log.Println("Dropped legacy column app_users.vip_level")
}

func backfillAppUserRegisteredEmail() {
	if err := DB.Exec(`
		UPDATE app_users
		SET registered_email = username
		WHERE deleted_at IS NULL
		  AND (registered_email IS NULL OR registered_email = '')
	`).Error; err != nil {
		log.Printf("backfill app_users.registered_email: %v", err)
	}
}

func cleanDuplicateEpisodes() {
	DB.Exec(`
		DELETE e1 FROM episodes e1
		INNER JOIN episodes e2
		ON e1.drama_id = e2.drama_id AND e1.episode_number = e2.episode_number AND e1.id > e2.id
	`)

	var indexExists int64
	DB.Raw("SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'episodes' AND index_name = 'idx_drama_episode'").Scan(&indexExists)
	if indexExists == 0 {
		if err := DB.Exec("CREATE UNIQUE INDEX idx_drama_episode ON episodes(drama_id, episode_number)").Error; err != nil {
			fmt.Printf("[Warning] Failed to create unique index on episodes: %v\n", err)
		} else {
			fmt.Println("Created unique index idx_drama_episode on episodes")
		}
	}
}

func syncAllDramaEpisodeCounts() {
	var dramas []models.Drama
	DB.Find(&dramas)

	updated := 0
	for _, d := range dramas {
		var count int64
		DB.Model(&models.Episode{}).Where("drama_id = ?", d.ID).Count(&count)
		if int(count) != d.TotalEpisodes {
			DB.Model(&models.Drama{}).Where("id = ?", d.ID).Update("total_episodes", count)
			updated++
		}
	}
	fmt.Printf("Synced episode counts: %d/%d dramas updated\n", updated, len(dramas))
}

// runOneTimeDataFixes 仅执行一次的数据修正（见 models.DataFixMarker）。
func runOneTimeDataFixes() {
	const marker = "episode_nonfree_unlock_coins_all_1"
	err := DB.Transaction(func(tx *gorm.DB) error {
		var n int64
		tx.Model(&models.DataFixMarker{}).Where("marker = ?", marker).Count(&n)
		if n > 0 {
			return nil
		}
		if err := tx.Model(&models.Episode{}).Where("is_free = ?", false).Update("unlock_coins", 1).Error; err != nil {
			return err
		}
		return tx.Create(&models.DataFixMarker{Marker: marker}).Error
	})
	if err != nil {
		log.Printf("runOneTimeDataFixes: %v\n", err)
	}
}

func Seed() {
	seedAdmin()

	var count int64
	DB.Model(&models.Drama{}).Count(&count)
	if count > 0 {
		return
	}

	categories := []models.Category{
		{Name: "推荐", Sort: 0}, {Name: "言情", Sort: 1}, {Name: "动作", Sort: 2},
		{Name: "悬疑", Sort: 3}, {Name: "古装", Sort: 4}, {Name: "都市", Sort: 5},
		{Name: "甜宠", Sort: 6}, {Name: "复仇", Sort: 7}, {Name: "穿越", Sort: 8}, {Name: "豪门", Sort: 9},
	}
	DB.Create(&categories)

	dramas := []models.Drama{
		{Title: "总裁的双面人生", Description: "秘密、阴影以及一段本不该发生的爱恋。体验本季排名第一的热播剧。", Category: "言情,都市,豪门", TotalEpisodes: 60, Rating: 9.6, Heat: 9800000, Status: "completed"},
		{Title: "霓虹之影", Description: "一段关于勇气与冒险的故事，在霓虹闪烁的都市中展开。", Category: "动作,冒险", TotalEpisodes: 82, Rating: 9.1, Heat: 8200000, Status: "ongoing"},
		{Title: "午夜契约", Description: "午夜时分签下的契约，改变了两个人的命运。", Category: "言情,剧情", TotalEpisodes: 24, Rating: 9.5, Heat: 1285000, Status: "ongoing"},
		{Title: "终极试镜", Description: "一场试镜背后隐藏的惊天秘密。", Category: "悬疑,惊悚", TotalEpisodes: 100, Rating: 9.2, Heat: 952000, Status: "completed"},
		{Title: "镜头之下", Description: "镜头记录的不只是画面，还有人心。", Category: "动作,冒险", TotalEpisodes: 12, Rating: 8.9, Heat: 763000, Status: "ongoing"},
		{Title: "红线", Description: "跨越时代的爱情传奇。", Category: "古装,年代,剧情", TotalEpisodes: 80, Rating: 9.4, Heat: 1104000, Status: "completed"},
		{Title: "雨夜街头", Description: "雨夜的街头，邂逅一段不期而遇的感情。", Category: "都市,制作推荐", TotalEpisodes: 12, Rating: 9.3, Heat: 9985000, Status: "completed"},
		{Title: "首席的秘密继承人", Description: "真相大白，继承权的最终归属。", Category: "豪门,都市", TotalEpisodes: 72, Rating: 9.7, Heat: 8862000, Status: "ongoing"},
	}
	DB.Create(&dramas)

	for _, drama := range dramas {
		for i := 1; i <= drama.TotalEpisodes && i <= 15; i++ {
			isFree := i <= 6
			ep := models.Episode{
				DramaID:       drama.ID,
				EpisodeNumber: i,
				Title:         fmt.Sprintf("%s 第%d集", drama.Title, i),
				VideoURL:      fmt.Sprintf("https://example.com/videos/%d/%d.mp4", drama.ID, i),
				IsFree:        isFree,
				UnlockCoins:   0,
			}
			if !isFree {
				ep.UnlockCoins = 1
			}
			DB.Create(&ep)
		}
	}

	hotSearches := []models.HotSearch{
		{Keyword: "错爱豪门", Heat: 1285000, Badge: "热", Rank: 1},
		{Keyword: "暗夜复仇", Heat: 982000, Badge: "新", Rank: 2},
		{Keyword: "闪婚娇妻有点甜", Heat: 851000, Badge: "升", Rank: 3},
		{Keyword: "豪门弃少之逆天改命", Heat: 724000, Badge: "", Rank: 4},
		{Keyword: "我的替身女友", Heat: 668000, Badge: "", Rank: 5},
		{Keyword: "战神归来", Heat: 592000, Badge: "", Rank: 6},
		{Keyword: "离婚后前夫哭着求复婚", Heat: 545000, Badge: "", Rank: 7},
		{Keyword: "落跑甜心", Heat: 489000, Badge: "", Rank: 8},
		{Keyword: "最强赘婿", Heat: 412000, Badge: "", Rank: 9},
		{Keyword: "千金归来", Heat: 357000, Badge: "", Rank: 10},
	}
	DB.Create(&hotSearches)

	fmt.Println("Seed data created successfully")
}

// backfillAdSkipV2Data 为既有库补全 package_type / skip_count，并将仍有效的旧「仅时长免广」用户给予默认次数
func backfillAdSkipV2Data() {
	if err := DB.Exec(`
		UPDATE ad_skip_configs SET package_type = ?
		WHERE package_type = '' OR package_type IS NULL
	`, models.AdSkipPackageTypeTime).Error; err != nil {
		log.Printf("backfill ad_skip_configs.package_type: %v", err)
	}
	if err := DB.Model(&models.AdSkipConfig{}).Where("skip_count < ?", 1).Update("skip_count", 100).Error; err != nil {
		log.Printf("backfill ad_skip_configs.skip_count: %v", err)
	}
	// 未到期且余额为 0 的老用户，近似对齐旧「无次数限制」
	if err := DB.Exec(`
		UPDATE app_users
		SET ad_skip_remaining = 100
		WHERE deleted_at IS NULL
		  AND ad_skip_expires_at IS NOT NULL
		  AND ad_skip_expires_at > NOW()
		  AND (ad_skip_remaining = 0 OR ad_skip_remaining IS NULL)
	`).Error; err != nil {
		log.Printf("backfill app_users ad_skip_remaining: %v", err)
	}
}

func seedCommerceDefaults() {
	var n int64
	DB.Model(&models.AdSkipConfig{}).Count(&n)
	if n == 0 {
		DB.Create(&models.AdSkipConfig{
			Name:          "24小时体验",
			PackageType:   models.AdSkipPackageTypeTime,
			DurationHours: 24,
			SkipCount:     100,
			PriceCoins:    500,
			Enabled:       true,
			Sort:          1,
		})
	}
	DB.Model(&models.RechargePackage{}).Count(&n)
	if n == 0 {
		pkgs := []models.RechargePackage{
			{Name: "6元·600金币", Coins: 600, PriceYuan: 6, Enabled: true, Sort: 1},
			{Name: "30元·3000金币", Coins: 3000, PriceYuan: 30, Enabled: true, Sort: 2},
		}
		DB.Create(&pkgs)
	}
	DB.Model(&models.PayProductConfig{}).Count(&n)
	if n == 0 {
		// 示例产品 ID，联调时按台方实际替换；未开启 LUBZF 时不影响
		DB.Create(&models.PayProductConfig{ProductID: "8010", Name: "天猫支付宝（示例）", Enabled: true, Sort: 1})
	}
}

func seedAdmin() {
	var superRole models.AdminRole
	if DB.Where("name = ?", "超级管理员").First(&superRole).RowsAffected == 0 {
		superRole = models.AdminRole{
			Name:        "超级管理员",
			Description: "拥有所有权限",
			Permissions: models.AllPermissions,
		}
		DB.Create(&superRole)
	}

	var count int64
	DB.Model(&models.Admin{}).Count(&count)
	if count > 0 {
		DB.Model(&models.Admin{}).Where("role_id = ?", 0).Update("role_id", superRole.ID)
		return
	}
	hash, _ := hashPassword("admin123")
	admin := models.Admin{
		Username:     "admin",
		Nickname:     "管理员",
		PasswordHash: hash,
		RoleID:       superRole.ID,
		Status:       1,
	}
	DB.Create(&admin)
}

func hashPassword(password string) (string, error) {
	bytes, err := bcrypt.GenerateFromPassword([]byte(password), bcrypt.DefaultCost)
	return string(bytes), err
}
