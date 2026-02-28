package com.kenny.openimgur.fragments;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.SwitchPreference;
import android.preference.RingtonePreference;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.kenny.openimgur.BuildConfig;
import com.kenny.openimgur.R;
import com.kenny.openimgur.activities.SettingsActivity;
import com.kenny.openimgur.classes.ImgurTheme;
import com.kenny.openimgur.classes.VideoCache;
import com.kenny.openimgur.util.DBContracts;
import com.kenny.openimgur.util.FileUtil;
import com.kenny.openimgur.util.ImageUtil;
import com.kenny.openimgur.util.LogUtil;
import com.kenny.openimgur.util.SqlHelper;

import java.io.File;
import java.lang.ref.WeakReference;

public class SettingsFragment extends BasePreferenceFragment implements Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener {
    private PreferenceCategory mAppearanceCategory;

    private SwitchPreference mAmoledPreference;

    public static SettingsFragment createInstance() {
        return new SettingsFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        bindListPreference(findPreference(SettingsActivity.KEY_CACHE_SIZE));
        bindListPreference(findPreference(SettingsActivity.KEY_THREAD_SIZE));
        bindListPreference(findPreference(SettingsActivity.KEY_CACHE_LOC));
        bindListPreference(findPreference(SettingsActivity.KEY_THUMBNAIL_QUALITY));
        bindListPreference(findPreference(SettingsActivity.KEY_NOTIFICATION_FREQUENCY));
        mAppearanceCategory = (PreferenceCategory) findPreference("appearanceSettings");
        mAmoledPreference = (SwitchPreference) findPreference(SettingsActivity.KEY_AMOLED_THEME);
        findPreference(SettingsActivity.KEY_CURRENT_CACHE_SIZE).setOnPreferenceClickListener(this);
        findPreference("licenses").setOnPreferenceClickListener(this);
        findPreference("openSource").setOnPreferenceClickListener(this);
        findPreference("redditHistory").setOnPreferenceClickListener(this);
        findPreference("mySubreddits").setOnPreferenceClickListener(this);
        findPreference("gallerySearchHistory").setOnPreferenceClickListener(this);
        
        findPreference(SettingsActivity.KEY_THEME_NEW).setOnPreferenceClickListener(this);
        findPreference(SettingsActivity.KEY_ADB).setOnPreferenceChangeListener(this);
        findPreference(SettingsActivity.KEY_DARK_THEME).setOnPreferenceChangeListener(this);
        findPreference(SettingsActivity.KEY_AMOLED_THEME).setOnPreferenceChangeListener(this);
        findPreference(SettingsActivity.KEY_NOTIFICATION_RINGTONE).setOnPreferenceChangeListener(this);
        findPreference("privacyPolicy").setOnPreferenceClickListener(this);
        updateAmoledPreference(mApp.getPreferences().getBoolean(SettingsActivity.KEY_DARK_THEME, true));
        updateThemeSummary();
    }

    @Override
    public void onResume() {
        super.onResume();
        long cacheSize = ImageUtil.getTotalImageCacheSize(mApp);
        findPreference(SettingsActivity.KEY_CURRENT_CACHE_SIZE).setSummary(FileUtil.humanReadableByteCount(cacheSize, false));

        try {
            String version = getActivity().getPackageManager().getPackageInfo(getActivity().getPackageName(), 0).versionName;
            findPreference("version").setSummary(version);
        } catch (PackageManager.NameNotFoundException e) {
            LogUtil.e(TAG, "Unable to get version summary", e);
        }

        String ringtone = mApp.getPreferences().getString(SettingsActivity.KEY_NOTIFICATION_RINGTONE, null);

        if (!TextUtils.isEmpty(ringtone)) {
            findPreference(SettingsActivity.KEY_NOTIFICATION_RINGTONE).setSummary(getNotificationRingtone(ringtone));
        }

        updateThemeSummary();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object object) {
        boolean updated = super.onPreferenceChange(preference, object);
        String key = preference.getKey();

        if (preference instanceof CheckBoxPreference || preference instanceof SwitchPreference) {
            switch (key) {
                case SettingsActivity.KEY_ADB:
                    // Ignore if its a debug build
                    if (!BuildConfig.DEBUG) {
                        LogUtil.SHOULD_WRITE_LOGS = (Boolean) object;
                        mApp.setAllowLogs((Boolean) object);
                    }
                    updated = true;
                    break;

                case SettingsActivity.KEY_DARK_THEME:
                    mApp.getImgurTheme().isDarkTheme = (Boolean) object;
                    if (!(Boolean) object) {
                        mApp.getImgurTheme().isAmoled = false;
                        mApp.getPreferences().edit().putBoolean(SettingsActivity.KEY_AMOLED_THEME, false).apply();
                    }
                    updateAmoledPreference((Boolean) object);
                    getActivity().recreate();
                    updated = true;
                    break;

                case SettingsActivity.KEY_AMOLED_THEME:
                    mApp.getImgurTheme().isAmoled = (Boolean) object;
                    getActivity().recreate();
                    updated = true;
                    break;
            }
        } else if (preference instanceof RingtonePreference) {
            preference.setSummary(getNotificationRingtone(object.toString()));
            updated = true;
        }

        return updated;
    }

    @Override
    public boolean onPreferenceClick(final Preference preference) {
        switch (preference.getKey()) {
            case SettingsActivity.KEY_THEME_NEW:
                showThemeChooser();
                return true;

            case SettingsActivity.KEY_CURRENT_CACHE_SIZE:
                new AlertDialog.Builder(getActivity(), mApp.getImgurTheme().getAlertDialogTheme())
                        .setTitle(R.string.clear_cache)
                        .setMessage(R.string.clear_cache_message)
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Snackbar.make(getActivity().findViewById(android.R.id.content), R.string.clearing_cache, Snackbar.LENGTH_LONG).show();
                                new DeleteCacheTask(SettingsFragment.this, null).execute();
                            }
                        }).show();
                return true;

            case "licenses":
                WebView webView = new WebView(getActivity());
                new AlertDialog.Builder(getActivity(), mApp.getImgurTheme().getAlertDialogTheme())
                        .setPositiveButton(R.string.dismiss, null)
                        .setView(webView)
                        .show();

                webView.loadUrl("file:///android_asset/licenses.html");
                return true;

            case "openSource":
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/puksh/Opengur"));

                if (browserIntent.resolveActivity(getActivity().getPackageManager()) != null) {
                    startActivity(browserIntent);
                } else {
                    Snackbar.make(getActivity().findViewById(android.R.id.content), R.string.cant_launch_intent, Snackbar.LENGTH_LONG).show();
                }

                return true;

            case "redditHistory":
                SqlHelper.getInstance(getActivity()).deleteFromTable(DBContracts.SubRedditContract.TABLE_NAME);
                Snackbar.make(getActivity().findViewById(android.R.id.content), R.string.pref_reddit_deleted, Snackbar.LENGTH_LONG).show();
                return true;

            case "gallerySearchHistory":
                SqlHelper.getInstance(getActivity()).deleteFromTable(DBContracts.GallerySearchContract.TABLE_NAME);
                Snackbar.make(getActivity().findViewById(android.R.id.content), R.string.pref_search_deleted, Snackbar.LENGTH_LONG).show();
                return true;

            case "mySubreddits":
                mApp.getPreferences().edit().remove(RedditFragment.KEY_PINNED_SUBREDDITS).apply();
                Snackbar.make(getActivity().findViewById(android.R.id.content), R.string.pref_reddit_deleted, Snackbar.LENGTH_LONG).show();
                return true;

            case "privacyPolicy":
                Intent privacyIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/puksh/Opengur/blob/master/Privacy_Policy.md"));

                if (privacyIntent.resolveActivity(getActivity().getPackageManager()) != null) {
                    startActivity(privacyIntent);
                } else {
                    WebView wv = new WebView(getActivity());
                    new AlertDialog.Builder(getActivity(), mApp.getImgurTheme().getAlertDialogTheme())
                            .setPositiveButton(R.string.dismiss, null)
                            .setView(wv)
                            .show();

                    wv.loadUrl("https://github.com/puksh/Opengur/blob/master/Privacy_Policy.md");
                }
                return true;
        }

        return super.onPreferenceClick(preference);
    }

    @Nullable
    private String getNotificationRingtone(String uri) {
        try {
            Uri ringTone = Uri.parse(uri);
            return RingtoneManager.getRingtone(getActivity(), ringTone).getTitle(getActivity());
        } catch (Exception ex) {
            LogUtil.e(TAG, "Unable to parse ringtone", ex);
            return null;
        }
    }

    private static class DeleteCacheTask extends AsyncTask<Void, Void, Long> {
        private WeakReference<SettingsFragment> mFragment;

        private String mCacheDirKey;

        public DeleteCacheTask(SettingsFragment fragment, String cacheDirKey) {
            mFragment = new WeakReference<>(fragment);
            mCacheDirKey = cacheDirKey;
        }

        @Override
        protected Long doInBackground(Void... voids) {
            SettingsFragment frag = mFragment.get();

            if (frag != null) {
                Activity activity = frag.getActivity();
                frag.mApp.deleteAllCache();

                if (!TextUtils.isEmpty(mCacheDirKey)) {
                    File dir = ImageUtil.getCacheDirectory(activity, mCacheDirKey);
                    ImageUtil.initImageLoader(activity);
                    VideoCache.getInstance().setCacheDirectory(dir);
                }


                return ImageUtil.getTotalImageCacheSize(frag.mApp);
            } else {
                return -1L;
            }
        }

        @Override
        protected void onPostExecute(Long cacheSize) {
            SettingsFragment frag = mFragment.get();

            if (frag != null) {
                frag.findPreference(SettingsActivity.KEY_CURRENT_CACHE_SIZE).setSummary(FileUtil.humanReadableByteCount(cacheSize, false));
                mFragment.clear();
            }
        }
    }

    @Override
    protected int getPreferenceXML() {
        return R.xml.settings;
    }

    private void showThemeChooser() {
        final ImgurTheme[] themes = new ImgurTheme[]{
                ImgurTheme.IMGUR,
                ImgurTheme.BLACK,
                ImgurTheme.BLUE,
                ImgurTheme.CYAN,
                ImgurTheme.GREEN,
                ImgurTheme.GREY,
                ImgurTheme.ORANGE,
                ImgurTheme.PINK,
                ImgurTheme.PURPLE,
                ImgurTheme.RED,
                ImgurTheme.TEAL
        };

        AlertDialog dialog = new AlertDialog.Builder(getActivity(), mApp.getImgurTheme().getAlertDialogTheme())
                .setTitle(R.string.pref_theme_title)
                .setAdapter(new ThemeChooserAdapter(themes), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which >= 0 && which < themes.length) {
                            applyThemeSelection(themes[which]);
                        }
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();

        if (dialog.getListView() != null) {
            dialog.getListView().setDivider(null);
            dialog.getListView().setDividerHeight(0);
        }
    }

    private void applyThemeSelection(ImgurTheme selectedTheme) {
        ImgurTheme currentTheme = mApp.getImgurTheme();

        if (selectedTheme == currentTheme) {
            return;
        }

        selectedTheme.isDarkTheme = currentTheme.isDarkTheme;
        selectedTheme.isAmoled = currentTheme.isAmoled;
        mApp.setImgurTheme(selectedTheme);
        mApp.getPreferences().edit().putInt(SettingsActivity.KEY_THEME_NEW, getResources().getColor(selectedTheme.primaryColor)).apply();
        updateThemeSummary();
        getActivity().recreate();
    }

    private void updateThemeSummary() {
        Preference pref = findPreference(SettingsActivity.KEY_THEME_NEW);

        if (pref != null) {
            pref.setSummary(getThemeName(mApp.getImgurTheme()));
        }
    }

    private String getThemeName(ImgurTheme theme) {
        switch (theme) {
            case IMGUR:
                return "Imgur certified™";
            case BLACK:
                return "Black";
            case BLUE:
                return "Blue";
            case CYAN:
                return "Cyan";
            case GREEN:
                return "Green";
            case GREY:
                return "Grey";
            case ORANGE:
                return "Orange";
            case PINK:
                return "Pink";
            case PURPLE:
                return "Purple";
            case RED:
                return "Red";
            case TEAL:
            default:
                return "Teal";
        }
    }

    private class ThemeChooserAdapter extends BaseAdapter {
        private final ImgurTheme[] mThemes;

        ThemeChooserAdapter(ImgurTheme[] themes) {
            mThemes = themes;
        }

        @Override
        public int getCount() {
            return mThemes.length;
        }

        @Override
        public Object getItem(int position) {
            return mThemes[position];
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;

            if (view == null) {
                view = LayoutInflater.from(getActivity()).inflate(R.layout.item_theme_color_bar, parent, false);
            }

            TextView name = (TextView) view.findViewById(R.id.themeName);
            View dark = view.findViewById(R.id.themeColorBarDark);
            View primary = view.findViewById(R.id.themeColorBarPrimary);
            View accent = view.findViewById(R.id.themeColorBarAccent);
            ImgurTheme theme = mThemes[position];

            name.setText(getThemeName(theme));
            dark.setBackgroundColor(getResources().getColor(theme.darkColor));
            primary.setBackgroundColor(getResources().getColor(theme.primaryColor));
            accent.setBackgroundColor(getResources().getColor(theme.accentColor));
            return view;
        }
    }

    private void updateAmoledPreference(boolean isDarkTheme) {
        if (mAppearanceCategory == null || mAmoledPreference == null) {
            return;
        }

        if (isDarkTheme) {
            if (mAppearanceCategory.findPreference(SettingsActivity.KEY_AMOLED_THEME) == null) {
                mAppearanceCategory.addPreference(mAmoledPreference);
            }
        } else {
            if (mAppearanceCategory.findPreference(SettingsActivity.KEY_AMOLED_THEME) != null) {
                mAppearanceCategory.removePreference(mAmoledPreference);
            }
        }
    }
}