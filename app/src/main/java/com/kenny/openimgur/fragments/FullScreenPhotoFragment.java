package com.kenny.openimgur.fragments;

import android.Manifest;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v13.app.FragmentCompat;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.kenny.openimgur.R;
import com.kenny.openimgur.activities.SettingsActivity;
import com.kenny.openimgur.classes.ImgurHandler;
import com.kenny.openimgur.classes.ImgurPhoto;
import com.kenny.openimgur.classes.VideoCache;
import com.kenny.openimgur.services.DownloaderService;
import com.kenny.openimgur.ui.VideoView;
import com.kenny.openimgur.ui.CustomMediaController;
import com.kenny.openimgur.util.FileUtil;
import com.kenny.openimgur.util.ImageUtil;
import com.kenny.openimgur.util.LinkUtils;
import com.kenny.openimgur.util.LogUtil;
import com.kenny.openimgur.util.PermissionUtils;
import com.kenny.openimgur.util.RequestCodes;
import com.kenny.openimgur.util.ViewUtils;
import com.kennyc.view.MultiStateView;
import com.nostra13.universalimageloader.core.assist.FailReason;
import com.nostra13.universalimageloader.core.assist.ImageSize;
import com.nostra13.universalimageloader.core.listener.ImageLoadingProgressListener;
import com.nostra13.universalimageloader.core.listener.SimpleImageLoadingListener;

import java.io.File;
import java.io.IOException;

import butterknife.BindView;
import pl.droidsonroids.gif.GifDrawable;

/**
 * Created by kcampagna on 6/2/15.
 */
public class FullScreenPhotoFragment extends BaseFragment {
    private static final String KEY_IMGUR_OBJECT = "imgur_photo_object";

    private static final String KEY_VIDEO_POSITION = "position";

    private static final long GIF_DELAY = 350L;

    @BindView(R.id.image)
    SubsamplingScaleImageView imageView;

    @BindView(R.id.multiView)
    MultiStateView multiView;

    @BindView(R.id.video)
    VideoView videoView;

    @BindView(R.id.gifImage)
    ImageView gifImageView;

    @BindView(R.id.loadingView)
    View loadingView;

    @BindView(R.id.fileTransfer)
    TextView fileTransfer;

    @BindView(R.id.percentage)
    TextView percentage;

    @BindView(R.id.progressBar)
    ProgressBar progressBar;

    ImgurPhoto photo;

    String url;

    boolean startedToLoad = false;

    PhotoHandler handler = new PhotoHandler();

    boolean replacedPNG = false;

    public static FullScreenPhotoFragment createInstance(@NonNull ImgurPhoto photo) {
        FullScreenPhotoFragment fragment = new FullScreenPhotoFragment();
        Bundle args = new Bundle(1);
        args.putParcelable(KEY_IMGUR_OBJECT, photo);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_full_screen, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ViewUtils.setErrorText(multiView, R.id.errorMessage, R.string.loading_image_error);
        ((Button) multiView.getView(MultiStateView.VIEW_STATE_ERROR).findViewById(R.id.errorButton)).setText(null);

        if (savedInstanceState != null) {
            photo = savedInstanceState.getParcelable(KEY_IMGUR_OBJECT);
        } else if (getArguments() != null) {
            photo = getArguments().getParcelable(KEY_IMGUR_OBJECT);
        }

        if (photo == null) {
            multiView.setViewState(MultiStateView.VIEW_STATE_ERROR);
        } else {
            configView(savedInstanceState);
        }
    }

    private void configView(Bundle savedInstanceState) {
        if (photo.isAnimated()) {
            boolean hasVideo = photo.hasVideoLink() && !TextUtils.isEmpty(photo.getVideoLink());
            boolean isGifSource = isGifSource(photo);

            if (hasVideo && (!isGifSource || photo.isLinkAThumbnail() || photo.getSize() > (1024 * 1024 * 5))) {
                url = photo.getVideoLink();
                displayVideo(savedInstanceState);
            } else {
                url = photo.getLink();
                if (hasVideo && LinkUtils.isVideoLink(url)) {
                    displayVideo(savedInstanceState);
                } else {
                    displayImage();
                }
            }
        } else {
            url = photo.getLink();
            displayImage();
        }
    }

    private boolean isGifSource(@NonNull ImgurPhoto photo) {
        if (ImgurPhoto.IMAGE_TYPE_GIF.equalsIgnoreCase(photo.getType())) {
            return true;
        }

        String link = photo.getLink();
        return !TextUtils.isEmpty(link) && link.toLowerCase().endsWith(".gif");
    }

    @Override
    public void onDestroyView() {
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
            handler = null;
        }

        // Free up some memory
        if (gifImageView != null && gifImageView.getDrawable() instanceof GifDrawable) {
            ((GifDrawable) gifImageView.getDrawable()).recycle();
        } else if (videoView != null) {
            videoView.stopPlayback();
            videoView.suspend(); // Release media player resources
        } else if (imageView != null) {
            imageView.recycle();
        }

        // Clear image loader memory cache for this fragment
        if (getActivity() != null) {
            ImageUtil.getImageLoader(getActivity()).clearMemoryCache();
        }

        super.onDestroyView();
    }

    /**
     * Displays the image
     */
    void displayImage() {
        multiView.setViewState(MultiStateView.VIEW_STATE_LOADING);
        
        if (!replacedPNG && LinkUtils.isImgurPNG(url)) {
            replacedPNG = true;
            LogUtil.v(TAG, "Replacing png link with jpeg");
            url = url.replace(".png", ".jpeg");
        }

        if (LinkUtils.isVideoLink(url)) {
            if (photo != null && !TextUtils.isEmpty(photo.getId())) {
                url = "https://i.imgur.com/" + photo.getId() + ImgurPhoto.THUMBNAIL_HUGE + FileUtil.EXTENSION_JPEG;
            } else {
                LogUtil.w(TAG, "Cannot display video file as image, calling displayVideo instead");
                displayVideo(null);
                return;
            }
        }

        ImageUtil.getImageLoader(getActivity()).loadImage(url, new ImageSize(1, 1), ImageUtil.getDisplayOptionsForFullscreen().build(), simpleImageLoadingListener, progressListener);
    }

    private void displayVideo(Bundle savedInstance) {
        File file = VideoCache.getInstance().getVideoFile(url);
        final int position;

        // Check if our video was playing during a rotation
        if (savedInstance != null) {
            position = savedInstance.getInt(KEY_VIDEO_POSITION, 0);
        } else {
            position = 0;
        }

        if (file != null) {
            multiView.setViewState(MultiStateView.VIEW_STATE_CONTENT);
            videoView.setVisibility(View.VISIBLE);
            imageView.setVisibility(View.GONE);

            CustomMediaController mediaController = new CustomMediaController(getActivity());
            mediaController.setVideoUri(file.getAbsolutePath());
            videoView.setCustomMediaController(mediaController);

            videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mediaPlayer) {
                    mediaPlayer.setLooping(true);
                    mediaPlayer.seekTo(position);
                }
            });

            videoView.setVideoPath(file.getAbsolutePath());
            // Check if video autoplay is disabled in settings
            boolean disableVideoAutoplay = app.getPreferences().getBoolean(SettingsActivity.KEY_DISABLE_VIDEO_AUTOPLAY, false);
            if (getUserVisibleHint() && !disableVideoAutoplay) videoView.start();
        } else {
            VideoCache.getInstance().putVideo(url, new VideoCache.VideoCacheListener() {
                @Override
                public void onVideoDownloadStart(String key, String url) {
                    if (loadingView != null) loadingView.setVisibility(View.VISIBLE);
                }

                @Override
                public void onVideoDownloadFailed(Exception ex, String url) {
                    if (!isAdded() || isRemoving() || multiView == null) return;

                    multiView.setViewState(MultiStateView.VIEW_STATE_ERROR);
                }

                @Override
                public void onVideoDownloadComplete(File file) {
                    if (!isAdded() || isRemoving() || getView() == null || multiView == null || videoView == null || imageView == null) {
                        return;
                    }

                    multiView.setViewState(MultiStateView.VIEW_STATE_CONTENT);
                    videoView.setVisibility(View.VISIBLE);
                    imageView.setVisibility(View.GONE);

                    CustomMediaController mediaController = new CustomMediaController(getActivity());
                    mediaController.setVideoUri(file.getAbsolutePath());
                    videoView.setCustomMediaController(mediaController);

                    videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                        @Override
                        public void onPrepared(MediaPlayer mediaPlayer) {
                            mediaPlayer.setLooping(true);
                            mediaPlayer.seekTo(position);
                        }
                    });

                    videoView.setVideoPath(file.getAbsolutePath());
                    // Check if video autoplay is disabled in settings
                    boolean disableVideoAutoplay = app.getPreferences().getBoolean(SettingsActivity.KEY_DISABLE_VIDEO_AUTOPLAY, false);
                    if (getUserVisibleHint() && !disableVideoAutoplay) videoView.start();
                    if (loadingView != null) loadingView.setVisibility(View.GONE);
                }

                @Override
                public void onProgress(int downloaded, int total) {
                    if (!isAdded() || isRemoving() || getView() == null || progressBar == null || percentage == null || fileTransfer == null || total <= 0) {
                        return;
                    }

                    int progress = (downloaded * 100) / total;
                    progressBar.setProgress(progress);
                    percentage.setText(progress + "%");
                    fileTransfer.setText(FileUtil.humanReadableByteCount(downloaded, false) + "/" + FileUtil.humanReadableByteCount(total, false));
                }
            });
        }
    }

    void displayGif(String url) {
        if (LinkUtils.isVideoLink(url) && !url.toLowerCase().endsWith(".gif") && !url.toLowerCase().endsWith(".gifv")) {
            LogUtil.w(TAG, "Cannot display video file as gif, calling displayVideo instead");
            displayVideo(null);
            return;
        }

        if (getUserVisibleHint()) {
            loadGifFromCacheAsync(url);
        } else {
            videoView.setVisibility(View.GONE);
            imageView.setVisibility(View.GONE);
            ImageUtil.getImageLoader(getActivity()).displayImage(url, gifImageView, null, simpleImageLoadingListener, progressListener);
            multiView.setViewState(MultiStateView.VIEW_STATE_CONTENT);
        }
    }

    private void loadGifFromCacheAsync(final String gifUrl) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                GifDrawable gifDrawable = null;

                try {
                    if (!isAdded() || isRemoving() || getActivity() == null) {
                        return;
                    }

                    File file = ImageUtil.getImageLoader(getActivity()).getDiskCache().get(gifUrl);

                    if (!FileUtil.isFileValid(file)) {
                        postGifLoadError();
                        return;
                    }

                    gifDrawable = new GifDrawable(file);
                } catch (IOException e) {
                    postGifLoadError();
                    return;
                }

                final GifDrawable finalGifDrawable = gifDrawable;

                if (!isAdded() || isRemoving() || getActivity() == null) {
                    if (finalGifDrawable != null) {
                        finalGifDrawable.recycle();
                    }
                    return;
                }

                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!isAdded() || isRemoving() || gifImageView == null || multiView == null || videoView == null || imageView == null) {
                            if (finalGifDrawable != null) {
                                finalGifDrawable.recycle();
                            }
                            return;
                        }

                        videoView.setVisibility(View.GONE);
                        imageView.setVisibility(View.GONE);
                        gifImageView.setImageDrawable(finalGifDrawable);
                        multiView.setViewState(MultiStateView.VIEW_STATE_CONTENT);
                    }
                });
            }
        }).start();
    }

    private void postGifLoadError() {
        if (!isAdded() || isRemoving() || getActivity() == null) {
            return;
        }

        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (multiView != null) {
                    multiView.setViewState(MultiStateView.VIEW_STATE_ERROR);
                }
            }
        });
    }

    private void displayStaticImageFromCacheAsync(final String imageUrl) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final File file = ImageUtil.getImageLoader(getActivity()).getDiskCache().get(imageUrl);

                    if (!FileUtil.isFileValid(file)) {
                        postStaticImageError();
                        return;
                    }

                    int width = photo != null ? photo.getWidth() : 0;
                    int height = photo != null ? photo.getHeight() : 0;

                    if (width <= 0 || height <= 0) {
                        int[] dimensions = ImageUtil.getBitmapDimensions(file);
                        width = dimensions[0];
                        height = dimensions[1];
                    }

                    if (width <= 0 || height <= 0) {
                        postStaticImageError();
                        return;
                    }

                    final int finalWidth = width;
                    final int finalHeight = height;
                    final boolean enableTiling = finalWidth > 2048 || finalHeight > 2048;
                    final Uri fileUri = Uri.fromFile(file);

                    if (!isAdded() || isRemoving() || getActivity() == null) {
                        return;
                    }

                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (!isAdded() || isRemoving() || multiView == null || videoView == null || gifImageView == null || imageView == null) {
                                return;
                            }

                            LogUtil.v(TAG, "Tiling enabled for image " + enableTiling);

                            imageView.setOnImageEventListener(new SubsamplingScaleImageView.OnImageEventListener() {
                                @Override
                                public void onReady() {

                                }

                                @Override
                                public void onImageLoaded() {

                                }

                                @Override
                                public void onPreviewLoadError(Exception e) {

                                }

                                @Override
                                public void onImageLoadError(Exception e) {
                                    LogUtil.e(TAG, "Error loading image", e);
                                    if (multiView != null)
                                        multiView.setViewState(MultiStateView.VIEW_STATE_ERROR);
                                }

                                @Override
                                public void onTileLoadError(Exception e) {
                                    LogUtil.e(TAG, "Error creating tile", e);
                                    if (multiView != null)
                                        multiView.setViewState(MultiStateView.VIEW_STATE_ERROR);
                                }

                                @Override
                                public void onPreviewReleased() {

                                }
                            });

                            imageView.setMinimumTileDpi(160);
                            imageView.setImage(ImageSource.uri(fileUri).dimensions(finalWidth, finalHeight).tiling(enableTiling));
                            videoView.setVisibility(View.GONE);
                            gifImageView.setVisibility(View.GONE);
                            multiView.setViewState(MultiStateView.VIEW_STATE_CONTENT);

                            if (loadingView != null) {
                                loadingView.setVisibility(View.GONE);
                            }
                        }
                    });
                } catch (Exception e) {
                    LogUtil.e(TAG, "Error creating tile bitmap", e);
                    postStaticImageError();
                }
            }
        }).start();
    }

    private void postStaticImageError() {
        if (!isAdded() || isRemoving() || getActivity() == null) {
            return;
        }

        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (multiView != null) {
                    multiView.setViewState(MultiStateView.VIEW_STATE_ERROR);
                }

                if (loadingView != null) {
                    loadingView.setVisibility(View.GONE);
                }
            }
        });
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.view_photo, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.download:
                @PermissionUtils.PermissionLevel int permissionLevel = PermissionUtils.getPermissionLevel(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);

                switch (permissionLevel) {
                    case PermissionUtils.PERMISSION_AVAILABLE:
                        getActivity().startService(DownloaderService.createIntent(getActivity(), url));
                        break;

                    case PermissionUtils.PERMISSION_DENIED:
                        Snackbar.make(multiView, R.string.permission_rationale_download, Snackbar.LENGTH_LONG)
                                .setAction(R.string.okay, new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        FragmentCompat.requestPermissions(FullScreenPhotoFragment.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, RequestCodes.REQUEST_PERMISSION_WRITE);
                                    }
                                }).show();
                        break;

                    case PermissionUtils.PERMISSION_NEVER_ASKED:
                    default:
                        FragmentCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, RequestCodes.REQUEST_PERMISSION_WRITE);
                        break;
                }
                return true;

            case R.id.share:
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                String link;

                if (photo != null && !TextUtils.isEmpty(photo.getTitle())) {
                    link = photo.getTitle() + " ";
                    if (TextUtils.isEmpty(photo.getRedditLink())) {
                        link += photo.getGalleryLink();
                    } else {
                        link += String.format("https://reddit.com%s", photo.getRedditLink());
                    }
                } else {
                    link = url;
                }

                shareIntent.putExtra(Intent.EXTRA_TEXT, link);
                share(shareIntent, R.string.share);
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(KEY_IMGUR_OBJECT, photo);
        if (videoView.isPlaying())
            outState.putInt(KEY_VIDEO_POSITION, videoView.getCurrentPosition());
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);

        if (isVisibleToUser) {
            if (gifImageView != null && gifImageView.getDrawable() instanceof GifDrawable) {
                ((GifDrawable) gifImageView.getDrawable()).start();
            } else if (videoView != null && videoView.getDuration() > 0) {
                // Check if video autoplay is disabled in settings
                boolean disableVideoAutoplay = app.getPreferences().getBoolean(SettingsActivity.KEY_DISABLE_VIDEO_AUTOPLAY, false);
                if (!disableVideoAutoplay) {
                    videoView.start();
                }
            } else {
                if (handler != null) handler.sendEmptyMessageDelayed(0, GIF_DELAY);
            }
        } else {
            if (gifImageView != null && gifImageView.getDrawable() instanceof GifDrawable) {
                ((GifDrawable) gifImageView.getDrawable()).pause();
            } else if (videoView != null && videoView.getDuration() > 0) {
                videoView.pause();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case RequestCodes.REQUEST_PERMISSION_WRITE:
                boolean granted = PermissionUtils.verifyPermissions(grantResults);

                if (granted) {
                    getActivity().startService(DownloaderService.createIntent(getActivity(), url));
                } else {
                    Snackbar.make(multiView, R.string.permission_denied, Snackbar.LENGTH_LONG).show();
                }
                break;
        }
    }

    class PhotoHandler extends ImgurHandler {
        @Override
        public void handleMessage(Message msg) {
            if (getUserVisibleHint() && !startedToLoad && gifImageView != null && LinkUtils.isLinkAnimated(url) && !LinkUtils.isVideoLink(url)) {
                displayGif(url);
            }

            super.handleMessage(msg);
        }
    }

    private final ImageLoadingProgressListener progressListener = new ImageLoadingProgressListener() {
        @Override
        public void onProgressUpdate(String imageUri, View view, int current, int total) {
            if (!isAdded() || isRemoving() || progressBar == null || percentage == null || fileTransfer == null || total <= 0) {
                return;
            }

            int progress = (current * 100) / total;
            progressBar.setProgress(progress);
            percentage.setText(progress + "%");
            fileTransfer.setText(FileUtil.humanReadableByteCount(current, false) + "/" + FileUtil.humanReadableByteCount(total, false));
        }
    };

    private final SimpleImageLoadingListener simpleImageLoadingListener = new SimpleImageLoadingListener() {
        @Override
        public void onLoadingFailed(String s, View view, FailReason failReason) {
            startedToLoad = false;
            if (!isAdded() || isRemoving()) return;

            if (replacedPNG) {
                LogUtil.w(TAG, "Replacing png with jpeg failed, reverting back to png");
                url = url.replace(".jpeg", ".png");
                displayImage();
                return;
            }

            multiView.setViewState(MultiStateView.VIEW_STATE_ERROR);
        }

        @Override
        public void onLoadingComplete(String url, View view, Bitmap bitmap) {
            startedToLoad = false;
            bitmap.recycle();
            if (!isAdded() || isRemoving() || multiView == null || loadingView == null || videoView == null || gifImageView == null || imageView == null) {
                return;
            }

            if (url.endsWith(".gif")) {
                displayGif(url);
            } else {
                displayStaticImageFromCacheAsync(url);
            }

            if (url.endsWith(".gif")) {
                loadingView.setVisibility(View.GONE);
            }
        }

        @Override
        public void onLoadingStarted(String imageUri, View view) {
            startedToLoad = true;
            if (loadingView != null) loadingView.setVisibility(View.VISIBLE);
        }
    };
}
