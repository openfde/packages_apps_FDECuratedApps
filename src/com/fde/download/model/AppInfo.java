package com.fde.download.model;

import android.os.Parcel;
import android.os.Parcelable;

public class AppInfo implements Parcelable {
    private String name;
    private String packageName;
    private String version;
    private boolean isAvailable;
    private boolean isInstall;
    private boolean isUpdate;
    private String iconString;
    private String primaryUrl;
    private long primarySize;
    private String primaryMd5Checksum;
    private String backupUrl;
    private long backupSize;
    private String backupMd5Checksum;

    // 构造函数
    public AppInfo(String name, String packageName, String version, boolean isAvailable,
                   boolean isInstall, boolean isUpdate, String iconString, String primaryUrl,
                   long primarySize, String primaryMd5Checksum, String backupUrl,
                   long backupSize, String backupMd5Checksum) {
        this.name = name;
        this.packageName = packageName;
        this.version = version;
        this.isAvailable = isAvailable;
        this.isInstall = isInstall;
        this.isUpdate = isUpdate;
        this.iconString = iconString;
        this.primaryUrl = primaryUrl;
        this.primarySize = primarySize;
        this.primaryMd5Checksum = primaryMd5Checksum;
        this.backupUrl = backupUrl;
        this.backupSize = backupSize;
        this.backupMd5Checksum = backupMd5Checksum;
    }

    // Getter 和 Setter 方法
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public boolean isAvailable() {
        return isAvailable;
    }

    public void setAvailable(boolean available) {
        isAvailable = available;
    }

    public boolean isInstall() {
        return isInstall;
    }

    public void setInstall(boolean install) {
        isInstall = install;
    }

    public boolean isUpdate() {
        return isUpdate;
    }

    public void setUpdate(boolean update) {
        isUpdate = update;
    }

    public String getIconString() {
        return iconString;
    }

    public void setIconString(String iconString) {
        this.iconString = iconString;
    }

    public String getPrimaryUrl() {
        return primaryUrl;
    }

    public void setPrimaryUrl(String primaryUrl) {
        this.primaryUrl = primaryUrl;
    }

    public long getPrimarySize() {
        return primarySize;
    }

    public void setPrimarySize(long primarySize) {
        this.primarySize = primarySize;
    }

    public String getPrimaryMd5Checksum() {
        return primaryMd5Checksum;
    }

    public void setPrimaryMd5Checksum(String primaryMd5Checksum) {
        this.primaryMd5Checksum = primaryMd5Checksum;
    }

    public String getBackupUrl() {
        return backupUrl;
    }

    public void setBackupUrl(String backupUrl) {
        this.backupUrl = backupUrl;
    }

    public long getBackupSize() {
        return backupSize;
    }

    public void setBackupSize(long backupSize) {
        this.backupSize = backupSize;
    }

    public String getBackupMd5Checksum() {
        return backupMd5Checksum;
    }

    public void setBackupMd5Checksum(String backupMd5Checksum) {
        this.backupMd5Checksum = backupMd5Checksum;
    }

    // Parcelable 方法
    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(name);
        dest.writeString(packageName);
        dest.writeString(version);
        dest.writeByte((byte) (isAvailable ? 1 : 0));
        dest.writeByte((byte) (isInstall ? 1 : 0));
        dest.writeByte((byte) (isUpdate ? 1 : 0));
        dest.writeString(iconString);
        dest.writeString(primaryUrl);
        dest.writeLong(primarySize);
        dest.writeString(primaryMd5Checksum);
        dest.writeString(backupUrl);
        dest.writeLong(backupSize);
        dest.writeString(backupMd5Checksum);
    }

    // CREATOR
    public static final Parcelable.Creator<AppInfo> CREATOR = new Parcelable.Creator<AppInfo>() {
        @Override
        public AppInfo createFromParcel(Parcel source) {
            return new AppInfo(source);
        }

        @Override
        public AppInfo[] newArray(int size) {
            return new AppInfo[size];
        }
    };

    // 构造函数（用于从 Parcel 中读取数据）
    private AppInfo(Parcel source) {
        name = source.readString();
        packageName = source.readString();
        version = source.readString();
        isAvailable = source.readByte() != 0;
        isInstall = source.readByte() != 0;
        isUpdate = source.readByte() != 0;
        iconString = source.readString();
        primaryUrl = source.readString();
        primarySize = source.readLong();
        primaryMd5Checksum = source.readString();
        backupUrl = source.readString();
        backupSize = source.readLong();
        backupMd5Checksum = source.readString();
    }
}