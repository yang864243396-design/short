package com.hongguo.theater.model;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;

/**
 * 兼容评论列表 data 的两种形态：
 * <ul>
 *   <li>分页对象：{@code { "list": [...], "has_more": bool }}</li>
 *   <li>旧接口：{@code data} 直接为评论数组 {@code [...]}</li>
 * </ul>
 */
public class CommentPageJsonAdapter implements JsonDeserializer<CommentPage> {

    /** 避免匿名 TypeToken 在 R8 下泛型擦除导致反序列化崩溃 */
    private static final Type COMMENT_LIST_TYPE =
            TypeToken.getParameterized(List.class, Comment.class).getType();

    @Override
    public CommentPage deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext ctx)
            throws JsonParseException {
        if (json == null || json.isJsonNull()) {
            return new CommentPage(Collections.emptyList(), false);
        }
        if (json.isJsonArray()) {
            List<Comment> list = ctx.deserialize(json, COMMENT_LIST_TYPE);
            if (list == null) {
                list = Collections.emptyList();
            }
            return new CommentPage(list, false);
        }
        if (json.isJsonObject()) {
            JsonObject o = json.getAsJsonObject();
            List<Comment> list = Collections.emptyList();
            if (o.has("list") && !o.get("list").isJsonNull()) {
                list = ctx.deserialize(o.get("list"), COMMENT_LIST_TYPE);
                if (list == null) {
                    list = Collections.emptyList();
                }
            }
            boolean hasMore = false;
            if (o.has("has_more") && !o.get("has_more").isJsonNull()) {
                JsonElement hm = o.get("has_more");
                if (hm.isJsonPrimitive()) {
                    if (hm.getAsJsonPrimitive().isBoolean()) {
                        hasMore = hm.getAsBoolean();
                    } else if (hm.getAsJsonPrimitive().isNumber()) {
                        hasMore = hm.getAsInt() != 0;
                    }
                }
            }
            return new CommentPage(list, hasMore);
        }
        return new CommentPage(Collections.emptyList(), false);
    }
}
