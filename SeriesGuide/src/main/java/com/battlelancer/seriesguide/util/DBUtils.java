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

import android.app.ProgressDialog;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.text.TextUtils;

import com.battlelancer.seriesguide.SeriesGuideApplication;
import com.battlelancer.seriesguide.dataliberation.JsonExportTask.ShowStatusExport;
import com.battlelancer.seriesguide.dataliberation.model.Show;
import com.battlelancer.seriesguide.items.Series;
import com.battlelancer.seriesguide.provider.SeriesContract.EpisodeSearch;
import com.battlelancer.seriesguide.provider.SeriesContract.Episodes;
import com.battlelancer.seriesguide.provider.SeriesContract.Seasons;
import com.battlelancer.seriesguide.provider.SeriesContract.Shows;
import com.battlelancer.thetvdbapi.TheTVDB.ShowStatus;
import com.uwetrottmann.androidutils.Lists;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class DBUtils {

    static final String TAG = "SeriesDatabase";

    /**
     * Use 9223372036854775807 (Long.MAX_VALUE) for unknown airtime/no next
     * episode so they will get sorted last.
     */
    public static final String UNKNOWN_NEXT_AIR_DATE = "9223372036854775807";

    interface UnwatchedQuery {
        static final String[] PROJECTION = new String[] {
                Episodes._ID
        };

        static final String NOAIRDATE_SELECTION = Episodes.WATCHED + "=? AND "
                + Episodes.FIRSTAIREDMS + "=?";

        static final String FUTURE_SELECTION = Episodes.WATCHED + "=? AND " + Episodes.FIRSTAIREDMS
                + ">?";

        static final String AIRED_SELECTION = Episodes.WATCHED + "=? AND " + Episodes.FIRSTAIREDMS
                + " !=? AND " + Episodes.FIRSTAIREDMS + "<=?";
    }

    /**
     * Returns how many episodes of a show are left to collect.
     */
    public static int getUncollectedEpisodesOfShow(Context context, String showId) {
        if (context == null) {
            return -1;
        }
        final ContentResolver resolver = context.getContentResolver();
        final Uri episodesOfShowUri = Episodes.buildEpisodesOfShowUri(showId);

        // unwatched, aired episodes
        final Cursor uncollected = resolver.query(episodesOfShowUri, new String[] {
                Episodes._ID, Episodes.COLLECTED
        },
                Episodes.COLLECTED + "=0", null, null);
        if (uncollected == null) {
            return -1;
        }
        final int count = uncollected.getCount();
        uncollected.close();

        return count;
    }

    private static final String[] SHOW_PROJECTION = new String[] {
            Shows._ID, Shows.ACTORS, Shows.AIRSDAYOFWEEK, Shows.AIRSTIME, Shows.CONTENTRATING,
            Shows.FIRSTAIRED, Shows.GENRES, Shows.NETWORK, Shows.OVERVIEW, Shows.POSTER,
            Shows.RATING, Shows.RUNTIME, Shows.TITLE, Shows.STATUS, Shows.IMDBID,
            Shows.NEXTEPISODE, Shows.LASTEDIT
    };

    /**
     * Returns a {@link Series} object. Might return {@code null} if there is no
     * show with that TVDb id.
     */
    public static Series getShow(Context context, int showTvdbId) {
        Cursor details = context.getContentResolver().query(Shows.buildShowUri(showTvdbId),
                SHOW_PROJECTION, null,
                null, null);

        Series show = null;
        if (details != null) {
            if (details.moveToFirst()) {
                show = new Series();

                show.setId(details.getString(0));
                show.setActors(details.getString(1));
                show.setAirsDayOfWeek(details.getString(2));
                show.setAirsTime(details.getLong(3));
                show.setContentRating(details.getString(4));
                show.setFirstAired(details.getString(5));
                show.setGenres(details.getString(6));
                show.setNetwork(details.getString(7));
                show.setOverview(details.getString(8));
                show.setPoster(details.getString(9));
                show.setRating(details.getString(10));
                show.setRuntime(details.getString(11));
                show.setTitle(details.getString(12));
                show.setStatus(details.getInt(13));
                show.setImdbId(details.getString(14));
                show.setNextEpisode(details.getLong(15));
                show.setLastEdit(details.getLong(16));
            }
            details.close();
        }

        return show;
    }

    public static boolean isShowExists(String showId, Context context) {
        Cursor testsearch = context.getContentResolver().query(Shows.buildShowUri(showId),
                new String[] {
                    Shows._ID
                }, null, null, null);
        boolean isShowExists = testsearch.getCount() != 0 ? true : false;
        testsearch.close();
        return isShowExists;
    }

    /**
     * Builds a {@link ContentProviderOperation} for inserting or updating a
     * show (depending on {@code isNew}.
     * 
     * @param show
     * @param context
     * @param isNew
     * @return
     */
    public static ContentProviderOperation buildShowOp(Show show, Context context, boolean isNew) {
        ContentValues values = new ContentValues();
        values = putCommonShowValues(show, values);

        if (isNew) {
            values.put(Shows._ID, show.tvdbId);
            return ContentProviderOperation.newInsert(Shows.CONTENT_URI).withValues(values).build();
        } else {
            return ContentProviderOperation
                    .newUpdate(Shows.buildShowUri(String.valueOf(show.tvdbId)))
                    .withValues(values).build();
        }
    }

    /**
     * Transforms a {@link Show} objects attributes into {@link ContentValues}
     * using the correct {@link Shows} columns.
     */
    private static ContentValues putCommonShowValues(Show show, ContentValues values) {
        values.put(Shows.TITLE, show.title);
        values.put(Shows.OVERVIEW, show.overview);
        values.put(Shows.ACTORS, show.actors);
        values.put(Shows.AIRSDAYOFWEEK, show.airday);
        values.put(Shows.AIRSTIME, show.airtime);
        values.put(Shows.FIRSTAIRED, show.firstAired);
        values.put(Shows.GENRES, show.genres);
        values.put(Shows.NETWORK, show.network);
        values.put(Shows.RATING, show.rating);
        values.put(Shows.RUNTIME, show.runtime);
        values.put(Shows.CONTENTRATING, show.contentRating);
        values.put(Shows.POSTER, show.poster);
        values.put(Shows.IMDBID, show.imdbId);
        values.put(Shows.LASTEDIT, show.lastEdited);
        values.put(Shows.LASTUPDATED, System.currentTimeMillis());
        int status;
        if (ShowStatusExport.CONTINUING.equals(show.status)) {
            status = ShowStatus.CONTINUING;
        } else if (ShowStatusExport.ENDED.equals(show.status)) {
            status = ShowStatus.ENDED;
        } else {
            status = ShowStatus.UNKNOWN;
        }
        values.put(Shows.STATUS, status);
        return values;
    }

    /**
     * Returns the episode IDs and their last edit time for a given show as a
     * efficiently searchable HashMap.
     * 
     * @return HashMap containing the shows existing episodes
     */
    public static HashMap<Long, Long> getEpisodeMapForShow(Context context, int showTvdbId) {
        Cursor eptest = context.getContentResolver().query(
                Episodes.buildEpisodesOfShowUri(showTvdbId), new String[] {
                        Episodes._ID, Episodes.LAST_EDITED
                }, null, null, null);
        HashMap<Long, Long> episodeMap = new HashMap<Long, Long>();
        if (eptest != null) {
            while (eptest.moveToNext()) {
                episodeMap.put(eptest.getLong(0), eptest.getLong(1));
            }
            eptest.close();
        }
        return episodeMap;
    }

    /**
     * Returns the season IDs for a given show as a efficiently searchable
     * HashMap.
     * 
     * @return HashMap containing the shows existing seasons
     */
    public static HashSet<Long> getSeasonIDsForShow(Context context, int showTvdbId) {
        Cursor setest = context.getContentResolver().query(
                Seasons.buildSeasonsOfShowUri(showTvdbId),
                new String[]{
                        Seasons._ID
                }, null, null, null);
        HashSet<Long> seasonIDs = new HashSet<Long>();
        if (setest != null) {
            while (setest.moveToNext()) {
                seasonIDs.add(setest.getLong(0));
            }
            setest.close();
        }
        return seasonIDs;
    }

    /**
     * Creates an update {@link ContentProviderOperation} for the given episode
     * values.
     */
    public static ContentProviderOperation buildEpisodeUpdateOp(ContentValues values) {
        final String episodeId = values.getAsString(Episodes._ID);
        ContentProviderOperation op = ContentProviderOperation
                .newUpdate(Episodes.buildEpisodeUri(episodeId))
                .withValues(values).build();
        return op;
    }

    /**
     * Creates a {@link ContentProviderOperation} for insert if isNew, or update
     * instead for with the given season values.
     * 
     * @param values
     * @param isNew
     * @return
     */
    public static ContentProviderOperation buildSeasonOp(ContentValues values, boolean isNew) {
        ContentProviderOperation op;
        final String seasonId = values.getAsString(Seasons.REF_SEASON_ID);
        final ContentValues seasonValues = new ContentValues();
        seasonValues.put(Seasons.COMBINED, values.getAsString(Episodes.SEASON));

        if (isNew) {
            seasonValues.put(Seasons._ID, seasonId);
            seasonValues.put(Shows.REF_SHOW_ID, values.getAsString(Shows.REF_SHOW_ID));
            op = ContentProviderOperation.newInsert(Seasons.CONTENT_URI).withValues(seasonValues)
                    .build();
        } else {
            op = ContentProviderOperation.newUpdate(Seasons.buildSeasonUri(seasonId))
                    .withValues(seasonValues).build();
        }
        return op;
    }

    private interface NextEpisodeQuery {
        /**
         * Unwatched, airing later or has a different number or season if airing
         * the same time.
         */
        String SELECT_NEXT = Episodes.WATCHED + "=0 AND ("
                + "(" + Episodes.FIRSTAIREDMS + "=? AND "
                + "(" + Episodes.NUMBER + "!=? OR " + Episodes.SEASON + "!=?)) "
                + "OR " + Episodes.FIRSTAIREDMS + ">?)";

        String SELECT_WITHAIRDATE = " AND " + Episodes.FIRSTAIREDMS + "!=-1";

        String SELECT_ONLYFUTURE = " AND " + Episodes.FIRSTAIREDMS + ">=?";

        String[] PROJECTION_WATCHED = new String[] {
                Episodes._ID, Episodes.SEASON, Episodes.NUMBER, Episodes.FIRSTAIREDMS
        };

        String[] PROJECTION_NEXT = new String[] {
                Episodes._ID, Episodes.SEASON, Episodes.NUMBER, Episodes.FIRSTAIREDMS,
                Episodes.TITLE
        };

        /**
         * Air time, then lowest season, or if identical lowest episode number.
         */
        String SORTING_NEXT = Episodes.FIRSTAIREDMS + " ASC," + Episodes.SEASON + " ASC,"
                + Episodes.NUMBER + " ASC";

        int _ID = 0;

        int SEASON = 1;

        int NUMBER = 2;

        int FIRSTAIREDMS = 3;

        int TITLE = 4;
    }
}
