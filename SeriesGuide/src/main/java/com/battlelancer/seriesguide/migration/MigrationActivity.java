package com.battlelancer.seriesguide.migration;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.battlelancer.seriesguide.dataliberation.JsonExportTask;
import com.battlelancer.seriesguide.dataliberation.OnTaskFinishedListener;
import com.battlelancer.seriesguide.provider.SeriesContract;
import com.battlelancer.seriesguide.ui.SeriesGuidePreferences;
import com.battlelancer.seriesguide.util.Utils;
import com.google.analytics.tracking.android.EasyTracker;
import com.uwetrottmann.androidutils.AndroidUtils;
import com.uwetrottmann.seriesguide.R;

import java.io.File;

/**
 * Helps users migrate their show database to the free version of SeriesGuide. When using
 * SeriesGuide X a backup assistant and install+launch the free version assistant is shown.
 * When using any other version an import assistant is shown.
 */
public class MigrationActivity extends SherlockFragmentActivity implements JsonExportTask.OnTaskProgressListener, OnTaskFinishedListener {

    private static final String KEY_MIGRATION_OPT_OUT = "com.battlelancer.seriesguide.migration.optout";
    private static final String MARKETLINK_HTTP = "http://play.google.com/store/apps/details?id=com.battlelancer.seriesguide";
    private static final String PACKAGE_SERIESGUIDE = "com.battlelancer.seriesguide";
    private boolean mIsX;
    private ProgressBar mProgressBar;
    private Button mButtonBackup;
    private Button mButtonLaunch;
    private TextView mTextViewBackupInstructions;
    private TextView mTextViewLaunchInstructions;
    private AsyncTask<Void, Integer, Integer> mTask;
    private Intent mLaunchIntentForPackage;
    private View.OnClickListener mSeriesGuideLaunchListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            if (mLaunchIntentForPackage != null) {
                startActivity(mLaunchIntentForPackage);
            }
        }
    };
    private View.OnClickListener mSeriesGuideInstallListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            // launch SeriesGuide Play Store page
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(MARKETLINK_HTTP));
            Utils.tryStartActivity(MigrationActivity.this, intent, true);
        }
    };

    public static boolean isQualifiedForMigration(Context context) {
        if (Utils.getChannel(context) != Utils.SGChannel.X) {
            return false;
        }
        return !PreferenceManager.getDefaultSharedPreferences(context).getBoolean(KEY_MIGRATION_OPT_OUT,
                false);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // set a theme based on user preference
        setTheme(SeriesGuidePreferences.THEME);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_migration);

        mIsX = Utils.getChannel(this) == Utils.SGChannel.X;

        setupActionBar();
        setupViews();
    }

    private void setupActionBar() {
        final ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(false);
    }

    private void setupViews() {
        mProgressBar = (ProgressBar) findViewById(R.id.progressBarMigration);

        /**
         * Change from backup to import tool whether we use X or any other version (internal beta,
         * free).
         */
        mTextViewBackupInstructions = (TextView) findViewById(R.id.textViewMigrationBackupInstructions);
        mTextViewBackupInstructions.setText(mIsX ? R.string.migration_backup : R.string.migration_import);

        mButtonBackup = (Button) findViewById(R.id.buttonMigrationExport);
        mButtonBackup.setText(mIsX ? R.string.migration_action_backup
                : R.string.migration_action_import);
        mButtonBackup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // backup shows
                mTask = new JsonExportTask(MigrationActivity.this, MigrationActivity.this,
                        MigrationActivity.this,
                        true, false);
                mTask.execute();

                mProgressBar.setVisibility(View.VISIBLE);

                preventUserInput(true);
            }
        });

        mTextViewLaunchInstructions = (TextView) findViewById(R.id.textViewMigrationLaunchInstructions);
        mButtonLaunch = (Button) findViewById(R.id.buttonMigrationLaunch);
    }

    @Override
    protected void onStart() {
        super.onStart();
        validateLaunchStep();

        EasyTracker.getInstance().activityStart(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        EasyTracker.getInstance().activityStop(this);
    }

    @Override
    protected void onDestroy() {
        // clean up backup task
        if (mTask != null && mTask.getStatus() != AsyncTask.Status.FINISHED) {
            mTask.cancel(true);
        }
        mTask = null;

        super.onDestroy();
    }

    private void validateLaunchStep() {
        if (!mIsX) {
            // don't show launcher if importing
            setLauncherVisibility(false);
            return;
        }

        // check if SeriesGuide is already installed
        mLaunchIntentForPackage = getPackageManager().getLaunchIntentForPackage(PACKAGE_SERIESGUIDE);
        boolean isSeriesGuideInstalled = mLaunchIntentForPackage != null;

        // prepare next step
        mTextViewLaunchInstructions.setText(isSeriesGuideInstalled ? R.string.migration_launch : R.string.migration_install);
        mButtonLaunch.setText(isSeriesGuideInstalled ? R.string.migration_action_launch : R.string.migration_action_install);
        mButtonLaunch.setOnClickListener(isSeriesGuideInstalled ? mSeriesGuideLaunchListener : mSeriesGuideInstallListener);

        // decide whether to show next step
        boolean hasShows = hasShows();
        setBackupVisibility(hasShows);
        setLauncherVisibility(!hasShows || hasRecentBackup());
    }

    private void preventUserInput(boolean isLockdown) {
        // toggle buttons enabled state
        mButtonBackup.setEnabled(!isLockdown);
        mButtonLaunch.setEnabled(!isLockdown);
    }

    private void setBackupVisibility(boolean isVisible) {
        mTextViewBackupInstructions.setVisibility(isVisible ? View.VISIBLE : View.GONE);
        mButtonBackup.setVisibility(isVisible ? View.VISIBLE : View.GONE);
    }

    private void setLauncherVisibility(boolean isVisible) {
        mTextViewLaunchInstructions.setVisibility(isVisible ? View.VISIBLE : View.GONE);
        mButtonLaunch.setVisibility(isVisible ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onProgressUpdate(Integer... values) {
        if (mProgressBar == null) {
            return;
        }
        mProgressBar.setIndeterminate(values[0] == values[1]);
        mProgressBar.setMax(values[0]);
        mProgressBar.setProgress(values[1]);
    }

    @Override
    public void onTaskFinished() {
        mProgressBar.setVisibility(View.GONE);
        preventUserInput(false);
        validateLaunchStep();
    }

    private boolean hasShows() {
        final Cursor shows = getContentResolver().query(SeriesContract.Shows.CONTENT_URI,
                new String[]{SeriesContract.Shows._ID}, null, null, null);
        if (shows != null) {
            boolean hasShows = shows.getCount() > 0;
            shows.close();
            if (!hasShows) {
                return false;
            }
        }

        return true;
    }

    private boolean hasRecentBackup() {
        if (AndroidUtils.isExtStorageAvailable()) {
            // ensure at least show JSON is available
            File path = JsonExportTask.getExportPath(false);
            File backup = new File(path, JsonExportTask.EXPORT_JSON_FILE_SHOWS);
            if (backup.exists() && backup.canRead()) {
                // not older than 24 hours?
                long lastModified = backup.lastModified();
                long now = System.currentTimeMillis();
                if (lastModified - now < 24 * DateUtils.HOUR_IN_MILLIS) {
                    return true;
                }
            }
        }

        return false;
    }
}
