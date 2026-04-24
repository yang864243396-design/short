package com.hongguo.theater.api;

import androidx.annotation.Nullable;

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

    // ============ Search ============
    @GET("search")
    Call<ApiResponse<List<Drama>>> search(@Query("keyword") String keyword);

    @GET("search/history")
    Call<ApiResponse<List<String>>> getSearchHistory();

    @DELETE("search/history")
    Call<ApiResponse<Void>> clearSearchHistory();

    // ============ Rankings ============
    @GET("rankings")
    Call<ApiResponse<List<RankItem>>> getRankings(@Query("type") String type);

    // ============ Ad ============
    @GET("ad/video")
    Call<ApiResponse<Map<String, Object>>> getAdVideo(@Query("episode_id") Long episodeId);

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

    @POST("auth/send-register-code")
    Call<ApiResponse<Object>> sendRegisterCode(@Body Map<String, String> body);

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

    @GET("user/wallet")
    Call<ApiResponse<WalletBalance>> getWallet();

    @GET("user/wallet/transactions")
    Call<ApiResponse<WalletTransactionsPage>> getWalletTransactions(
            @Query("page") int page,
            @Query("page_size") int pageSize,
            @Query("type") String type
    );

    @GET("recharge-packages")
    Call<ApiResponse<RechargePackagesEnvelope>> getRechargePackages();

    @POST("recharge-orders")
    Call<ApiResponse<CreateRechargeOrderResponse>> createRechargeOrder(@Body Map<String, Object> body);

    @GET("recharge-orders/query")
    Call<ApiResponse<CreateRechargeOrderResponse>> queryRechargeOrder(
            @Query("mch_order_no") @Nullable String mchOrderNo,
            @Query("pay_order_id") @Nullable String payOrderId
    );

    @POST("recharge-orders/{id}/simulate-pay")
    Call<ApiResponse<Map<String, Object>>> simulateRechargePay(@Path("id") long orderId);

    @GET("user/ad-skip")
    Call<ApiResponse<AdSkipStatus>> getAdSkipStatus();

    @POST("user/ad-skip/purchase")
    Call<ApiResponse<Map<String, Object>>> purchaseAdSkip(@Body Map<String, Object> body);

    // ============ Episode Interactions (Protected) ============
    @GET("episodes/{id}/interaction")
    Call<ApiResponse<Map<String, Object>>> getEpisodeInteraction(@Path("id") long episodeId);

    @POST("episodes/{id}/like")
    Call<ApiResponse<Map<String, Object>>> likeEpisode(@Path("id") long episodeId);

    @POST("episodes/{id}/collect")
    Call<ApiResponse<Map<String, Object>>> collectEpisode(@Path("id") long episodeId);

    @POST("episodes/{id}/history")
    Call<ApiResponse<Void>> recordHistory(@Path("id") long episodeId);

    @POST("episodes/{id}/unlock-coins")
    Call<ApiResponse<Map<String, Object>>> unlockEpisodeWithCoins(@Path("id") long episodeId);

    // ============ Comments ============
    @GET("episodes/{id}/comments")
    Call<ApiResponse<CommentPage>> getComments(
            @Path("id") long episodeId,
            @Query("page") int page,
            @Query("page_size") int pageSize
    );

    @GET("episodes/{id}/replies/{root_id}")
    Call<ApiResponse<CommentPage>> getCommentReplies(
            @Path("id") long episodeId,
            @Path("root_id") long rootId,
            @Query("page") int page,
            @Query("page_size") int pageSize
    );

    @POST("episodes/{id}/comments")
    Call<ApiResponse<Comment>> postComment(
            @Path("id") long episodeId,
            @Body Map<String, Object> body
    );

    @POST("comments/{id}/like")
    Call<ApiResponse<Map<String, Object>>> likeComment(@Path("id") long commentId);
}
