/*
 * Copyright (C) 2006 The Android Open Source Project
 * This code has been modified.  Portions copyright (C) 2012, ParanoidAndroid Project.
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
 */

package com.android.systemui.statusbar.policy;

import android.app.ActivityManagerNative;
import android.app.StatusBarManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.database.ContentObserver;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.provider.AlarmClock;
import android.provider.Settings;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.format.DateFormat;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.AttributeSet;
import android.util.ExtendedPropertiesUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;
import java.math.BigInteger;

import com.android.internal.R;

/**
 * This widget display an analogic clock with two hands for hours and
 * minutes.
 */
public class Clock extends TextView implements OnClickListener, OnLongClickListener {
    private boolean mAttached;
    private Calendar mCalendar;
    private String mClockFormatString;
    private SimpleDateFormat mClockFormat;

    private static final int AM_PM_SIZE_NORMAL  = 0;
    private static final int AM_PM_SIZE_SMALL   = 1;

    private int AM_PM_SIZE = AM_PM_SIZE_SMALL;

    private boolean SHOW_AM_PM = false;

    private static final int WEEKDAY_SIZE_NORMAL = 0;
    private static final int WEEKDAY_SIZE_SMALL  = 1;

    private int WEEKDAY_SIZE = WEEKDAY_SIZE_SMALL;

    private static final int WEEKDAY_FORMAT_SHORT = 0;
    private static final int WEEKDAY_FORMAT_MEDIUM  = 1;
    private static final int WEEKDAY_FORMAT_LONG   = 2;

    private int WEEKDAY_FORMAT = WEEKDAY_FORMAT_MEDIUM;

    private boolean SHOW_WEEKDAY = false;

    private static final int DAYMONTH_SIZE_NORMAL = 0;
    private static final int DAYMONTH_SIZE_SMALL  = 1;

    private int DAYMONTH_SIZE = DAYMONTH_SIZE_SMALL;

    private boolean SHOW_DAYMONTH = false;

    private int mAmPmSize;
    private int mWeekdaySize;
    private int mWeekdayFormat;
    private int mDaymonthSize;
    private boolean mShowClock;
    private boolean mShowAmPm;
    private boolean mShowWeekday;
    private boolean mShowDaymonth;
    private boolean mShowAlways;
    private boolean mShowMore;

    Handler mHandler;

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_AM_PM_SIZE), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_SHOW_AM_PM), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_WEEKDAY_SIZE), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_WEEKDAY_FORMAT), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_SHOW_WEEKDAY), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_DAYMONTH_SIZE), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_SHOW_DAYMONTH), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(		
                    Settings.System.STATUS_BAR_COLOR), false, this);
        }

        @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }
    }

    public Clock(Context context) {
        this(context, null);
    }

    public Clock(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public Clock(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        
        TypedArray a = context.obtainStyledAttributes(attrs, com.android.systemui.R.styleable.Clock, defStyle, 0);
        mShowAlways = a.getBoolean(com.android.systemui.R.styleable.Clock_showAlways, false);
        mShowMore = a.getBoolean(com.android.systemui.R.styleable.Clock_showMore, true);

        mHandler = new Handler();
        SettingsObserver settingsObserver = new SettingsObserver(mHandler);
        settingsObserver.observe();
        if(isClickable()){
            setOnClickListener(this);
            setOnLongClickListener(this);
        }
        updateSettings();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (!mAttached) {
            mAttached = true;
            IntentFilter filter = new IntentFilter();

            filter.addAction(Intent.ACTION_TIME_TICK);
            filter.addAction(Intent.ACTION_TIME_CHANGED);
            filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
            filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);

            getContext().registerReceiver(mIntentReceiver, filter, null, getHandler());
        }

        // NOTE: It's safe to do these after registering the receiver since the receiver always runs
        // in the main thread, therefore the receiver can't run before this method returns.

        // The time zone may have changed while the receiver wasn't registered, so update the Time
        mCalendar = Calendar.getInstance(TimeZone.getDefault());

        // Make sure we update to the current time
        updateClock();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mAttached) {
            getContext().unregisterReceiver(mIntentReceiver);
            mAttached = false;
        }
    }

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_TIMEZONE_CHANGED)) {
                String tz = intent.getStringExtra("time-zone");
                mCalendar = Calendar.getInstance(TimeZone.getTimeZone(tz));
                if (mClockFormat != null) {
                    mClockFormat.setTimeZone(mCalendar.getTimeZone());
                }
            }
            updateClock();
        }
    };

    final void updateClock() {
        mCalendar.setTimeInMillis(System.currentTimeMillis());
        setText(getSmallTime());
    }

    private final CharSequence getSmallTime() {
        Context context = getContext();
        boolean b24 = DateFormat.is24HourFormat(context);
        int res;

        if (b24) {
            res = R.string.twenty_four_hour_time_format;
        } else {
            res = R.string.twelve_hour_time_format;
        }

        final char MAGIC1 = '\uEF00';
        final char MAGIC2 = '\uEF01';

        SimpleDateFormat sdf;
        String format = context.getString(res);
        if (!format.equals(mClockFormatString)) {
            /*
             * Search for an unquoted "a" in the format string, so we can
             * add dummy characters around it to let us find it again after
             * formatting and change its size.
             */
            if (AM_PM_SIZE != AM_PM_SIZE_NORMAL || !mShowAmPm) {
                int a = -1;
                boolean quoted = false;
                for (int i = 0; i < format.length(); i++) {
                    char c = format.charAt(i);

                    if (c == '\'') {
                        quoted = !quoted;
                    }
                    if (!quoted && c == 'a') {
                        a = i;
                        break;
                    }
                }

                if (a >= 0) {
                    // Move a back so any whitespace before AM/PM is also in the alternate size.
                    final int b = a;
                    while (a > 0 && Character.isWhitespace(format.charAt(a-1))) {
                        a--;
                    }
                    format = format.substring(0, a) + MAGIC1 + format.substring(a, b)
                        + "a" + MAGIC2 + format.substring(b + 1);
                }
            }

            mClockFormat = sdf = new SimpleDateFormat(format);
            mClockFormatString = format;
        } else {
            sdf = mClockFormat;
        }
        String result = sdf.format(mCalendar.getTime());

        String currentDay = null;
        String currentMonth = null;

        if(mShowMore) {
            Calendar calendar = Calendar.getInstance();
            int day = calendar.get(Calendar.DAY_OF_WEEK);
            int month = calendar.get(Calendar.MONTH);

            String dayofmonth = String.valueOf(calendar.get(Calendar.DAY_OF_MONTH));

            if (mShowDaymonth) {
                currentMonth = getMonth(month);
                result = dayofmonth + " " + currentMonth + result;
            }

            if (mShowWeekday) {
                currentDay = getDay(day);
                result = currentDay + result;
            }
        }

        SpannableStringBuilder formatted = new SpannableStringBuilder(result);

        int magic1 = result.indexOf(MAGIC1);
        int magic2 = result.indexOf(MAGIC2);
        if (magic1 >= 0 && magic2 > magic1) {
            if (!mShowAmPm) {
                formatted.delete(magic1, magic2+1);
            } else {
                if (AM_PM_SIZE == AM_PM_SIZE_SMALL) {
                    CharacterStyle style = new RelativeSizeSpan(0.7f);
                    formatted.setSpan(style, magic1, magic2,
                            Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
                }
                formatted.delete(magic2, magic2 + 1);
                formatted.delete(magic1, magic1 + 1);
            }
        }

        if(mShowMore) {
            if (WEEKDAY_SIZE != WEEKDAY_SIZE_NORMAL) {
                if (currentDay != null) {
                    if (!mShowWeekday) {
                        formatted.delete(result.indexOf(currentDay), result.lastIndexOf(currentDay)+currentDay.length());
                    } else if (WEEKDAY_SIZE == WEEKDAY_SIZE_SMALL) {
                            CharacterStyle style = new RelativeSizeSpan(0.7f);
                            formatted.setSpan(style, result.indexOf(currentDay), result.lastIndexOf(currentDay)+currentDay.length(), Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
                    }
                }
            }

            if (DAYMONTH_SIZE != DAYMONTH_SIZE_NORMAL) {
                if (currentMonth != null) {
                    if (!mShowDaymonth) {
                        formatted.delete(result.indexOf(currentMonth), result.lastIndexOf(currentMonth)+currentMonth.length());
                    } else if (DAYMONTH_SIZE == DAYMONTH_SIZE_SMALL) {
                            CharacterStyle style = new RelativeSizeSpan(0.7f);
                            formatted.setSpan(style, result.indexOf(currentMonth), result.lastIndexOf(currentMonth)+currentMonth.length(), Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
                    }
                }
            }
        }
        ForegroundColorSpan fcs = new ForegroundColorSpan(getColor());
        formatted.setSpan(fcs, 0, formatted.length(), Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
        return formatted;
    }

    private int getColor() {
        String curSetting = Settings.System.getString(mContext.getContentResolver(),
            Settings.System.STATUS_BAR_COLOR);

        String[] curColors = (curSetting == null || curSetting.equals("") ?
            ExtendedPropertiesUtils.PARANOID_COLORS_DEFAULTS[
            ExtendedPropertiesUtils.PARANOID_COLORS_STATBAR] : curSetting).split(
            ExtendedPropertiesUtils.PARANOID_STRING_DELIMITER);
        String curColor = curColors[Integer.parseInt(curColors[2])];
        
        int red = new BigInteger(curColor.substring(2,4),16).intValue();
        int green = new BigInteger(curColor.substring(4,6),16).intValue();
        int blue = new BigInteger(curColor.substring(6,8),16).intValue();
        int yiq = ((red*299)+(green*587)+(blue*114))/1000;
        
        return (yiq >= 128) ? Color.rgb(0, 0, 0) : Color.rgb(255, 255, 255);
       // int currentColor = new BigInteger(mCurColor, 16).intValue();     
    }

    private String getDay(int today) {
        String currentDay = null;
        switch(WEEKDAY_FORMAT) {
            case WEEKDAY_FORMAT_SHORT:
                switch (today) {
                    case 1:
                        currentDay = getResources().getString(R.string.day_of_week_short_sunday);
                    break;
                    case 2:
                        currentDay = getResources().getString(R.string.day_of_week_short_monday);
                    break;
                    case 3:
                        currentDay = getResources().getString(R.string.day_of_week_short_tuesday);
                    break;
                    case 4:
                        currentDay = getResources().getString(R.string.day_of_week_short_wednesday);
                    break;
                    case 5:
                        currentDay = getResources().getString(R.string.day_of_week_short_thursday);
                    break;
                    case 6:
                        currentDay = getResources().getString(R.string.day_of_week_short_friday);
                    break;
                    case 7:
                        currentDay = getResources().getString(R.string.day_of_week_short_saturday);
                    break;
                }
            break;
            case WEEKDAY_FORMAT_MEDIUM:
                switch (today) {
                    case 1:
                        currentDay = getResources().getString(R.string.day_of_week_medium_sunday);
                    break;
                    case 2:
                        currentDay = getResources().getString(R.string.day_of_week_medium_monday);
                    break;
                    case 3:
                        currentDay = getResources().getString(R.string.day_of_week_medium_tuesday);
                    break;
                    case 4:
                        currentDay = getResources().getString(R.string.day_of_week_medium_wednesday);
                    break;
                    case 5:
                        currentDay = getResources().getString(R.string.day_of_week_medium_thursday);
                    break;
                    case 6:
                        currentDay = getResources().getString(R.string.day_of_week_medium_friday);
                    break;
                    case 7:
                        currentDay = getResources().getString(R.string.day_of_week_medium_saturday);
                    break;
                }
            break;
            case WEEKDAY_FORMAT_LONG:
                switch (today) {
                    case 1:
                        currentDay = getResources().getString(R.string.day_of_week_long_sunday);
                    break;
                    case 2:
                        currentDay = getResources().getString(R.string.day_of_week_long_monday);
                    break;
                    case 3:
                        currentDay = getResources().getString(R.string.day_of_week_long_tuesday);
                    break;
                    case 4:
                        currentDay = getResources().getString(R.string.day_of_week_long_wednesday);
                    break;
                    case 5:
                        currentDay = getResources().getString(R.string.day_of_week_long_thursday);
                    break;
                    case 6:
                        currentDay = getResources().getString(R.string.day_of_week_long_friday);
                    break;
                    case 7:
                        currentDay = getResources().getString(R.string.day_of_week_long_saturday);
                    break;
                }
            break;
        }
        return currentDay.toUpperCase() + " ";
    }

    private String getMonth(int month) {
        String currentMonth = null;
        switch (month) {
            case 0:
                currentMonth = getResources().getString(R.string.month_medium_january);
            break;
            case 1:
                currentMonth = getResources().getString(R.string.month_medium_february);
            break;
            case 2:
                currentMonth = getResources().getString(R.string.month_medium_march);
            break;
            case 3:
                currentMonth = getResources().getString(R.string.month_medium_april);
            break;
            case 4:
                currentMonth = getResources().getString(R.string.month_medium_may);
            break;
            case 5:
                currentMonth = getResources().getString(R.string.month_medium_june);
            break;
            case 6:
                currentMonth = getResources().getString(R.string.month_medium_july);
            break;
            case 7:
                currentMonth = getResources().getString(R.string.month_medium_august);
            break;
            case 8:
                currentMonth = getResources().getString(R.string.month_medium_september);
            break;
            case 9:
                currentMonth = getResources().getString(R.string.month_medium_october);
            break;
            case 10:
                currentMonth = getResources().getString(R.string.month_medium_november);
            break;
            case 11:
                currentMonth = getResources().getString(R.string.month_medium_december);
            break;
        }
        return currentMonth.toUpperCase() + " ";
    }

    private void updateSettings(){
        ContentResolver resolver = mContext.getContentResolver();

        mShowAmPm = (Settings.System.getInt(resolver,
                Settings.System.STATUS_BAR_SHOW_AM_PM, 0) == 1);
        mAmPmSize = (Settings.System.getInt(resolver,
                Settings.System.STATUS_BAR_AM_PM_SIZE, 1));

        if(mShowMore) {
            mShowWeekday = (Settings.System.getInt(resolver,
                    Settings.System.STATUS_BAR_SHOW_WEEKDAY, 0) == 1);
            mWeekdaySize = (Settings.System.getInt(resolver,
                    Settings.System.STATUS_BAR_WEEKDAY_SIZE, 1));
            mWeekdayFormat = (Settings.System.getInt(resolver,
                    Settings.System.STATUS_BAR_WEEKDAY_FORMAT, 1));

            mShowDaymonth = (Settings.System.getInt(resolver,
                    Settings.System.STATUS_BAR_SHOW_DAYMONTH, 0) == 1);
            mDaymonthSize = (Settings.System.getInt(resolver,
                    Settings.System.STATUS_BAR_DAYMONTH_SIZE, 1));
        }

        if (mAmPmSize != AM_PM_SIZE) {
            AM_PM_SIZE = mAmPmSize;
            mClockFormatString = "";
        }

        if (mShowAmPm != SHOW_AM_PM) {
            SHOW_AM_PM = mShowAmPm;
            mClockFormatString = "";
        }

        if(mShowMore) {
            if (mWeekdaySize != WEEKDAY_SIZE) {
                WEEKDAY_SIZE = mWeekdaySize;
            }

            if (mWeekdayFormat != WEEKDAY_FORMAT) {
                WEEKDAY_FORMAT = mWeekdayFormat;
            }

            if (mShowWeekday != SHOW_WEEKDAY) {
                SHOW_WEEKDAY = mShowWeekday;
            }

            if (mDaymonthSize != DAYMONTH_SIZE) {
                DAYMONTH_SIZE = mDaymonthSize;
            }

            if (mShowDaymonth != SHOW_DAYMONTH) {
                SHOW_DAYMONTH = mShowDaymonth;
            }
        }

        if (mAttached) {
            updateClock();
        }
    }

    private void collapseStartActivity(Intent what) {
        // collapse status bar
        StatusBarManager statusBarManager = (StatusBarManager) getContext().getSystemService(
                Context.STATUS_BAR_SERVICE);
        statusBarManager.collapse();

        // dismiss keyguard in case it was active and no passcode set
        try {
            ActivityManagerNative.getDefault().dismissKeyguardOnNextActivity();
        } catch (Exception ex) {
            // no action needed here
        }

        // start activity
        what.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(what);
    }

    @Override
    public void onClick(View v) {
        Intent intent = new Intent(AlarmClock.ACTION_SET_ALARM);
        collapseStartActivity(intent);
    }

    @Override
    public boolean onLongClick(View v) {
        Intent intent = new Intent("android.settings.DATE_SETTINGS");
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        collapseStartActivity(intent);

        // consume event
        return true;
    }
}

