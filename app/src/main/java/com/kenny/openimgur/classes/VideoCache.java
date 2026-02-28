package com.kenny.openimgur.classes;

import android.os.AsyncTask;
import android.os.StrictMode;
import android.net.TrafficStats;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import com.kenny.openimgur.activities.SettingsActivity;
import com.kenny.openimgur.util.FileUtil;
import com.kenny.openimgur.util.ImageUtil;
import com.kenny.openimgur.util.LogUtil;
import com.nostra13.universalimageloader.cache.disc.naming.Md5FileNameGenerator;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Created by kcampagna on 10/9/14.
 */
public class VideoCache {
    private static final String TAG = "VideoCache";

    private static final int TRAFFIC_STATS_TAG_VIDEO = 0xF0A2;

    private static VideoCache mInstance;

    private File mCacheDir;

    private Md5FileNameGenerator mKeyGenerator;
    private OkHttpClient mHttpClient;

    public static VideoCache getInstance() {
        if (mInstance == null) {
            mInstance = new VideoCache();
        }

        return mInstance;
    }

    private VideoCache() {
        OpengurApp app = OpengurApp.getInstance();
        String cacheKey = app.getPreferences().getString(SettingsActivity.KEY_CACHE_LOC, SettingsActivity.CACHE_LOC_INTERNAL);
        StrictMode.ThreadPolicy policy = allowDiskAccess();

        try {
            File dir = ImageUtil.getCacheDirectory(app.getApplicationContext(), cacheKey);
            mCacheDir = new File(dir, "video_cache");
            mCacheDir.mkdirs();
        } finally {
            StrictMode.setThreadPolicy(policy);
        }

        mKeyGenerator = new Md5FileNameGenerator();
        mHttpClient = new OkHttpClient();
    }

    public void setCacheDirectory(File dir) {
        mCacheDir = new File(dir, "video_cache");
        StrictMode.ThreadPolicy policy = allowDiskAccess();

        try {
            mCacheDir.mkdirs();
        } finally {
            StrictMode.setThreadPolicy(policy);
        }
    }

    /**
     * Downloads and saves the video file to the cache
     *
     * @param url      The url of the video
     * @param listener Optional VideoCacheListener
     */
    public void putVideo(@Nullable String url, @Nullable VideoCacheListener listener) {
        if (TextUtils.isEmpty(url)) {
            Exception e = new IllegalArgumentException("Url is null");
            LogUtil.e(TAG, "Invalid url", e);
            if (listener != null) listener.onVideoDownloadFailed(e, url);
            return;
        }

        new DownloadVideo(url, listener).execute();
    }

    /**
     * Returns the cached video file for the given url. NULL if it does not exist
     *
     * @param url
     * @return
     */
    public File getVideoFile(String url) {
        if (TextUtils.isEmpty(url)) return null;

        String ext = getExtension(url);
        if (TextUtils.isEmpty(ext)) return null;

        String key = mKeyGenerator.generate(url);
        File file = new File(mCacheDir, key + ext);
        StrictMode.ThreadPolicy policy = allowDiskAccess();

        try {
            return FileUtil.isFileValid(file) ? file : null;
        } finally {
            StrictMode.setThreadPolicy(policy);
        }
    }

    public void deleteCache() {
        StrictMode.ThreadPolicy policy = allowDiskAccess();

        try {
            FileUtil.deleteDirectory(mCacheDir);
            mCacheDir.mkdirs();
        } finally {
            StrictMode.setThreadPolicy(policy);
        }
    }

    public long getCacheSize() {
        StrictMode.ThreadPolicy policy = allowDiskAccess();

        try {
            return FileUtil.getDirectorySize(mCacheDir);
        } finally {
            StrictMode.setThreadPolicy(policy);
        }
    }

    public interface VideoCacheListener {
        // Called when the Video download starts
        void onVideoDownloadStart(String key, String url);

        // Called when the video download fails
        void onVideoDownloadFailed(Exception ex, String url);

        // Called when the video download completes
        void onVideoDownloadComplete(File file);

        void onProgress(int downloaded, int total);
    }

    private class DownloadVideo extends AsyncTask<Void, Integer, Object> {
        private final String mKey;

        private final VideoCacheListener mListener;

        private final String mOriginalUrl;

        private final String mDownloadUrl;

        public DownloadVideo(String url, VideoCacheListener listener) {
            mKey = mKeyGenerator.generate(url);
            mListener = listener;
            mOriginalUrl = url;
            mDownloadUrl = url.endsWith(".gifv") ? url.replace(".gifv", ".mp4") : url;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            if (mListener != null) mListener.onVideoDownloadStart(mKey, mDownloadUrl);
        }

        @Override
        protected Object doInBackground(Void... values) {
            InputStream in = null;
            BufferedOutputStream buffer = null;
            LogUtil.v(TAG, "Downloading video from " + mDownloadUrl);
            File writeFile = null;
            TrafficStats.setThreadStatsTag(TRAFFIC_STATS_TAG_VIDEO);

            try {
                String ext = getExtension(mOriginalUrl);

                if (TextUtils.isEmpty(ext)) {
                    return new IllegalArgumentException("Invalid extension for url " + mOriginalUrl);
                }

                writeFile = new File(mCacheDir, mKey + ext);

                // If file exists and is valid, we'll attempt to resume by requesting a Range starting
                // at the current file size. If server doesn't support Range, we fall back to full download.
                long existing = 0L;
                if (writeFile.exists()) {
                    existing = writeFile.length();
                }

                Request.Builder reqBuilder = new Request.Builder().url(mDownloadUrl).get();

                if (existing > 0) {
                    reqBuilder.addHeader("Range", "bytes=" + existing + "-");
                }

                Request req = reqBuilder.build();

                Response response = mHttpClient.newCall(req).execute();

                if (!response.isSuccessful()) {
                    // If partial request failed and we had an existing partial file, try without Range
                    if (existing > 0) {
                        req = new Request.Builder().url(mDownloadUrl).get().build();
                        response = mHttpClient.newCall(req).execute();
                        if (!response.isSuccessful()) {
                            return new IOException("Failed to download file: " + response.code());
                        }
                        // start fresh
                        existing = 0L;
                        if (writeFile.exists()) writeFile.delete();
                        writeFile.createNewFile();
                    } else {
                        return new IOException("Failed to download file: " + response.code());
                    }
                }

                ResponseBody body = response.body();

                if (body == null) {
                    return new IOException("Empty response body");
                }

                long contentLength = body.contentLength();
                // If contentLength is unknown (-1), we'll report -1 as total
                int total = (int) existing;
                int size = contentLength > 0 ? (int) (existing + contentLength) : -1;

                in = body.byteStream();

                // Open file for append when resuming, otherwise overwrite
                buffer = new BufferedOutputStream(new FileOutputStream(writeFile, existing > 0));
                byte[] byt = new byte[8192];
                int read;

                while ((read = in.read(byt)) != -1) {
                    buffer.write(byt, 0, read);
                    total += read;
                    publishProgress(total, size);
                }

                buffer.flush();
                return writeFile;
            } catch (Exception e) {
                LogUtil.e(TAG, "An error occurred whiling downloading video", e);
                if (writeFile != null && writeFile.exists()) writeFile.delete();
                return e;
            } finally {
                FileUtil.closeStream(in);
                FileUtil.closeStream(buffer);
                TrafficStats.clearThreadStatsTag();
            }
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            if (values != null && values.length == 2) {
                if (mListener != null) mListener.onProgress(values[0], values[1]);
            }
        }

        @Override
        protected void onPostExecute(Object o) {
            if (o instanceof File) {
                LogUtil.v(TAG, "Video downloaded successfully to " + ((File) o).getAbsolutePath());
                if (mListener != null) {
                    mListener.onVideoDownloadComplete((File) o);
                }
            } else if (mListener != null) {
                mListener.onVideoDownloadFailed((Exception) o, mDownloadUrl);
            }
        }
    }

    @Nullable
    private String getExtension(@NonNull String url) {
        if (url.endsWith(".gifv") || url.endsWith(".mp4")) {
            return ".mp4";
        } else if (url.endsWith(".webm")) {
            return ".webm";
        }

        return null;
    }

    private StrictMode.ThreadPolicy allowDiskAccess() {
        StrictMode.ThreadPolicy policy = StrictMode.getThreadPolicy();
        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder(policy)
                .permitDiskReads()
                .permitDiskWrites()
                .build());
        return policy;
    }
}
