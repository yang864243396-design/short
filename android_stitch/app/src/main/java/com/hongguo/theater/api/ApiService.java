package com.hongguo.theater.api;

import com.hongguo.theater.model.*;

import java.util.List;
import java.util.Map;

import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.http.*;

public interface ApiService {

    // ============ Home ============
    @GET("home")
    Call<ApiResponse<HomeData>> getHome();

    // ============ Banners ============
    @GET("banners")
    Call<ApiResponse<List<Banner>>> getBanners();

    // ============ Drama ============
    @GET("dramas")
    Call<ApiResponse<List<Drama>>> getDramas(
            @Query("category") String category,
            @Query("page") int page,
            @Query("page_size") int pageSize
    );

    @GET("dramas/hot")
    Call<ApiResponse<List<Drama>>> getHotDramas();

    @GET("dramas/recommend")
    Call<ApiResponse<List<Drama>>> getRecommendDramas();

    @GET("dramas/{id}")
    Call<ApiResponse<Drama>> getDramaDetail(@Path("id") long id);

    @GET("dramas/{id}/episodes")
    Call<ApiResponse<List<Episode>>> getDramaEpisodes(@Path("id") long dramaId);

    // ============ Categories ============
    @GET("categories")
    Call<ApiResponse<List<Category>>> getCategories();

    // ============ Search ============
    @GET("search")
    Call<ApiResponse<List<Drama>>> search(@Query("keyword") String keyword);

    @GET("search/hot")
    Call<ApiResponse<List<HotSearch>>> getHotSearch();

    @GET("search/history")
    Call<ApiResponse<List<String>>> getSearchHistory();

    @DELETE("search/history")
    Call<ApiResponse<Void>> clearSearchHistory();

    @GET("search/suggest")
    Call<ApiResponse<List<Drama>>> getSearchSuggest();

    // ============ Rankings ============
    @GET("rankings")
    Call<ApiResponse<List<RankItem>>> getRankings(@Query("type") String type);

    // ============ Ad ============
    @GET("ad/video")
    Call<ApiResponse<Map<String, Object>>> getAdVideo();

    // ============ Feed ============
    @GET("feed")
    Call<ApiResponse<List<Episode>>> getFeed(
            @Query("page") int page,
            @Query("page_size") int pageSize,
            @Query("episode_number") int episodeNumber
    );

    // ============ Auth ============
    @POST("auth/login")
    Call<ApiResponse<Map<String, Object>>> login(@Body Map<String, String> body);

    @POST("auth/register")
    Call<ApiResponse<Map<String, Object>>> register(@Body Map<String, String> body);

    // ============ User (Protected) ============
    @GET("user/profile")
    Call<ApiResponse<User>> getProfile();

    @PUT("user/profile")
    Call<ApiResponse<User>> updateProfile(@Body Map<String, String> body);

    @Multipart
    @POST("user/avatar")
    Call<ApiResponse<User>> uploadAvatar(@Part MultipartBody.Part file);

    @GET("user/history")
    Call<ApiResponse<List<WatchHistory>>> getHistory();

    @GET("user/collections")
    Call<ApiResponse<List<Drama>>> getCollections();

    @GET("user/likes")
    Call<ApiResponse<List<Episode>>> getLikedEpisodes();

    // ============ Episode Interactions (Protected) ============
    @GET("episodes/{id}/interaction")
    Call<ApiResponse<Map<String, Object>>> getEpisodeInteraction(@Path("id") long episodeId);

    @POST("episodes/{id}/like")
    Call<ApiResponse<Map<String, Object>>> likeEpisode(@Path("id") long episodeId);

    @POST("episodes/{id}/collect")
    Call<ApiResponse<Map<String, Object>>> collectEpisode(@Path("id") long episodeId);

    @POST("episodes/{id}/history")
    Call<ApiResponse<Void>> recordHistory(@Path("id") long episodeId);

    // ============ Comments ============
    @GET("episodes/{id}/comments")
    Call<ApiResponse<List<Comment>>> getComments(
            @Path("id") long episodeId,
            @Query("page") int page,
            @Query("page_size") int pageSize
    );

    @POST("episodes/{id}/comments")
    Call<ApiResponse<Comment>> postComment(
            @Path("id") long episodeId,
            @Body Map<String, String> body
    );

    @POST("comments/{id}/like")
    Call<ApiResponse<Map<String, Object>>> likeComment(@Path("id") long commentId);
}
