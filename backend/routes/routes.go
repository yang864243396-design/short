package routes

import (
	"time"

	"github.com/gin-gonic/gin"
	"short-drama-backend/config"
	"short-drama-backend/handlers"
	"short-drama-backend/middleware"
)

func Setup(r *gin.Engine) {
	cfg := config.Load()
	streamPerMin := cfg.StreamIPRequestsPerMinute
	if streamPerMin < 1 {
		streamPerMin = 120
	}

	r.Use(middleware.CORS())

	r.Static("/uploads/images", "./uploads/images")
	r.Static("/uploads/avatars", "./uploads/avatars")

	api := r.Group("/api/v1")
	{
		// 聚合异步通知（公网，无鉴权，验签）
		api.POST("/pay/lbzf/notify", handlers.LubzfNotify)

		// 用于排查：进程是否监听、MySQL/Redis、当前 Redis 配置摘要（不含密码）
		api.GET("/health", handlers.GetHealth)

		api.GET("/home", handlers.GetHomePage)

		api.GET("/dramas", handlers.GetDramas)
		api.GET("/dramas/hot", handlers.GetHotDramas)
		api.GET("/dramas/recommend", handlers.GetRecommendDramas)
		api.GET("/dramas/:id", handlers.GetDramaDetail)
		api.GET("/dramas/:id/episodes", middleware.OptionalAuth(), handlers.GetEpisodes)
		api.GET("/categories", handlers.GetCategories)
		api.GET("/search", middleware.OptionalAuth(), handlers.SearchDramas)

		api.GET("/rankings", handlers.GetRankings)

		api.GET("/banners", handlers.GetActiveBanners)

		api.GET("/feed", middleware.OptionalAuth(), handlers.GetFeed)

		api.GET("/ad/video", middleware.OptionalAuth(), handlers.GetAdVideo)

		api.GET("/stream/:episodeId", middleware.RateLimit(streamPerMin, time.Minute), middleware.OptionalAuth(), handlers.StreamVideo)

		auth := api.Group("/auth")
		{
			auth.POST("/send-register-code", handlers.SendRegisterCode)
			auth.POST("/register", handlers.Register)
			auth.POST("/login", handlers.Login)
		}

		api.GET("/episodes/:id/comments", handlers.GetComments)
		api.GET("/episodes/:id/replies/:root_id", handlers.GetCommentReplies)

		protected := api.Group("")
		protected.Use(middleware.Auth())
		{
			protected.GET("/user/profile", handlers.GetProfile)
			protected.PUT("/user/profile", handlers.UpdateProfile)
			protected.POST("/user/avatar", handlers.UploadAvatar)
			protected.GET("/user/history", handlers.GetHistory)
			protected.GET("/user/collections", handlers.GetCollections)
			protected.GET("/user/likes", handlers.GetLikedEpisodes)
			protected.GET("/user/wallet", handlers.GetWallet)
			protected.GET("/user/wallet/transactions", handlers.GetWalletTransactions)
			protected.GET("/recharge-packages", handlers.ListRechargePackages)
			protected.POST("/recharge-orders", handlers.CreateRechargeOrder)
			protected.GET("/recharge-orders/query", handlers.QueryLubzfRechargeOrder)
			protected.POST("/recharge-orders/:id/simulate-pay", handlers.SimulateRechargeOrderComplete)
			protected.GET("/user/ad-skip", handlers.GetAdSkipStatus)
			protected.POST("/user/ad-skip/purchase", handlers.PurchaseAdSkip)

			protected.GET("/episodes/:id/interaction", handlers.GetEpisodeInteraction)
			protected.POST("/episodes/:id/like", handlers.LikeEpisode)
			protected.POST("/episodes/:id/collect", handlers.CollectEpisode)
			protected.POST("/episodes/:id/history", handlers.RecordHistory)
			protected.POST("/episodes/:id/unlock-coins", handlers.UnlockEpisodeWithCoins)
			protected.POST("/episodes/:id/comments", handlers.PostComment)
			protected.POST("/comments/:id/like", handlers.LikeComment)

			protected.GET("/search/history", handlers.GetSearchHistory)
			protected.DELETE("/search/history", handlers.ClearSearchHistory)
		}

		admin := api.Group("/admin")
		{
			admin.POST("/login", handlers.AdminLogin)
		}

		adminProtected := api.Group("/admin")
		adminProtected.Use(middleware.AdminAuth())
		{
			adminProtected.GET("/dashboard", handlers.AdminDashboard)

			adminProtected.GET("/dramas", handlers.AdminGetDramas)
			adminProtected.POST("/dramas", handlers.AdminCreateDrama)
			adminProtected.PUT("/dramas/:id", handlers.AdminUpdateDrama)
			adminProtected.DELETE("/dramas/:id", handlers.AdminDeleteDrama)

			adminProtected.GET("/episodes", handlers.AdminGetEpisodes)
			adminProtected.POST("/episodes", handlers.AdminCreateEpisode)
			adminProtected.PUT("/episodes/:id", handlers.AdminUpdateEpisode)
			adminProtected.DELETE("/episodes/:id", handlers.AdminDeleteEpisode)

			adminProtected.GET("/users", handlers.AdminGetUsers)
			adminProtected.GET("/users/:id", handlers.AdminGetAppUser)
			adminProtected.PUT("/users/:id", handlers.AdminUpdateUser)
			adminProtected.DELETE("/users/:id", handlers.AdminDeleteAppUser)
			adminProtected.POST("/users/:id/wallet/recharge", handlers.AdminRechargeUser)
			adminProtected.POST("/users/:id/wallet/deduct", handlers.AdminDeductUser)
			adminProtected.GET("/wallet/transactions", handlers.AdminListWalletTransactions)
			adminProtected.GET("/recharge-orders", handlers.AdminListRechargeOrders)
			adminProtected.GET("/users/:id/wallet/recent", handlers.AdminListUserRecentWalletTx)

			adminProtected.GET("/recharge-packages", handlers.AdminListRechargePackages)
			adminProtected.POST("/recharge-packages", handlers.AdminCreateRechargePackage)
			adminProtected.PUT("/recharge-packages/:id", handlers.AdminUpdateRechargePackage)
			adminProtected.DELETE("/recharge-packages/:id", handlers.AdminDeleteRechargePackage)
			adminProtected.GET("/pay-product-configs", handlers.AdminListPayProductConfigs)
			adminProtected.POST("/pay-product-configs", handlers.AdminCreatePayProductConfig)
			adminProtected.PUT("/pay-product-configs/:id", handlers.AdminUpdatePayProductConfig)
			adminProtected.DELETE("/pay-product-configs/:id", handlers.AdminDeletePayProductConfig)
			adminProtected.GET("/ad-skip-configs", handlers.AdminListAdSkipConfigs)
			adminProtected.POST("/ad-skip-configs", handlers.AdminCreateAdSkipConfig)
			adminProtected.PUT("/ad-skip-configs/:id", handlers.AdminUpdateAdSkipConfig)
			adminProtected.DELETE("/ad-skip-configs/:id", handlers.AdminDeleteAdSkipConfig)

			adminProtected.GET("/admins", handlers.AdminGetAdmins)
			adminProtected.POST("/admins", handlers.AdminCreateAdmin)
			adminProtected.PUT("/admins/:id", handlers.AdminUpdateAdmin)
			adminProtected.DELETE("/admins/:id", handlers.AdminDeleteAdmin)

			adminProtected.GET("/comments", handlers.AdminGetComments)
			adminProtected.DELETE("/comments/:id", handlers.AdminDeleteComment)

			adminProtected.GET("/categories", handlers.AdminGetCategories)
			adminProtected.POST("/categories", handlers.AdminCreateCategory)
			adminProtected.PUT("/categories/:id", handlers.AdminUpdateCategory)
			adminProtected.DELETE("/categories/:id", handlers.AdminDeleteCategory)

			adminProtected.GET("/banners", handlers.AdminGetBanners)
			adminProtected.POST("/banners", handlers.AdminCreateBanner)
			adminProtected.PUT("/banners/:id", handlers.AdminUpdateBanner)
			adminProtected.DELETE("/banners/:id", handlers.AdminDeleteBanner)

			adminProtected.GET("/roles", handlers.AdminGetRoles)
			adminProtected.POST("/roles", handlers.AdminCreateRole)
			adminProtected.PUT("/roles/:id", handlers.AdminUpdateRole)
			adminProtected.DELETE("/roles/:id", handlers.AdminDeleteRole)

			adminProtected.GET("/ads", handlers.AdminGetAds)
			adminProtected.POST("/ads", handlers.AdminCreateAd)
			adminProtected.PUT("/ads/:id", handlers.AdminUpdateAd)
			adminProtected.DELETE("/ads/:id", handlers.AdminDeleteAd)

			adminProtected.POST("/upload/video", handlers.UploadVideo)
			adminProtected.POST("/upload/image", handlers.UploadImage)
		}
	}
}
