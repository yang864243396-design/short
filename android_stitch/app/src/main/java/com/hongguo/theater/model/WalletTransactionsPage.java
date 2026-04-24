package com.hongguo.theater.model;

import com.google.gson.annotations.SerializedName;

import java.util.Collections;
import java.util.List;

public class WalletTransactionsPage {

    @SerializedName("list")
    private List<WalletTransaction> list;

    @SerializedName("total")
    private int total;

    @SerializedName("page")
    private int page;

    @SerializedName("page_size")
    private int pageSize;

    public List<WalletTransaction> getList() {
        return list != null ? list : Collections.emptyList();
    }

    public int getTotal() {
        return total;
    }

    public int getPage() {
        return page;
    }

    public int getPageSize() {
        return pageSize;
    }
}
