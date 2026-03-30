package routes

import (
	"time"

	"github.com/gin-gonic/gin"
	"short-drama-backend/handlers"
	"short-drama-backend/middleware"
)

func Setup(r *gin.Engine) {
	r.Use(middleware.CORS())

	r.Static("/uploads", "./uploads")

	api := r.Group("/api/v1")
	{
		api.GET("/home", handlers.GetHomePage)

		api.GET("/dramas", handlers.GetDramas)
		api.GET("/dramas/hot", handlers.GetHotDramas)
		api.GET("/dramas/recommend", handlers.GetRecommendDramas)
		api.GET("/dramas/:id", handlers.GetDramaDetail)
		api.GET("/dramas/:id/episodes", handlers.GetEpisodes)
		api.GET("/categories", handlers.GetCategories)
		api.GET("/search", handlers.SearchDramas)

		api.GET("/rankings", handlers.GetRankings)

		api.GET("/search/hot", handlers.GetHotSearch)
		api.GET("/search/suggest", handlers.GetSearchSuggest)

		api.GET("/banners", handlers.GetActiveBanners)

		api.GET("/feed", handlers.GetFeed)

		api.GET("/stream/:episodeId", middleware.RateLimit(60, time.Minute), handlers.StreamVideo)

		auth := api.Group("/auth")
		{
			auth.POST("/register", handlers.Register)
			auth.POST("/login", handlers.Login)
		}

		api.GET("/episodes/:id/comments", handlers.GetComments)

		protected := api.Group("")
		protected.Use(middleware.Auth())
		{
			protected.GET("/user/profile", handlers.GetProfile)
			protected.PUT("/user/profile", handlers.UpdateProfile)
			protected.GET("/user/history", handlers.GetHistory)
			protected.GET("/user/collections", handlers.GetCollections)

			protected.POST("/episodes/:id/like", handlers.LikeEpisode)
			protected.POST("/episodes/:id/collect", handlers.CollectEpisode)
			protected.POST("/episodes/:id/comments", handlers.PostComment)
			protected.POST("/comments/:id/like", handlers.LikeComment)

			protected.GET("/welfare/tasks", handlers.GetWelfareTasks)
			protected.POST("/welfare/checkin", handlers.DailyCheckin)

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
			adminProtected.PUT("/users/:id", handlers.AdminUpdateUser)

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

			adminProtected.POST("/upload/video", handlers.UploadVideo)
			adminProtected.POST("/upload/image", handlers.UploadImage)
		}
	}
}
