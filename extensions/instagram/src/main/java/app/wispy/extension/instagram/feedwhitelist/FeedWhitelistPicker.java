/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 *
 * Modified for wispy-morphe-tweaks: made independent of the Piko extension.
 */

package app.wispy.extension.instagram.feedwhitelist;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The menu, opened by long-pressing the Home tab: the whitelist or blacklist mode, the selected
 * accounts, then every followed account.
 */
@SuppressWarnings("unused")
public final class FeedWhitelistPicker {

    private static final String MAIN_ACTIVITY = "com.instagram.mainactivity.InstagramMainActivity";

    private static final int TYPE_MODE = 0;
    private static final int TYPE_HEADER = 1;
    private static final int TYPE_INFO = 2;
    private static final int TYPE_ACCOUNT = 3;

    /** One row of the list. The key identifies it across rebuilds. */
    private static final class Row {
        final int type;
        final String key;
        final boolean inFollowing;
        String title;
        String summary;
        Runnable onClick;
        FeedWhitelist.Account account;

        Row(int type, String key, boolean inFollowing) {
            this.type = type;
            this.key = key;
            this.inFollowing = inFollowing;
        }
    }

    private final Activity activity;
    private final Dialog dialog;
    private final Context context;
    private final Style style;
    private final ListView list;
    private final List<Row> rows = new ArrayList<>();
    private final Map<String, FeedWhitelist.Account> followingById = new HashMap<>();
    private List<FeedWhitelist.Account> following;
    private String statusTitle;
    private String statusSummary;
    private Runnable statusAction;
    private String filter = "";

    private final BaseAdapter adapter = new BaseAdapter() {
        @Override
        public int getCount() {
            return rows.size();
        }

        @Override
        public Row getItem(int position) {
            return rows.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getViewTypeCount() {
            return 4;
        }

        @Override
        public int getItemViewType(int position) {
            return rows.get(position).type;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public boolean isEnabled(int position) {
            return rows.get(position).onClick != null;
        }

        @Override
        public View getView(int position, View view, ViewGroup parent) {
            Row row = rows.get(position);
            switch (row.type) {
                case TYPE_MODE:
                    if (view == null) view = createModeView();
                    bindModeView(view);
                    break;
                case TYPE_HEADER:
                    if (view == null) view = createHeaderView();
                    ((TextView) view).setText(row.title);
                    break;
                case TYPE_INFO:
                    if (view == null) view = createInfoView();
                    bindInfoView(view, row);
                    break;
                default:
                    if (view == null) view = createAccountView();
                    bindAccountView(view, row.account);
            }
            return view;
        }
    };

    /** Injection point. */
    public static void onActivityCreate(Activity activity) {
        try {
            FeedWhitelist.setContext(activity.getApplicationContext());
        } catch (Throwable e) {
            Log.e(FeedWhitelist.TAG, "Failed to read the app context", e);
        }
    }

    /** Injection point: makes a long press on the Home tab open the menu. */
    public static void onActivityResume(Activity activity) {
        try {
            if (!MAIN_ACTIVITY.equals(activity.getClass().getName())) return;
            int tabId = activity.getResources().getIdentifier("feed_tab", "id", activity.getPackageName());
            if (tabId == 0) return;
            View decor = activity.getWindow().getDecorView();
            if (attach(activity, tabId)) return;
            // The tab bar may not exist yet.
            decor.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    if (attach(activity, tabId) && decor.getViewTreeObserver().isAlive()) {
                        decor.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    }
                }
            });
        } catch (Throwable e) {
            Log.e(FeedWhitelist.TAG, "Failed to hook the Home tab", e);
        }
    }

    private static boolean attach(Activity activity, int tabId) {
        View tab = activity.findViewById(tabId);
        if (tab == null) return false;
        tab.setOnLongClickListener(view -> {
            try {
                new FeedWhitelistPicker(activity).show();
            } catch (Throwable e) {
                Log.e(FeedWhitelist.TAG, "Failed to open the menu", e);
            }
            return true;
        });
        return true;
    }

    private FeedWhitelistPicker(Activity activity) {
        this.activity = activity;
        style = new Style(activity);
        dialog = new Dialog(activity, style.dark
                ? android.R.style.Theme_DeviceDefault_NoActionBar
                : android.R.style.Theme_DeviceDefault_Light_NoActionBar);
        context = dialog.getContext();
        list = new ListView(context);
    }

    private void show() {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(style.background);
        // Keeps the content clear of the system bars and the keyboard on edge-to-edge windows.
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        root.addView(createToolbar());
        root.addView(createSearchBar(), searchBarParams());

        list.setDivider(null);
        list.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        list.setBackgroundColor(style.background);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            Runnable onClick = rows.get(position).onClick;
            if (onClick != null) onClick.run();
        });
        root.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
            window.setStatusBarColor(style.background);
            window.setNavigationBarColor(style.background);
            int flags = window.getDecorView().getSystemUiVisibility();
            int light = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            window.getDecorView().setSystemUiVisibility(style.dark ? flags & ~light : flags | light);
        }
        dialog.show();
        load(false);
    }

    private void load(boolean refresh) {
        showStatus("Loading followed accounts…", "", null);
        FollowingFetcher.load(activity, refresh, new FollowingFetcher.Listener() {
            @Override
            public void onProgress(int loaded) {
                if (following == null && dialog.isShowing()) {
                    showStatus("Loading followed accounts…", loaded + " accounts loaded", null);
                }
            }

            @Override
            public void onLoaded(List<FeedWhitelist.Account> accounts) {
                following = accounts;
                followingById.clear();
                for (FeedWhitelist.Account account : accounts) followingById.put(account.id, account);
                rebuild(false);
            }

            @Override
            public void onError() {
                showStatus("Couldn't load followed accounts", "Tap to try again", () -> load(true));
            }
        });
    }

    private void showStatus(String title, String summary, Runnable action) {
        following = null;
        statusTitle = title;
        statusSummary = summary;
        statusAction = action;
        rebuild(false);
    }

    /**
     * Rebuilds the rows. Rows added to or removed from the selected section would push the list
     * below it, so with [keepScroll] the first visible row below it is put back where it was.
     */
    private void rebuild(boolean keepScroll) {
        String anchorKey = null;
        int anchorTop = 0;
        if (keepScroll) {
            for (int i = 0; i < list.getChildCount(); i++) {
                int position = list.getFirstVisiblePosition() + i;
                if (position < rows.size() && rows.get(position).inFollowing) {
                    anchorKey = rows.get(position).key;
                    anchorTop = list.getChildAt(i).getTop();
                    break;
                }
            }
        }

        rows.clear();
        rows.add(new Row(TYPE_MODE, "mode", false));
        addSelectedRows();
        addFollowingRows();
        adapter.notifyDataSetChanged();

        if (anchorKey == null) return;
        for (int position = 0; position < rows.size(); position++) {
            if (rows.get(position).key.equals(anchorKey)) {
                // The offset is measured from inside the list's top padding.
                list.setSelectionFromTop(position, anchorTop - list.getPaddingTop());
                return;
            }
        }
    }

    private void addSelectedRows() {
        Map<String, FeedWhitelist.Account> selected = FeedWhitelist.selected();
        rows.add(header("selected", "Selected (" + selected.size() + ")", false));
        if (selected.isEmpty()) {
            rows.add(info("none", "No accounts selected",
                    "Tap Feed or Story on an account below to filter its posts or stories on the home page.",
                    null, false));
            return;
        }
        for (FeedWhitelist.Account stored : selected.values()) {
            // Stored selections get the current name and picture.
            FeedWhitelist.Account current = followingById.get(stored.id);
            FeedWhitelist.Account account = (current == null ? stored : current)
                    .withTargets(stored.feed, stored.story);
            if (!matches(account)) continue;
            Row row = new Row(TYPE_ACCOUNT, "selected:" + account.id, false);
            row.account = account;
            rows.add(row);
        }
        if (filter.isEmpty()) rows.add(info("clear", "Clear selection", "", this::confirmClear, false));
    }

    private void addFollowingRows() {
        if (following == null) {
            rows.add(header("following", "Following", true));
            rows.add(info("status", statusTitle, statusSummary, statusAction, true));
            return;
        }
        rows.add(header("following", "Following (" + following.size() + ")", true));
        if (following.isEmpty()) {
            rows.add(info("empty", "You don't follow anyone yet", "", null, true));
            return;
        }
        for (FeedWhitelist.Account account : following) {
            if (!matches(account)) continue;
            FeedWhitelist.Account stored = FeedWhitelist.get(account.id);
            Row row = new Row(TYPE_ACCOUNT, "following:" + account.id, true);
            row.account = account.withTargets(stored != null && stored.feed, stored != null && stored.story);
            rows.add(row);
        }
    }

    private static Row header(String key, String title, boolean inFollowing) {
        Row row = new Row(TYPE_HEADER, "header:" + key, inFollowing);
        row.title = title;
        return row;
    }

    private static Row info(String key, String title, String summary, Runnable onClick, boolean inFollowing) {
        Row row = new Row(TYPE_INFO, "info:" + key, inFollowing);
        row.title = title;
        row.summary = summary;
        row.onClick = onClick;
        return row;
    }

    private boolean matches(FeedWhitelist.Account account) {
        return filter.isEmpty()
                || account.username.toLowerCase(Locale.ROOT).contains(filter)
                || account.fullName.toLowerCase(Locale.ROOT).contains(filter);
    }

    private void confirmClear() {
        new AlertDialog.Builder(context)
                .setMessage("Remove all selected accounts?")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (confirm, which) -> {
                    FeedWhitelist.clear();
                    rebuild(true);
                })
                .show();
    }

    private View createToolbar() {
        LinearLayout toolbar = new LinearLayout(context);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(4), dp(6), dp(15), dp(6));

        TextView back = new TextView(context);
        back.setText("←");
        back.setContentDescription("Back");
        back.setGravity(Gravity.CENTER);
        back.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        back.setTextColor(style.primaryText);
        back.setOnClickListener(view -> dialog.dismiss());
        toolbar.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));

        TextView title = new TextView(context);
        title.setText("Whitelist/Blacklist followed accounts");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(style.primaryText);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return toolbar;
    }

    private View createSearchBar() {
        EditText search = new EditText(context);
        search.setSingleLine(true);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setHint("Search username");
        search.setHintTextColor(style.secondaryText);
        search.setTextColor(style.primaryText);
        search.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        search.setBackground(rounded(style.fieldBackground, 12));
        search.setPadding(dp(14), dp(10), dp(14), dp(10));
        search.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String next = s.toString().trim().toLowerCase(Locale.ROOT);
                if (next.equals(filter)) return;
                filter = next;
                rebuild(false);
            }
        });
        return search;
    }

    private LinearLayout.LayoutParams searchBarParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(12), 0, dp(12), dp(8));
        return params;
    }

    /** Whitelist and blacklist buttons, with what the current mode does. */
    private View createModeView() {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(15), dp(12), dp(15), dp(8));

        LinearLayout segments = new LinearLayout(context);
        segments.setOrientation(LinearLayout.HORIZONTAL);
        segments.setBackground(rounded(style.fieldBackground, 10));
        segments.setPadding(dp(3), dp(3), dp(3), dp(3));
        segments.addView(createSegment("whitelist", "Whitelist"), new LinearLayout.LayoutParams(0, dp(36), 1f));
        segments.addView(createSegment("blacklist", "Blacklist"), new LinearLayout.LayoutParams(0, dp(36), 1f));
        root.addView(segments, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView description = new TextView(context);
        description.setTag("description");
        description.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        description.setTextColor(style.secondaryText);
        description.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        description.setPadding(0, dp(10), 0, 0);
        root.addView(description);
        return root;
    }

    private TextView createSegment(String tag, String label) {
        TextView segment = new TextView(context);
        segment.setTag(tag);
        segment.setText(label);
        segment.setGravity(Gravity.CENTER);
        segment.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        segment.setTypeface(Typeface.DEFAULT_BOLD);
        return segment;
    }

    private void bindModeView(View view) {
        boolean blacklist = FeedWhitelist.isBlacklist();
        bindSegment(view.findViewWithTag("whitelist"), !blacklist, false);
        bindSegment(view.findViewWithTag("blacklist"), blacklist, true);
        TextView description = view.findViewWithTag("description");
        description.setText((blacklist
                ? "The home page hides posts and stories from the accounts selected for them."
                : "The home page only shows posts and stories from the accounts selected for them. "
                + "If no account is selected for posts or for stories, those aren't filtered. "
                + "Suggested posts from accounts you don't follow aren't affected.")
                + " Changes apply to posts and stories loaded from now on, so refresh the home page "
                + "or restart Instagram to see them.");
    }

    private void bindSegment(TextView segment, boolean selected, boolean blacklist) {
        segment.setBackground(rounded(selected ? style.background : Color.TRANSPARENT, 8));
        segment.setTextColor(selected ? style.primaryText : style.secondaryText);
        segment.setSelected(selected);
        segment.setOnClickListener(view -> {
            if (blacklist == FeedWhitelist.isBlacklist()) return;
            FeedWhitelist.setBlacklist(blacklist);
            rebuild(true);
        });
    }

    private View createHeaderView() {
        TextView header = new TextView(context);
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        header.setTextColor(style.secondaryText);
        header.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        header.setPadding(dp(15), dp(16), dp(15), dp(6));
        return header;
    }

    /** A plain text row, optionally tappable. */
    private View createInfoView() {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(48));
        row.setPadding(dp(15), dp(10), dp(15), dp(10));
        row.addView(createText("title", 16, style.primaryText, false));
        row.addView(createText("summary", 14, style.secondaryText, false));
        return row;
    }

    private void bindInfoView(View view, Row row) {
        ((TextView) view.findViewWithTag("title")).setText(row.title);
        TextView summary = view.findViewWithTag("summary");
        summary.setText(row.summary);
        summary.setVisibility(TextUtils.isEmpty(row.summary) ? View.GONE : View.VISIBLE);
    }

    /** A followed account, with toggles for filtering its home feed posts and its stories. */
    private View createAccountView() {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(64));
        row.setPadding(dp(15), dp(6), dp(12), dp(6));

        ImageView avatar = new ImageView(context);
        avatar.setTag("avatar");
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        GradientDrawable placeholder = new GradientDrawable();
        placeholder.setShape(GradientDrawable.OVAL);
        placeholder.setColor(style.fieldBackground);
        avatar.setBackground(placeholder);
        avatar.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        avatar.setClipToOutline(true);
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(dp(44), dp(44));
        avatarParams.setMarginEnd(dp(12));
        row.addView(avatar, avatarParams);

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textParams.setMarginEnd(dp(8));
        row.addView(texts, textParams);
        texts.addView(createText("title", 16, style.primaryText, true));
        texts.addView(createText("summary", 14, style.secondaryText, true));

        row.addView(createToggle("feed", "Feed"));
        LinearLayout.LayoutParams storyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        storyParams.setMarginStart(dp(6));
        row.addView(createToggle("story", "Story"), storyParams);
        return row;
    }

    private TextView createText(String tag, int sizeSp, int color, boolean singleLine) {
        TextView text = new TextView(context);
        text.setTag(tag);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        text.setTextColor(color);
        text.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        if (singleLine) {
            text.setSingleLine(true);
            text.setEllipsize(TextUtils.TruncateAt.END);
        }
        return text;
    }

    private TextView createToggle(String tag, String label) {
        TextView toggle = new TextView(context);
        toggle.setTag(tag);
        toggle.setText(label);
        toggle.setGravity(Gravity.CENTER);
        toggle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        toggle.setTypeface(Typeface.DEFAULT_BOLD);
        toggle.setMinWidth(dp(64));
        toggle.setMinHeight(dp(34));
        toggle.setPadding(dp(12), 0, dp(12), 0);
        return toggle;
    }

    private void bindAccountView(View view, FeedWhitelist.Account account) {
        ((TextView) view.findViewWithTag("title")).setText(account.username);
        TextView summary = view.findViewWithTag("summary");
        summary.setText(account.fullName);
        summary.setVisibility(account.fullName.isEmpty() ? View.GONE : View.VISIBLE);
        bindToggle(view.findViewWithTag("feed"), account.feed, account, !account.feed, account.story);
        bindToggle(view.findViewWithTag("story"), account.story, account, account.feed, !account.story);
        Avatars.load(view.findViewWithTag("avatar"), account.pictureUrl);
    }

    private void bindToggle(TextView toggle, boolean on, FeedWhitelist.Account account,
                            boolean feedOnTap, boolean storyOnTap) {
        int accent = FeedWhitelist.isBlacklist() ? style.blacklistAccent : style.whitelistAccent;
        // No ripple: its fade-out takes the old colour over the new state.
        toggle.setBackground(rounded(on ? accent : style.fieldBackground, 8));
        toggle.setTextColor(on ? Color.WHITE : style.primaryText);
        toggle.setSelected(on);
        toggle.setContentDescription(toggle.getText() + (on ? ", on" : ", off"));
        toggle.setOnClickListener(view -> {
            FeedWhitelist.set(account, feedOnTap, storyOnTap);
            rebuild(true);
        });
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setColor(color);
        return drawable;
    }

    private int dp(int value) {
        return Style.dp(context, value);
    }
}
