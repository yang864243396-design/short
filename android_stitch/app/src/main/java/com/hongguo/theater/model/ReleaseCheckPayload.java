package com.hongguo.theater.model;

import com.google.gson.annotations.SerializedName;

/** 对应 GET app/release-check 的 data 对象 */
public class ReleaseCheckPayload {

    @SerializedName("version")
    private String version;

    @SerializedName("force_update")
    private boolean forceUpdate;

    @SerializedName("release_notes")
    private String releaseNotes;

    @SerializedName("download_url")
    private String downloadUrl;

    @SerializedName("install_url")
    private String installUrl;

    public String getVersion() {
        return version;
    }

    public boolean isForceUpdate() {
        return forceUpdate;
    }

    public String getReleaseNotes() {
        return releaseNotes;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public String getInstallUrl() {
        return installUrl;
    }
}
