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
		&models.User{},
		&models.Comment{},
		&models.Category{},
		&models.UserLike{},
		&models.UserCollect{},
		&models.UserHistory{},
		&models.CommentLike{},
		&models.CheckIn{},
		&models.WelfareTask{},
		&models.HotSearch{},
		&models.SearchHistory{},
		&models.FeedRecommend{},
		&models.Banner{},
		&models.DramaStats{},
		&models.DailySnapshot{},
		&models.UserRating{},
	)
	if err != nil {
		log.Fatalf("Failed to migrate: %v", err)
	}
	fmt.Println("Database migrated successfully")
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
		{Title: "总裁的双面人生", Description: "秘密、阴影以及一段本不该发生的爱恋。体验本季排名第一的热播剧。", Category: "言情", Tags: "言情,都市,豪门", TotalEpisodes: 60, Rating: 9.6, Heat: 9800000, Status: "completed"},
		{Title: "霓虹之影", Description: "一段关于勇气与冒险的故事，在霓虹闪烁的都市中展开。", Category: "动作", Tags: "动作,冒险", TotalEpisodes: 82, Rating: 9.1, Heat: 8200000, Status: "ongoing"},
		{Title: "午夜契约", Description: "午夜时分签下的契约，改变了两个人的命运。", Category: "言情", Tags: "剧情,言情", TotalEpisodes: 24, Rating: 9.5, Heat: 1285000, Status: "ongoing"},
		{Title: "终极试镜", Description: "一场试镜背后隐藏的惊天秘密。", Category: "悬疑", Tags: "惊悚,悬疑", TotalEpisodes: 100, Rating: 9.2, Heat: 952000, Status: "completed"},
		{Title: "镜头之下", Description: "镜头记录的不只是画面，还有人心。", Category: "动作", Tags: "动作,冒险", TotalEpisodes: 12, Rating: 8.9, Heat: 763000, Status: "ongoing"},
		{Title: "红线", Description: "跨越时代的爱情传奇。", Category: "古装", Tags: "年代,剧情", TotalEpisodes: 80, Rating: 9.4, Heat: 1104000, Status: "completed"},
		{Title: "雨夜街头", Description: "雨夜的街头，邂逅一段不期而遇的感情。", Category: "都市", Tags: "制作推荐,都市", TotalEpisodes: 12, Rating: 9.3, Heat: 9985000, Status: "completed"},
		{Title: "首席的秘密继承人", Description: "真相大白，继承权的最终归属。", Category: "豪门", Tags: "都市,豪门", TotalEpisodes: 72, Rating: 9.7, Heat: 8862000, Status: "ongoing"},
	}
	DB.Create(&dramas)

	for _, drama := range dramas {
		for i := 1; i <= drama.TotalEpisodes && i <= 15; i++ {
			ep := models.Episode{
				DramaID:       drama.ID,
				EpisodeNumber: i,
				Title:         fmt.Sprintf("%s 第%d集", drama.Title, i),
				VideoURL:      fmt.Sprintf("https://example.com/videos/%d/%d.mp4", drama.ID, i),
				Duration:      600 + i*30,
				IsFree:        i <= 6,
			}
			DB.Create(&ep)
		}
	}

	tasks := []models.WelfareTask{
		{Title: "每日签到", Icon: "📋", Reward: "+50金币", RewardCoins: 50, TaskType: "checkin"},
		{Title: "观看短剧", Icon: "▶️", Reward: "+30金币/集", RewardCoins: 30, TaskType: "watch"},
		{Title: "分享短剧", Icon: "🔗", Reward: "+20金币", RewardCoins: 20, TaskType: "share"},
		{Title: "邀请好友", Icon: "👥", Reward: "+200金币", RewardCoins: 200, TaskType: "invite"},
		{Title: "发表评论", Icon: "💬", Reward: "+10金币", RewardCoins: 10, TaskType: "comment"},
	}
	DB.Create(&tasks)

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

func seedAdmin() {
	var count int64
	DB.Model(&models.User{}).Where("role = ?", "admin").Count(&count)
	if count > 0 {
		return
	}
	hash, _ := hashPassword("admin123")
	admin := models.User{
		Username:     "admin",
		Nickname:     "管理员",
		PasswordHash: hash,
		Role:         "admin",
		Status:       1,
	}
	DB.Create(&admin)
}

func hashPassword(password string) (string, error) {
	bytes, err := bcrypt.GenerateFromPassword([]byte(password), bcrypt.DefaultCost)
	return string(bytes), err
}
