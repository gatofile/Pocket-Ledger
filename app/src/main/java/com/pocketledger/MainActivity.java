package com.pocketledger;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowInsets;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.view.ViewConfiguration;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Switch;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.text.DateFormatSymbols;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Currency;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Offline income, spending, category summaries, and appearance settings. */
public final class MainActivity extends Activity {
    private static final int DEFAULT_ACCENT = Color.rgb(23, 126, 104);
    private static final int DEFAULT_ACCENT_PALE = Color.rgb(231, 243, 239);
    private static final int REQUEST_EXPORT = 7101;
    private static final int REQUEST_IMPORT = 7102;
    private int INK = Color.rgb(29, 43, 37);
    private int MUTED = Color.rgb(108, 119, 112);
    private int GREEN = DEFAULT_ACCENT;
    private int GREEN_PALE = DEFAULT_ACCENT_PALE;
    private int CONTROL = DEFAULT_ACCENT;
    private int CONTROL_TEXT = Color.WHITE;
    private int SOFT_CONTROL = DEFAULT_ACCENT_PALE;
    private int SOFT_CONTROL_TEXT = Color.rgb(29, 43, 37);
    private int DESTRUCTIVE_CONTROL = Color.rgb(190, 75, 70);
    private int DESTRUCTIVE_TEXT = Color.WHITE;
    private int RED = Color.rgb(190, 75, 70);
    private int RED_PALE = Color.rgb(250, 235, 233);
    private int PAPER = Color.rgb(246, 247, 244);
    private int WHITE = Color.WHITE;
    private static final String TRANSACTIONS_KEY = "transactions_json";
    private static final String SELECTED_MONTH_KEY = "selected_month_start";
    private static final String CATEGORIES_KEY = "categories_json";
    private static final String CURRENCY_KEY = "currency_code";
    private static final String THEME_KEY = "appearance_theme";
    private static final String ACCENT_KEY = "accent_color";
    private static final String LOGO_KEY = "logo_style";
    private static final String LANGUAGE_KEY = "app_language";
    private static final String[] CURRENCIES = {"EUR", "USD", "GBP", "CAD", "AUD", "JPY", "INR", "BRL", "CHF"};
    private static final int[] CATEGORY_COLORS = new int[]{
            Color.rgb(53, 168, 129), Color.rgb(224, 138, 87), Color.rgb(100, 132, 216),
            Color.rgb(189, 108, 172), Color.rgb(224, 181, 68), Color.rgb(76, 169, 189),
            Color.rgb(220, 106, 114), Color.rgb(116, 141, 122), Color.rgb(151, 119, 207),
            Color.rgb(207, 131, 95)
    };

    private SharedPreferences preferences;
    private ArrayList<TransactionRecord> transactions;
    private ArrayList<CategoryRecord> categories;
    private Calendar selectedMonth;
    private String selectedTab = "main";
    private String themeStyle = "light";
    private String logoStyle = "light";
    private String currencyCode = "EUR";
    private String languageCode = "en";
    private JSONObject translations;
    private int accentColor = DEFAULT_ACCENT;
    private TextView monthView;
    private final ArrayList<TextView> monthViews = new ArrayList<>();
    private TextView spendingAmountView;
    private TextView incomeAmountView;
    private TextView netAmountView;
    private TextView netMessageView;
    private TextView comparisonIncomeView;
    private TextView comparisonSpendingView;
    private ComparisonBarView incomeComparisonBar;
    private ComparisonBarView spendingComparisonBar;
    private LinearLayout transactionList;
    private LinearLayout recentTransactionList;
    private LinearLayout categoryBreakdownList;
    private LinearLayout categoryOverviewContent;
    private LinearLayout categoryDetailPanel;
    private LinearLayout categoryDetailList;
    private TextView categoryDetailTitle;
    private TextView categoryDetailTotal;
    private String selectedCategoryName;
    private TextView categoryMonthlyTotalView;
    private CategoryChartView monthlyCategoryChart;
    private final ArrayList<View> tabPages = new ArrayList<>();
    private final ArrayList<LinearLayout> tabButtons = new ArrayList<>();
    private ImageView brandLogo;
    private LedgerDialog categoryManagerDialog;
    private final ArrayList<String> navigationHistory = new ArrayList<>();
    private boolean restoringNavigation;
    private SwipeRow openSwipeRow;
    private boolean externalPickerOpen;
    private boolean wasStopped;
    private boolean stoppedForExternalPicker;
    private AppUpdater appUpdater;
    private TextView updateStatusView;
    private Button checkUpdatesButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SharedPreferences earlyPreferences = getSharedPreferences("pocket-ledger", MODE_PRIVATE);
        String initialTheme = earlyPreferences.getString(THEME_KEY, "light");
        setTheme(isThemeStyle(initialTheme) && !"light".equals(initialTheme)
                ? R.style.AppThemeDark : R.style.AppTheme);
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences("pocket-ledger", MODE_PRIVATE);
        Bundle navigationState = savedInstanceState;
        if (navigationState != null) {
            externalPickerOpen = navigationState.getBoolean("external_picker_open", false);
            selectedTab = navigationState.getString("selected_tab", "main");
            selectedCategoryName = navigationState.getString("selected_category");
            ArrayList<String> history = navigationState.getStringArrayList("navigation_history");
            if (history != null) navigationHistory.addAll(history);
        }
        loadTranslations();
        applySavedSettings();
        selectedMonth = externalPickerOpen ? loadSelectedMonth() : firstDayOfCurrentMonth();
        preferences.edit().putLong(SELECTED_MONTH_KEY, selectedMonth.getTimeInMillis()).apply();
        categories = loadCategories();
        transactions = loadTransactionsOrMigrate();
        configureLauncherIcon(logoStyle);
        configureSystemBars();
        initializeUpdater();
        setContentView(createScreen());
        refreshScreen();
        restorePagePositions(navigationState);
        if (!externalPickerOpen) animateAppOpening();
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::handleBackNavigation);
        }
    }

    @Override protected void onStart() {
        super.onStart();
        if (wasStopped && !stoppedForExternalPicker && transactionList != null) {
            selectedMonth = firstDayOfCurrentMonth();
            selectedCategoryName = null;
            preferences.edit().putLong(SELECTED_MONTH_KEY, selectedMonth.getTimeInMillis()).apply();
            refreshScreen();
            animateAppOpening();
        }
        wasStopped = false;
        stoppedForExternalPicker = false;
    }

    @Override protected void onStop() {
        wasStopped = !isChangingConfigurations();
        stoppedForExternalPicker = externalPickerOpen;
        if (!externalPickerOpen && appUpdater != null) appUpdater.onStop();
        finishOpeningAnimation();
        super.onStop();
    }

    private void animateAppOpening() {
        finishOpeningAnimation();
        boolean enabled = Build.VERSION.SDK_INT >= 26 ? android.animation.ValueAnimator.areAnimatorsEnabled()
                : android.provider.Settings.Global.getFloat(getContentResolver(),
                        android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0;
        if (!enabled) return;
        View content = findViewById(android.R.id.content);
        if (content == null) return;
        content.setAlpha(0f);
        content.setTranslationY(dp(8));
        content.animate().alpha(1f).translationY(0f).setStartDelay(0).setDuration(300)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        if (brandLogo != null) {
            brandLogo.setScaleX(.82f); brandLogo.setScaleY(.82f);
            brandLogo.animate().scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(420)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        }
    }

    private void finishOpeningAnimation() {
        View content = findViewById(android.R.id.content);
        if (content != null) {
            content.animate().cancel();
            content.setAlpha(1f); content.setTranslationY(0f);
        }
        if (brandLogo != null) {
            brandLogo.animate().cancel();
            brandLogo.setScaleX(1f); brandLogo.setScaleY(1f);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (transactionList != null) refreshScreen();
        if (appUpdater != null) appUpdater.onResume();
    }

    @Override protected void onPause() {
        if (appUpdater != null) appUpdater.onPause();
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (appUpdater != null) appUpdater.close();
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putAll(captureNavigationState());
        outState.putBoolean("external_picker_open", externalPickerOpen);
        super.onSaveInstanceState(outState);
    }

    private Bundle captureNavigationState() {
        Bundle state = new Bundle();
        state.putString("selected_tab", selectedTab);
        state.putString("selected_category", selectedCategoryName);
        state.putStringArrayList("navigation_history", new ArrayList<>(navigationHistory));
        int[] positions = new int[tabPages.size()];
        for (int index = 0; index < tabPages.size(); index++) positions[index] = tabPages.get(index).getScrollY();
        state.putIntArray("page_scroll_positions", positions);
        return state;
    }

    private void restorePagePositions(Bundle state) {
        if (state == null) return;
        int[] positions = state.getIntArray("page_scroll_positions");
        if (positions == null) return;
        for (int index = 0; index < Math.min(positions.length, tabPages.size()); index++) {
            View page = tabPages.get(index);
            int scrollY = positions[index];
            page.post(() -> page.scrollTo(0, scrollY));
        }
    }

    private void confirmSettingsClose(Runnable applyChange) {
        new LedgerDialogBuilder().setTitle(tr("Close app to apply changes?"))
                .setMessage(tr("Applying this change will close Pocket Ledger. Open the app again to use the new settings. Do you want to continue?"))
                .setNegativeButton(tr("Cancel"), null)
                .setPositiveButton(tr("Apply and close"), (dialog, which) -> applyChange.run()).show();
    }

    private void saveSettingsAndClose(SharedPreferences.Editor changes) {
        if (!changes.commit()) {
            Toast.makeText(this, tr("Could not save the settings. Please try again."), Toast.LENGTH_LONG).show();
            return;
        }
        closeForSettingsChange();
    }

    private void closeForSettingsChange() {
        // Finish icon changes before closing so the next manual launch needs no relaunch.
        configureLauncherIcon(preferences.getString(LOGO_KEY, "light"));
        finishAndRemoveTask();
    }

    private String currentAppVersion() {
        try {
            String version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            return version == null ? tr("Unknown") : version;
        } catch (PackageManager.NameNotFoundException exception) { return tr("Unknown"); }
    }

    // API 33+ uses the gesture callback registered in onCreate; older versions use this method.
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed() { handleBackNavigation(); }

    private void handleBackNavigation() {
        if (openSwipeRow != null && openSwipeRow.isOpen()) {
            openSwipeRow.close();
            return;
        }
        if ("categories".equals(selectedTab) && selectedCategoryName != null) {
            selectedCategoryName = null;
            refreshCategoryDetail();
            return;
        }
        if (!navigationHistory.isEmpty()) {
            String previous = navigationHistory.remove(navigationHistory.size() - 1);
            restoringNavigation = true;
            selectTab(previous);
            restoringNavigation = false;
        } else if (!"main".equals(selectedTab)) {
            restoringNavigation = true;
            selectTab("main");
            restoringNavigation = false;
        } else finish();
    }

    private void beginExportBackup() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "Pocket-Ledger-Backup.json");
        try { externalPickerOpen = true; startActivityForResult(intent, REQUEST_EXPORT); }
        catch (Exception exception) {
            externalPickerOpen = false;
            Toast.makeText(this, tr("Could not open the save location."), Toast.LENGTH_LONG).show();
        }
    }

    private void beginImportBackup() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        try { externalPickerOpen = true; startActivityForResult(intent, REQUEST_IMPORT); }
        catch (Exception exception) {
            externalPickerOpen = false;
            Toast.makeText(this, tr("Could not open the file picker."), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_EXPORT || requestCode == REQUEST_IMPORT) externalPickerOpen = false;
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQUEST_EXPORT) exportBackup(uri);
        else if (requestCode == REQUEST_IMPORT) readBackup(uri);
    }

    private void exportBackup(Uri uri) {
        try {
            JSONObject backup = new JSONObject();
            backup.put("format", "pocket-ledger-backup");
            backup.put("version", 1);
            backup.put("exportedAt", new Date().toString());
            JSONObject data = new JSONObject();
            data.put("currency", currencyCode);
            data.put("theme", themeStyle);
            data.put("accentColor", String.format(Locale.US, "#%06X", 0xFFFFFF & accentColor));
            data.put("logoStyle", logoStyle);
            data.put("language", languageCode);
            data.put("selectedMonth", selectedMonth.getTimeInMillis());
            JSONArray categoryArray = new JSONArray();
            for (CategoryRecord category : categories) {
                JSONObject item = new JSONObject();
                item.put("id", category.id); item.put("name", category.name); item.put("type", category.type);
                item.put("color", String.format(Locale.US, "#%06X", 0xFFFFFF & category.color));
                item.put("isDefault", category.isDefault); categoryArray.put(item);
            }
            data.put("categories", categoryArray);
            JSONArray transactionArray = new JSONArray();
            for (TransactionRecord transaction : transactions) {
                Calendar date = Calendar.getInstance(); date.setTimeInMillis(transaction.timestamp);
                JSONObject item = new JSONObject();
                item.put("id", transaction.id); item.put("type", transaction.type);
                item.put("amount", transaction.amount);
                item.put("date", String.format(Locale.US, "%04d-%02d-%02d", date.get(Calendar.YEAR),
                        date.get(Calendar.MONTH) + 1, date.get(Calendar.DAY_OF_MONTH)));
                item.put("time", String.format(Locale.US, "%02d:%02d", date.get(Calendar.HOUR_OF_DAY), date.get(Calendar.MINUTE)));
                item.put("categoryId", transaction.categoryId.isEmpty() ? JSONObject.NULL : transaction.categoryId);
                item.put("customCategory", transaction.categoryId.isEmpty() ? transaction.categoryName : "");
                item.put("note", transaction.note); item.put("createdAt", transaction.timestamp);
                transactionArray.put(item);
            }
            data.put("transactions", transactionArray);
            backup.put("data", data);
            OutputStream output = getContentResolver().openOutputStream(uri);
            if (output == null) throw new IOException("Cannot open destination");
            try { output.write(backup.toString(2).getBytes(StandardCharsets.UTF_8)); }
            finally { output.close(); }
            Toast.makeText(this, tr("Backup exported."), Toast.LENGTH_SHORT).show();
        } catch (Exception exception) {
            Toast.makeText(this, tr("Could not export the backup."), Toast.LENGTH_LONG).show();
        }
    }

    private void readBackup(Uri uri) {
        try {
            InputStream input = getContentResolver().openInputStream(uri);
            if (input == null) throw new IOException("Cannot open backup");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            try { while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count); }
            finally { input.close(); }
            JSONObject backup = validateBackup(new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8)));
            new LedgerDialogBuilder().setTitle(tr("Import this backup?"))
                    .setMessage(tr("Importing will replace all transactions, categories, currency, and appearance settings saved on this device. This cannot be undone.")
                            + (backupNeedsClose(backup) ? "\n\n" + tr("Pocket Ledger will close after importing. Open the app again to use the imported settings.") : ""))
                    .setNegativeButton(tr("Cancel"), null)
                    .setPositiveButton(tr(backupNeedsClose(backup) ? "Import and close" : "Import backup"),
                            (dialog, which) -> applyBackup(backup)).show();
        } catch (Exception exception) {
            Toast.makeText(this, tr("Could not import this file. Choose a Pocket Ledger backup JSON file."), Toast.LENGTH_LONG).show();
        }
    }

    private JSONObject validateBackup(JSONObject backup) throws JSONException {
        boolean webFormat = "pocket-ledger-backup".equals(backup.optString("format"))
                && backup.optInt("version", 0) == 1;
        boolean nativeFormat = "pocket-ledger".equals(backup.optString("app"))
                && backup.optInt("formatVersion", 0) == 1;
        if (!webFormat && !nativeFormat) throw new JSONException("Unsupported backup format");
        JSONObject webData = webFormat ? backup.optJSONObject("data") : null;
        if (webFormat && webData == null) throw new JSONException("Invalid backup data");
        JSONArray transactionInput = webFormat ? webData.optJSONArray("transactions") : backup.optJSONArray("transactions");
        JSONArray categoryInput = webFormat ? webData.optJSONArray("categories") : backup.optJSONArray("categories");
        if (transactionInput == null || categoryInput == null || transactionInput.length() > 100000
                || categoryInput.length() > 1000) throw new JSONException("Invalid backup data");
        JSONArray validTransactions = new JSONArray();
        HashSet<String> transactionIds = new HashSet<>();
        for (int index = 0; index < transactionInput.length(); index++) {
            JSONObject source = transactionInput.optJSONObject(index);
            if (source == null) throw new JSONException("Invalid transaction");
            String type = source.optString("type", "");
            double amount = source.optDouble("amount", Double.NaN);
            String id = source.optString("id", "");
            long timestamp = source.optLong("timestamp", 0);
            String date = source.optString("date", "");
            String time = source.optString("time", "");
            if (webFormat) {
                if (!date.matches("\\d{4}-\\d{2}-\\d{2}") || !time.matches("(?:[01]\\d|2[0-3]):[0-5]\\d")) {
                    throw new JSONException("Invalid transaction date or time");
                }
                String[] dateParts = date.split("-");
                int year = Integer.parseInt(dateParts[0]);
                int month = Integer.parseInt(dateParts[1]);
                int day = Integer.parseInt(dateParts[2]);
                String[] timeParts = time.split(":");
                Calendar timestampDate = Calendar.getInstance();
                timestampDate.clear();
                timestampDate.set(year, month - 1, day, Integer.parseInt(timeParts[0]), Integer.parseInt(timeParts[1]));
                if (timestampDate.get(Calendar.YEAR) != year || timestampDate.get(Calendar.MONTH) != month - 1
                        || timestampDate.get(Calendar.DAY_OF_MONTH) != day) throw new JSONException("Invalid date");
                timestamp = timestampDate.getTimeInMillis();
            }
            if (!isValidType(type) || Double.isNaN(amount) || Double.isInfinite(amount) || amount <= 0 || timestamp <= 0) {
                throw new JSONException("Invalid transaction");
            }
            if (id.isEmpty() || !transactionIds.add(id)) throw new JSONException("Invalid or duplicate transaction id");
            JSONObject transaction = new JSONObject();
            transaction.put("id", id);
            transaction.put("type", type); transaction.put("amount", amount); transaction.put("timestamp", timestamp);
            transaction.put("note", source.optString("note", ""));
            String categoryId = source.isNull("categoryId") ? "" : source.optString("categoryId", "");
            String categoryName = source.optString("categoryName", "");
            if (webFormat && categoryName.isEmpty()) categoryName = source.optString("customCategory", "");
            if (categoryName.isEmpty()) categoryName = "Other";
            transaction.put("categoryId", categoryId.isEmpty() ? (webFormat ? "" : type + "-other") : categoryId);
            transaction.put("categoryName", categoryName);
            validTransactions.put(transaction);
        }
        JSONArray validCategories = new JSONArray();
        HashSet<String> categoryIds = new HashSet<>();
        for (int index = 0; index < categoryInput.length(); index++) {
            JSONObject source = categoryInput.optJSONObject(index);
            if (source == null) throw new JSONException("Invalid category");
            String id = source.optString("id", "").trim();
            String name = source.optString("name", "").trim();
            String type = source.optString("type", "");
            if (id.isEmpty() || name.isEmpty() || !isValidType(type)) throw new JSONException("Invalid category");
            if (!categoryIds.add(id)) throw new JSONException("Duplicate category id");
            int color;
            try {
                color = source.opt("color") instanceof String
                        ? Color.parseColor(source.optString("color"))
                        : source.optInt("color", CATEGORY_COLORS[index % CATEGORY_COLORS.length]);
            } catch (IllegalArgumentException exception) { throw new JSONException("Invalid category color"); }
            JSONObject category = new JSONObject();
            category.put("id", id); category.put("name", name); category.put("type", type);
            category.put("color", color);
            category.put("isDefault", source.optBoolean("isDefault", false));
            validCategories.put(category);
        }
        for (int index = 0; index < validTransactions.length(); index++) {
            JSONObject transaction = validTransactions.getJSONObject(index);
            String categoryId = transaction.optString("categoryId", "");
            if (categoryId.isEmpty()) continue;
            boolean found = false;
            for (int categoryIndex = 0; categoryIndex < validCategories.length(); categoryIndex++) {
                JSONObject category = validCategories.getJSONObject(categoryIndex);
                if (categoryId.equals(category.optString("id"))) {
                    if (!transaction.optString("type").equals(category.optString("type"))) {
                        throw new JSONException("Transaction category type does not match");
                    }
                    transaction.put("categoryName", category.optString("name"));
                    found = true;
                    break;
                }
            }
            if (!found) throw new JSONException("Transaction category is missing");
        }
        JSONObject sourceSettings = webFormat ? webData : backup.optJSONObject("settings");
        JSONObject settings = sourceSettings == null ? new JSONObject() : sourceSettings;
        String currency = settings.optString("currency", "EUR");
        String theme = settings.optString("theme", "light");
        if ("dark".equals(theme)) theme = "green";
        String logo = settings.optString(webFormat ? "logoStyle" : "logo", "light");
        if ("dark".equals(logo)) logo = "green";
        String accent = settings.optString(webFormat ? "accentColor" : "accent", "#177E68");
        String language = settings.optString("language", "en");
        if (!accent.matches("#[0-9a-fA-F]{6}")) throw new JSONException("Invalid accent");
        if (!isSupportedCurrency(currency) || !isThemeStyle(theme) || !isLogoStyle(logo)) throw new JSONException("Invalid settings");
        if (!isSupportedLanguage(language)) language = "en";
        JSONObject result = new JSONObject();
        result.put("transactions", validTransactions); result.put("categories", validCategories);
        JSONObject cleanSettings = new JSONObject();
        cleanSettings.put("currency", currency); cleanSettings.put("theme", theme); cleanSettings.put("logo", logo);
        cleanSettings.put("accent", accent); cleanSettings.put("selectedMonth", settings.optLong("selectedMonth", -1));
        cleanSettings.put("language", language);
        result.put("settings", cleanSettings);
        return result;
    }

    private boolean backupNeedsClose(JSONObject backup) {
        JSONObject settings = backup.optJSONObject("settings");
        return settings != null && (!themeStyle.equals(settings.optString("theme", "light"))
                || !logoStyle.equals(settings.optString("logo", "light")));
    }

    private void applyBackup(JSONObject backup) {
        JSONObject settings = backup.optJSONObject("settings");
        boolean needsClose = backupNeedsClose(backup);
        SharedPreferences.Editor changes = preferences.edit()
                .putString(TRANSACTIONS_KEY, backup.optJSONArray("transactions").toString())
                .putString(CATEGORIES_KEY, backup.optJSONArray("categories").toString())
                .putString(CURRENCY_KEY, settings.optString("currency", "EUR"))
                .putString(THEME_KEY, settings.optString("theme", "light"))
                .putString(LOGO_KEY, settings.optString("logo", "light"))
                .putString(LANGUAGE_KEY, settings.optString("language", "en"))
                .putString(ACCENT_KEY, settings.optString("accent", "#177E68"))
                .putLong(SELECTED_MONTH_KEY, settings.optLong("selectedMonth", firstDayOfCurrentMonth().getTimeInMillis()));
        if (!changes.commit()) {
            Toast.makeText(this, tr("Could not save the imported data. There may not be enough storage on this device."), Toast.LENGTH_LONG).show();
            return;
        }
        selectedCategoryName = null;
        if (needsClose) {
            closeForSettingsChange();
            return;
        }
        applySavedSettings();
        selectedMonth = loadSelectedMonth();
        categories = loadCategories();
        transactions = loadTransactionsOrMigrate();
        Toast.makeText(this, tr("Backup imported."), Toast.LENGTH_SHORT).show();
        rebuildScreen();
    }

    private void confirmDeleteAllData() {
        new LedgerDialogBuilder().setTitle(tr("Delete all activity?"))
                .setMessage(tr("This will erase all income and spending entries and remove categories you created. Default categories, currency, and appearance will stay. This cannot be undone."))
                .setNegativeButton(tr("Cancel"), null)
                .setPositiveButton("Continue", (dialog, which) -> showTypedDeleteConfirmation()).show();
    }

    private void showTypedDeleteConfirmation() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), 0, dp(24), 0);
        TextView instruction = bodyText(tr("Type ") + "DELETE" + tr(" to confirm"), MUTED, 14);
        content.addView(instruction);
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("DELETE");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(-1, -2);
        inputParams.topMargin = dp(8);
        content.addView(input, inputParams);
        LedgerDialog dialog = new LedgerDialogBuilder().setDestructive(true).setTitle(tr("PLEASE CONFIRM"))
                .setView(content).setNegativeButton(tr("Cancel"), null).setPositiveButton(tr("Delete all activity"), null).create();
        dialog.setOnShowListener(ignored -> {
            Button confirm = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            confirm.setEnabled(false); confirm.setTextColor(DESTRUCTIVE_TEXT); confirm.setAlpha(.45f);
            input.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    boolean enabled = "DELETE".equals(s.toString().trim());
                    confirm.setEnabled(enabled); confirm.setAlpha(enabled ? 1f : .45f);
                }
                @Override public void afterTextChanged(Editable s) { }
            });
            confirm.setOnClickListener(view -> { dialog.dismiss(); deleteAllActivity(); });
        });
        dialog.show();
    }

    private void deleteAllActivity() {
        transactions.clear();
        selectedCategoryName = null;
        ArrayList<CategoryRecord> defaults = new ArrayList<>();
        for (CategoryRecord category : categories) if (category.isDefault) defaults.add(category);
        categories = defaults;
        selectedMonth = firstDayOfCurrentMonth();
        SharedPreferences.Editor editor = preferences.edit()
                .putString(TRANSACTIONS_KEY, "[]")
                .putLong(SELECTED_MONTH_KEY, selectedMonth.getTimeInMillis());
        for (String key : preferences.getAll().keySet()) {
            if (key.startsWith("spending_")) editor.remove(key);
        }
        editor.apply();
        persistCategories();
        rebuildScreen();
        selectTab("settings");
        Toast.makeText(this, tr("Activity and custom categories deleted"), Toast.LENGTH_SHORT).show();
    }

    private void configureSystemBars() {
        Window window = getWindow();
        window.setStatusBarColor(PAPER);
        window.setNavigationBarColor(PAPER);
        boolean light = "light".equals(themeStyle);
        int flags = light ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR : 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && light) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        window.getDecorView().setSystemUiVisibility(flags);
    }

    private View createScreen() {
        tabPages.clear(); tabButtons.clear(); monthViews.clear();
        monthView = null;
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(PAPER);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setGravity(Gravity.CENTER);
        header.setPadding(dp(16), dp(8), dp(16), dp(4));
        shell.addView(header, new LinearLayout.LayoutParams(-1, -2));
        header.setMinimumHeight(dp(82));
        brandLogo = new ImageView(this);
        brandLogo.setImageResource(R.drawable.ic_logo_wallet);
        brandLogo.setColorFilter(UiColors.textOnSurfaces(accentColor,
                logoBackgroundColor(logoStyle), logoBackgroundColor(logoStyle)));
        brandLogo.setBackground(rounded(logoBackgroundColor(logoStyle), dp(13)));
        brandLogo.setPadding(dp(8), dp(8), dp(8), dp(8));
        brandLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        header.addView(brandLogo, new LinearLayout.LayoutParams(dp(40), dp(40)));
        TextView brand = new TextView(this);
        brand.setText(R.string.app_name); brand.setTextColor(GREEN); brand.setTextSize(13);
        brand.setTypeface(Typeface.DEFAULT, Typeface.BOLD); brand.setGravity(Gravity.CENTER);
        brand.setPadding(0, dp(5), 0, dp(3));
        header.addView(brand, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout pageHost = new FrameLayout(this);
        shell.addView(pageHost, new LinearLayout.LayoutParams(-1, 0, 1f));

        ScrollView mainPage = newPageScroll();
        LinearLayout mainContent = pageContent(mainPage);
        mainContent.addView(createMonthHeading(tr("Spent this month")));
        mainContent.addView(createSummaryCard());
        Button mainAdd = primaryButton(tr("Add transaction"));
        mainAdd.setOnClickListener(view -> showTransactionDialog(null));
        addPageGap(mainContent, mainAdd, 12);
        LinearLayout recentHeading = sectionHeading(tr("Recent spending"), tr("All activity"), () -> selectTab("transactions"));
        addPageGap(mainContent, recentHeading, 14);
        recentTransactionList = new LinearLayout(this);
        recentTransactionList.setOrientation(LinearLayout.VERTICAL);
        mainContent.addView(recentTransactionList);
        pageHost.addView(mainPage);
        tabPages.add(mainPage);

        ScrollView transactionsPage = newPageScroll();
        LinearLayout transactionContent = pageContent(transactionsPage);
        transactionContent.addView(createMonthHeading(tr("Transactions")));
        transactionContent.addView(createMonthlyComparison());
        Button transactionAdd = primaryButton(tr("Add transaction"));
        transactionAdd.setOnClickListener(view -> showTransactionDialog(null));
        addPageGap(transactionContent, transactionAdd, 14);
        transactionList = new LinearLayout(this);
        transactionList.setOrientation(LinearLayout.VERTICAL);
        addPageGap(transactionContent, transactionList, 12);
        pageHost.addView(transactionsPage);
        tabPages.add(transactionsPage);

        ScrollView categoriesPage = newPageScroll();
        LinearLayout categoriesContent = pageContent(categoriesPage);
        categoriesContent.addView(createMonthHeading(tr("Categories")));
        categoryOverviewContent = new LinearLayout(this);
        categoryOverviewContent.setOrientation(LinearLayout.VERTICAL);
        monthlyCategoryChart = new CategoryChartView(this, new LinkedHashMap<>());
        LinearLayout chartCard = settingsCard();
        chartCard.setGravity(Gravity.CENTER_HORIZONTAL);
        chartCard.addView(monthlyCategoryChart, new LinearLayout.LayoutParams(-1, dp(190)));
        categoryMonthlyTotalView = bodyText(tr("Spent this month") + " · " + formatCurrency(0), INK, 14);
        categoryMonthlyTotalView.setGravity(Gravity.CENTER);
        categoryMonthlyTotalView.setPadding(0, dp(8), 0, dp(4));
        chartCard.addView(categoryMonthlyTotalView, new LinearLayout.LayoutParams(-1, -2));
        addPageGap(categoryOverviewContent, chartCard, 12);
        categoryBreakdownList = new LinearLayout(this);
        categoryBreakdownList.setOrientation(LinearLayout.VERTICAL);
        addPageGap(categoryOverviewContent, sectionTitle(tr("MONTHLY BREAKDOWN")), 12);
        categoryOverviewContent.addView(categoryBreakdownList);
        Button allTimeButton = secondaryButton(tr("View category totals"));
        allTimeButton.setOnClickListener(view -> showCategoryTotals(true));
        addPageGap(categoryOverviewContent, allTimeButton, 12);
        Button manageButton = secondaryButton(tr("Manage categories"));
        manageButton.setOnClickListener(view -> showManageCategories());
        addPageGap(categoryOverviewContent, manageButton, 8);
        categoriesContent.addView(categoryOverviewContent);
        categoryDetailPanel = new LinearLayout(this);
        categoryDetailPanel.setOrientation(LinearLayout.VERTICAL);
        categoryDetailPanel.setVisibility(View.GONE);
        categoriesContent.addView(categoryDetailPanel);
        pageHost.addView(categoriesPage);
        tabPages.add(categoriesPage);

        ScrollView settingsPage = newPageScroll();
        LinearLayout settingsContent = pageContent(settingsPage);
        TextView settingsTitle = bodyText(tr("Settings"), INK, 22);
        settingsTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams settingsTitleParams = new LinearLayout.LayoutParams(-1, dp(42));
        settingsTitleParams.topMargin = dp(8);
        settingsContent.addView(settingsTitle, settingsTitleParams);
        TextView appVersion = bodyText(tr("Version") + " " + currentAppVersion(), MUTED, 12);
        appVersion.setPadding(0, 0, 0, dp(6));
        settingsContent.addView(appVersion, new LinearLayout.LayoutParams(-1, -2));
        buildSettingsPage(settingsContent);
        pageHost.addView(settingsPage);
        tabPages.add(settingsPage);

        LinearLayout navigation = new LinearLayout(this);
        navigation.setGravity(Gravity.CENTER);
        navigation.setPadding(dp(12), dp(7), dp(12), dp(8));
        navigation.setBackgroundColor(WHITE);
        shell.addView(navigation, new LinearLayout.LayoutParams(-1, -2));
        String[] ids = {"main", "transactions", "categories", "settings"};
        String[] labels = {tr("Main"), tr("Transactions"), tr("Categories"), tr("Settings")};
        int[] tabIcons = {R.drawable.ic_tab_home, R.drawable.ic_tab_transactions,
                R.drawable.ic_tab_categories, R.drawable.ic_tab_settings};
        for (int index = 0; index < ids.length; index++) {
            final String id = ids[index];
            LinearLayout bubble = new LinearLayout(this);
            bubble.setOrientation(LinearLayout.VERTICAL);
            bubble.setGravity(Gravity.CENTER);
            bubble.setPadding(dp(4), dp(4), dp(4), dp(4));
            ImageView icon = new ImageView(this);
            icon.setImageResource(tabIcons[index]);
            icon.setTag("tab_icon");
            bubble.addView(icon, new LinearLayout.LayoutParams(dp(20), dp(20)));
            TextView label = bodyText(labels[index], MUTED, 10);
            label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            label.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, -2);
            label.setMaxLines(2);
            labelParams.topMargin = dp(1);
            bubble.addView(label, labelParams);
            LinearLayout.LayoutParams bubbleParams = new LinearLayout.LayoutParams(0, -2, 1f);
            bubble.setMinimumHeight(dp(52));
            bubbleParams.setMargins(dp(3), 0, dp(3), 0);
            navigation.addView(bubble, bubbleParams);
            bubble.setOnClickListener(view -> selectTab(id));
            tabButtons.add(bubble);
        }
        selectTab(selectedTab);
        return shell;
    }

    private ScrollView newPageScroll() {
        ScrollView page = new ScrollView(this);
        page.setFillViewport(true);
        page.setBackgroundColor(PAPER);
        return page;
    }

    private LinearLayout pageContent(ScrollView page) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(8), dp(16), dp(24));
        page.addView(content, new ScrollView.LayoutParams(-1, -2));
        return content;
    }

    private void addPageGap(LinearLayout parent, View child, int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        if (topMargin > 0) params.topMargin = dp(topMargin);
        parent.addView(child, params);
    }

private LinearLayout createMonthHeading(String titleText) {
        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setMinimumHeight(dp(58));
        heading.setPadding(0, dp(8), 0, dp(8));
        TextView title = bodyText(titleText, INK, 16);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1f);
        titleParams.rightMargin = dp(6);
        heading.addView(title, titleParams);
        Button previous = monthArrow("‹", tr("Previous month"));
        previous.setOnClickListener(view -> stepMonth(-1));
        heading.addView(previous, new LinearLayout.LayoutParams(dp(30), dp(40)));
        TextView monthLabel = bodyText("", MUTED, 11);
        monthLabel.setGravity(Gravity.CENTER);
        monthLabel.setMaxLines(2);
        heading.addView(monthLabel, new LinearLayout.LayoutParams(dp(104), -2));
        monthViews.add(monthLabel);
        if (monthView == null) monthView = monthLabel;
        Button next = monthArrow("›", tr("Next month"));
        next.setOnClickListener(view -> stepMonth(1));
        heading.addView(next, new LinearLayout.LayoutParams(dp(30), dp(40)));
        return heading;
    }

    private LinearLayout createSummaryCard() {
        LinearLayout card = settingsCard();
        TextView caption = bodyText(tr("TOTAL SPENDING"), MUTED, 12);
        caption.setLetterSpacing(0.08f);
        card.addView(caption);
        spendingAmountView = bodyText("", INK, 36);
        spendingAmountView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        fitAmountText(spendingAmountView, 36);
        LinearLayout.LayoutParams amountParams = new LinearLayout.LayoutParams(-1, -2);
        amountParams.topMargin = dp(6);
        card.addView(spendingAmountView, amountParams);
        View divider = new View(this);
        divider.setBackgroundColor(themeStyle.equals("light") ? Color.rgb(232, 235, 231) : Color.rgb(55, 60, 61));
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, dp(1));
        dividerParams.topMargin = dp(14); dividerParams.bottomMargin = dp(12);
        card.addView(divider, dividerParams);
        LinearLayout incomeLine = new LinearLayout(this);
        incomeLine.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(incomeLine, new LinearLayout.LayoutParams(-1, -2));
        TextView captionIncome = bodyText(tr("Received"), MUTED, 14);
        incomeLine.addView(captionIncome, new LinearLayout.LayoutParams(0, -2, 1f));
        incomeAmountView = bodyText("", GREEN, 16);
        incomeAmountView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        incomeLine.addView(incomeAmountView);
        return card;
    }

    private LinearLayout createMonthlyComparison() {
        LinearLayout card = settingsCard();
        TextView eyebrow = bodyText(tr("MONTHLY COMPARISON"), MUTED, 10);
        eyebrow.setLetterSpacing(.08f);
        card.addView(eyebrow);
        TextView title = bodyText(tr("In and out"), INK, 17);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = dp(3);
        card.addView(title, titleParams);

        LinearLayout receivedLine = new LinearLayout(this);
        receivedLine.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams receivedParams = new LinearLayout.LayoutParams(-1, dp(28));
        receivedParams.topMargin = dp(11);
        card.addView(receivedLine, receivedParams);
        receivedLine.addView(bodyText(tr("Received"), MUTED, 13), new LinearLayout.LayoutParams(0, -2, 1f));
        comparisonIncomeView = bodyText(formatCurrency(0), GREEN, 13);
        comparisonIncomeView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        receivedLine.addView(comparisonIncomeView);
        incomeComparisonBar = new ComparisonBarView(this, GREEN);
        card.addView(incomeComparisonBar, new LinearLayout.LayoutParams(-1, dp(8)));

        LinearLayout spendingLine = new LinearLayout(this);
        spendingLine.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams spendingParams = new LinearLayout.LayoutParams(-1, dp(28));
        spendingParams.topMargin = dp(7);
        card.addView(spendingLine, spendingParams);
        spendingLine.addView(bodyText(tr("Spent"), MUTED, 13), new LinearLayout.LayoutParams(0, -2, 1f));
        comparisonSpendingView = bodyText(formatCurrency(0), RED, 13);
        comparisonSpendingView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        spendingLine.addView(comparisonSpendingView);
        spendingComparisonBar = new ComparisonBarView(this, RED);
        card.addView(spendingComparisonBar, new LinearLayout.LayoutParams(-1, dp(8)));

        LinearLayout netLine = new LinearLayout(this);
        netLine.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams netParams = new LinearLayout.LayoutParams(-1, -2);
        netParams.topMargin = dp(13);
        card.addView(netLine, netParams);
        netLine.addView(bodyText(tr("NET THIS MONTH"), MUTED, 13), new LinearLayout.LayoutParams(0, -2, 1f));
        netAmountView = bodyText(formatCurrency(0), GREEN, 15);
        netAmountView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        netLine.addView(netAmountView);
        netMessageView = bodyText(tr("Income minus spending"), MUTED, 11);
        netMessageView.setGravity(Gravity.RIGHT);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(-1, -2);
        messageParams.topMargin = dp(2);
        card.addView(netMessageView, messageParams);
        return card;
    }

    private LinearLayout sectionHeading(String title, String action, Runnable onAction) {
        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = bodyText(title, INK, 17);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.addView(label, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView button = bodyText(action + "  →", SOFT_CONTROL_TEXT, 12);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(9), dp(7), dp(9), dp(7));
        button.setBackground(rounded(SOFT_CONTROL, dp(12)));
        button.setContentDescription(action);
        button.setOnClickListener(view -> onAction.run());
        heading.addView(button);
        return heading;
    }

    private TextView sectionTitle(String title) {
        TextView label = bodyText(title, INK, 17);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return label;
    }

    private int logoBackgroundColor(String style) {
        if ("green".equals(style)) return Color.rgb(19, 34, 28);
        if ("gray".equals(style)) return Color.rgb(39, 42, 45);
        if ("amoled".equals(style)) return Color.rgb(8, 8, 8);
        return Color.WHITE;
    }

    private LinearLayout settingsCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(15), dp(16), dp(15));
        card.setBackground(rounded(WHITE, dp(18)));
        return card;
    }

    private TextView bodyText(String text, int color, int size) {
        TextView view = new TextView(this);
        view.setText(text); view.setTextColor(color); view.setTextSize(size);
        view.setIncludeFontPadding(false);
        return view;
    }

    private Button baseButton() {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setMinWidth(0); button.setMinimumWidth(0);
        button.setMinHeight(0); button.setMinimumHeight(0);
        button.setIncludeFontPadding(false);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setTextSize(14);
        button.setStateListAnimator(null);
        button.setElevation(0);
        return button;
    }

    private Button primaryButton(String text) {
        Button button = baseButton();
        button.setText(text); button.setTextColor(CONTROL_TEXT); button.setTextSize(16);
        button.setAllCaps(false); button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(rounded(CONTROL, dp(17)));
        button.setMinimumHeight(dp(52));
        button.setPadding(dp(14), dp(12), dp(14), dp(12));
        return button;
    }

    private Button secondaryButton(String text) {
        Button button = baseButton();
        button.setText(text); button.setTextColor(SOFT_CONTROL_TEXT); button.setTextSize(14);
        button.setAllCaps(false); button.setBackground(rounded(SOFT_CONTROL, dp(15)));
        button.setMinimumHeight(dp(46));
        return button;
    }

    private void selectTab(String tab) {
        if (!tab.equals(selectedTab) && !restoringNavigation) navigationHistory.add(selectedTab);
        if (openSwipeRow != null) openSwipeRow.close();
        selectedTab = tab;
        String[] ids = {"main", "transactions", "categories", "settings"};
        for (int index = 0; index < ids.length; index++) {
            boolean selected = ids[index].equals(tab);
            tabPages.get(index).setVisibility(selected ? View.VISIBLE : View.GONE);
            LinearLayout bubble = tabButtons.get(index);
            bubble.setBackground(rounded(selected ? CONTROL : PAPER, dp(18)));
            bubble.setScaleX(selected ? 1f : .96f);
            bubble.setScaleY(selected ? 1f : .96f);
            ImageView icon = (ImageView) bubble.getChildAt(0);
            icon.setColorFilter(selected ? CONTROL_TEXT : MUTED);
            ((TextView) bubble.getChildAt(1)).setTextColor(selected ? CONTROL_TEXT : MUTED);
        }
    }

    private void buildSettingsPage(LinearLayout parent) {
        LinearLayout updatesCard = settingsCard();
        addSettingHeading(updatesCard, tr("App updates"), tr("Get the latest version from GitHub."), null, R.drawable.ic_settings_backup);
        updateStatusView = bodyText(tr(appUpdater.status()), MUTED, 12);
        LinearLayout.LayoutParams updateStatusParams = new LinearLayout.LayoutParams(-1, -2);
        updateStatusParams.topMargin = dp(10);
        updatesCard.addView(updateStatusView, updateStatusParams);
        checkUpdatesButton = secondaryButton(tr("Check for updates"));
        LinearLayout.LayoutParams checkUpdateParams = new LinearLayout.LayoutParams(-1, dp(48));
        checkUpdateParams.topMargin = dp(10);
        updatesCard.addView(checkUpdatesButton, checkUpdateParams);
        checkUpdatesButton.setOnClickListener(view -> appUpdater.check(true));
        refreshUpdateStatus();
        addPageGap(parent, updatesCard, 8);

        LinearLayout languageCard = settingsCard();
        addSettingHeading(languageCard, tr("Language"), tr("Choose the language used in the app."), "文", 0);
        TextView languageFormatNote = bodyText(tr("The app language also sets the date, time, and number formats."), MUTED, 11);
        LinearLayout.LayoutParams languageNoteParams = new LinearLayout.LayoutParams(-1, -2);
        languageNoteParams.topMargin = dp(3);
        languageCard.addView(languageFormatNote, languageNoteParams);
        Button languageButton = secondaryButton(languageLabel(languageCode) + "  ▾");
        languageButton.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        languageButton.setPadding(dp(14), 0, dp(14), 0);
        LinearLayout.LayoutParams languageButtonParams = new LinearLayout.LayoutParams(-1, dp(46));
        languageButtonParams.topMargin = dp(9);
        languageCard.addView(languageButton, languageButtonParams);
        languageButton.setOnClickListener(view -> showLanguagePicker());
        addPageGap(parent, languageCard, 10);

        LinearLayout currencyCard = settingsCard();
        addSettingHeading(currencyCard, tr("Currency"), tr("Choose how amounts appear throughout the app."), "¤", 0);
        Button currencyButton = secondaryButton(currencyLabel(currencyCode) + "  ▾");
        currencyButton.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        currencyButton.setPadding(dp(14), 0, dp(14), 0);
        LinearLayout.LayoutParams currencyButtonParams = new LinearLayout.LayoutParams(-1, dp(48));
        currencyButtonParams.topMargin = dp(11);
        currencyCard.addView(currencyButton, currencyButtonParams);
        currencyButton.setOnClickListener(view -> showCurrencyPicker());
        addPageGap(parent, currencyCard, 8);

        LinearLayout appearanceCard = settingsCard();
        addSettingHeading(appearanceCard, tr("Appearance"), tr("Pick a light or dark look for Pocket Ledger."), null, R.drawable.ic_settings_theme);
        String[][] themes = {{"light", tr("Light")}, {"green", tr("Green dark")}, {"gray", tr("Gray dark")}, {"amoled", "AMOLED"}};
        LinearLayout themeOptions = new LinearLayout(this);
        themeOptions.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams themeParams = new LinearLayout.LayoutParams(-1, -2);
        themeParams.topMargin = dp(8);
        appearanceCard.addView(themeOptions, themeParams);
        for (String[] option : themes) {
            Button row = settingOptionButton(option[1], option[0].equals(themeStyle));
            LinearLayout.LayoutParams optionParams = new LinearLayout.LayoutParams(-1, dp(48));
            optionParams.bottomMargin = dp(4);
            themeOptions.addView(row, optionParams);
            row.setOnClickListener(view -> {
                if (option[0].equals(themeStyle)) return;
                confirmSettingsClose(() -> saveSettingsAndClose(
                        preferences.edit().putString(THEME_KEY, option[0])));
            });
        }
        Button resetAppearance = secondaryButton(tr("Reset appearance"));
        LinearLayout.LayoutParams resetAppearanceParams = new LinearLayout.LayoutParams(-1, dp(44));
        resetAppearanceParams.topMargin = dp(9);
        appearanceCard.addView(resetAppearance, resetAppearanceParams);
        resetAppearance.setOnClickListener(view -> resetAppearance());
        addPageGap(parent, appearanceCard, 10);

        LinearLayout accentCard = settingsCard();
        addSettingHeading(accentCard, tr("Accent color"), tr("Choose the color used for buttons and selected tabs."), "●", 0);
        LinearLayout accentActions = new LinearLayout(this);
        accentActions.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams accentActionsParams = new LinearLayout.LayoutParams(-1, dp(48));
        accentActionsParams.topMargin = dp(10);
        accentCard.addView(accentActions, accentActionsParams);
        View accentPreview = new View(this);
        accentPreview.setBackground(rounded(accentColor, dp(14)));
        accentActions.addView(accentPreview, new LinearLayout.LayoutParams(dp(28), dp(28)));
        TextView accentHex = bodyText(String.format(Locale.US, "#%06X", 0xFFFFFF & accentColor), INK, 13);
        LinearLayout.LayoutParams accentHexParams = new LinearLayout.LayoutParams(0, -2, 1f);
        accentHexParams.leftMargin = dp(10);
        accentActions.addView(accentHex, accentHexParams);
        Button chooseAccent = secondaryButton(tr("Choose a color"));
        accentActions.addView(chooseAccent, new LinearLayout.LayoutParams(dp(126), dp(42)));
        chooseAccent.setOnClickListener(view -> showAccentPicker());
        Button resetAccent = secondaryButton(tr("Reset accent"));
        LinearLayout.LayoutParams resetAccentParams = new LinearLayout.LayoutParams(-1, dp(42));
        resetAccentParams.topMargin = dp(6);
        accentCard.addView(resetAccent, resetAccentParams);
        resetAccent.setOnClickListener(view -> resetAccentColor());
        addPageGap(parent, accentCard, 10);

        LinearLayout logoCard = settingsCard();
        addSettingHeading(logoCard, tr("Logo style"), tr("Choose the background used behind the Pocket Ledger logo."), null, R.drawable.ic_logo_wallet);
        LinearLayout logoOptions = new LinearLayout(this);
        logoOptions.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams logoOptionsParams = new LinearLayout.LayoutParams(-1, dp(92));
        logoOptionsParams.topMargin = dp(7);
        logoCard.addView(logoOptions, logoOptionsParams);
        String[][] logos = {{"light", tr("Light")}, {"green", tr("Green")}, {"gray", tr("Gray")}, {"amoled", "AMOLED"}};
        for (String[] option : logos) {
            LinearLayout choice = new LinearLayout(this);
            choice.setOrientation(LinearLayout.VERTICAL);
            choice.setGravity(Gravity.CENTER);
            choice.setPadding(dp(2), dp(3), dp(2), dp(3));
            choice.setBackground(rounded(option[0].equals(logoStyle) ? CONTROL : WHITE, dp(12)));
            LinearLayout.LayoutParams choiceParams = new LinearLayout.LayoutParams(0, dp(82), 1f);
            choiceParams.setMargins(dp(2), 0, dp(2), 0);
            logoOptions.addView(choice, choiceParams);
            ImageView logo = new ImageView(this);
            int resource = getResources().getIdentifier("ic_launcher_" + option[0], "mipmap", getPackageName());
            if (resource != 0) logo.setImageResource(resource);
            choice.addView(logo, new LinearLayout.LayoutParams(dp(42), dp(42)));
            TextView label = bodyText(option[1], option[0].equals(logoStyle) ? CONTROL_TEXT : MUTED, 10);
            label.setGravity(Gravity.CENTER);
            choice.addView(label, new LinearLayout.LayoutParams(-1, dp(25)));
            choice.setOnClickListener(view -> chooseLogo(option[0]));
        }
        addPageGap(parent, logoCard, 10);

        LinearLayout backupCard = settingsCard();
        addSettingHeading(backupCard, tr("Data backup"), tr("Export your transactions, categories, and settings, or import a backup from another browser or device."), null, R.drawable.ic_settings_backup);
        LinearLayout backupButtons = new LinearLayout(this);
        LinearLayout.LayoutParams backupButtonsParams = new LinearLayout.LayoutParams(-1, dp(46));
        backupButtonsParams.topMargin = dp(10);
        backupCard.addView(backupButtons, backupButtonsParams);
        Button export = secondaryButton(tr("Export data"));
        Button importButton = secondaryButton(tr("Import data"));
        backupButtons.addView(export, new LinearLayout.LayoutParams(0, -1, 1f));
        View backupGap = new View(this);
        backupButtons.addView(backupGap, new LinearLayout.LayoutParams(dp(8), 1));
        backupButtons.addView(importButton, new LinearLayout.LayoutParams(0, -1, 1f));
        export.setOnClickListener(view -> beginExportBackup());
        importButton.setOnClickListener(view -> beginImportBackup());
        TextView localNote = bodyText(tr("Your data is saved on this device. Export a backup to move it to another device."), MUTED, 12);
        LinearLayout.LayoutParams localNoteParams = new LinearLayout.LayoutParams(-1, -2);
        localNoteParams.topMargin = dp(8);
        backupCard.addView(localNote, localNoteParams);
        addPageGap(parent, backupCard, 10);

        LinearLayout supportCard = settingsCard();
        addSettingHeading(supportCard, tr("Support Pocket Ledger"),
                tr("Help support development with an optional donation on Ko-fi."),
                null, R.drawable.ic_settings_support);
        Button donate = secondaryButton(tr("Donate"));
        LinearLayout.LayoutParams donateParams = new LinearLayout.LayoutParams(-1, dp(48));
        donateParams.topMargin = dp(12);
        supportCard.addView(donate, donateParams);
        donate.setOnClickListener(view -> openDonationPage());
        addPageGap(parent, supportCard, 10);

        LinearLayout activityCard = settingsCard();
        LinearLayout dangerHeader = new LinearLayout(this);
        dangerHeader.setGravity(Gravity.CENTER_VERTICAL);
        activityCard.addView(dangerHeader, new LinearLayout.LayoutParams(-1, -2));
        ImageView dangerIcon = new ImageView(this);
        dangerIcon.setImageResource(R.drawable.ic_action_delete);
        dangerIcon.setColorFilter(RED);
        dangerIcon.setBackground(rounded(RED_PALE, dp(11)));
        dangerIcon.setPadding(dp(8), dp(8), dp(8), dp(8));
        LinearLayout.LayoutParams dangerIconParams = new LinearLayout.LayoutParams(dp(34), dp(34));
        dangerIconParams.rightMargin = dp(10);
        dangerHeader.addView(dangerIcon, dangerIconParams);
        LinearLayout dangerCopy = new LinearLayout(this);
        dangerCopy.setOrientation(LinearLayout.VERTICAL);
        dangerHeader.addView(dangerCopy, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView dangerTitle = bodyText(tr("Activity data"), INK, 16);
        dangerTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        dangerCopy.addView(dangerTitle);
        TextView dangerDescription = bodyText(tr("Remove saved entries and categories you created."), MUTED, 12);
        LinearLayout.LayoutParams dangerDescriptionParams = new LinearLayout.LayoutParams(-1, -2);
        dangerDescriptionParams.topMargin = dp(3);
        dangerCopy.addView(dangerDescription, dangerDescriptionParams);
        TextView deleteButton = actionButton("Delete all", RED, RED_PALE, tr("Delete all activity"));
        LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(dp(78), dp(38));
        deleteParams.leftMargin = dp(9);
        LinearLayout dangerActions = new LinearLayout(this);
        dangerActions.setGravity(Gravity.RIGHT);
        LinearLayout.LayoutParams dangerActionsParams = new LinearLayout.LayoutParams(-1, -2);
        dangerActionsParams.topMargin = dp(12);
        activityCard.addView(dangerActions, dangerActionsParams);
        dangerActions.addView(deleteButton, deleteParams);
        deleteButton.setOnClickListener(view -> confirmDeleteAllData());
        addPageGap(parent, activityCard, 10);

        TextView offline = bodyText(tr("Pocket Ledger works offline. Your financial data stays on this device unless you export it."), MUTED, 12);
        offline.setGravity(Gravity.CENTER);
        offline.setPadding(dp(8), dp(12), dp(8), dp(14));
        parent.addView(offline);
    }

    private void openDonationPage() {
        Intent donation = new Intent(Intent.ACTION_VIEW, Uri.parse("https://ko-fi.com/gatofile"));
        donation.addCategory(Intent.CATEGORY_BROWSABLE);
        try {
            startActivity(donation);
        } catch (android.content.ActivityNotFoundException exception) {
            Toast.makeText(this, tr("No browser is available to open the donation page."),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void initializeUpdater() {
        appUpdater = new AppUpdater(this, new AppUpdater.Listener() {
            @Override public void onStatusChanged() { refreshUpdateStatus(); }
            @Override public void onUpdateAvailable(String version, Runnable accept) {
                new LedgerDialogBuilder().setTitle(tr("Update available"))
                        .setMessage(tr("A new version of Pocket Ledger is available.") + "\n\n"
                                + tr("Version") + " " + version + "\n\n" + tr("Download and install the update?"))
                        .setNegativeButton(tr("Later"), null)
                        .setPositiveButton(tr("Update now"), (dialog, which) -> accept.run()).show();
            }
            @Override public void onInstallPermissionNeeded(Runnable openSettings) {
                new LedgerDialogBuilder().setTitle(tr("Allow app updates"))
                        .setMessage(tr("Android needs permission to install updates from Pocket Ledger. Enable it on the next screen, then return to install the update."))
                        .setNegativeButton(tr("Cancel"), null)
                        .setPositiveButton(tr("Open Android settings"), (dialog, which) -> openSettings.run()).show();
            }
            @Override public void onMessage(String title, String message) {
                new LedgerDialogBuilder().setTitle(tr(title)).setMessage(tr(message))
                        .setPositiveButton(tr("Close"), null).show();
            }
        });
    }

    private void refreshUpdateStatus() {
        if (appUpdater == null || updateStatusView == null || checkUpdatesButton == null) return;
        String progress = appUpdater.progress() >= 0 ? " " + appUpdater.progress() + "%" : "";
        updateStatusView.setText(tr(appUpdater.status()) + progress);
        checkUpdatesButton.setEnabled(!appUpdater.isBusy());
        checkUpdatesButton.setAlpha(appUpdater.isBusy() ? .65f : 1f);
    }

    private void addSettingHeading(LinearLayout card, String title, String description, String glyph, int iconResource) {
        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(heading, new LinearLayout.LayoutParams(-1, -2));
        if (glyph != null || iconResource != 0) {
            FrameLayout badge = new FrameLayout(this);
            badge.setBackground(rounded("●".equals(glyph) ? PAPER : SOFT_CONTROL, dp(11)));
            LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(dp(36), dp(36));
            badgeParams.rightMargin = dp(11);
            heading.addView(badge, badgeParams);
            if (iconResource != 0) {
                ImageView icon = new ImageView(this);
                icon.setImageResource(iconResource);
                icon.setColorFilter(SOFT_CONTROL_TEXT);
                FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER);
                badge.addView(icon, iconParams);
            } else {
                TextView symbol = bodyText(glyph, SOFT_CONTROL_TEXT, 17);
                symbol.setGravity(Gravity.CENTER);
                if ("●".equals(glyph)) symbol.setTextColor(accentColor);
                badge.addView(symbol, new FrameLayout.LayoutParams(-1, -1));
            }
        }
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        heading.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView titleView = bodyText(title, INK, 16);
        titleView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        copy.addView(titleView);
        TextView descriptionView = bodyText(description, MUTED, 12);
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(-1, -2);
        descriptionParams.topMargin = dp(3);
        copy.addView(descriptionView, descriptionParams);
    }

    private Button settingOptionButton(String label, boolean selected) {
        Button button = baseButton();
        button.setText((selected ? "✓   " : "○   ") + label);
        button.setTextColor(selected ? CONTROL_TEXT : INK);
        button.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setBackground(rounded(selected ? CONTROL : WHITE, dp(12)));
        return button;
    }

    private void showCurrencyPicker() {
        String[] labels = new String[CURRENCIES.length];
        int checked = 0;
        for (int index = 0; index < CURRENCIES.length; index++) {
            labels[index] = currencyLabel(CURRENCIES[index]);
            if (CURRENCIES[index].equals(currencyCode)) checked = index;
        }
        new LedgerDialogBuilder().setTitle(tr("Currency"))
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    preferences.edit().putString(CURRENCY_KEY, CURRENCIES[which]).apply();
                    dialog.dismiss();
                    applySavedSettings();
                    rebuildScreen();
                }).setNegativeButton("Cancel", null).show();
    }

    private void showLanguagePicker() {
        String[] codes = {"en", "pt", "es", "fr", "de", "it", "ja"};
        String[] labels = new String[codes.length];
        int checked = 0;
        for (int index = 0; index < codes.length; index++) {
            labels[index] = languageLabel(codes[index]);
            if (codes[index].equals(languageCode)) checked = index;
        }
        new LedgerDialogBuilder().setTitle(tr("Choose language"))
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    preferences.edit().putString(LANGUAGE_KEY, codes[which]).apply();
                    dialog.dismiss();
                    applySavedSettings();
                    rebuildScreen();
                }).setNegativeButton(tr("Cancel"), null).show();
    }

    private String languageLabel(String code) {
        switch (code) {
            case "pt": return "Português";
            case "es": return "Español";
            case "fr": return "Français";
            case "de": return "Deutsch";
            case "it": return "Italiano";
            case "ja": return "日本語";
            default: return "English";
        }
    }

    private String currencyLabel(String code) {
        try {
            Currency currency = Currency.getInstance(code);
            return code + "  ·  " + currency.getDisplayName(appLocale());
        } catch (IllegalArgumentException exception) {
            return code;
        }
    }

    private void showAccentPicker() {
        int[] channels = {Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)};
        int[] color = {accentColor};
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(24), dp(4), dp(24), dp(4));
        View preview = new View(this);
        preview.setBackground(rounded(color[0], dp(15)));
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(-1, dp(42));
        previewParams.bottomMargin = dp(8);
        form.addView(preview, previewParams);
        TextView hex = bodyText(String.format(Locale.US, "#%06X", 0xFFFFFF & color[0]), INK, 14);
        hex.setGravity(Gravity.CENTER);
        form.addView(hex, new LinearLayout.LayoutParams(-1, dp(28)));
        String[] channelNames = {"Red", "Green", "Blue"};
        EditText[] hexInputRef = {null};
        SeekBar[] sliderRefs = new SeekBar[3];
        for (int channel = 0; channel < 3; channel++) {
            final int index = channel;
            TextView label = bodyText(channelNames[channel] + "  " + channels[channel], MUTED, 12);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, dp(24));
            labelParams.topMargin = dp(3);
            form.addView(label, labelParams);
            SeekBar slider = new SeekBar(this);
            slider.setProgressTintList(android.content.res.ColorStateList.valueOf(GREEN));
            slider.setThumbTintList(android.content.res.ColorStateList.valueOf(GREEN));
            sliderRefs[channel] = slider;
            slider.setMax(255);
            slider.setProgress(channels[channel]);
            form.addView(slider, new LinearLayout.LayoutParams(-1, dp(32)));
            slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    channels[index] = progress;
                    color[0] = Color.rgb(channels[0], channels[1], channels[2]);
                    label.setText(channelNames[index] + "  " + progress);
                    preview.setBackground(rounded(color[0], dp(15)));
                    hex.setText(String.format(Locale.US, "#%06X", 0xFFFFFF & color[0]));
                    if (hexInputRef[0] != null) {
                        String current = String.format(Locale.US, "#%06X", 0xFFFFFF & color[0]);
                        if (!current.contentEquals(hexInputRef[0].getText())) hexInputRef[0].setText(current);
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) { }
                @Override public void onStopTrackingTouch(SeekBar seekBar) { }
            });
        }
        TextView hexLabel = bodyText("Hex color", MUTED, 12);
        LinearLayout.LayoutParams hexLabelParams = new LinearLayout.LayoutParams(-1, dp(24));
        hexLabelParams.topMargin = dp(6);
        form.addView(hexLabel, hexLabelParams);
        EditText hexInput = new EditText(this);
        hexInput.setSingleLine(true);
        hexInput.setHint("#177E68");
        hexInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        hexInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(7)});
        hexInput.setText(String.format(Locale.US, "#%06X", 0xFFFFFF & color[0]));
        hexInputRef[0] = hexInput;
        form.addView(hexInput, new LinearLayout.LayoutParams(-1, dp(54)));
        hexInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String text = s.toString();
                if (text.matches("#[0-9a-fA-F]{6}")) {
                    color[0] = Color.parseColor(text);
                    channels[0] = Color.red(color[0]); channels[1] = Color.green(color[0]); channels[2] = Color.blue(color[0]);
                    preview.setBackground(rounded(color[0], dp(15)));
                    hex.setText(text.toUpperCase(Locale.US));
                    for (int channel = 0; channel < sliderRefs.length; channel++) {
                        if (sliderRefs[channel].getProgress() != channels[channel]) {
                            sliderRefs[channel].setProgress(channels[channel]);
                        }
                    }
                }
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        LedgerDialog dialog = new LedgerDialogBuilder().setTitle(tr("Custom accent color"))
                .setView(form).setNegativeButton("Cancel", null).setPositiveButton("Apply color", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            if (!hexInput.getText().toString().trim().matches("#[0-9a-fA-F]{6}")) {
                hexInput.setError("Enter a color like #177E68");
                return;
            }
            color[0] = Color.parseColor(hexInput.getText().toString().trim());
            preferences.edit().putString(ACCENT_KEY, String.format(Locale.US, "#%06X", 0xFFFFFF & color[0])).apply();
            dialog.dismiss();
            applySavedSettings();
            rebuildScreen();
        }));
        dialog.show();
    }

    private void resetAccentColor() {
        preferences.edit().putString(ACCENT_KEY, "#177E68").apply();
        applySavedSettings();
        rebuildScreen();
        Toast.makeText(this, tr("Accent color reset"), Toast.LENGTH_SHORT).show();
    }

    private void resetAppearance() {
        boolean needsClose = !"light".equals(themeStyle) || !"light".equals(logoStyle);
        Runnable reset = () -> {
            SharedPreferences.Editor changes = preferences.edit().putString(THEME_KEY, "light")
                    .putString(ACCENT_KEY, "#177E68").putString(LOGO_KEY, "light");
            if (needsClose) saveSettingsAndClose(changes);
            else {
                changes.apply();
                applySavedSettings();
                rebuildScreen();
                Toast.makeText(this, tr("Appearance reset to default"), Toast.LENGTH_SHORT).show();
            }
        };
        if (needsClose) confirmSettingsClose(reset);
        else reset.run();
    }

    private void chooseLogo(String style) {
        if (style.equals(logoStyle)) return;
        confirmSettingsClose(() -> saveSettingsAndClose(preferences.edit().putString(LOGO_KEY, style)));
    }

    private void applySavedSettings() {
        String storedTheme = preferences.getString(THEME_KEY, "light");
        themeStyle = isThemeStyle(storedTheme) ? storedTheme : "light";
        String storedLogo = preferences.getString(LOGO_KEY, "light");
        logoStyle = isLogoStyle(storedLogo) ? storedLogo : "light";
        String storedCurrency = preferences.getString(CURRENCY_KEY, "EUR");
        currencyCode = isSupportedCurrency(storedCurrency) ? storedCurrency : "EUR";
        String storedLanguage = preferences.getString(LANGUAGE_KEY, "en");
        languageCode = isSupportedLanguage(storedLanguage) ? storedLanguage : "en";
        try { accentColor = Color.parseColor(preferences.getString(ACCENT_KEY, "#177E68")); }
        catch (IllegalArgumentException exception) { accentColor = DEFAULT_ACCENT; }
        configureThemeColors();
    }

    private void loadTranslations() {
        try (InputStream input = getResources().openRawResource(R.raw.translations)) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
            translations = new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            translations = new JSONObject();
        }
    }

    private String tr(String source) {
        if ("en".equals(languageCode) || translations == null) return source;
        JSONObject language = translations.optJSONObject(languageCode);
        return language == null ? source : language.optString(source, source);
    }

    private Locale appLocale() {
        switch (languageCode) {
            case "pt": return new Locale("pt", "PT");
            case "es": return new Locale("es", "ES");
            case "fr": return new Locale("fr", "FR");
            case "de": return new Locale("de", "DE");
            case "it": return new Locale("it", "IT");
            case "ja": return new Locale("ja", "JP");
            default: return Locale.US;
        }
    }

    private void configureThemeColors() {
        if ("light".equals(themeStyle)) {
            PAPER = Color.rgb(246, 247, 244); WHITE = Color.WHITE; INK = Color.rgb(29, 43, 37);
            MUTED = Color.rgb(108, 119, 112); RED = Color.rgb(190, 75, 70); RED_PALE = Color.rgb(250, 235, 233);
        } else if ("green".equals(themeStyle)) {
            PAPER = Color.rgb(10, 20, 17); WHITE = Color.rgb(19, 34, 28); INK = Color.rgb(232, 243, 237);
            MUTED = Color.rgb(163, 183, 172); RED = Color.rgb(255, 129, 120); RED_PALE = Color.rgb(61, 36, 34);
        } else if ("gray".equals(themeStyle)) {
            PAPER = Color.rgb(25, 27, 29); WHITE = Color.rgb(39, 42, 45); INK = Color.rgb(241, 242, 243);
            MUTED = Color.rgb(173, 178, 183); RED = Color.rgb(255, 130, 123); RED_PALE = Color.rgb(64, 39, 37);
        } else {
            PAPER = Color.BLACK; WHITE = Color.rgb(8, 8, 8); INK = Color.rgb(246, 246, 246);
            MUTED = Color.rgb(168, 168, 168); RED = Color.rgb(255, 125, 117); RED_PALE = Color.rgb(50, 30, 29);
        }
        GREEN_PALE = blendWithBackground(accentColor, "light".equals(themeStyle) ? 0.12f : 0.25f);
        CONTROL = UiColors.softSurface(accentColor, WHITE, true);
        CONTROL_TEXT = UiColors.textOnSurfaces(INK, CONTROL, CONTROL);
        SOFT_CONTROL = UiColors.softSurface(accentColor, WHITE, false);
        SOFT_CONTROL_TEXT = UiColors.textOnSurfaces(INK, SOFT_CONTROL, SOFT_CONTROL);
        DESTRUCTIVE_CONTROL = UiColors.softSurface(RED, WHITE, true);
        DESTRUCTIVE_TEXT = UiColors.textOnSurfaces(INK, DESTRUCTIVE_CONTROL, DESTRUCTIVE_CONTROL);
        GREEN = UiColors.textOnSurfaces(accentColor, PAPER, WHITE);
    }

    private int blendWithBackground(int foreground, float amount) {
        int background = "light".equals(themeStyle) ? Color.WHITE : Color.rgb(39, 42, 45);
        int red = Math.round(Color.red(background) * (1 - amount) + Color.red(foreground) * amount);
        int green = Math.round(Color.green(background) * (1 - amount) + Color.green(foreground) * amount);
        int blue = Math.round(Color.blue(background) * (1 - amount) + Color.blue(foreground) * amount);
        return Color.rgb(red, green, blue);
    }

    private boolean isThemeStyle(String value) {
        return "light".equals(value) || "green".equals(value) || "gray".equals(value) || "amoled".equals(value);
    }

    private boolean isLogoStyle(String value) { return isThemeStyle(value); }
    private boolean isSupportedCurrency(String value) {
        for (String code : CURRENCIES) if (code.equals(value)) return true;
        return false;
    }

    private boolean isSupportedLanguage(String value) {
        return "en".equals(value) || "pt".equals(value) || "es".equals(value) || "fr".equals(value)
                || "de".equals(value) || "it".equals(value) || "ja".equals(value);
    }

    private void rebuildScreen() {
        configureSystemBars();
        setContentView(createScreen());
        refreshScreen();
    }

    private void configureLauncherIcon(String style) {
        String[] styles = {"light", "green", "gray", "amoled"};
        String[] aliases = {"LauncherLight", "LauncherGreen", "LauncherGray", "LauncherAmoled"};
        PackageManager manager = getPackageManager();
        // Enable the selected icon first; never leave the app without a launcher entry.
        for (int pass = 0; pass < 2; pass++) for (int index = 0; index < aliases.length; index++) {
            boolean selected = styles[index].equals(style);
            if ((pass == 0) != selected) continue;
            ComponentName component = new ComponentName(this, getPackageName() + "." + aliases[index]);
            int current = manager.getComponentEnabledSetting(component);
            boolean enabled = current == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    || (current == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && index == 0);
            if (enabled == selected) continue;
            int state = selected
                    ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
            manager.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP);
        }
    }

    private Button monthArrow(String label, String accessibilityLabel) {
        Button button = baseButton();
        button.setText("");
        button.setAllCaps(false);
        button.setPadding(0, 0, 0, 0);
        button.setMinWidth(dp(32));
        button.setMinimumWidth(dp(32));
        button.setBackground(rounded(SOFT_CONTROL, dp(14)));
        android.graphics.drawable.Drawable icon = getDrawable("‹".equals(label)
                ? R.drawable.ic_month_previous : R.drawable.ic_month_next);
        if (icon != null) {
            icon.setTint(SOFT_CONTROL_TEXT);
            icon.setBounds(0, 0, dp(18), dp(18));
            android.graphics.drawable.LayerDrawable layers = new android.graphics.drawable.LayerDrawable(
                    new android.graphics.drawable.Drawable[]{rounded(SOFT_CONTROL, dp(14)), icon});
            layers.setLayerSize(1, dp(18), dp(18));
            layers.setLayerGravity(1, Gravity.CENTER);
            button.setBackground(layers);
        }
        button.setContentDescription(accessibilityLabel);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(32), dp(36));
        params.setMargins(dp(2), 0, dp(2), 0);
        button.setLayoutParams(params);
        return button;
    }

    private void stepMonth(int delta) {
        selectedMonth.add(Calendar.MONTH, delta);
        selectedMonth.set(Calendar.DAY_OF_MONTH, 1);
        preferences.edit().putLong(SELECTED_MONTH_KEY, selectedMonth.getTimeInMillis()).apply();
        refreshScreen();
    }

    private void refreshScreen() {
        String monthText = new SimpleDateFormat("MMMM yyyy", appLocale()).format(selectedMonth.getTime());
        for (TextView view : monthViews) view.setText(monthText);

        double spending = 0;
        double income = 0;
        ArrayList<TransactionRecord> visibleTransactions = new ArrayList<>();
        for (TransactionRecord transaction : transactions) {
            if (!isInSelectedMonth(transaction.timestamp)) continue;
            visibleTransactions.add(transaction);
            if ("income".equals(transaction.type)) income += transaction.amount;
            else spending += transaction.amount;
        }
        spendingAmountView.setText(formatCurrency(spending));
        incomeAmountView.setText(formatCurrency(income));
        double net = income - spending;
        netAmountView.setText((net > 0 ? "+ " : net < 0 ? "− " : "") + formatCurrency(Math.abs(net)));
        netAmountView.setTextColor(net < 0 ? RED : GREEN);
        netMessageView.setText(net > 0 ? tr("You received more than you spent")
                : net < 0 ? tr("You spent more than you received") : tr("Income and spending are even"));
        double scale = Math.max(Math.max(income, spending), 1d);
        comparisonIncomeView.setText(formatCurrency(income));
        comparisonSpendingView.setText(formatCurrency(spending));
        incomeComparisonBar.setFraction((float) (income / scale));
        spendingComparisonBar.setFraction((float) (spending / scale));

        Collections.sort(visibleTransactions, new Comparator<TransactionRecord>() {
            @Override
            public int compare(TransactionRecord left, TransactionRecord right) {
                return Long.compare(right.timestamp, left.timestamp);
            }
        });
        recentTransactionList.removeAllViews();
        int recentCount = 0;
        for (TransactionRecord transaction : visibleTransactions) {
            if (!"expense".equals(transaction.type)) continue;
            recentTransactionList.addView(createTransactionRow(transaction, false));
            if (++recentCount == 5) break;
        }
        if (recentCount == 0) {
            TextView emptyRecent = bodyText(tr("No spending yet"), MUTED, 14);
            emptyRecent.setGravity(Gravity.CENTER);
            emptyRecent.setPadding(dp(8), dp(20), dp(8), dp(20));
            recentTransactionList.addView(emptyRecent);
        }
        refreshCategoryPage();
        transactionList.removeAllViews();
        if (visibleTransactions.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(tr("No entries this month"));
            empty.setTextColor(MUTED);
            empty.setTextSize(14);
            empty.setPadding(dp(4), dp(14), dp(4), dp(14));
            transactionList.addView(empty);
            return;
        }
        for (TransactionRecord transaction : visibleTransactions) {
            transactionList.addView(createTransactionRow(transaction, true));
        }
    }

    private void refreshCategoryPage() {
        if (categoryBreakdownList == null || monthlyCategoryChart == null || categoryMonthlyTotalView == null) return;
        Map<String, Double> totals = new LinkedHashMap<>();
        Map<String, String> ids = new LinkedHashMap<>();
        double total = 0;
        for (TransactionRecord transaction : transactions) {
            if (!"expense".equals(transaction.type) || !isInSelectedMonth(transaction.timestamp)) continue;
            String name = categoryDisplayName(transaction);
            totals.put(name, totals.containsKey(name) ? totals.get(name) + transaction.amount : transaction.amount);
            ids.put(name, transaction.categoryId);
            total += transaction.amount;
        }
        monthlyCategoryChart.setData(totals);
        categoryMonthlyTotalView.setText(tr("Spent this month") + "  ·  " + formatCurrency(total));
        categoryBreakdownList.removeAllViews();
        ArrayList<String> names = new ArrayList<>(totals.keySet());
        Collections.sort(names, (left, right) -> Double.compare(totals.get(right), totals.get(left)));
        if (names.isEmpty()) {
            TextView empty = bodyText(tr("No spending this month"), MUTED, 14);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(8), dp(20), dp(8), dp(20));
            categoryBreakdownList.addView(empty);
            refreshCategoryDetail();
            return;
        }
        for (String name : names) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(13), 0, dp(13), 0);
            row.setBackground(rounded(WHITE, dp(15)));
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
            row.setMinimumHeight(dp(62));
            row.setPadding(dp(13), dp(10), dp(13), dp(10));
            rowParams.bottomMargin = dp(7);
            categoryBreakdownList.addView(row, rowParams);
            View dot = new View(this);
            dot.setBackground(rounded(colorForCategory(ids.get(name), name), dp(9)));
            row.addView(dot, new LinearLayout.LayoutParams(dp(13), dp(13)));
            TextView label = bodyText(name, INK, 14);
            label.setMaxLines(1);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1f);
            labelParams.leftMargin = dp(10);
            labelParams.rightMargin = dp(10);
            row.addView(label, labelParams);
            LinearLayout values = new LinearLayout(this);
            values.setOrientation(LinearLayout.VERTICAL);
            values.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            row.addView(values);
            TextView amount = bodyText(formatCurrency(totals.get(name)), INK, 13);
            amount.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            values.addView(amount);
            TextView percent = bodyText(String.format(appLocale(), "%.1f%%", 100 * totals.get(name) / total), MUTED, 11);
            values.addView(percent);
            ImageView chevron = new ImageView(this);
            chevron.setImageResource(R.drawable.ic_month_next);
            chevron.setColorFilter(MUTED);
            LinearLayout.LayoutParams chevronParams = new LinearLayout.LayoutParams(dp(17), dp(17));
            chevronParams.leftMargin = dp(7);
            row.addView(chevron, chevronParams);
            row.setOnClickListener(view -> showCategoryTransactions(name, false));
        }
        refreshCategoryDetail();
    }

    private boolean isInSelectedMonth(long timestamp) {
        Calendar date = Calendar.getInstance();
        date.setTimeInMillis(timestamp);
        return date.get(Calendar.YEAR) == selectedMonth.get(Calendar.YEAR)
                && date.get(Calendar.MONTH) == selectedMonth.get(Calendar.MONTH);
    }

    private View createTransactionRow(TransactionRecord transaction, boolean swipeEnabled) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(12), dp(12), dp(12));
        card.setBackground(rounded(WHITE, dp(18)));

        boolean isIncome = "income".equals(transaction.type);
        int categoryColor = colorForCategory(transaction.categoryId, transaction.categoryName);
        ImageView directionIcon = new ImageView(this);
        directionIcon.setImageResource(isIncome ? R.drawable.ic_transaction_income : R.drawable.ic_transaction_expense);
        directionIcon.setColorFilter(categoryColor);
        directionIcon.setPadding(dp(8), dp(8), dp(8), dp(8));
        directionIcon.setBackground(rounded(Color.argb(34, Color.red(categoryColor),
                Color.green(categoryColor), Color.blue(categoryColor)), dp(12)));
        LinearLayout.LayoutParams directionParams = new LinearLayout.LayoutParams(dp(36), dp(36));
        directionParams.rightMargin = dp(11);
        card.addView(directionIcon, directionParams);

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(details, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        String title = transaction.note.trim();
        if (title.isEmpty()) title = categoryDisplayName(transaction);
        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(INK);
        titleView.setTextSize(15);
        titleView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        titleView.setMaxLines(1);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        details.addView(titleView);

        TextView dateView = new TextView(this);
        String category = categoryDisplayName(transaction);
        dateView.setText(category + "\n" + new SimpleDateFormat("EEE, d MMM · HH:mm", appLocale())
                .format(new Date(transaction.timestamp)));
        dateView.setTextColor(MUTED);
        dateView.setTextSize(11);
        dateView.setMaxLines(3);
        dateView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dateParams.topMargin = dp(4);
        details.addView(dateView, dateParams);

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(-2, -2);
        valueParams.leftMargin = dp(12);
        card.addView(right, valueParams);

        TextView amount = new TextView(this);
        amount.setText((isIncome ? "+ " : "− ") + formatCurrency(transaction.amount));
        amount.setTextColor(isIncome ? GREEN : RED);
        amount.setTextSize(14);
        amount.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        fitAmountText(amount, 14);
        right.addView(amount);
        amount.setMaxWidth(dp(122));

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.bottomMargin = dp(8);
        card.setLayoutParams(rowParams);
        if (!swipeEnabled) return card;

        SwipeRow swipeRow = new SwipeRow();
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);
        TextView edit = actionButton("Edit", GREEN, Color.TRANSPARENT, "Edit transaction");
        TextView delete = actionButton("Delete", RED, Color.TRANSPARENT, "Delete transaction");
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(dp(44), dp(48));
        actions.addView(edit, actionParams);
        View actionGap = new View(this);
        actions.addView(actionGap, new LinearLayout.LayoutParams(dp(8), 1));
        actions.addView(delete, new LinearLayout.LayoutParams(dp(44), dp(48)));
        FrameLayout.LayoutParams actionsParams = new FrameLayout.LayoutParams(dp(112), -1,
                Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        swipeRow.addView(actions, actionsParams);
        FrameLayout.LayoutParams contentParams = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER_VERTICAL);
        swipeRow.addView(card, contentParams);
        swipeRow.setContent(card);
        edit.setOnClickListener(view -> { swipeRow.close(); showTransactionDialog(transaction); });
        delete.setOnClickListener(view -> { swipeRow.close(); confirmDelete(transaction); });
        swipeRow.setLayoutParams(rowParams);
        return swipeRow;
    }

    private TextView actionButton(String label, int color, int backgroundColor, String accessibilityLabel) {
        TextView button = new TextView(this);
        boolean iconOnly = "Edit".equals(label) || "Delete".equals(label);
        button.setText(iconOnly ? "" : label);
        int surface = UiColors.softSurface(color, WHITE, true);
        button.setTextColor(iconOnly ? color : UiColors.textOnSurfaces(INK, surface, surface));
        button.setTextSize(11);
        button.setGravity(Gravity.CENTER);
        button.setBackground(rounded(iconOnly ? Color.TRANSPARENT : surface, dp(10)));
        if (iconOnly) {
            android.graphics.drawable.Drawable icon = getDrawable("Edit".equals(label)
                    ? R.drawable.ic_action_edit : R.drawable.ic_action_delete);
            if (icon != null) {
                icon.setTint(color);
                icon.setBounds(0, 0, dp(19), dp(19));
                android.graphics.drawable.LayerDrawable layers = new android.graphics.drawable.LayerDrawable(
                        new android.graphics.drawable.Drawable[]{rounded(Color.TRANSPARENT, dp(10)), icon});
                layers.setLayerSize(1, dp(20), dp(20));
                layers.setLayerGravity(1, Gravity.CENTER);
                button.setBackground(layers);
            }
            button.setMinimumWidth(dp(36));
            button.setMinimumHeight(dp(36));
        }
        button.setContentDescription(accessibilityLabel);
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    private ArrayList<CategoryRecord> loadCategories() {
        ArrayList<CategoryRecord> loaded = new ArrayList<>();
        String saved = preferences.getString(CATEGORIES_KEY, null);
        if (saved != null) {
            try {
                JSONArray array = new JSONArray(saved);
                for (int index = 0; index < array.length(); index++) {
                    JSONObject object = array.optJSONObject(index);
                    if (object == null) continue;
                    String id = object.optString("id", "");
                    String name = object.optString("name", "").trim();
                    String type = object.optString("type", "expense");
                    if (id.isEmpty() || name.isEmpty() || !isValidType(type)) continue;
                    loaded.add(new CategoryRecord(id, name, type,
                            object.optInt("color", CATEGORY_COLORS[0]), object.optBoolean("isDefault", false)));
                }
            } catch (JSONException ignored) {
        Toast.makeText(this, tr("Saved categories could not be read."), Toast.LENGTH_LONG).show();
            }
        }
        if (saved == null) addMissingDefaultCategories(loaded);
        categories = loaded;
        persistCategories();
        return loaded;
    }

    private void addMissingDefaultCategories(ArrayList<CategoryRecord> list) {
        String[][] defaults = new String[][]{
                {"expense-food", "Food & groceries", "expense"},
                {"expense-home", "Home & bills", "expense"},
                {"expense-transport", "Transport", "expense"},
                {"expense-shopping", "Shopping", "expense"},
                {"expense-health", "Health", "expense"},
                {"expense-leisure", "Leisure", "expense"},
                {"expense-other", "Other", "expense"},
                {"income-salary", "Salary", "income"},
                {"income-freelance", "Freelance", "income"},
                {"income-refund", "Refund", "income"},
                {"income-other", "Other", "income"}
        };
        for (int index = 0; index < defaults.length; index++) {
            String id = defaults[index][0];
            boolean exists = false;
            for (CategoryRecord category : list) if (category.id.equals(id)) { exists = true; break; }
            if (!exists) list.add(new CategoryRecord(id, defaults[index][1], defaults[index][2],
                    CATEGORY_COLORS[index % CATEGORY_COLORS.length], true));
        }
    }

    private void persistCategories() {
        JSONArray array = new JSONArray();
        for (CategoryRecord category : categories) {
            JSONObject object = new JSONObject();
            try {
                object.put("id", category.id);
                object.put("name", category.name);
                object.put("type", category.type);
                object.put("color", category.color);
                object.put("isDefault", category.isDefault);
                array.put(object);
            } catch (JSONException ignored) { }
        }
        preferences.edit().putString(CATEGORIES_KEY, array.toString()).apply();
    }

    private boolean isValidType(String type) {
        return "income".equals(type) || "expense".equals(type);
    }

    private String defaultCategoryId(String type) {
        for (CategoryRecord category : categories) if (category.type.equals(type)) return category.id;
        return "";
    }

    private String defaultCategoryName(String type) {
        for (CategoryRecord category : categories) if (category.type.equals(type)) return category.name;
        return "income".equals(type) ? "Money received" : "Other";
    }

    private boolean hasCategory(String id, String type) {
        if (id == null || id.isEmpty()) return false;
        for (CategoryRecord category : categories) {
            if (category.id.equals(id) && category.type.equals(type)) return true;
        }
        return false;
    }

    private CategoryRecord findCategory(String id) {
        if (id == null) return null;
        for (CategoryRecord category : categories) if (category.id.equals(id)) return category;
        return null;
    }

    private int colorForCategory(String id, String name) {
        CategoryRecord found = findCategory(id);
        if (found != null) return found.color;
        for (CategoryRecord category : categories) if (category.name.equalsIgnoreCase(name)) return category.color;
        return CATEGORY_COLORS[(name.hashCode() & 0x7fffffff) % CATEGORY_COLORS.length];
    }

    private String categoryDisplayName(TransactionRecord transaction) {
        CategoryRecord category = findCategory(transaction.categoryId);
        if (category != null && category.isDefault) return tr(category.name);
        if (!transaction.categoryName.isEmpty()) return transaction.categoryName;
        return tr("Uncategorized");
    }

private boolean isOtherCategory(CategoryRecord category) {
        String name = category.name.replace("…", "").replace("...", "").trim();
        return "Other".equalsIgnoreCase(name)
                || tr("Other").equalsIgnoreCase(name);
    }

    private void showCategoryPicker(String type, String[] chosenId, String[] chosenName, Runnable onChange) {
        ArrayList<CategoryRecord> options = new ArrayList<>();
        for (CategoryRecord category : categories) {
            if (category.type.equals(type) && !isOtherCategory(category)) options.add(category);
        }
        String[] labels = new String[options.size() + 1];
        int checked = options.size();
        for (int i = 0; i < options.size(); i++) {
            CategoryRecord category = options.get(i);
            labels[i] = category.isDefault ? tr(category.name) : category.name;
            if (category.id.equals(chosenId[0])) checked = i;
        }
        labels[options.size()] = tr("Other");
        new LedgerDialogBuilder().setTitle(tr("Category"))
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    if (which < options.size()) {
                        CategoryRecord category = options.get(which);
                        chosenId[0] = category.id;
                        chosenName[0] = category.name;
                    } else {
                        chosenId[0] = "";
                        chosenName[0] = "Other";
                    }
                    onChange.run();
                }).setNegativeButton(tr("Cancel"), null).show();
    }



    private CategoryRecord findCategoryByName(String name, String type) {
        for (CategoryRecord category : categories) {
            if (category.type.equals(type) && (category.name.equalsIgnoreCase(name.trim())
                    || (category.isDefault && tr(category.name).equalsIgnoreCase(name.trim())))) return category;
        }
        return null;
    }

    private void showManageCategories() {
        if (isFinishing() || isDestroyed()) return;
        if (categoryManagerDialog != null && categoryManagerDialog.isShowing()) return;
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(8), dp(20), dp(8));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        for (String type : new String[]{"expense", "income"}) {
            TextView heading = new TextView(this);
            heading.setText("income".equals(type) ? tr("Received") : tr("Spending"));
            heading.setTextColor(MUTED);
            heading.setTextSize(11);
            heading.setLetterSpacing(0.08f);
            LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(34));
            headingParams.topMargin = dp(8);
            content.addView(heading, headingParams);
            for (CategoryRecord category : categories) {
                if (!category.type.equals(type)) continue;
                LinearLayout row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(12), 0, dp(8), 0);
                row.setBackground(rounded(PAPER, dp(12)));
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, -2);
                row.setMinimumHeight(dp(60));
                rowParams.bottomMargin = dp(6);
                content.addView(row, rowParams);
                View dot = new View(this);
                dot.setBackground(rounded(category.color, dp(10)));
                row.addView(dot, new LinearLayout.LayoutParams(dp(12), dp(12)));
                TextView name = new TextView(this);
                name.setText((category.isDefault ? tr(category.name) : category.name)
                        );
                name.setTextColor(INK);
                name.setTextSize(14);
                name.setSingleLine(true);
                name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                nameParams.leftMargin = dp(10);
                row.addView(name, nameParams);
                TextView edit = actionButton("Edit", GREEN, GREEN_PALE, tr("Edit saved category"));
                row.addView(edit, new LinearLayout.LayoutParams(dp(44), dp(44)));
                edit.setOnClickListener(view -> {
                    if (categoryManagerDialog != null) categoryManagerDialog.dismiss();
                    showCategoryEditor(category);
                });
                {
                    View gap = new View(this);
                    row.addView(gap, new LinearLayout.LayoutParams(dp(5), 1));
                    TextView delete = actionButton("Delete", RED, RED_PALE, tr("Remove this category?"));
                    row.addView(delete, new LinearLayout.LayoutParams(dp(44), dp(44)));
                    delete.setOnClickListener(view -> {
                        if (categoryManagerDialog != null) categoryManagerDialog.dismiss();
                        confirmDeleteCategory(category);
                    });
                }
            }
        }
        LedgerDialog dialog = new LedgerDialogBuilder().setTitle(tr("Manage categories"))
                .setView(scroll).setNegativeButton(tr("Close"), null)
                .setPositiveButton(tr("New category"), null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> { dialog.dismiss(); showCategoryEditor(null); }));
        categoryManagerDialog = dialog;
        dialog.show();
    }

    private void showCategoryEditor(CategoryRecord editing) {
        boolean isEditing = editing != null;
        EditText nameInput = new EditText(this);
        nameInput.setSingleLine(true);
        nameInput.setHint("Category name");
        nameInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(36)});
        if (isEditing) nameInput.setText(editing.name);

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(24), 0, dp(24), 0);
        TextView nameLabel = new TextView(this);
        nameLabel.setText(tr("Name")); nameLabel.setTextColor(MUTED); nameLabel.setTextSize(13);
        form.addView(nameLabel);
        form.addView(nameInput);

        String[] type = {isEditing ? editing.type : "expense"};
        LinearLayout typeRow = new LinearLayout(this);
        LinearLayout.LayoutParams typeRowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        typeRow.setPadding(dp(4), dp(4), dp(4), dp(4));
        typeRow.setBackground(rounded(PAPER, dp(16)));
        typeRowParams.topMargin = dp(12);
        form.addView(typeRow, typeRowParams);
        Button expense = baseButton(); expense.setText(tr("Spending")); expense.setAllCaps(false);
        Button income = baseButton(); income.setText(tr("Received")); income.setAllCaps(false);
        typeRow.addView(expense, new LinearLayout.LayoutParams(0, -1, 1));
        typeRow.addView(income, new LinearLayout.LayoutParams(0, -1, 1));
        Runnable updateType = () -> {
            boolean isIncome = "income".equals(type[0]);
            expense.setTextColor(isIncome ? MUTED : CONTROL_TEXT);
            income.setTextColor(isIncome ? CONTROL_TEXT : MUTED);
            expense.setBackground(rounded(isIncome ? PAPER : CONTROL, dp(12)));
            income.setBackground(rounded(isIncome ? CONTROL : PAPER, dp(12)));
        };
        expense.setOnClickListener(view -> { type[0] = "expense"; updateType.run(); });
        income.setOnClickListener(view -> { type[0] = "income"; updateType.run(); });
        updateType.run();

        TextView colorLabel = new TextView(this);
        colorLabel.setText(tr("Color")); colorLabel.setTextColor(MUTED); colorLabel.setTextSize(13);
        LinearLayout.LayoutParams colorLabelParams = new LinearLayout.LayoutParams(-1, -2);
        colorLabelParams.topMargin = dp(10);
        form.addView(colorLabel, colorLabelParams);
        int initialColor = isEditing ? editing.color : CATEGORY_COLORS[categories.size() % CATEGORY_COLORS.length];
        int[] selectedColor = {initialColor};
        LinearLayout swatches = new LinearLayout(this);
        swatches.setOrientation(LinearLayout.VERTICAL);
        form.addView(swatches, new LinearLayout.LayoutParams(-1, -2));
        ArrayList<TextView> swatchViews = new ArrayList<>();
        for (int rowIndex = 0; rowIndex < 2; rowIndex++) {
            LinearLayout swatchRow = new LinearLayout(this);
            swatchRow.setGravity(Gravity.CENTER_VERTICAL);
            swatches.addView(swatchRow, new LinearLayout.LayoutParams(-1, dp(50)));
            for (int column = 0; column < 5; column++) {
                int color = CATEGORY_COLORS[rowIndex * 5 + column];
                FrameLayout cell = new FrameLayout(this);
                swatchRow.addView(cell, new LinearLayout.LayoutParams(0, -1, 1f));
                TextView swatch = bodyText(color == selectedColor[0] ? "✓" : "", Color.WHITE, 18);
                swatch.setGravity(Gravity.CENTER);
                swatch.setBackground(rounded(color, dp(18)));
                swatch.setTag(color);
                swatch.setContentDescription(String.format(Locale.US, "#%06X", color & 0xFFFFFF));
                cell.addView(swatch, new FrameLayout.LayoutParams(dp(36), dp(36), Gravity.CENTER));
                swatchViews.add(swatch);
                cell.setOnClickListener(view -> {
                    selectedColor[0] = color;
                    for (TextView item : swatchViews) item.setText(((Integer) item.getTag()) == color ? "✓" : "");
                });
            }
        }
        LedgerDialog dialog = new LedgerDialogBuilder().setTitle(isEditing ? tr("Edit saved category") : tr("New category"))
                .setView(form).setNegativeButton(tr("Cancel"), null).setPositiveButton(tr("Save category"), null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String name = nameInput.getText().toString().trim();
            if (name.isEmpty()) { nameInput.setError(tr("Enter a category name.")); return; }
            CategoryRecord duplicate = findCategoryByName(name, type[0]);
            if (duplicate != null && (!isEditing || !duplicate.id.equals(editing.id))) {
                nameInput.setError(tr("That category is already saved.")); return;
            }
            if (isEditing) {
                editing.name = name;
                editing.type = type[0];
                editing.color = selectedColor[0];
                for (TransactionRecord transaction : transactions) {
                    if (editing.id.equals(transaction.categoryId)) transaction.categoryName = name;
                }
                persistTransactions();
            } else categories.add(new CategoryRecord(UUID.randomUUID().toString(), name,
                    type[0], selectedColor[0], false));
            persistCategories();
            dialog.dismiss();
            refreshScreen();
        }));
        dialog.setOnDismissListener(ignored -> showManageCategories());
        dialog.show();
    }

    private void confirmDeleteCategory(CategoryRecord category) {
        LedgerDialog dialog = new LedgerDialogBuilder().setDestructive(true).setTitle(tr("Remove this category?"))
                .setMessage(tr("This saved category will be removed.") + " " + tr("Its past entries will keep the category name as one-time labels."))
                .setNegativeButton(tr("Cancel"), null).setPositiveButton(tr("Remove"), (ignored, which) -> {
                    categories.remove(category);
                    for (TransactionRecord transaction : transactions) {
                        if (category.id.equals(transaction.categoryId)) {
                            transaction.categoryId = "";
                            transaction.categoryName = category.name;
                        }
                    }
                    persistCategories(); persistTransactions(); refreshScreen();
                    Toast.makeText(this, tr("Category removed"), Toast.LENGTH_SHORT).show();
                }).create();
        dialog.setOnDismissListener(ignored -> getWindow().getDecorView().post(this::showManageCategories));
        dialog.show();
    }

    private void showCategoryTotals(boolean allTime) {
        Map<String, Double> totals = new LinkedHashMap<>();
        Map<String, String> ids = new LinkedHashMap<>();
        double total = 0;
        for (TransactionRecord transaction : transactions) {
            if (!"expense".equals(transaction.type)) continue;
            if (!allTime && !isInSelectedMonth(transaction.timestamp)) continue;
            String name = categoryDisplayName(transaction);
            totals.put(name, totals.containsKey(name) ? totals.get(name) + transaction.amount : transaction.amount);
            ids.put(name, transaction.categoryId);
            total += transaction.amount;
        }
        String title = allTime ? tr("All-time category totals") : tr("View category totals") + " · "
                + new SimpleDateFormat("MMMM yyyy", appLocale()).format(selectedMonth.getTime());
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(8), dp(20), dp(8));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        content.addView(new CategoryChartView(this, totals));
        TextView totalLabel = new TextView(this);
        totalLabel.setText((allTime ? tr("SINCE YOU STARTED") : tr("Spent this month")) + "  ·  " + formatCurrency(total));
        totalLabel.setTextColor(INK); totalLabel.setTextSize(14); totalLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        totalLabel.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams totalParams = new LinearLayout.LayoutParams(-1, dp(42));
        content.addView(totalLabel, totalParams);

        ArrayList<String> names = new ArrayList<>(totals.keySet());
        Collections.sort(names, (left, right) -> Double.compare(totals.get(right), totals.get(left)));
        if (names.isEmpty()) {
            TextView empty = new TextView(this); empty.setText(tr("No spending yet"));
            empty.setTextColor(MUTED); empty.setGravity(Gravity.CENTER); empty.setPadding(0, dp(16), 0, dp(16));
            content.addView(empty);
        }
        LedgerDialog[] totalsDialog = new LedgerDialog[1];
        for (String name : names) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(12), 0, dp(12), 0);
            row.setBackground(rounded(WHITE, dp(13)));
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
            row.setMinimumHeight(dp(64));
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            rowParams.bottomMargin = dp(7); content.addView(row, rowParams);
            View dot = new View(this); dot.setBackground(rounded(colorForCategory(ids.get(name), name), dp(8)));
            row.addView(dot, new LinearLayout.LayoutParams(dp(12), dp(12)));
            TextView label = new TextView(this); label.setText(name); label.setTextColor(INK); label.setTextSize(14);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1f);
            labelParams.leftMargin = dp(10); row.addView(label, labelParams);
            LinearLayout values = new LinearLayout(this); values.setOrientation(LinearLayout.VERTICAL);
            values.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL); row.addView(values);
            TextView amount = new TextView(this); amount.setText(formatCurrency(totals.get(name)));
            amount.setTextColor(INK); amount.setTextSize(13); amount.setTypeface(Typeface.DEFAULT, Typeface.BOLD); values.addView(amount);
            TextView percent = new TextView(this); percent.setText(total == 0 ? "0%" : String.format(appLocale(), "%.1f%%", 100 * totals.get(name) / total));
            percent.setTextColor(MUTED); percent.setTextSize(11); values.addView(percent);
            row.setOnClickListener(view -> {
                if (!allTime && totalsDialog[0] != null) totalsDialog[0].dismiss();
                showCategoryTransactions(name, allTime);
            });
        }
        LedgerDialog dialog = new LedgerDialogBuilder().setTitle(title).setView(scroll)
                .setNegativeButton(tr("Close"), null).setPositiveButton(allTime ? tr("Monthly totals") : tr("All-time category totals"), null).create();
        totalsDialog[0] = dialog;
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            dialog.dismiss(); showCategoryTotals(!allTime);
        }));
        dialog.show();
    }

    private void showCategoryTransactions(String categoryName, boolean allTime) {
        if (!allTime) {
            selectedCategoryName = categoryName;
            refreshCategoryDetail();
            return;
        }
        ArrayList<TransactionRecord> matching = new ArrayList<>();
        for (TransactionRecord transaction : transactions) {
            String name = categoryDisplayName(transaction);
            if ("expense".equals(transaction.type) && name.equals(categoryName)
                    && (allTime || isInSelectedMonth(transaction.timestamp))) matching.add(transaction);
        }
        Collections.sort(matching, (left, right) -> Long.compare(right.timestamp, left.timestamp));
        LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(16), dp(8), dp(16), dp(8));
        ScrollView scroll = new ScrollView(this); scroll.addView(list);
        TextView summary = new TextView(this);
        double total = 0; for (TransactionRecord entry : matching) total += entry.amount;
        summary.setText(matching.size() + (matching.size() == 1 ? " transaction" : " transactions")
                + "  ·  " + formatCurrency(total));
        summary.setTextColor(MUTED); summary.setTextSize(13); summary.setPadding(dp(4), dp(4), dp(4), dp(12));
        list.addView(summary);
        for (TransactionRecord transaction : matching) {
            LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(12), dp(9), dp(12), dp(9)); row.setBackground(rounded(WHITE, dp(12)));
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2); rowParams.bottomMargin = dp(6);
            list.addView(row, rowParams);
            TextView title = new TextView(this);
            title.setText(transaction.note.isEmpty() ? categoryName : transaction.note);
            title.setTextColor(INK); title.setTextSize(14); title.setTypeface(Typeface.DEFAULT, Typeface.BOLD); row.addView(title);
            TextView date = new TextView(this);
            date.setText(new SimpleDateFormat("EEE, d MMM yyyy · HH:mm", appLocale()).format(new Date(transaction.timestamp)));
            date.setTextColor(MUTED); date.setTextSize(12); row.addView(date);
            TextView amount = new TextView(this); amount.setText("− " + formatCurrency(transaction.amount));
            amount.setTextColor(RED); amount.setTextSize(13); amount.setTypeface(Typeface.DEFAULT, Typeface.BOLD); row.addView(amount);
        }
        String title = categoryName + (allTime ? " · all time" : " · "
                + new SimpleDateFormat("MMMM yyyy", appLocale()).format(selectedMonth.getTime()));
        new LedgerDialogBuilder().setTitle(title).setView(scroll).setPositiveButton("Close", null).show();
    }

    private void refreshCategoryDetail() {
        if (categoryDetailPanel == null) return;
        boolean showingDetail = selectedCategoryName != null;
        categoryOverviewContent.setVisibility(showingDetail ? View.GONE : View.VISIBLE);
        categoryDetailPanel.setVisibility(showingDetail ? View.VISIBLE : View.GONE);
        if (!showingDetail) return;
        categoryDetailPanel.removeAllViews();

        LinearLayout detailTop = new LinearLayout(this);
        detailTop.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = bodyText("‹  " + tr("All categories"), SOFT_CONTROL_TEXT, 13);
        back.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        back.setPadding(dp(8), dp(8), dp(12), dp(8));
        back.setBackground(rounded(SOFT_CONTROL, dp(11)));
        back.setOnClickListener(view -> {
            selectedCategoryName = null;
            refreshCategoryDetail();
        });
        detailTop.addView(back);
        LinearLayout.LayoutParams topParams = new LinearLayout.LayoutParams(-1, -2);
        topParams.bottomMargin = dp(10);
        categoryDetailPanel.addView(detailTop, topParams);

        TextView eyebrow = bodyText(tr("SPENT IN") + "  ·  "
                + new SimpleDateFormat("MMMM yyyy", appLocale()).format(selectedMonth.getTime()).toUpperCase(appLocale()), MUTED, 10);
        eyebrow.setLetterSpacing(.08f);
        categoryDetailPanel.addView(eyebrow);
        categoryDetailTitle = bodyText(selectedCategoryName, INK, 24);
        categoryDetailTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = dp(4);
        categoryDetailPanel.addView(categoryDetailTitle, titleParams);

        double total = 0;
        ArrayList<TransactionRecord> matching = new ArrayList<>();
        for (TransactionRecord transaction : transactions) {
            String name = categoryDisplayName(transaction);
            if ("expense".equals(transaction.type) && selectedCategoryName.equals(name)
                    && isInSelectedMonth(transaction.timestamp)) {
                total += transaction.amount;
                matching.add(transaction);
            }
        }
        Collections.sort(matching, (left, right) -> Long.compare(right.timestamp, left.timestamp));
        categoryDetailTotal = bodyText(formatCurrency(total), INK, 26);
        categoryDetailTotal.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams totalParams = new LinearLayout.LayoutParams(-1, -2);
        totalParams.topMargin = dp(4);
        totalParams.bottomMargin = dp(12);
        categoryDetailPanel.addView(categoryDetailTotal, totalParams);

        categoryDetailList = new LinearLayout(this);
        categoryDetailList.setOrientation(LinearLayout.VERTICAL);
        categoryDetailPanel.addView(categoryDetailList);
        if (matching.isEmpty()) {
            TextView empty = bodyText(tr("No transactions in this category for this month."), MUTED, 14);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(8), dp(20), dp(8), dp(20));
            categoryDetailList.addView(empty);
        } else {
            for (TransactionRecord transaction : matching) {
                categoryDetailList.addView(createTransactionRow(transaction, false));
            }
        }
    }

private final class SwipeRow extends FrameLayout {
        private float downX, downY, startTranslation;
        private boolean dragging;
        private View content;
        private final int revealWidth = dp(112);
        private final int touchSlop = ViewConfiguration.get(MainActivity.this).getScaledTouchSlop();

        SwipeRow() { super(MainActivity.this); setClipChildren(true); setClipToPadding(true); }

        void setContent(View card) {
            content = card;
            card.setClickable(true);
            card.setOnClickListener(view -> close());
        }

        @Override public boolean dispatchTouchEvent(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                downX = event.getX(); downY = event.getY();
                startTranslation = content == null ? 0 : content.getTranslationX();
                dragging = false;
                if (content != null) content.animate().cancel();
            } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                float dx = event.getX() - downX, dy = event.getY() - downY;
                if (Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy) * 1.2f) {
                    dragging = true;
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                }
            }
            return super.dispatchTouchEvent(event);
        }

        @Override public boolean onInterceptTouchEvent(MotionEvent event) { return dragging; }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (content == null) return false;
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) return true;
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE && dragging) {
                if (openSwipeRow != null && openSwipeRow != this) openSwipeRow.close();
                float next = startTranslation + event.getX() - downX;
                content.setTranslationX(Math.max(-revealWidth, Math.min(0, next)));
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                if (dragging) {
                    boolean open = event.getActionMasked() != MotionEvent.ACTION_CANCEL
                            && content.getTranslationX() < -revealWidth * .35f;
                    content.animate().translationX(open ? -revealWidth : 0).setDuration(160).start();
                    openSwipeRow = open ? this : null;
                    dragging = false;
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                    return true;
                }
                performClick();
            }
            return true;
        }

        @Override public boolean performClick() { super.performClick(); close(); return true; }
        boolean isOpen() { return content != null && content.getTranslationX() < -dp(2); }
        void close() {
            if (content != null) content.animate().translationX(0).setDuration(140).start();
            if (openSwipeRow == this) openSwipeRow = null;
        }
    }

    /** A single, responsive dialog style for every menu and confirmation. */
    private final class LedgerDialogBuilder {
        private String title = "";
        private String message;
        private View content;
        private String[] choices;
        private int checked = -1;
        private DialogInterface.OnClickListener choiceListener;
        private final String[] labels = new String[3];
        private final DialogInterface.OnClickListener[] listeners = new DialogInterface.OnClickListener[3];
        private boolean destructive;
        LedgerDialogBuilder setTitle(String value) { title = value; return this; }
        LedgerDialogBuilder setMessage(String value) { message = value; return this; }
        LedgerDialogBuilder setView(View value) { content = value; return this; }
        LedgerDialogBuilder setDestructive(boolean value) { destructive = value; return this; }
        LedgerDialogBuilder setPositiveButton(String label, DialogInterface.OnClickListener listener) {
            labels[0] = label; listeners[0] = listener; return this;
        }
        LedgerDialogBuilder setNegativeButton(String label, DialogInterface.OnClickListener listener) {
            labels[1] = label; listeners[1] = listener; return this;
        }
        LedgerDialogBuilder setNeutralButton(String label, DialogInterface.OnClickListener listener) {
            labels[2] = label; listeners[2] = listener; return this;
        }
        LedgerDialogBuilder setItems(String[] values, DialogInterface.OnClickListener listener) {
            choices = values; choiceListener = listener; return this;
        }
        LedgerDialogBuilder setSingleChoiceItems(String[] values, int selected, DialogInterface.OnClickListener listener) {
            checked = selected; return setItems(values, listener);
        }
        LedgerDialog create() { return new LedgerDialog(this); }
        LedgerDialog show() { LedgerDialog dialog = create(); dialog.show(); return dialog; }
    }

    private final class LedgerDialog extends Dialog {
        private final Button[] buttons = new Button[3];
        private final LinearLayout panel;
        private final ScrollView scroll;
        private int observedKeyboardHeight = -1;
        private final android.view.ViewTreeObserver.OnGlobalLayoutListener keyboardObserver = this::refreshKeyboardViewport;
        LedgerDialog(LedgerDialogBuilder builder) {
            super(MainActivity.this);
            requestWindowFeature(Window.FEATURE_NO_TITLE);
            setCanceledOnTouchOutside(true);
            panel = new LinearLayout(MainActivity.this);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setBackground(rounded(WHITE, dp(24)));
            panel.setClipToOutline(true);

            LinearLayout header = new LinearLayout(MainActivity.this);
            header.setGravity(Gravity.CENTER_VERTICAL);
            header.setPadding(dp(20), dp(14), dp(12), dp(10));
            TextView title = bodyText(builder.title, INK, 20);
            title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            header.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
            Button close = baseButton();
            close.setPadding(0, 0, 0, 0);
            close.setText("×"); close.setTextSize(25); close.setTextColor(MUTED);
            close.setBackground(rounded(PAPER, dp(20)));
            close.setContentDescription(tr("Close"));
            close.setOnClickListener(view -> cancel());
            LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(dp(40), dp(40));
            closeParams.leftMargin = dp(10);
            header.addView(close, closeParams);
            panel.addView(header, new LinearLayout.LayoutParams(-1, -2));

            LinearLayout body = new LinearLayout(MainActivity.this);
            body.setOrientation(LinearLayout.VERTICAL);
            body.setPadding(dp(20), dp(4), dp(20), dp(12));
            if (builder.message != null) {
                TextView message = bodyText(builder.message, MUTED, 14);
                message.setLineSpacing(dp(3), 1f);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
                params.bottomMargin = dp(14);
                body.addView(message, params);
            }
            if (builder.choices != null) {
                for (int index = 0; index < builder.choices.length; index++) {
                    final int choice = index;
                    LinearLayout row = new LinearLayout(MainActivity.this);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setMinimumHeight(dp(52));
                    row.setPadding(dp(14), dp(12), dp(14), dp(12));
                    boolean selected = builder.checked == index;
                    row.setBackground(rounded(selected ? CONTROL : PAPER, dp(14)));
                    TextView label = bodyText(builder.choices[index], selected ? CONTROL_TEXT : INK, 15);
                    row.addView(label, new LinearLayout.LayoutParams(0, -2, 1f));
                    TextView indicator = bodyText(selected ? "✓" : "›", selected ? CONTROL_TEXT : MUTED, 19);
                    indicator.setGravity(Gravity.CENTER);
                    row.addView(indicator, new LinearLayout.LayoutParams(dp(26), -2));
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
                    params.bottomMargin = dp(6);
                    body.addView(row, params);
                    row.setOnClickListener(view -> {
                        dismiss();
                        if (builder.choiceListener != null) builder.choiceListener.onClick(this, choice);
                    });
                }
            }
            if (builder.content != null) {
                View content = builder.content;
                if (content instanceof ScrollView) {
                    ScrollView oldScroll = (ScrollView) content;
                    content = oldScroll.getChildAt(0);
                    oldScroll.removeAllViews();
                }
                if (content != null) {
                    content.setPadding(0, dp(4), 0, dp(4));
                    styleDialogFields(content);
                    body.addView(content, new LinearLayout.LayoutParams(-1, -2));
                }
            }
            LinearLayout footer = new LinearLayout(MainActivity.this);
            footer.setOrientation(LinearLayout.VERTICAL);
            footer.setPadding(dp(20), dp(8), dp(20), dp(14));
            int[] order = {0, 2, 1};
            int buttonCount = 0;
            for (int index : order) {
                if (builder.labels[index] == null) continue;
                buttonCount++;
                final int slot = index;
                Button button = index == 0 ? primaryButton(builder.labels[index]) : secondaryButton(builder.labels[index]);
                if (index == 0 && builder.destructive) {
                    button.setBackground(rounded(DESTRUCTIVE_CONTROL, dp(15)));
                    button.setTextColor(DESTRUCTIVE_TEXT);
                }
                if (index == 1) { button.setBackgroundColor(Color.TRANSPARENT); button.setTextColor(MUTED); }
                button.setTextSize(14);
                button.setMinimumHeight(dp(index == 0 ? 48 : 42));
                button.setPadding(dp(12), dp(9), dp(12), dp(9));
                buttons[index] = button;
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
                if (footer.getChildCount() > 0) params.topMargin = dp(5);
                footer.addView(button, params);
                button.setOnClickListener(view -> {
                    dismiss();
                    if (builder.listeners[slot] != null) builder.listeners[slot].onClick(this, -1 - slot);
                });
            }
            scroll = new ScrollView(MainActivity.this) {
                @Override protected void onMeasure(int widthSpec, int heightSpec) {
                    android.graphics.Rect viewport = usableWindowBounds();
                    int available = viewport.height() - keyboardHeight(viewport);
                    header.measure(widthSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                    footer.measure(widthSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                    int reserved = header.getMeasuredHeight() + footer.getMeasuredHeight() + dp(32);
                    int limit = Math.max(dp(72), available - reserved);
                    int parentLimit = MeasureSpec.getMode(heightSpec) == MeasureSpec.UNSPECIFIED ? limit : MeasureSpec.getSize(heightSpec);
                    super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(Math.min(limit, parentLimit), MeasureSpec.AT_MOST));
                }
            };
            scroll.setClipToPadding(false);
            scroll.setVerticalScrollBarEnabled(false);
            scroll.addView(body);
            panel.addView(scroll, new LinearLayout.LayoutParams(-1, -2));
            if (buttonCount > 0) panel.addView(footer, new LinearLayout.LayoutParams(-1, -2));
            setContentView(panel);
            Window window = getWindow();
            if (window != null) {
                window.setBackgroundDrawableResource(android.R.color.transparent);
                window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                window.setDimAmount(.52f);
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                        | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
            }
        }
        Button getButton(int which) { return buttons[-which - 1]; }

        /** Full app bounds, independent of the floating menu and keyboard size. */
        private android.graphics.Rect usableWindowBounds() {
            if (Build.VERSION.SDK_INT >= 30) {
                android.view.WindowMetrics metrics = MainActivity.this.getWindowManager().getCurrentWindowMetrics();
                android.graphics.Rect bounds = new android.graphics.Rect(metrics.getBounds());
                android.graphics.Insets safe = metrics.getWindowInsets().getInsetsIgnoringVisibility(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                bounds.left += safe.left; bounds.top += safe.top;
                bounds.right -= safe.right; bounds.bottom -= safe.bottom;
                return bounds;
            }
            android.graphics.Point size = new android.graphics.Point();
            MainActivity.this.getWindowManager().getDefaultDisplay().getSize(size);
            WindowInsets insets = MainActivity.this.getWindow().getDecorView().getRootWindowInsets();
            int top = insets == null ? 0 : insets.getSystemWindowInsetTop();
            return new android.graphics.Rect(0, top, size.x, size.y);
        }

        private int keyboardHeight(android.graphics.Rect viewport) {
            int height = 0;
            WindowInsets insets = getWindow().getDecorView().getRootWindowInsets();
            if (Build.VERSION.SDK_INT >= 30 && insets != null && insets.isVisible(WindowInsets.Type.ime())) {
                height = Math.max(0, insets.getInsets(WindowInsets.Type.ime()).bottom
                        - insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()).bottom);
            }
            // Older Android versions expose keyboard occlusion through the full activity's frame.
            android.graphics.Rect visible = new android.graphics.Rect();
            MainActivity.this.getWindow().getDecorView().getWindowVisibleDisplayFrame(visible);
            int obscured = viewport.bottom - visible.bottom;
            if (visible.height() > 0 && obscured > Math.max(dp(100), viewport.height() * .15f)) {
                height = Math.max(height, obscured);
            }
            return Math.min(height, viewport.height());
        }

        private void refreshKeyboardViewport() {
            if (!isShowing()) return;
            int current = keyboardHeight(usableWindowBounds());
            if (current == observedKeyboardHeight) return;
            boolean closed = observedKeyboardHeight > 0 && current == 0;
            observedKeyboardHeight = current;
            if (closed) {
                // Drop the keyboard-constrained window measurement and use the original constraints again.
                resizeWindow();
                panel.forceLayout();
                scroll.forceLayout();
            }
            scroll.requestLayout();
            panel.requestLayout();
        }

        private void resizeWindow() {
            Window window = getWindow();
            if (window != null) {
                window.setLayout(Math.min(usableWindowBounds().width() - dp(24), dp(440)), -2);
                window.setGravity(Gravity.CENTER);
            }
        }

        @Override protected void onStart() {
            super.onStart();
            observedKeyboardHeight = -1;
            resizeWindow();
            Window window = getWindow();
            if (window != null) {
                View decor = window.getDecorView();
                decor.getViewTreeObserver().addOnGlobalLayoutListener(keyboardObserver);
                MainActivity.this.getWindow().getDecorView().getViewTreeObserver().addOnGlobalLayoutListener(keyboardObserver);
                decor.setOnApplyWindowInsetsListener((view, insets) -> {
                    decor.post(this::refreshKeyboardViewport);
                    return view.onApplyWindowInsets(insets);
                });
                decor.requestApplyInsets();
            }
        }

        @Override protected void onStop() {
            View decor = getWindow().getDecorView();
            if (decor.getViewTreeObserver().isAlive()) decor.getViewTreeObserver().removeOnGlobalLayoutListener(keyboardObserver);
            View activityDecor = MainActivity.this.getWindow().getDecorView();
            if (activityDecor.getViewTreeObserver().isAlive()) {
                activityDecor.getViewTreeObserver().removeOnGlobalLayoutListener(keyboardObserver);
            }
            decor.setOnApplyWindowInsetsListener(null);
            super.onStop();
        }
    }

    private void styleDialogFields(View view) {
        if (view instanceof EditText) {
            EditText input = (EditText) view;
            input.setTextColor(INK); input.setHintTextColor(MUTED);
            input.setTextSize("time".equals(input.getTag()) ? 32 : "amount".equals(input.getTag()) ? 26 : 16); input.setMinHeight(dp(52));
            input.setPadding(dp(14), dp(12), dp(14), dp(12));
            input.setBackground(rounded(PAPER, dp(14)));
            input.setBackgroundTintList(null);
            input.setHighlightColor(GREEN_PALE);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) styleDialogFields(group.getChildAt(i));
        }
    }

    private final class ComparisonBarView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int fillColor;
        private float fraction;
        ComparisonBarView(Activity activity, int fillColor) { super(activity); this.fillColor = fillColor; }
        void setFraction(float value) { fraction = Math.max(0, Math.min(1, value)); invalidate(); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float h = getHeight(), radius = h / 2f;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor("light".equals(themeStyle) ? Color.rgb(233, 236, 232) : Color.rgb(55, 60, 61));
            canvas.drawRoundRect(0, 0, getWidth(), h, radius, radius, paint);
            if (fraction <= 0) return;
            paint.setColor(fillColor);
            canvas.drawRoundRect(0, 0, Math.max(h, getWidth() * fraction), h, radius, radius, paint);
        }
    }

    private final class CategoryChartView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Map<String, Double> values;
        private ArrayList<String> names;
        CategoryChartView(Activity activity, Map<String, Double> values) {
            super(activity); this.values = values; this.names = new ArrayList<>(values.keySet());
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }
        void setData(Map<String, Double> newValues) {
            values = newValues;
            names = new ArrayList<>(newValues.keySet());
            invalidate();
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float diameter = Math.min(getWidth() - dp(36), getHeight() - dp(40));
            if (diameter <= 0) return;
            float centerX = getWidth() / 2f, centerY = getHeight() / 2f;
            RectF bounds = new RectF(centerX - diameter / 2, centerY - diameter / 2,
                    centerX + diameter / 2, centerY + diameter / 2);
            double total = 0; for (double value : values.values()) total += value;
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(22));
            paint.setStrokeCap(Paint.Cap.BUTT);
            if (total <= 0) {
                paint.setColor(GREEN_PALE); canvas.drawOval(bounds, paint);
            } else {
                float start = -90f;
                for (String name : names) {
                    float sweep = (float) (360 * values.get(name) / total);
                    CategoryRecord category = findCategoryByName(name, "expense");
                    paint.setColor(colorForCategory(category == null ? "" : category.id, name));
                    canvas.drawArc(bounds, start, sweep, false, paint); start += sweep;
                }
            }
            paint.setStyle(Paint.Style.FILL); paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.DEFAULT_BOLD); paint.setColor(MUTED);
            paint.setTextSize(sp(10));
            String caption = tr("Categories");
            while (paint.measureText(caption) > diameter * .7f && paint.getTextSize() > sp(7)) paint.setTextSize(paint.getTextSize() - 1);
            canvas.drawText(caption, centerX, centerY - dp(5), paint);
            paint.setTextSize(sp(16)); paint.setColor(INK);
            String amount = formatCurrency(total);
            while (paint.measureText(amount) > diameter * .72f && paint.getTextSize() > sp(8)) paint.setTextSize(paint.getTextSize() - 1);
            canvas.drawText(amount, centerX, centerY + dp(18), paint);
        }
        private float sp(float value) { return value * getResources().getDisplayMetrics().scaledDensity; }
        @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(resolveSize(dp(220), widthMeasureSpec), resolveSize(dp(210), heightMeasureSpec));
        }
    }

    private interface CalendarSelection { void onSelected(Calendar value); }

    private void setButtonStartIcon(TextView button, int resource, int color) {
        android.graphics.drawable.Drawable icon = getDrawable(resource);
        if (icon == null) return;
        icon.setTint(color);
        icon.setBounds(0, 0, dp(17), dp(17));
        button.setCompoundDrawablePadding(dp(6));
        button.setCompoundDrawables(icon, null, null, null);
        button.setPadding(dp(8), 0, dp(8), 0);
    }

    private String dateLabel(Calendar value) {
        return new SimpleDateFormat("EEE, d MMM yyyy", appLocale()).format(value.getTime());
    }

    private String timeLabel(Calendar value) {
        String pattern = "en".equals(languageCode) ? "h:mm a" : "HH:mm";
        return new SimpleDateFormat(pattern, appLocale()).format(value.getTime());
    }

    private void showCalendarPicker(Calendar initial, CalendarSelection selection) {
        Calendar picked = (Calendar) initial.clone();
        Calendar month = (Calendar) initial.clone();
        month.set(Calendar.DAY_OF_MONTH, 1);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        TextView preview = bodyText(dateLabel(picked), CONTROL_TEXT, 18);
        preview.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        preview.setGravity(Gravity.CENTER);
        preview.setPadding(dp(10), dp(16), dp(10), dp(16));
        preview.setBackground(rounded(CONTROL, dp(16)));
        content.addView(preview, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout monthHeader = new LinearLayout(this);
        monthHeader.setGravity(Gravity.CENTER_VERTICAL);
        monthHeader.setPadding(0, dp(12), 0, dp(4));
        Button previous = monthArrow("‹", tr("Previous month"));
        TextView monthTitle = bodyText("", INK, 16);
        monthTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        monthTitle.setGravity(Gravity.CENTER);
        Button next = monthArrow("›", tr("Next month"));
        monthHeader.addView(previous, new LinearLayout.LayoutParams(dp(40), dp(40)));
        monthHeader.addView(monthTitle, new LinearLayout.LayoutParams(0, -2, 1f));
        monthHeader.addView(next, new LinearLayout.LayoutParams(dp(40), dp(40)));
        content.addView(monthHeader);
        LinearLayout weekdays = new LinearLayout(this);
        content.addView(weekdays, new LinearLayout.LayoutParams(-1, dp(34)));
        LinearLayout weeks = new LinearLayout(this);
        weeks.setOrientation(LinearLayout.VERTICAL);
        content.addView(weeks);
        Button today = secondaryButton(tr("Today"));
        LinearLayout.LayoutParams todayParams = new LinearLayout.LayoutParams(-1, dp(44));
        todayParams.topMargin = dp(10);
        content.addView(today, todayParams);

        Runnable[] redraw = new Runnable[1];
        redraw[0] = () -> {
            monthTitle.setText(new SimpleDateFormat("MMMM yyyy", appLocale()).format(month.getTime()));
            preview.setText(dateLabel(picked));
            weekdays.removeAllViews(); weeks.removeAllViews();
            String[] names = DateFormatSymbols.getInstance(appLocale()).getShortWeekdays();
            int firstWeekday = Calendar.getInstance(appLocale()).getFirstDayOfWeek();
            for (int i = 0; i < 7; i++) {
                String name = names[(firstWeekday - 1 + i) % 7 + 1];
                TextView day = bodyText(name.length() > 2 ? name.substring(0, 2) : name, MUTED, 11);
                day.setGravity(Gravity.CENTER);
                weekdays.addView(day, new LinearLayout.LayoutParams(0, -1, 1f));
            }
            Calendar first = (Calendar) month.clone();
            first.set(Calendar.DAY_OF_MONTH, 1);
            int offset = (first.get(Calendar.DAY_OF_WEEK) - firstWeekday + 7) % 7;
            for (int week = 0; week < 6; week++) {
                LinearLayout row = new LinearLayout(this);
                weeks.addView(row, new LinearLayout.LayoutParams(-1, dp(42)));
                for (int column = 0; column < 7; column++) {
                    Calendar date = (Calendar) first.clone();
                    date.add(Calendar.DAY_OF_MONTH, week * 7 + column - offset);
                    boolean sameMonth = date.get(Calendar.MONTH) == month.get(Calendar.MONTH);
                    boolean selected = sameDate(date, picked);
                    TextView day = bodyText(String.valueOf(date.get(Calendar.DAY_OF_MONTH)),
                            selected ? CONTROL_TEXT : sameMonth ? INK : MUTED, 14);
                    day.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
                    day.setGravity(Gravity.CENTER);
                    day.setBackground(rounded(selected ? CONTROL : Color.TRANSPARENT, dp(20)));
                    day.setAlpha(sameMonth || selected ? 1f : .4f);
                    day.setContentDescription(dateLabel(date));
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(38), 1f);
                    params.setMargins(dp(1), dp(2), dp(1), dp(2));
                    row.addView(day, params);
                    day.setOnClickListener(view -> {
                        picked.set(Calendar.YEAR, date.get(Calendar.YEAR));
                        picked.set(Calendar.MONTH, date.get(Calendar.MONTH));
                        picked.set(Calendar.DAY_OF_MONTH, date.get(Calendar.DAY_OF_MONTH));
                        if (!sameMonth) { month.setTimeInMillis(date.getTimeInMillis()); month.set(Calendar.DAY_OF_MONTH, 1); }
                        redraw[0].run();
                    });
                }
            }
        };
        previous.setOnClickListener(view -> { month.add(Calendar.MONTH, -1); redraw[0].run(); });
        next.setOnClickListener(view -> { month.add(Calendar.MONTH, 1); redraw[0].run(); });
        today.setOnClickListener(view -> {
            Calendar now = Calendar.getInstance();
            picked.set(Calendar.DAY_OF_MONTH, 1);
            picked.set(Calendar.YEAR, now.get(Calendar.YEAR)); picked.set(Calendar.MONTH, now.get(Calendar.MONTH));
            picked.set(Calendar.DAY_OF_MONTH, now.get(Calendar.DAY_OF_MONTH));
            month.setTimeInMillis(picked.getTimeInMillis()); month.set(Calendar.DAY_OF_MONTH, 1);
            redraw[0].run();
        });
        redraw[0].run();
        new LedgerDialogBuilder().setTitle(tr("Date")).setView(content)
                .setPositiveButton(tr("Set date"), (dialog, which) -> selection.onSelected((Calendar) picked.clone()))
                .setNegativeButton(tr("Cancel"), null).show();
    }

    private boolean sameDate(Calendar left, Calendar right) {
        return left.get(Calendar.YEAR) == right.get(Calendar.YEAR)
                && left.get(Calendar.DAY_OF_YEAR) == right.get(Calendar.DAY_OF_YEAR);
    }

    private EditText timeField(int value) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setGravity(Gravity.CENTER);
        input.setText(String.format(Locale.US, "%02d", value));
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setSelectAllOnFocus(true);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2)});
        input.setTag("time");
        return input;
    }

    private int timeValue(EditText input, int maximum) {
        try { return Math.max(0, Math.min(maximum, Integer.parseInt(input.getText().toString()))); }
        catch (NumberFormatException ignored) { return 0; }
    }

    private void showTimePicker(Calendar target, Runnable onChange) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        TextView hint = bodyText(tr("24-hour time"), MUTED, 13);
        hint.setGravity(Gravity.CENTER);
        content.addView(hint, new LinearLayout.LayoutParams(-1, dp(28)));
        LinearLayout fields = new LinearLayout(this);
        fields.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(fields, new LinearLayout.LayoutParams(-1, -2));
        EditText hours = timeField(target.get(Calendar.HOUR_OF_DAY));
        EditText minutes = timeField(target.get(Calendar.MINUTE));
        EditText[] inputs = {hours, minutes};
        String[] labels = {tr("Hour"), tr("Minute")};
        for (int index = 0; index < 2; index++) {
            final int slot = index;
            LinearLayout column = new LinearLayout(this);
            column.setOrientation(LinearLayout.VERTICAL);
            column.setGravity(Gravity.CENTER_HORIZONTAL);
            TextView label = bodyText(labels[index], MUTED, 12); label.setGravity(Gravity.CENTER);
            column.addView(label, new LinearLayout.LayoutParams(-1, dp(30)));
            Button plus = secondaryButton("+");
            column.addView(plus, new LinearLayout.LayoutParams(-1, dp(44)));
            LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(-1, dp(78));
            inputParams.setMargins(0, dp(6), 0, dp(6));
            column.addView(inputs[index], inputParams);
            Button minus = secondaryButton("−");
            column.addView(minus, new LinearLayout.LayoutParams(-1, dp(44)));
            int modulus = index == 0 ? 24 : 60;
            plus.setContentDescription(tr("Increase") + " " + labels[index]);
            minus.setContentDescription(tr("Decrease") + " " + labels[index]);
            plus.setOnClickListener(view -> inputs[slot].setText(String.format(Locale.US, "%02d", (timeValue(inputs[slot], modulus - 1) + 1) % modulus)));
            minus.setOnClickListener(view -> inputs[slot].setText(String.format(Locale.US, "%02d", (timeValue(inputs[slot], modulus - 1) + modulus - 1) % modulus)));
            fields.addView(column, new LinearLayout.LayoutParams(0, -2, 1f));
            if (index == 0) {
                TextView colon = bodyText(":", MUTED, 30); colon.setGravity(Gravity.CENTER);
                fields.addView(colon, new LinearLayout.LayoutParams(dp(36), -2));
            }
        }
        LinearLayout presets = new LinearLayout(this);
        LinearLayout.LayoutParams presetsParams = new LinearLayout.LayoutParams(-1, dp(44));
        presetsParams.topMargin = dp(18);
        content.addView(presets, presetsParams);
        for (int value : new int[]{0, 15, 30, 45}) {
            Button preset = secondaryButton(String.format(Locale.US, ":%02d", value));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -1, 1f);
            params.setMargins(dp(2), 0, dp(2), 0);
            presets.addView(preset, params);
            preset.setOnClickListener(view -> minutes.setText(String.format(Locale.US, "%02d", value)));
        }
        Button now = secondaryButton(tr("Now"));
        LinearLayout.LayoutParams nowParams = new LinearLayout.LayoutParams(-1, dp(44));
        nowParams.topMargin = dp(10);
        content.addView(now, nowParams);
        now.setOnClickListener(view -> {
            Calendar current = Calendar.getInstance();
            hours.setText(String.format(Locale.US, "%02d", current.get(Calendar.HOUR_OF_DAY)));
            minutes.setText(String.format(Locale.US, "%02d", current.get(Calendar.MINUTE)));
            hours.setError(null); minutes.setError(null);
        });
        LedgerDialog dialog = new LedgerDialogBuilder().setTitle(tr("Time")).setView(content)
                .setNegativeButton(tr("Cancel"), null).setPositiveButton(tr("Set time"), null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            try {
                int hour = Integer.parseInt(hours.getText().toString());
                int minute = Integer.parseInt(minutes.getText().toString());
                if (hour < 0 || hour > 23 || minute < 0 || minute > 59) throw new NumberFormatException();
                target.set(Calendar.HOUR_OF_DAY, hour); target.set(Calendar.MINUTE, minute);
                onChange.run(); dialog.dismiss();
            } catch (NumberFormatException exception) {
                hours.setError(tr("Enter a valid time"));
            }
        }));
        dialog.show();
    }

    private void addFormLabel(LinearLayout form, String label) {
        TextView view = bodyText(label, MUTED, 13);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(16); params.bottomMargin = dp(7);
        form.addView(view, params);
    }

    private void showTransactionDialog(TransactionRecord editing) {
        boolean isEditing = editing != null;
        Calendar[] entryDateTime = {Calendar.getInstance()};
        if (isEditing) entryDateTime[0].setTimeInMillis(editing.timestamp);
        String initialType = isEditing ? editing.type : "expense";
        String[] selectedType = {initialType};
        String[] selectedCategoryId = {isEditing ? editing.categoryId : defaultCategoryId(initialType)};
        String[] selectedCategoryName = {isEditing ? editing.categoryName : defaultCategoryName(initialType)};
        boolean[] usingOther = {false};
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);

        LinearLayout typeOptions = new LinearLayout(this);
        typeOptions.setPadding(dp(4), dp(4), dp(4), dp(4));
        typeOptions.setBackground(rounded(PAPER, dp(16)));
        Button expenseOption = baseButton(); expenseOption.setText(tr("I spent"));
        Button incomeOption = baseButton(); incomeOption.setText(tr("I received"));
        typeOptions.addView(expenseOption, new LinearLayout.LayoutParams(0, dp(44), 1f));
        typeOptions.addView(incomeOption, new LinearLayout.LayoutParams(0, dp(44), 1f));
        form.addView(typeOptions, new LinearLayout.LayoutParams(-1, -2));
        Runnable updateType = () -> {
            boolean income = "income".equals(selectedType[0]);
            expenseOption.setTextColor(income ? MUTED : CONTROL_TEXT);
            incomeOption.setTextColor(income ? CONTROL_TEXT : MUTED);
            expenseOption.setBackground(rounded(income ? Color.TRANSPARENT : CONTROL, dp(12)));
            incomeOption.setBackground(rounded(income ? CONTROL : Color.TRANSPARENT, dp(12)));
        };
        updateType.run();

        addFormLabel(form, tr("Amount") + " · " + currencyCode);
        EditText amount = new EditText(this);
        amount.setSingleLine(true); amount.setHint("0.00"); amount.setTag("amount");
        amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        amount.setFilters(new InputFilter[]{new InputFilter.LengthFilter(18)});
        if (isEditing) amount.setText(amountForInput(editing.amount));
        form.addView(amount, new LinearLayout.LayoutParams(-1, dp(64)));

        addFormLabel(form, tr("Category"));
        Button categoryPicker = secondaryButton("");
        categoryPicker.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        categoryPicker.setPadding(dp(14), dp(10), dp(14), dp(10));
        form.addView(categoryPicker, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout otherFields = new LinearLayout(this);
        otherFields.setOrientation(LinearLayout.VERTICAL);
        addFormLabel(otherFields, tr("Name this category"));
        EditText otherName = new EditText(this);
        otherName.setSingleLine(true); otherName.setHint(tr("Category name (optional)"));
        otherName.setFilters(new InputFilter[]{new InputFilter.LengthFilter(36)});
        CategoryRecord initialCategory = findCategory(selectedCategoryId[0]);
        if (isEditing && initialCategory == null && !"Other".equalsIgnoreCase(selectedCategoryName[0])) {
            otherName.setText(selectedCategoryName[0]);
        }
        otherFields.addView(otherName, new LinearLayout.LayoutParams(-1, -2));
        Switch saveCategory = new Switch(this);
        saveCategory.setText(tr("Save this category for later?"));
        saveCategory.setTextSize(13); saveCategory.setTextColor(INK);
        saveCategory.setSwitchPadding(dp(10));
        saveCategory.setPadding(0, dp(12), 0, dp(4));
        otherFields.addView(saveCategory, new LinearLayout.LayoutParams(-1, -2));
        form.addView(otherFields, new LinearLayout.LayoutParams(-1, -2));
        Runnable updateCategory = () -> {
            CategoryRecord selected = findCategory(selectedCategoryId[0]);
            usingOther[0] = selected == null || isOtherCategory(selected);
            if (!usingOther[0]) selectedCategoryName[0] = selected.name;
            categoryPicker.setText((usingOther[0] ? tr("Other") : selected.isDefault ? tr(selected.name) : selected.name) + "   ▾");
            otherFields.setVisibility(usingOther[0] ? View.VISIBLE : View.GONE);
        };
        updateCategory.run();
        categoryPicker.setOnClickListener(view -> showCategoryPicker(selectedType[0], selectedCategoryId, selectedCategoryName, updateCategory));
        View.OnClickListener changeType = view -> {
            selectedType[0] = view == incomeOption ? "income" : "expense";
            if (!hasCategory(selectedCategoryId[0], selectedType[0])) {
                selectedCategoryId[0] = defaultCategoryId(selectedType[0]);
                selectedCategoryName[0] = defaultCategoryName(selectedType[0]);
            }
            updateType.run(); updateCategory.run();
        };
        expenseOption.setOnClickListener(changeType); incomeOption.setOnClickListener(changeType);

        addFormLabel(form, tr("Date"));
        Button date = secondaryButton(""); date.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        form.addView(date, new LinearLayout.LayoutParams(-1, dp(50)));
        addFormLabel(form, tr("Time"));
        Button time = secondaryButton(""); time.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        form.addView(time, new LinearLayout.LayoutParams(-1, dp(50)));
        Runnable updateDateTime = () -> {
            date.setText(dateLabel(entryDateTime[0])); time.setText(timeLabel(entryDateTime[0]));
            setButtonStartIcon(date, R.drawable.ic_calendar, SOFT_CONTROL_TEXT);
            setButtonStartIcon(time, R.drawable.ic_clock, SOFT_CONTROL_TEXT);
        };
        updateDateTime.run();
        date.setOnClickListener(view -> showCalendarPicker(entryDateTime[0], chosen -> {
            entryDateTime[0] = chosen; updateDateTime.run();
        }));
        time.setOnClickListener(view -> showTimePicker(entryDateTime[0], updateDateTime));
        addFormLabel(form, tr("Note") + " · " + tr("Optional"));
        EditText note = new EditText(this);
        note.setSingleLine(true); note.setHint(tr("e.g. Groceries or salary"));
        note.setFilters(new InputFilter[]{new InputFilter.LengthFilter(60)});
        if (isEditing) note.setText(editing.note);
        form.addView(note, new LinearLayout.LayoutParams(-1, -2));

        LedgerDialog dialog = new LedgerDialogBuilder().setTitle(isEditing ? tr("Edit transaction") : tr("Add transaction"))
                .setView(form).setNegativeButton(tr("Cancel"), null)
                .setPositiveButton(isEditing ? tr("Save changes") : tr("Save transaction"), null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            Double value = parseAmount(amount.getText().toString());
            if (value == null || Double.isNaN(value) || Double.isInfinite(value) || value <= 0) {
                amount.setError(tr("Enter an amount greater than zero.")); amount.requestFocus(); return;
            }
            if (usingOther[0]) {
                String name = otherName.getText().toString().trim();
                if (name.isEmpty()) name = "Other";
                CategoryRecord existing = findCategoryByName(name, selectedType[0]);
                if (saveCategory.isChecked() && existing == null) {
                    existing = new CategoryRecord(UUID.randomUUID().toString(), name, selectedType[0],
                            CATEGORY_COLORS[categories.size() % CATEGORY_COLORS.length], false);
                    categories.add(existing); persistCategories();
                }
                selectedCategoryId[0] = saveCategory.isChecked() && existing != null ? existing.id : "";
                selectedCategoryName[0] = name;
            }
            entryDateTime[0].set(Calendar.SECOND, 0); entryDateTime[0].set(Calendar.MILLISECOND, 0);
            long timestamp = entryDateTime[0].getTimeInMillis();
            if (isEditing) {
                editing.type = selectedType[0]; editing.amount = value; editing.timestamp = timestamp;
                editing.note = note.getText().toString().trim();
                editing.categoryId = selectedCategoryId[0]; editing.categoryName = selectedCategoryName[0];
            } else transactions.add(new TransactionRecord(UUID.randomUUID().toString(), selectedType[0], value, timestamp,
                    note.getText().toString().trim(), selectedCategoryId[0], selectedCategoryName[0]));
            selectedMonth.setTimeInMillis(timestamp); selectedMonth.set(Calendar.DAY_OF_MONTH, 1);
            preferences.edit().putLong(SELECTED_MONTH_KEY, selectedMonth.getTimeInMillis()).apply();
            persistTransactions(); refreshScreen(); dialog.dismiss();
            Toast.makeText(this, tr(isEditing ? "Transaction updated" : "Transaction saved"), Toast.LENGTH_SHORT).show();
        }));
        dialog.show();
    }

    private void confirmDelete(TransactionRecord transaction) {
        new LedgerDialogBuilder().setDestructive(true)
                .setTitle(tr("Delete this transaction?"))
                .setMessage(tr("This transaction will be removed from your activity and monthly totals. This cannot be undone."))
                .setNegativeButton(tr("Cancel"), null)
                .setPositiveButton(tr("Delete transaction"), (dialog, which) -> {
                    transactions.remove(transaction);
                    persistTransactions();
                    refreshScreen();
                    Toast.makeText(this, tr("Transaction deleted"), Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private Calendar loadSelectedMonth() {
        long stored = preferences.getLong(SELECTED_MONTH_KEY, -1L);
        Calendar month = stored > 0 ? Calendar.getInstance() : firstDayOfCurrentMonth();
        if (stored > 0) month.setTimeInMillis(stored);
        month.set(Calendar.DAY_OF_MONTH, 1);
        month.set(Calendar.HOUR_OF_DAY, 0);
        month.set(Calendar.MINUTE, 0);
        month.set(Calendar.SECOND, 0);
        month.set(Calendar.MILLISECOND, 0);
        return month;
    }

    private Calendar firstDayOfCurrentMonth() {
        Calendar month = Calendar.getInstance();
        month.set(Calendar.DAY_OF_MONTH, 1);
        month.set(Calendar.HOUR_OF_DAY, 0);
        month.set(Calendar.MINUTE, 0);
        month.set(Calendar.SECOND, 0);
        month.set(Calendar.MILLISECOND, 0);
        return month;
    }

    private ArrayList<TransactionRecord> loadTransactionsOrMigrate() {
        String saved = preferences.getString(TRANSACTIONS_KEY, null);
        if (saved != null) {
            ArrayList<TransactionRecord> loaded = new ArrayList<>();
            try {
                JSONArray array = new JSONArray(saved);
                for (int index = 0; index < array.length(); index++) {
                    JSONObject object = array.optJSONObject(index);
                    if (object == null) continue;
                    String type = object.optString("type", "expense");
                    double amount = object.optDouble("amount", Double.NaN);
                    long timestamp = object.optLong("timestamp", 0L);
                    if (!("income".equals(type) || "expense".equals(type))
                            || Double.isNaN(amount) || Double.isInfinite(amount) || amount <= 0 || timestamp <= 0) {
                        continue;
                    }
                    String categoryName = object.has("categoryName")
                            ? object.optString("categoryName", "Other") : "Other";
                    String categoryId = object.has("categoryId")
                            ? object.optString("categoryId", "") : type + "-other";
                    loaded.add(new TransactionRecord(object.optString("id", UUID.randomUUID().toString()),
                            type, amount, timestamp, object.optString("note", ""), categoryId, categoryName));
                }
            } catch (JSONException ignored) {
            Toast.makeText(this, tr("Saved transactions could not be read."), Toast.LENGTH_LONG).show();
            }
            return loaded;
        }

        ArrayList<TransactionRecord> migrated = migrateFirstVersionTotals();
        persistTransactions(migrated);
        return migrated;
    }

    private ArrayList<TransactionRecord> migrateFirstVersionTotals() {
        ArrayList<TransactionRecord> migrated = new ArrayList<>();
        Set<String> oldKeys = new HashSet<>();
        for (String key : preferences.getAll().keySet()) {
            if (key.startsWith("spending_")) oldKeys.add(key);
        }
        for (String key : oldKeys) {
            String[] parts = key.split("_");
            if (parts.length != 3) continue;
            try {
                int year = Integer.parseInt(parts[1]);
                int month = Integer.parseInt(parts[2]);
                Object stored = preferences.getAll().get(key);
                if (!(stored instanceof Long)) continue;
                double amount = Double.longBitsToDouble((Long) stored);
                if (Double.isNaN(amount) || Double.isInfinite(amount) || amount <= 0) continue;
                Calendar timestamp = Calendar.getInstance();
                timestamp.clear();
                timestamp.set(year, month, 1, 12, 0, 0);
                migrated.add(new TransactionRecord(UUID.randomUUID().toString(), "expense", amount,
                        timestamp.getTimeInMillis(), "Earlier spending total", "expense-other", "Other"));
            } catch (NumberFormatException ignored) {
                // Ignore any unrelated preference that happens to share the old key prefix.
            }
        }
        return migrated;
    }

    private void persistTransactions() {
        persistTransactions(transactions);
    }

    private void persistTransactions(ArrayList<TransactionRecord> records) {
        JSONArray array = new JSONArray();
        for (TransactionRecord transaction : records) {
            JSONObject object = new JSONObject();
            try {
                object.put("id", transaction.id);
                object.put("type", transaction.type);
                object.put("amount", transaction.amount);
                object.put("timestamp", transaction.timestamp);
                object.put("note", transaction.note);
                object.put("categoryId", transaction.categoryId);
                object.put("categoryName", transaction.categoryName);
                array.put(object);
            } catch (JSONException ignored) {
                // These fields are primitive values and should always serialize.
            }
        }
        preferences.edit().putString(TRANSACTIONS_KEY, array.toString()).apply();
    }

    private Double parseAmount(String input) {
        try {
            return Double.parseDouble(input.trim().replace(',', '.'));
        } catch (NumberFormatException error) {
            return null;
        }
    }

    private String amountForInput(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private String formatCurrency(double value) {
        NumberFormat format = NumberFormat.getCurrencyInstance(appLocale());
        Currency currency = Currency.getInstance(currencyCode);
        format.setCurrency(currency);
        int digits = Math.max(0, currency.getDefaultFractionDigits());
        format.setMaximumFractionDigits(digits);
        format.setMinimumFractionDigits(digits);
        return format.format(value);
    }

    private void fitAmountText(TextView view, int preferredSize) {
        view.setSingleLine(true);
        view.addOnLayoutChangeListener((changed, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            int width = view.getWidth() - view.getPaddingLeft() - view.getPaddingRight();
            if (width <= 0) return;
            float scaledDensity = getResources().getDisplayMetrics().scaledDensity;
            float size = preferredSize * scaledDensity;
            Paint measure = new Paint(view.getPaint());
            measure.setTextSize(size);
            while (measure.measureText(view.getText().toString()) > width && size > 9 * scaledDensity) {
                size -= scaledDensity;
                measure.setTextSize(size);
            }
            if (Math.abs(view.getTextSize() - size) > .5f) view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, size);
        });
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(radius);
        return shape;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class TransactionRecord {
        final String id;
        String type;
        double amount;
        long timestamp;
        String note;
        String categoryId;
        String categoryName;

        TransactionRecord(String id, String type, double amount, long timestamp, String note,
                          String categoryId, String categoryName) {
            this.id = id;
            this.type = type;
            this.amount = amount;
            this.timestamp = timestamp;
            this.note = note == null ? "" : note;
            this.categoryId = categoryId == null ? "" : categoryId;
            this.categoryName = categoryName == null ? "" : categoryName;
        }
    }

    private static final class CategoryRecord {
        final String id;
        String name;
        String type;
        int color;
        final boolean isDefault;

        CategoryRecord(String id, String name, String type, int color, boolean isDefault) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.color = color;
            this.isDefault = isDefault;
        }
    }
}
