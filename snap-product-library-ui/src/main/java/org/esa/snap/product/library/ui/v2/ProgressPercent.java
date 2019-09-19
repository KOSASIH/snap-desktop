package org.esa.snap.product.library.ui.v2;

/**
 * Created by jcoravu on 13/9/2019.
 */
public class ProgressPercent {

    public static final byte PENDING_DOWNLOAD = 1;
    public static final byte DOWNLOADING = 2;
    public static final byte STOP_DOWNLOADING = 3;

    private short value;
    private byte status;

    public ProgressPercent() {
        this.value = 0;
        this.status = PENDING_DOWNLOAD;
    }

    public short getValue() {
        return value;
    }

    public void setValue(short value) {
        this.value = value;
        this.status = DOWNLOADING;
    }

    public void setStopDownloading() {
        this.status = STOP_DOWNLOADING;
    }

    public boolean isPendingDownload() {
        return (this.status == PENDING_DOWNLOAD);
    }

    public boolean isStoppedDownload() {
        return (this.status == STOP_DOWNLOADING);
    }

    public boolean isDownloading() {
        return (this.status == DOWNLOADING);
    }
}