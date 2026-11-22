/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 *
 * Modified for wispy-morphe-tweaks: made independent of the Piko extension.
 */

package app.wispy.extension.instagram.feedwhitelist;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.util.TypedValue;

/** Colours of Instagram's current theme, with fallbacks for attributes a build doesn't have. */
final class Style {
    final boolean dark;
    final int background;
    final int fieldBackground;
    final int primaryText;
    final int secondaryText;
    final int whitelistAccent;
    final int blacklistAccent;

    Style(Context context) {
        boolean night = (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        background = color(context, "igds_color_primary_background", night ? Color.BLACK : Color.WHITE);
        dark = Color.luminance(background) < 0.5f;
        fieldBackground = dark ? Color.rgb(43, 48, 54) : Color.rgb(241, 242, 246);
        primaryText = color(context, "igds_color_primary_text", dark ? Color.WHITE : Color.BLACK);
        secondaryText = color(context, "igds_color_secondary_text", dark ? 0xffb3b3b3 : 0xff737373);
        whitelistAccent = color(context, "igds_color_primary_button", 0xff0095f6);
        blacklistAccent = color(context, "igds_color_error_or_destructive", 0xffed4956);
    }

    private static int color(Context context, String attribute, int fallback) {
        try {
            int id = context.getResources().getIdentifier(attribute, "attr", context.getPackageName());
            TypedValue value = new TypedValue();
            if (id == 0 || !context.getTheme().resolveAttribute(id, value, true)) return fallback;
            if (value.resourceId != 0) return context.getColor(value.resourceId);
            if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT && value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                return value.data;
            }
        } catch (RuntimeException ignored) {
        }
        return fallback;
    }

    static int dp(Context context, float value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                context.getResources().getDisplayMetrics());
    }
}
