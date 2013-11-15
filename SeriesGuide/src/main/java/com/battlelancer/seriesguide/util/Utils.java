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

import com.google.analytics.tracking.android.EasyTracker;

import com.battlelancer.seriesguide.Constants;
import com.uwetrottmann.seriesguide.R;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

public class Utils {

    private static final String TAG = "Utils";

    private static final String TIMEZONE_ALWAYS_PST = "GMT-08:00";

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

}
