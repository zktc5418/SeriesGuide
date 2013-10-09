/*
 * Copyright 2011 Uwe Trottmann
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */

package com.battlelancer.seriesguide.util;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.database.Cursor;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.battlelancer.seriesguide.Constants;
import com.battlelancer.seriesguide.Constants.EpisodeSorting;
import com.battlelancer.seriesguide.provider.SeriesContract.ListItems;
import com.battlelancer.seriesguide.provider.SeriesContract.Shows;
import com.battlelancer.seriesguide.settings.ActivitySettings;
import com.battlelancer.seriesguide.settings.AdvancedSettings;
import com.battlelancer.seriesguide.ui.SeriesGuidePreferences;
import com.google.analytics.tracking.android.EasyTracker;
import com.uwetrottmann.androidutils.AndroidUtils;
import com.uwetrottmann.seriesguide.R;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormatSymbols;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class Utils {

    private static final String TIMEZONE_AMERICA_PREFIX = "America/";

    private static final String TAG = "Utils";

    private static final String TIMEZONE_ALWAYS_PST = "GMT-08:00";

    private static final String TIMEZONE_US_ARIZONA = "America/Phoenix";

    private static final String TIMEZONE_US_EASTERN = "America/New_York";

    private static final Object TIMEZONE_US_EASTERN_DETROIT = "America/Detroit";

    private static final String TIMEZONE_US_CENTRAL = "America/Chicago";

    private static final String TIMEZONE_US_PACIFIC = "America/Los_Angeles";

    private static final String TIMEZONE_US_MOUNTAIN = "America/Denver";

    public static final SimpleDateFormat thetvdbTimeFormatAMPM = new SimpleDateFormat("h:mm aa",
            Locale.US);

    public static final SimpleDateFormat thetvdbTimeFormatAMPMalt = new SimpleDateFormat("h:mmaa",
            Locale.US);

    public static final SimpleDateFormat thetvdbTimeFormatAMPMshort = new SimpleDateFormat("h aa",
            Locale.US);

    public static final SimpleDateFormat thetvdbTimeFormatNormal = new SimpleDateFormat("H:mm",
            Locale.US);

    /**
     * Parse a shows TVDb air time value to a ms value in Pacific Standard Time
     * (always without daylight saving).
     */
    public static long parseTimeToMilliseconds(String tvdbTimeString) {
        Date time = null;
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone(TIMEZONE_ALWAYS_PST));

        // try parsing with three different formats, most of the time the first
        // should match
        if (tvdbTimeString.length() != 0) {
            try {
                time = thetvdbTimeFormatAMPM.parse(tvdbTimeString);
            } catch (ParseException e) {
                try {
                    time = thetvdbTimeFormatAMPMalt.parse(tvdbTimeString);
                } catch (ParseException e1) {
                    try {
                        time = thetvdbTimeFormatAMPMshort.parse(tvdbTimeString);
                    } catch (ParseException e2) {
                        try {
                            time = thetvdbTimeFormatNormal.parse(tvdbTimeString);
                        } catch (ParseException e3) {
                            // string may be wrongly formatted
                            time = null;
                        }
                    }
                }
            }
        }

        if (time != null) {
            Calendar timeCal = Calendar.getInstance();
            timeCal.setTime(time);
            cal.set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY));
            cal.set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE));
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            return cal.getTimeInMillis();
        } else {
            return -1;
        }
    }

    /**
     * Returns the Calendar constant (e.g. <code>Calendar.SUNDAY</code>) for a
     * given TVDb airday string (Monday through Sunday and Daily). If no match
     * is found -1 will be returned.
     * 
     * @param day TVDb day string
     */
    private static int getDayOfWeek(String day) {
        // catch Daily
        if (day.equalsIgnoreCase("Daily")) {
            return 0;
        }

        // catch Monday through Sunday
        DateFormatSymbols dfs = new DateFormatSymbols(Locale.US);
        String[] weekdays = dfs.getWeekdays();

        for (int i = 1; i < weekdays.length; i++) {
            if (day.equalsIgnoreCase(weekdays[i])) {
                return i;
            }
        }

        // no match
        return -1;
    }

    /**
     * Returns a string like 'Mon in 3 days', the day followed by how far it is
     * away in relative time.<br>
     * Does <b>not</b> respect user offsets or 'Use my time zone' setting. The
     * time to be passed is expected to be already corrected for that.
     */
    public static String formatToDayAndTimeWithoutOffsets(Context context, long airtime) {
        StringBuilder timeAndDay = new StringBuilder();

        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(airtime);

        final SimpleDateFormat dayFormat = new SimpleDateFormat("E", Locale.getDefault());
        timeAndDay.append(dayFormat.format(cal.getTime()));

        timeAndDay.append(" ");

        // Show 'today' instead of '0 days ago'
        if (DateUtils.isToday(cal.getTimeInMillis())) {
            timeAndDay.append(context.getString(R.string.today));
        } else {
            timeAndDay.append(DateUtils
                    .getRelativeTimeSpanString(
                            cal.getTimeInMillis(),
                            System.currentTimeMillis(),
                            DateUtils.DAY_IN_MILLIS,
                            DateUtils.FORMAT_ABBREV_ALL));
        }

        return timeAndDay.toString();
    }

    public static long buildEpisodeAirtime(String tvdbDateString, long airtime) {
        TimeZone pacific = TimeZone.getTimeZone(TIMEZONE_ALWAYS_PST);
        SimpleDateFormat tvdbDateFormat = Constants.theTVDBDateFormat;
        tvdbDateFormat.setTimeZone(pacific);

        try {

            Date day = tvdbDateFormat.parse(tvdbDateString);

            Calendar dayCal = Calendar.getInstance(pacific);
            dayCal.setTime(day);

            // set an airtime if we have one (may not be the case for ended
            // shows)
            if (airtime != -1) {
                Calendar timeCal = Calendar.getInstance(pacific);
                timeCal.setTimeInMillis(airtime);

                dayCal.set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY));
                dayCal.set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE));
                dayCal.set(Calendar.SECOND, 0);
                dayCal.set(Calendar.MILLISECOND, 0);
            }

            return dayCal.getTimeInMillis();

        } catch (ParseException e) {
            // we just return -1 then
            return -1;
        }
    }

    /**
     * Splits the string and reassembles it, separating the items with commas.
     * The given object is returned with the new string.
     */
    public static String splitAndKitTVDBStrings(String tvdbstring) {
        if (tvdbstring == null) {
            tvdbstring = "";
        }
        String[] splitted = tvdbstring.split("\\|");
        tvdbstring = "";
        for (String item : splitted) {
            if (tvdbstring.length() != 0) {
                tvdbstring += ", ";
            }
            tvdbstring += item;
        }
        return tvdbstring;
    }

    public static String getVersion(Context context) {
        String version;
        try {
            version = context.getPackageManager().getPackageInfo(context.getPackageName(),
                    PackageManager.GET_META_DATA).versionName;
        } catch (NameNotFoundException e) {
            version = "UnknownVersion";
        }
        return version;
    }

    /**
     * Put the TVDb season number in, get a full 'Season X' or 'Special
     * Episodes' string out.
     */
    public static String getSeasonString(Context context, int seasonNumber) {
        if (seasonNumber == 0) {
            return context.getString(R.string.specialseason);
        } else {
            return context.getString(R.string.season_number, seasonNumber);
        }
    }

    public static String toSHA1(Context context, String message) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] messageBytes = message.getBytes("UTF-8");
            byte[] digest = md.digest(messageBytes);

            String result = "";
            for (int i = 0; i < digest.length; i++) {
                result += Integer.toString((digest[i] & 0xff) + 0x100, 16).substring(1);
            }

            return result;
        } catch (NoSuchAlgorithmException e) {
            Utils.trackExceptionAndLog(TAG, e);
        } catch (UnsupportedEncodingException e) {
            Utils.trackExceptionAndLog(TAG, e);
        }
        return null;
    }

    public enum SGChannel {
        STABLE("com.battlelancer.seriesguide"), BETA("com.battlelancer.seriesguide.beta"), X(
                "com.battlelancer.seriesguide.x");

        String packageName;

        private SGChannel(String packageName) {
            this.packageName = packageName;
        }
    }

    public static SGChannel getChannel(Context context) {
        String thisPackageName = context.getApplicationContext().getPackageName();
        if (thisPackageName.equals(SGChannel.BETA.packageName)) {
            return SGChannel.BETA;
        }
        if (thisPackageName.equals(SGChannel.X.packageName)) {
            return SGChannel.X;
        }
        return SGChannel.STABLE;
    }

    /**
     * Returns whether a regular check with the Google Play app is necessary to
     * determine access to X features (e.g. the subscription is still valid).
     */
    public static boolean requiresPurchaseCheck(Context context) {
        // dev builds and the SeriesGuide X key app are not handled through the
        // Play store
        if (getChannel(context) != SGChannel.STABLE || hasUnlockKeyInstalled(context)) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * Returns whether this user should currently get access to X features.
     */
    public static boolean hasAccessToX(Context context) {
        // dev builds, SeriesGuide X installed or a valid purchase unlock X
        // features
        if (!requiresPurchaseCheck(context) || AdvancedSettings.isSubscribedToX(context)) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Returns true if the user has the legacy SeriesGuide X version installed,
     * signed with the same key as we are.
     */
    public static boolean hasUnlockKeyInstalled(Context context) {
        try {
            // Get our signing key
            PackageManager manager = context.getPackageManager();
            PackageInfo appInfoSeriesGuide = manager
                    .getPackageInfo(
                            context.getApplicationContext().getPackageName(),
                            PackageManager.GET_SIGNATURES);

            // Try to find the X signing key
            PackageInfo appInfoSeriesGuideX = manager
                    .getPackageInfo(
                            "com.battlelancer.seriesguide.x",
                            PackageManager.GET_SIGNATURES);

            final String ourKey = appInfoSeriesGuide.signatures[0].toCharsString();
            final String xKey = appInfoSeriesGuideX.signatures[0].toCharsString();
            return ourKey.equals(xKey);
        } catch (NameNotFoundException e) {
            // Expected exception that occurs if the package is not present.
        }

        return false;
    }

    public static void setValueOrPlaceholder(View view, final String value) {
        TextView field = (TextView) view;
        if (value == null || value.length() == 0) {
            field.setText(R.string.unknown);
        } else {
            field.setText(value);
        }
    }

    public static void setLabelValueOrHide(View label, TextView text, final String value) {
        if (TextUtils.isEmpty(value)) {
            label.setVisibility(View.GONE);
            text.setVisibility(View.GONE);
        } else {
            label.setVisibility(View.VISIBLE);
            text.setVisibility(View.VISIBLE);
            text.setText(value);
        }
    }

    public static void setLabelValueOrHide(View label, TextView text, double value) {
        if (value > 0.0) {
            label.setVisibility(View.VISIBLE);
            text.setVisibility(View.VISIBLE);
            text.setText(String.valueOf(value));
        } else {
            label.setVisibility(View.GONE);
            text.setVisibility(View.GONE);
        }
    }

    /**
     * Sets the global app theme variable. Applied by all activities once they
     * are created.
     */
    public static synchronized void updateTheme(String themeIndex) {
        int theme = Integer.valueOf(themeIndex);
        switch (theme) {
            case 1:
                SeriesGuidePreferences.THEME = R.style.ICSBaseTheme;
                break;
            case 2:
                SeriesGuidePreferences.THEME = R.style.SeriesGuideThemeLight;
                break;
            default:
                SeriesGuidePreferences.THEME = R.style.SeriesGuideTheme;
                break;
        }
    }

    /**
     * Tracks an exception using the Google Analytics {@link EasyTracker}.
     */
    public static void trackException(String tag, Exception e) {
        EasyTracker.getTracker().sendException(tag + ": " + e.getMessage(), false);
    }

    /**
     * Tracks an exception using the Google Analytics {@link EasyTracker} and
     * the local log.
     */
    public static void trackExceptionAndLog(String tag, Exception e) {
        trackException(tag, e);
        Log.w(tag, e);
    }

    /**
     * Calls {@link Context#startActivity(Intent)} with the given
     * <b>implicit</b> {@link Intent} after making sure there is an
     * {@link Activity} to handle it. Can show an error toast, if not. <br>
     * <br>
     * This may happen if e.g. the web browser has been disabled through
     * restricted profiles.
     * 
     * @return Whether there was an {@link Activity} to handle the given
     *         {@link Intent}.
     */
    public static boolean tryStartActivity(Context context, Intent intent, boolean displayError) {
        if (intent.resolveActivity(context.getPackageManager()) != null) {
            context.startActivity(intent);
            return true;
        } else if (displayError) {
            Toast.makeText(context, R.string.app_not_available, Toast.LENGTH_LONG).show();
        }
        return false;
    }

    /**
     * Resolves the given attribute to the resource id for the given theme.
     */
    public static int resolveAttributeToResourceId(Resources.Theme theme, int attributeResId) {
        TypedValue outValue = new TypedValue();
        theme.resolveAttribute(attributeResId, outValue, true);
        return outValue.resourceId;
    }

}
