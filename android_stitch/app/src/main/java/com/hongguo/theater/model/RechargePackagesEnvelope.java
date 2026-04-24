package com.hongguo.theater.model;

import com.google.gson.annotations.SerializedName;

import java.util.Collections;
import java.util.List;

public class RechargePackagesEnvelope {

    @SerializedName("list")
    private List<RechargePackageItem> list;

    @SerializedName("pay_options")
    private List<PayProductItem> payOptions;

    @SerializedName("lubzf_enabled")
    private boolean lubzfEnabled;

    @SerializedName("simulate_allowed")
    private boolean simulateAllowed;

    public List<RechargePackageItem> getList() {
        return list != null ? list : Collections.emptyList();
    }

    public List<PayProductItem> getPayOptions() {
        return payOptions != null ? payOptions : Collections.emptyList();
    }

    public boolean isLubzfEnabled() {
        return lubzfEnabled;
    }

    public boolean isSimulateAllowed() {
        return simulateAllowed;
    }
}
