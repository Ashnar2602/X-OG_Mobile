package emu.xbox.og;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity implements TextureView.SurfaceTextureListener, TouchControllerView.Listener {
    private static final String TAG = "X-OG Mobile";
    private static final int REQ_MCPX = 100;
    private static final int REQ_BIOS = 101;
    private static final int REQ_HDD = 102;
    private static final int REQ_LIBRARY_TREE = 105;
    private static final String PREFS = "xboxemu_files";
    private static final String PREF_DISC_URI = "disc_uri";
    private static final String PREF_DISC_NAME = "disc_name";
    private static final String PREF_DISC_SIZE = "disc_size";
    private static final String PREF_LIBRARY_TREE_URI = "library_tree_uri";
    private static final String PREF_RENDERER = "renderer";
    private static final String PREF_AUDIO_VOLUME = "audio_volume";
    private static final String PREF_AUDIO_MUTED = "audio_muted";
    private static final String PREF_SKIP_BOOT = "skip_boot";
    private static final String PREF_AVPACK = "avpack";
    private static final String PREF_AUTO_PAUSE_MS = "auto_pause_ms";
    private static final String PREF_RAWG_API_KEY = "rawg_api_key";
    private static final String FALLBACK_RAWG_API_KEY = "49a269369d354baa8d31978bb9e4b4a4";
    private static final int XDVD_SECTOR_SIZE = 2048;
    private static final byte[] XDVD_MAGIC = "MICROSOFT*XBOX*MEDIA".getBytes(StandardCharsets.US_ASCII);
    private static final String[] RENDERER_LABELS = {"Vulkan", "OpenGL-GLES"};
    private static final String[] RENDERER_VALUES = {"vulkan", "opengl"};
    private static final String[] AVPACK_LABELS = {"HDTV", "Composite", "VGA", "SCART", "S-Video", "RFU", "None"};
    private static final String[] AVPACK_VALUES = {"hdtv", "composite", "vga", "scart", "svideo", "rfu", "none"};
    private static final String[] AUTO_PAUSE_LABELS = {"Off", "1 min", "5 min", "10 min"};
    private static final int[] AUTO_PAUSE_VALUES_MS = {0, 60_000, 300_000, 600_000};

    private final Map<Integer, File> files = new HashMap<>();
    private final List<LibraryGame> libraryGames = new ArrayList<>();
    private DiscSelection discSelection;
    private TextView statusText;
    private TextView logText;
    private View setupPanel;
    private View filesControls;
    private View settingsControls;
    private View infoFrame;
    private Button togglePanelButton;
    private Button filesTabButton;
    private Button settingsTabButton;
    private Spinner rendererSpinner;
    private Spinner avpackSpinner;
    private Spinner autoPauseSpinner;
    private SeekBar volumeSeek;
    private TextView volumeLabel;
    private CheckBox muteCheck;
    private CheckBox skipBootCheck;
    private EditText rawgApiKeyInput;
    private TextureView renderSurface;
    private Surface currentSurface;
    private int surfaceWidth;
    private int surfaceHeight;
    private boolean setupPanelVisible = true;
    private boolean libraryPanelVisible;
    private boolean settingsBinding;
    private boolean lifecyclePausedCore;
    private boolean emulationPaused;
    private volatile boolean importInProgress;
    private ParcelFileDescriptor launchDiscPfd;
    private TextView statusIcon;
    private Button fixNowButton;
    private View systemFilesControls;
    private View libraryPanel;
    private TextView libraryStatusText;
    private LinearLayout gameList;
    private TextView gameInfoTitle;
    private TextView gameInfoMeta;
    private ImageView gameInfoCover;
    private TextView gameInfoDescription;
    private TextView gameInfoCredits;
    private Button pauseButton;
    private View pauseOverlay;
    private View idleOverlay;
    private Handler uiHandler;
    private final Runnable idleAutoPauseRunnable = () -> {
        if (NativeBridge.nativeIsRunning() && !emulationPaused) {
            requestPause("Auto-paused after input inactivity.", true);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        uiHandler = new Handler(Looper.getMainLooper());
        enterImmersiveMode();

        renderSurface = findViewById(R.id.renderSurface);
        renderSurface.setSurfaceTextureListener(this);
        statusIcon = findViewById(R.id.statusIcon);
        statusText = findViewById(R.id.statusText);
        fixNowButton = findViewById(R.id.fixNowButton);
        logText = findViewById(R.id.logText);
        setupPanel = findViewById(R.id.setupPanel);
        libraryPanel = findViewById(R.id.libraryPanel);
        filesControls = findViewById(R.id.filesControls);
        settingsControls = findViewById(R.id.settingsControls);
        infoFrame = findViewById(R.id.infoFrame);
        systemFilesControls = findViewById(R.id.systemFilesControls);
        libraryStatusText = findViewById(R.id.libraryStatusText);
        gameList = findViewById(R.id.gameList);
        togglePanelButton = findViewById(R.id.togglePanelButton);
        filesTabButton = findViewById(R.id.filesTabButton);
        settingsTabButton = findViewById(R.id.settingsTabButton);
        rendererSpinner = findViewById(R.id.rendererSpinner);
        avpackSpinner = findViewById(R.id.avpackSpinner);
        autoPauseSpinner = findViewById(R.id.autoPauseSpinner);
        volumeSeek = findViewById(R.id.volumeSeek);
        volumeLabel = findViewById(R.id.volumeLabel);
        muteCheck = findViewById(R.id.muteCheck);
        skipBootCheck = findViewById(R.id.skipBootCheck);
        rawgApiKeyInput = findViewById(R.id.rawgApiKeyInput);
        gameInfoTitle = findViewById(R.id.gameInfoTitle);
        gameInfoMeta = findViewById(R.id.gameInfoMeta);
        gameInfoCover = findViewById(R.id.gameInfoCover);
        gameInfoDescription = findViewById(R.id.gameInfoDescription);
        gameInfoCredits = findViewById(R.id.gameInfoCredits);
        TouchControllerView touch = findViewById(R.id.touchController);
        touch.setListener(this);
        pauseButton = findViewById(R.id.pauseButton);
        pauseOverlay = findViewById(R.id.pauseOverlay);
        idleOverlay = findViewById(R.id.idleOverlay);

        bindPicker(R.id.pickMcpx, REQ_MCPX);
        bindPicker(R.id.pickBios, REQ_BIOS);
        bindPicker(R.id.pickHdd, REQ_HDD);
        findViewById(R.id.pickDisc).setOnClickListener(v -> showLibraryPanel());

        Button launch = findViewById(R.id.launchButton);
        launch.setOnClickListener(v -> launchEmulator());
        togglePanelButton.setOnClickListener(v -> handleTogglePanelButton());
        pauseButton.setOnClickListener(v -> handlePauseButton());
        findViewById(R.id.resumeYesButton).setOnClickListener(v -> requestResume("Resume confirmed."));
        findViewById(R.id.resumeNoButton).setOnClickListener(v -> hidePauseOverlay());
        findViewById(R.id.quitGameButton).setOnClickListener(v -> quitGameToHome());
        filesTabButton.setOnClickListener(v -> showFilesTab(true));
        settingsTabButton.setOnClickListener(v -> showFilesTab(false));
        fixNowButton.setOnClickListener(v -> showSystemFilesScreen());
        findViewById(R.id.configureSystemFilesButton).setOnClickListener(v -> showSystemFilesScreen());
        findViewById(R.id.backToSettingsButton).setOnClickListener(v -> showSettingsMain());
        findViewById(R.id.scanFolderButton).setOnClickListener(v -> openLibraryFolderPicker());
        bindSettingsControls();
        findViewById(R.id.stopButton).setOnClickListener(v -> stopEmulator());
        findViewById(R.id.ejectDiscButton).setOnClickListener(v -> ejectDiscSelection());

        appendLog(NativeBridge.nativeInit(
                getFilesDir().getAbsolutePath(),
                getCacheDir().getAbsolutePath(),
                getApplicationInfo().nativeLibraryDir));
        loadExistingFiles();
        scanSavedLibraryFolder(false);
        refreshStatus();
        showFilesTab(true);
        updateEmulationUi();
        if (discSelection != null) {
            loadSelectedGameInfoAsync(discSelection);
        }

        if (getIntent().getBooleanExtra("autolaunch", false)) {
            uiHandler.postDelayed(this::launchEmulator, 1500);
        }
    }

    @SuppressWarnings("deprecation")
    private void enterImmersiveMode() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams attrs = getWindow().getAttributes();
            attrs.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(attrs);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getDecorView().getWindowInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            enterImmersiveMode();
        }
    }

    private void bindSettingsControls() {
        settingsBinding = true;
        bindSpinner(rendererSpinner, RENDERER_LABELS,
                indexOf(RENDERER_VALUES, settingString(PREF_RENDERER, "vulkan")));
        bindSpinner(avpackSpinner, AVPACK_LABELS,
                indexOf(AVPACK_VALUES, settingString(PREF_AVPACK, "hdtv")));
        bindSpinner(autoPauseSpinner, AUTO_PAUSE_LABELS,
                indexOfInt(AUTO_PAUSE_VALUES_MS, settingInt(PREF_AUTO_PAUSE_MS, 0)));
        volumeSeek.setProgress(settingInt(PREF_AUDIO_VOLUME, 100));
        muteCheck.setChecked(settingBoolean(PREF_AUDIO_MUTED, false));
        skipBootCheck.setChecked(settingBoolean(PREF_SKIP_BOOT, false));
        rawgApiKeyInput.setText(settingString(PREF_RAWG_API_KEY, ""));
        updateVolumeLabel();
        settingsBinding = false;

        rendererSpinner.setOnItemSelectedListener(new SimpleItemSelectedListener(position ->
                saveString(PREF_RENDERER, RENDERER_VALUES[position])));
        avpackSpinner.setOnItemSelectedListener(new SimpleItemSelectedListener(position ->
                saveString(PREF_AVPACK, AVPACK_VALUES[position])));
        autoPauseSpinner.setOnItemSelectedListener(new SimpleItemSelectedListener(position -> {
            saveInt(PREF_AUTO_PAUSE_MS, AUTO_PAUSE_VALUES_MS[position]);
            scheduleIdleAutoPause();
        }));
        volumeSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (settingsBinding) {
                    return;
                }
                saveInt(PREF_AUDIO_VOLUME, progress);
                updateVolumeLabel();
                NativeBridge.nativeSetAudio(progress, muteCheck.isChecked());
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        muteCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (settingsBinding) {
                return;
            }
            saveBoolean(PREF_AUDIO_MUTED, isChecked);
            updateVolumeLabel();
            NativeBridge.nativeSetAudio(volumeSeek.getProgress(), isChecked);
        });
        skipBootCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!settingsBinding) {
                saveBoolean(PREF_SKIP_BOOT, isChecked);
            }
        });
        rawgApiKeyInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                if (!settingsBinding) {
                    saveString(PREF_RAWG_API_KEY, s.toString().trim());
                }
            }
        });
    }

    private void bindSpinner(Spinner spinner, String[] labels, int selected) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(Math.max(0, selected));
    }

    private void showFilesTab(boolean showFiles) {
        filesControls.setVisibility(showFiles ? View.VISIBLE : View.GONE);
        settingsControls.setVisibility(showFiles ? View.GONE : View.VISIBLE);
        systemFilesControls.setVisibility(View.GONE);
        infoFrame.setVisibility(showFiles ? View.VISIBLE : View.GONE);
        filesTabButton.setEnabled(!showFiles);
        settingsTabButton.setEnabled(showFiles);
    }

    private void showSettingsMain() {
        filesControls.setVisibility(View.GONE);
        settingsControls.setVisibility(View.VISIBLE);
        systemFilesControls.setVisibility(View.GONE);
        infoFrame.setVisibility(View.GONE);
        filesTabButton.setEnabled(true);
        settingsTabButton.setEnabled(false);
    }

    private void showSystemFilesScreen() {
        filesControls.setVisibility(View.GONE);
        settingsControls.setVisibility(View.GONE);
        systemFilesControls.setVisibility(View.VISIBLE);
        infoFrame.setVisibility(View.GONE);
        filesTabButton.setEnabled(true);
        settingsTabButton.setEnabled(true);
    }

    private SharedPreferences settings() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private String settingString(String key, String fallback) {
        return settings().getString(key, fallback);
    }

    private int settingInt(String key, int fallback) {
        return settings().getInt(key, fallback);
    }

    private boolean settingBoolean(String key, boolean fallback) {
        return settings().getBoolean(key, fallback);
    }

    private void saveString(String key, String value) {
        settings().edit().putString(key, value).apply();
    }

    private void saveInt(String key, int value) {
        settings().edit().putInt(key, value).apply();
    }

    private void saveBoolean(String key, boolean value) {
        settings().edit().putBoolean(key, value).apply();
    }

    private void updateVolumeLabel() {
        String suffix = muteCheck.isChecked() ? " (muted)" : "";
        volumeLabel.setText("Volume: " + volumeSeek.getProgress() + "%" + suffix);
    }

    private static int indexOf(String[] values, String value) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(value)) {
                return i;
            }
        }
        return 0;
    }

    private static int indexOfInt(int[] values, int value) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == value) {
                return i;
            }
        }
        return 0;
    }

    private void loadExistingFiles() {
        File dir = new File(getFilesDir(), "xbox-files");
        File mcpx = new File(dir, "mcpx.bin");
        File bios = new File(dir, "bios.bin");
        File hdd = new File(dir, "hdd.img");
        if (mcpx.isFile()) files.put(REQ_MCPX, mcpx);
        if (bios.isFile()) files.put(REQ_BIOS, bios);
        if (hdd.isFile()) files.put(REQ_HDD, hdd);
        loadDirectDiscSelection();
    }

    private void loadDirectDiscSelection() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String uriValue = prefs.getString(PREF_DISC_URI, null);
        if (uriValue == null || uriValue.isEmpty()) {
            return;
        }
        discSelection = new DiscSelection(
                Uri.parse(uriValue),
                prefs.getString(PREF_DISC_NAME, "selected disc"),
                prefs.getLong(PREF_DISC_SIZE, -1));
    }

    @SuppressWarnings("deprecation")
    private void bindPicker(int buttonId, int requestCode) {
        findViewById(buttonId).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            appendLog("Opening " + labelFor(requestCode) + " picker...");
            try {
                startActivityForResult(intent, requestCode);
            } catch (ActivityNotFoundException e) {
                appendLog("Picker unavailable: " + e.getMessage());
            }
        });
    }

    @SuppressWarnings("deprecation")
    private void openLibraryFolderPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQ_LIBRARY_TREE);
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        tryPersistableReadPermission(data, uri);
        if (requestCode == REQ_LIBRARY_TREE) {
            selectLibraryFolder(data, uri);
            return;
        }
        if (importInProgress) {
            appendLog("Import already in progress. Wait for it to finish before picking another file.");
            return;
        }
        importInProgress = true;
        appendLog("Importing " + labelFor(requestCode) + " from " + safeDisplayName(uri) + "...");
        new Thread(() -> {
            try {
                File copied = copyToPrivateFile(requestCode, uri);
                runOnUiThread(() -> {
                    files.put(requestCode, copied);
                    appendLog("Imported " + copied.getName() + " (" + humanSize(copied.length()) + ")");
                    refreshStatus();
                });
            } catch (Exception e) {
                runOnUiThread(() -> appendLog("Import failed: " + e.getMessage()));
            } finally {
                importInProgress = false;
            }
        }, "xbox-file-import").start();
    }

    private File copyToPrivateFile(int requestCode, Uri uri) throws Exception {
        long expectedSize = displaySize(uri);
        String suffix;
        switch (requestCode) {
            case REQ_MCPX: suffix = "mcpx.bin"; break;
            case REQ_BIOS: suffix = "bios.bin"; break;
            case REQ_HDD: suffix = "hdd.img"; break;
            default: suffix = "picked.bin";
        }
        File dir = new File(getFilesDir(), "xbox-files");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Unable to create " + dir);
        }
        File out = new File(dir, suffix);
        File tmp = new File(dir, suffix + ".part");
        if (tmp.exists() && !tmp.delete()) {
            throw new IllegalStateException("Unable to remove stale partial copy " + tmp.getName());
        }

        long copied = copyViaFileDescriptor(uri, tmp);
        if (expectedSize > 0 && copied != expectedSize) {
            tmp.delete();
            throw new IllegalStateException("Copy incomplete for " + suffix +
                    ": expected " + humanSize(expectedSize) +
                    ", copied " + humanSize(copied));
        }
        if (copied <= 0) {
            tmp.delete();
            throw new IllegalStateException("Copied file is empty: " + suffix);
        }
        if (out.exists() && !out.delete()) {
            tmp.delete();
            throw new IllegalStateException("Unable to replace old " + suffix);
        }
        if (!tmp.renameTo(out)) {
            tmp.delete();
            throw new IllegalStateException("Unable to finalize imported " + suffix);
        }
        return out;
    }

    private void selectLibraryFolder(Intent data, Uri treeUri) {
        int flags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
        if (flags == 0) {
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
        }
        try {
            getContentResolver().takePersistableUriPermission(treeUri, flags);
        } catch (Exception e) {
            appendLog("Library permission warning: " + e.getMessage());
        }
        settings().edit().putString(PREF_LIBRARY_TREE_URI, treeUri.toString()).apply();
        scanLibraryFolder(treeUri, true);
    }

    private void scanSavedLibraryFolder(boolean logMissing) {
        String value = settings().getString(PREF_LIBRARY_TREE_URI, null);
        if (value == null || value.isEmpty()) {
            libraryStatusText.setText(R.string.no_games_found);
            return;
        }
        try {
            scanLibraryFolder(Uri.parse(value), logMissing);
        } catch (Exception e) {
            if (logMissing) {
                appendLog("Library rescan failed: " + e.getMessage());
            }
            libraryStatusText.setText(R.string.no_games_found);
        }
    }

    private void scanLibraryFolder(Uri treeUri, boolean appendResultLog) {
        libraryGames.clear();
        String treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri);
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocumentId);
        String[] projection = {
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE
        };
        try (Cursor cursor = getContentResolver().query(childrenUri, projection, null, null, null)) {
            if (cursor != null) {
                int nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME);
                int idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID);
                int mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE);
                int sizeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE);
                while (cursor.moveToNext()) {
                    String name = nameIndex >= 0 ? cursor.getString(nameIndex) : null;
                    String documentId = idIndex >= 0 ? cursor.getString(idIndex) : null;
                    String mime = mimeIndex >= 0 ? cursor.getString(mimeIndex) : null;
                    long size = -1;
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                        size = cursor.getLong(sizeIndex);
                    }
                    if (name == null || documentId == null ||
                            DocumentsContract.Document.MIME_TYPE_DIR.equals(mime) ||
                            !isGameDiscName(name)) {
                        continue;
                    }
                    Uri gameUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId);
                    libraryGames.add(new LibraryGame(gameUri, name, size));
                }
            }
        } catch (Exception e) {
            libraryGames.clear();
            libraryStatusText.setText("Library scan failed: " + e.getMessage());
            appendLog("Library scan failed: " + e.getMessage());
            renderLibraryGames();
            return;
        }
        libraryGames.sort((left, right) -> left.name.compareToIgnoreCase(right.name));
        renderLibraryGames();
        if (appendResultLog) {
            appendLog("Library scan complete: " + libraryGames.size() + " ISO/XISO game(s) found.");
        }
    }

    private boolean isGameDiscName(String name) {
        String lower = name.toLowerCase(java.util.Locale.US);
        return lower.endsWith(".iso") || lower.endsWith(".xiso");
    }

    private void renderLibraryGames() {
        gameList.removeAllViews();
        if (libraryGames.isEmpty()) {
            libraryStatusText.setText(R.string.no_games_found);
            return;
        }
        libraryStatusText.setText(libraryGames.size() + " game(s) found");
        for (LibraryGame game : libraryGames) {
            gameList.addView(createGameRow(game));
        }
    }

    private View createGameRow(LibraryGame game) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setClickable(true);
        row.setPadding(12, 10, 12, 10);
        row.setBackgroundColor(getColor(isSelectedGame(game) ? R.color.bg : R.color.panel));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, 8);
        row.setLayoutParams(params);

        TextView title = new TextView(this);
        title.setText(game.name);
        title.setTextColor(getColor(R.color.text));
        title.setTextSize(14);
        title.setSingleLine(false);
        row.addView(title);

        TextView meta = new TextView(this);
        meta.setText(isSelectedGame(game) ? getString(R.string.game_selected) : humanSize(game.size));
        meta.setTextColor(getColor(isSelectedGame(game) ? R.color.accent : R.color.muted));
        meta.setTextSize(11);
        row.addView(meta);

        row.setOnClickListener(v -> selectLibraryGame(game));
        return row;
    }

    private void selectLibraryGame(LibraryGame game) {
        discSelection = new DiscSelection(game.uri, game.name, game.size);
        closeLaunchDiscPfd();
        settings().edit()
                .putString(PREF_DISC_URI, game.uri.toString())
                .putString(PREF_DISC_NAME, game.name)
                .putLong(PREF_DISC_SIZE, game.size)
                .apply();
        appendLog("Game selected: " + game.name + " (" + humanSize(game.size) + ")");
        renderLibraryGames();
        loadSelectedGameInfoAsync(discSelection);
    }

    private boolean isSelectedGame(LibraryGame game) {
        return discSelection != null && discSelection.uri.equals(game.uri);
    }

    private void loadSelectedGameInfoAsync(DiscSelection selection) {
        if (selection == null) {
            clearGameInfo();
            return;
        }
        setGameInfoLoading(selection.name);
        new Thread(() -> {
            String discTitle = extractXboxTitleName(selection);
            String fileTitle = stripDiscExtension(selection.name);
            if (discTitle == null || discTitle.isEmpty()) {
                discTitle = fileTitle;
            }
            final String resolvedTitle = discTitle;
            RawgGameInfo rawg = fetchRawgWithFallbacks(discTitle, fileTitle);
            runOnUiThread(() -> showGameInfo(resolvedTitle, rawg));
        }, "xbox-game-metadata").start();
    }

    private void setGameInfoLoading(String fallbackName) {
        gameInfoTitle.setText(stripDiscExtension(fallbackName));
        gameInfoMeta.setText("Reading disc metadata...");
        gameInfoCover.setVisibility(View.GONE);
        gameInfoDescription.setText("");
        gameInfoCredits.setText("");
    }

    private void clearGameInfo() {
        gameInfoTitle.setText(R.string.game_info_empty);
        gameInfoMeta.setText("");
        gameInfoCover.setVisibility(View.GONE);
        gameInfoCover.setImageDrawable(null);
        gameInfoDescription.setText("");
        gameInfoCredits.setText("");
    }

    private void showGameInfo(String title, RawgGameInfo rawg) {
        gameInfoTitle.setText(rawg != null && rawg.name != null ? rawg.name : title);
        String year = rawg != null && rawg.released != null && rawg.released.length() >= 4
                ? rawg.released.substring(0, 4) : "Unknown year";
        String genres = rawg != null && rawg.genres != null && !rawg.genres.isEmpty()
                ? String.join(" / ", rawg.genres) : "Unknown genre";
        gameInfoMeta.setText(year + " / " + genres);
        gameInfoDescription.setText(rawg != null && rawg.description != null && !rawg.description.isEmpty()
                ? rawg.description : "No RAWG description available yet.");
        String publisher = rawg != null && rawg.publishers != null && !rawg.publishers.isEmpty()
                ? String.join(", ", rawg.publishers) : "Unknown publisher";
        String developer = rawg != null && rawg.developers != null && !rawg.developers.isEmpty()
                ? String.join(", ", rawg.developers) : "Unknown developer";
        gameInfoCredits.setText(publisher + " / " + developer);
        if (rawg != null && rawg.cover != null) {
            gameInfoCover.setImageBitmap(rawg.cover);
            gameInfoCover.setVisibility(View.VISIBLE);
        } else {
            gameInfoCover.setVisibility(View.GONE);
            gameInfoCover.setImageDrawable(null);
        }
    }

    private RawgGameInfo fetchRawgWithFallbacks(String primaryTitle, String fallbackTitle) {
        RawgGameInfo primary = fetchRawgWithFallback(primaryTitle);
        if (primary != null || sameMetadataQuery(primaryTitle, fallbackTitle)) {
            return primary;
        }
        Log.i(TAG, "RAWG metadata not found for extracted title; trying filename: " + fallbackTitle);
        return fetchRawgWithFallback(fallbackTitle);
    }

    private RawgGameInfo fetchRawgWithFallback(String title) {
        RawgGameInfo cached = loadCachedRawgGameInfo(title);
        if (cached != null) {
            Log.i(TAG, "Using cached metadata for " + title);
            return cached;
        }

        String userKey = settingString(PREF_RAWG_API_KEY, "").trim();
        if (!userKey.isEmpty()) {
            RawgGameInfo info = fetchRawgGameInfo(title, userKey);
            if (info != null) {
                saveCachedRawgGameInfo(title, info);
                return info;
            }
        }
        RawgGameInfo info = fetchRawgGameInfo(title, FALLBACK_RAWG_API_KEY);
        if (info != null) {
            saveCachedRawgGameInfo(title, info);
        }
        return info;
    }

    private static boolean sameMetadataQuery(String first, String second) {
        if (first == null || second == null) {
            return first == second;
        }
        return first.trim().equalsIgnoreCase(second.trim());
    }

    private RawgGameInfo loadCachedRawgGameInfo(String title) {
        if (title == null || title.isEmpty()) {
            return null;
        }
        File jsonFile = rawgCacheJsonFile(title);
        if (!jsonFile.isFile()) {
            return null;
        }
        try (FileInputStream input = new FileInputStream(jsonFile)) {
            JSONObject json = new JSONObject(readUtf8(input));
            RawgGameInfo info = new RawgGameInfo();
            info.name = json.optString("name", title);
            info.released = json.optString("released", "");
            info.description = json.optString("description", "");
            info.coverUrl = json.optString("cover_url", "");
            info.genres = jsonStringList(json.optJSONArray("genres"));
            info.publishers = jsonStringList(json.optJSONArray("publishers"));
            info.developers = jsonStringList(json.optJSONArray("developers"));

            File coverFile = rawgCacheCoverFile(title);
            if (coverFile.isFile()) {
                info.cover = BitmapFactory.decodeFile(coverFile.getAbsolutePath());
            }
            return info;
        } catch (Exception e) {
            Log.w(TAG, "Could not read cached game metadata for " + title, e);
            return null;
        }
    }

    private void saveCachedRawgGameInfo(String title, RawgGameInfo info) {
        if (title == null || title.isEmpty() || info == null) {
            return;
        }
        File dir = rawgCacheDir();
        if (!dir.isDirectory() && !dir.mkdirs()) {
            Log.w(TAG, "Could not create RAWG metadata cache directory: " + dir);
            return;
        }
        try {
            JSONObject json = new JSONObject();
            json.put("cache_version", 1);
            json.put("lookup_title", title);
            json.put("name", info.name == null ? "" : info.name);
            json.put("released", info.released == null ? "" : info.released);
            json.put("description", info.description == null ? "" : info.description);
            json.put("cover_url", info.coverUrl == null ? "" : info.coverUrl);
            json.put("genres", stringListToJson(info.genres));
            json.put("publishers", stringListToJson(info.publishers));
            json.put("developers", stringListToJson(info.developers));

            try (FileOutputStream output = new FileOutputStream(rawgCacheJsonFile(title))) {
                output.write(json.toString().getBytes(StandardCharsets.UTF_8));
            }
            if (info.cover != null) {
                try (FileOutputStream output = new FileOutputStream(rawgCacheCoverFile(title))) {
                    info.cover.compress(Bitmap.CompressFormat.JPEG, 86, output);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not cache game metadata for " + title, e);
        }
    }

    private File rawgCacheDir() {
        return new File(getFilesDir(), "game-metadata");
    }

    private File rawgCacheJsonFile(String title) {
        return new File(rawgCacheDir(), metadataCacheKey(title) + ".json");
    }

    private File rawgCacheCoverFile(String title) {
        return new File(rawgCacheDir(), metadataCacheKey(title) + ".jpg");
    }

    private static String metadataCacheKey(String title) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(title.trim().toLowerCase(java.util.Locale.US)
                    .getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                out.append(String.format(java.util.Locale.US, "%02x", value));
            }
            return out.toString();
        } catch (Exception e) {
            return sanitize(title.trim().toLowerCase(java.util.Locale.US));
        }
    }

    private RawgGameInfo fetchRawgGameInfo(String title, String apiKey) {
        if (title == null || title.isEmpty() || apiKey == null || apiKey.isEmpty()) {
            return null;
        }
        HttpURLConnection conn = null;
        try {
            String query = URLEncoder.encode(title, "UTF-8");
            URL searchUrl = new URL("https://api.rawg.io/api/games?key=" + apiKey +
                    "&search=" + query + "&page_size=1&search_precise=true");
            conn = (HttpURLConnection) searchUrl.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(10000);
            if (conn.getResponseCode() != 200) {
                return null;
            }
            JSONObject search = new JSONObject(readUtf8(conn.getInputStream()));
            JSONArray results = search.optJSONArray("results");
            if (results == null || results.length() == 0) {
                return null;
            }
            int id = results.getJSONObject(0).optInt("id", 0);
            if (id == 0) {
                return null;
            }
            conn.disconnect();
            conn = (HttpURLConnection) new URL("https://api.rawg.io/api/games/" + id +
                    "?key=" + apiKey).openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(10000);
            if (conn.getResponseCode() != 200) {
                return null;
            }
            JSONObject detail = new JSONObject(readUtf8(conn.getInputStream()));
            RawgGameInfo info = new RawgGameInfo();
            info.name = detail.optString("name", title);
            info.released = detail.optString("released", "");
            info.description = detail.optString("description_raw", "");
            info.coverUrl = detail.optString("background_image", "");
            info.genres = jsonNames(detail.optJSONArray("genres"));
            info.publishers = jsonNames(detail.optJSONArray("publishers"));
            info.developers = jsonNames(detail.optJSONArray("developers"));
            if (info.coverUrl != null && !info.coverUrl.isEmpty()) {
                info.cover = downloadBitmap(info.coverUrl);
            }
            return info;
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static List<String> jsonNames(JSONArray array) {
        List<String> names = new ArrayList<>();
        if (array == null) {
            return names;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item != null) {
                String name = item.optString("name", "");
                if (!name.isEmpty()) {
                    names.add(name);
                }
            }
        }
        return names;
    }

    private static List<String> jsonStringList(JSONArray array) {
        List<String> values = new ArrayList<>();
        if (array == null) {
            return values;
        }
        for (int i = 0; i < array.length(); i++) {
            String value = array.optString(i, "");
            if (!value.isEmpty()) {
                values.add(value);
            }
        }
        return values;
    }

    private static JSONArray stringListToJson(List<String> values) {
        JSONArray array = new JSONArray();
        if (values == null) {
            return array;
        }
        for (String value : values) {
            if (value != null && !value.isEmpty()) {
                array.put(value);
            }
        }
        return array;
    }

    private static String readUtf8(InputStream stream) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(16 * 1024);
        StringBuilder out = new StringBuilder();
        byte[] bytes = new byte[16 * 1024];
        int read;
        while ((read = stream.read(bytes)) != -1) {
            buffer.clear();
            out.append(new String(bytes, 0, read, StandardCharsets.UTF_8));
        }
        return out.toString();
    }

    private Bitmap downloadBitmap(String url) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(10000);
            if (conn.getResponseCode() != 200) {
                return null;
            }
            return BitmapFactory.decodeStream(conn.getInputStream());
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private String extractXboxTitleName(DiscSelection selection) {
        try (ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(selection.uri, "r")) {
            if (pfd == null) {
                return null;
            }
            try (FileInputStream input = new FileInputStream(pfd.getFileDescriptor());
                 FileChannel channel = input.getChannel()) {
                XdvdfsFile xbe = findDefaultXbe(channel);
                if (xbe == null) {
                    return null;
                }
                return readXbeTitleName(channel, xbe.offset);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private XdvdfsFile findDefaultXbe(FileChannel channel) throws IOException {
        long[] descriptorOffsets = {0x10000L, 0x8000L, 0x20000L};
        for (long descriptorOffset : descriptorOffsets) {
            ByteBuffer descriptor = readBuffer(channel, descriptorOffset, XDVD_SECTOR_SIZE);
            if (descriptor == null || !matchesMagic(descriptor, 0)) {
                continue;
            }
            long rootSector = uint32(descriptor, 20);
            long rootSize = uint32(descriptor, 24);
            XdvdfsFile found = findDefaultXbeInDirectory(channel, rootSector * XDVD_SECTOR_SIZE,
                    rootSize, 0);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private XdvdfsFile findDefaultXbeInDirectory(FileChannel channel, long offset, long size,
                                                int depth) throws IOException {
        if (depth > 5 || size <= 0 || size > 1024 * 1024) {
            return null;
        }
        ByteBuffer dir = readBuffer(channel, offset, (int) size);
        if (dir == null) {
            return null;
        }
        int pos = 0;
        List<XdvdfsFile> dirs = new ArrayList<>();
        while (pos + 14 <= dir.limit()) {
            int nameLen = dir.get(pos + 13) & 0xff;
            if (nameLen <= 0 || pos + 14 + nameLen > dir.limit()) {
                break;
            }
            long sector = uint32(dir, pos + 4);
            long fileSize = uint32(dir, pos + 8);
            int attrs = dir.get(pos + 12) & 0xff;
            byte[] nameBytes = new byte[nameLen];
            for (int i = 0; i < nameLen; i++) {
                nameBytes[i] = dir.get(pos + 14 + i);
            }
            String name = new String(nameBytes, StandardCharsets.UTF_8);
            long fileOffset = sector * XDVD_SECTOR_SIZE;
            if ("default.xbe".equalsIgnoreCase(name)) {
                return new XdvdfsFile(fileOffset, fileSize);
            }
            if ((attrs & 0x10) != 0) {
                dirs.add(new XdvdfsFile(fileOffset, fileSize));
            }
            pos += ((14 + nameLen + 3) / 4) * 4;
        }
        for (XdvdfsFile dirEntry : dirs) {
            XdvdfsFile found = findDefaultXbeInDirectory(channel, dirEntry.offset, dirEntry.size,
                    depth + 1);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private String readXbeTitleName(FileChannel channel, long xbeOffset) throws IOException {
        ByteBuffer header = readBuffer(channel, xbeOffset, 0x180);
        if (header == null || header.getInt(0) != 0x48454258) {
            return null;
        }
        long base = uint32(header, 0x104);
        long headerSize = uint32(header, 0x108);
        long certAddr = uint32(header, 0x118);
        long certOffset = certAddr - base;
        if (headerSize <= 0 || headerSize > 0x10000 || certOffset < 0 ||
                certOffset + 92 > headerSize) {
            return null;
        }
        ByteBuffer headers = readBuffer(channel, xbeOffset, (int) headerSize);
        if (headers == null) {
            return null;
        }
        int titleOffset = (int) certOffset + 12;
        StringBuilder title = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            char c = (char) (headers.getShort(titleOffset + i * 2) & 0xffff);
            if (c == 0) {
                break;
            }
            title.append(c);
        }
        return title.toString().trim();
    }

    private ByteBuffer readBuffer(FileChannel channel, long offset, int size) throws IOException {
        if (size <= 0) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        channel.position(offset);
        while (buffer.hasRemaining()) {
            if (channel.read(buffer) < 0) {
                return null;
            }
        }
        buffer.flip();
        return buffer;
    }

    private static boolean matchesMagic(ByteBuffer buffer, int offset) {
        if (buffer.limit() < offset + XDVD_MAGIC.length) {
            return false;
        }
        for (int i = 0; i < XDVD_MAGIC.length; i++) {
            if (buffer.get(offset + i) != XDVD_MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    private static long uint32(ByteBuffer buffer, int offset) {
        return buffer.getInt(offset) & 0xffffffffL;
    }

    private static String stripDiscExtension(String name) {
        if (name == null || name.isEmpty()) {
            return "Selected game";
        }
        String clean = name;
        int slash = Math.max(clean.lastIndexOf('/'), clean.lastIndexOf('\\'));
        if (slash >= 0) {
            clean = clean.substring(slash + 1);
        }
        int dot = clean.lastIndexOf('.');
        if (dot > 0) {
            clean = clean.substring(0, dot);
        }
        return clean;
    }

    private String openDirectDiscPathForLaunch() throws Exception {
        closeLaunchDiscPfd();
        if (discSelection == null) {
            throw new IllegalStateException("No game disc selected");
        }
        launchDiscPfd = getContentResolver().openFileDescriptor(discSelection.uri, "r");
        if (launchDiscPfd == null) {
            throw new IllegalStateException("Unable to open selected DISC through SAF");
        }
        long statSize = launchDiscPfd.getStatSize();
        if (discSelection.size > 0 && statSize > 0 && statSize != discSelection.size) {
            appendLog("DISC size changed: picker said " + humanSize(discSelection.size) +
                    ", fd says " + humanSize(statSize));
        }
        return "/proc/self/fd/" + launchDiscPfd.getFd();
    }

    private void closeLaunchDiscPfd() {
        if (launchDiscPfd != null) {
            try {
                launchDiscPfd.close();
            } catch (IOException ignored) {
            }
            launchDiscPfd = null;
        }
    }

    private long copyViaFileDescriptor(Uri uri, File out) throws Exception {
        try (ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r")) {
            if (pfd == null) {
                throw new IllegalStateException("No file descriptor for " + uri);
            }
            try (FileInputStream fis = new FileInputStream(pfd.getFileDescriptor());
                 FileOutputStream fos = new FileOutputStream(out, false);
                 FileChannel source = fis.getChannel();
                 FileChannel target = fos.getChannel()) {
                long copied = 0;
                ByteBuffer buffer = ByteBuffer.allocateDirect(4 * 1024 * 1024);
                int read;
                while ((read = source.read(buffer)) != -1) {
                    buffer.flip();
                    while (buffer.hasRemaining()) {
                        target.write(buffer);
                    }
                    buffer.clear();
                    copied += read;
                }
                fos.getFD().sync();
                return copied;
            }
        }
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    return cursor.getString(idx);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private long displaySize(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (idx >= 0 && !cursor.isNull(idx)) {
                    return cursor.getLong(idx);
                }
            }
        } catch (Exception ignored) {
        }
        return -1;
    }

    private void tryPersistableReadPermission(Intent data, Uri uri) {
        int flags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
        if (flags == 0) {
            return;
        }
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
        }
    }

    private String safeDisplayName(Uri uri) {
        String name = displayName(uri);
        return name == null ? uri.toString() : name;
    }

    private String labelFor(int requestCode) {
        switch (requestCode) {
            case REQ_MCPX: return "MCPX";
            case REQ_BIOS: return "BIOS";
            case REQ_HDD: return "HDD";
            default: return "file";
        }
    }

    private static String sanitize(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private void launchEmulator() {
        Log.i(TAG, "Launch button clicked");
        if (importInProgress) {
            appendLog("Launch blocked: file import is still running.");
            return;
        }
        if (!isSystemOperational()) {
            appendLog("Launch blocked: configure MCPX, BIOS, and HDD before starting the core.");
            showSystemFilesScreen();
            return;
        }
        if (discSelection == null) {
            appendLog("Play blocked: select a game from the library first.");
            showLibraryPanel();
            return;
        }
        appendLog("Play pressed. Validating selected files before native core start...");
        String discPath;
        try {
            discPath = openDirectDiscPathForLaunch();
        } catch (Exception e) {
            appendLog("Launch blocked: " + e.getMessage());
            return;
        }
        if (discSelection != null) {
            appendLog("DISC direct: " + discSelection.name + " (" + humanSize(discSelection.size) +
                    ") via " + discPath);
        }
        String result = NativeBridge.nativeLaunch(
                path(REQ_MCPX),
                path(REQ_BIOS),
                path(REQ_HDD),
                discPath,
                settingString(PREF_RENDERER, "vulkan"),
                settingInt(PREF_AUDIO_VOLUME, 100),
                settingBoolean(PREF_AUDIO_MUTED, false),
                settingBoolean(PREF_SKIP_BOOT, false),
                settingString(PREF_AVPACK, "hdtv"),
                launchDiscPfd != null ? launchDiscPfd.getFd() : -1);
        appendLog(result);
        if (result.startsWith("xemu core launched") || result.startsWith("xemu core is already running")) {
            emulationPaused = false;
            hidePauseOverlay();
            updatePauseUi();
            updateEmulationUi();
            scheduleIdleAutoPause();
            setSetupPanelVisible(false);
        }
        if (currentSurface != null) {
            NativeBridge.nativeSurfaceChanged(currentSurface, surfaceWidth, surfaceHeight);
        }
    }

    private void stopEmulator() {
        appendLog("Stop requested. Waiting for orderly core shutdown...");
        stopCoreAsync(true);
    }

    private void quitGameToHome() {
        appendLog("Quit game requested from pause menu.");
        hidePauseOverlay();
        stopCoreAsync(true);
    }

    private void stopCoreAsync(boolean returnHome) {
        new Thread(() -> {
            String result = NativeBridge.nativeStop(3000);
            runOnUiThread(() -> {
                appendLog(result);
                closeLaunchDiscPfd();
                cancelIdleAutoPause();
                emulationPaused = false;
                hidePauseOverlay();
                updatePauseUi();
                updateEmulationUi();
                if (returnHome) {
                    showFilesTab(true);
                    setLibraryPanelVisible(false);
                    setSetupPanelVisible(true);
                }
            });
        }, "xbox-core-stop").start();
    }

    private void ejectDiscSelection() {
        discSelection = null;
        closeLaunchDiscPfd();
        settings().edit()
                .remove(PREF_DISC_URI)
                .remove(PREF_DISC_NAME)
                .remove(PREF_DISC_SIZE)
                .apply();
        clearGameInfo();
        renderLibraryGames();
        appendLog("DISC selection ejected. Change applies on next launch.");
        refreshStatus();
    }

    private void handlePauseButton() {
        if (!NativeBridge.nativeIsRunning()) {
            appendLog("Pause ignored: core is not running.");
            return;
        }
        if (emulationPaused) {
            showPauseOverlay();
        } else {
            requestPause("Paused by user.", true);
        }
    }

    private void requestPause(String reason, boolean showOverlay) {
        if (!NativeBridge.nativeIsRunning()) {
            return;
        }
        cancelIdleAutoPause();
        if (!emulationPaused) {
            appendLog(NativeBridge.nativePause());
        }
        emulationPaused = true;
        if (reason != null && !reason.isEmpty()) {
            appendLog(reason);
        }
        updatePauseUi();
        if (showOverlay) {
            showPauseOverlay();
        }
    }

    private void requestResume(String reason) {
        if (!NativeBridge.nativeIsRunning()) {
            hidePauseOverlay();
            emulationPaused = false;
            updatePauseUi();
            return;
        }
        if (emulationPaused) {
            appendLog(NativeBridge.nativeResume());
        }
        emulationPaused = false;
        if (reason != null && !reason.isEmpty()) {
            appendLog(reason);
        }
        hidePauseOverlay();
        updatePauseUi();
        noteGameInputActivity();
    }

    private void showPauseOverlay() {
        pauseOverlay.setVisibility(View.VISIBLE);
    }

    private void hidePauseOverlay() {
        pauseOverlay.setVisibility(View.GONE);
    }

    private void updatePauseUi() {
        pauseButton.setText(emulationPaused ? R.string.resume : R.string.pause);
    }

    private void updateEmulationUi() {
        boolean running = NativeBridge.nativeIsRunning();
        pauseButton.setVisibility(running ? View.VISIBLE : View.GONE);
        idleOverlay.setVisibility(running ? View.GONE : View.VISIBLE);
    }

    private void noteGameInputActivity() {
        scheduleIdleAutoPause();
    }

    private void scheduleIdleAutoPause() {
        if (uiHandler == null) {
            return;
        }
        cancelIdleAutoPause();
        int delayMs = settingInt(PREF_AUTO_PAUSE_MS, 0);
        if (delayMs > 0 && NativeBridge.nativeIsRunning() && !emulationPaused) {
            uiHandler.postDelayed(idleAutoPauseRunnable, delayMs);
        }
    }

    private void cancelIdleAutoPause() {
        if (uiHandler != null) {
            uiHandler.removeCallbacks(idleAutoPauseRunnable);
        }
    }

    // TODO(error-ui): replace the raw TextView log with a structured core error console
    // fed by native callbacks instead of relying on Logcat/stdout mirroring.

    // TODO(hdd-save-test): add a repeatable manual/automated scenario that saves in-game,
    // performs an orderly stop, restarts, and verifies the save persisted on hdd.img.

    private void handleTogglePanelButton() {
        if (libraryPanelVisible) {
            setLibraryPanelVisible(false);
            return;
        }
        setSetupPanelVisible(!setupPanelVisible);
    }

    private void setSetupPanelVisible(boolean visible) {
        setupPanelVisible = visible;
        setupPanel.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (!visible) {
            setLibraryPanelVisible(false);
        }
        updateTogglePanelButton();
        renderSurface.post(this::updateNativeSurfaceSize);
        uiHandler.postDelayed(this::updateNativeSurfaceSize, 150);
    }

    private void showLibraryPanel() {
        setLibraryPanelVisible(true);
    }

    private void setLibraryPanelVisible(boolean visible) {
        libraryPanelVisible = visible;
        libraryPanel.setVisibility(visible ? View.VISIBLE : View.GONE);
        updateTogglePanelButton();
        renderSurface.post(this::updateNativeSurfaceSize);
        uiHandler.postDelayed(this::updateNativeSurfaceSize, 150);
    }

    private void updateTogglePanelButton() {
        if (libraryPanelVisible) {
            togglePanelButton.setText(R.string.toggle_library_hide);
        } else if (setupPanelVisible) {
            togglePanelButton.setText(R.string.toggle_setup_hide);
        } else {
            togglePanelButton.setText(R.string.toggle_setup_show);
        }
    }

    private void updateNativeSurfaceSize() {
        if (currentSurface == null || renderSurface == null) {
            return;
        }
        int width = renderSurface.getWidth();
        int height = renderSurface.getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        surfaceWidth = width;
        surfaceHeight = height;
        NativeBridge.nativeSurfaceChanged(currentSurface, width, height);
    }

    private String path(int key) {
        File file = files.get(key);
        return file == null ? "" : file.getAbsolutePath();
    }

    private void refreshStatus() {
        boolean systemReady = isSystemOperational();
        statusIcon.setText(systemReady ? "\u2713" : "\u2715");
        statusIcon.setTextColor(getColor(systemReady ? R.color.accent : R.color.danger));
        statusText.setText(systemReady ? R.string.system_operational : R.string.system_setup_required);
        fixNowButton.setVisibility(systemReady ? View.GONE : View.VISIBLE);
    }

    private boolean isSystemOperational() {
        return files.containsKey(REQ_MCPX) && files.containsKey(REQ_BIOS) && files.containsKey(REQ_HDD);
    }

    private void appendLog(String line) {
        if (line == null || line.isEmpty()) {
            return;
        }
        logText.append(line + "\n");
    }

    private static String humanSize(long bytes) {
        if (bytes < 0) {
            return "unknown size";
        }
        double value = bytes;
        String[] units = {"B", "KiB", "MiB", "GiB"};
        int unit = 0;
        while (value >= 1024.0 && unit < units.length - 1) {
            value /= 1024.0;
            unit++;
        }
        return String.format(java.util.Locale.US, "%.2f %s", value, units[unit]);
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
        releaseCurrentSurface();
        currentSurface = new Surface(surfaceTexture);
        surfaceWidth = width;
        surfaceHeight = height;
        NativeBridge.nativeSurfaceCreated(currentSurface, width, height);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
        surfaceWidth = width;
        surfaceHeight = height;
        if (currentSurface == null) {
            currentSurface = new Surface(surfaceTexture);
        }
        NativeBridge.nativeSurfaceChanged(currentSurface, width, height);
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        NativeBridge.nativeSurfaceDestroyed();
        releaseCurrentSurface();
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
    }

    private void releaseCurrentSurface() {
        if (currentSurface != null) {
            currentSurface.release();
            currentSurface = null;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (NativeBridge.nativeIsRunning()) {
            lifecyclePausedCore = true;
            requestPause("Paused while app is in background.", false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        enterImmersiveMode();
        updateEmulationUi();
        if (lifecyclePausedCore && NativeBridge.nativeIsRunning()) {
            showPauseOverlay();
            updatePauseUi();
        }
        lifecyclePausedCore = false;
        renderSurface.post(this::updateNativeSurfaceSize);
    }

    @Override
    protected void onDestroy() {
        if (isFinishing()) {
            appendLog(NativeBridge.nativeStop(3000));
        }
        cancelIdleAutoPause();
        closeLaunchDiscPfd();
        releaseCurrentSurface();
        super.onDestroy();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int action = event.getAction();
        if (action != KeyEvent.ACTION_DOWN && action != KeyEvent.ACTION_UP) {
            return super.dispatchKeyEvent(event);
        }
        boolean pressed = action == KeyEvent.ACTION_DOWN;
        if (emulationPaused && isGameControllerSource(event.getSource())) {
            showPauseOverlay();
            return true;
        }
        int triggerAxis = AndroidInputMapper.mapTriggerKey(event.getKeyCode());
        if (triggerAxis >= 0 && isGameControllerSource(event.getSource())) {
            noteGameInputActivity();
            NativeBridge.nativeSetAxis(triggerAxis, pressed ? 1.0f : 0.0f);
            return true;
        }
        int button = AndroidInputMapper.mapKey(event.getKeyCode());
        if (button >= 0 && isGameControllerSource(event.getSource())) {
            noteGameInputActivity();
            NativeBridge.nativeSetButton(button, pressed);
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_MOVE && isGameControllerSource(event.getSource())) {
            if (emulationPaused) {
                showPauseOverlay();
                return true;
            }
            noteGameInputActivity();
            NativeBridge.nativeSetAxis(AndroidInputMapper.AXIS_LTRIGGER,
                    triggerValue(event, MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_BRAKE));
            NativeBridge.nativeSetAxis(AndroidInputMapper.AXIS_RTRIGGER,
                    triggerValue(event, MotionEvent.AXIS_RTRIGGER, MotionEvent.AXIS_GAS));
            NativeBridge.nativeSetAxis(AndroidInputMapper.AXIS_LSTICK_X,
                    centeredAxis(event, MotionEvent.AXIS_X));
            NativeBridge.nativeSetAxis(AndroidInputMapper.AXIS_LSTICK_Y,
                    -centeredAxis(event, MotionEvent.AXIS_Y));
            NativeBridge.nativeSetAxis(AndroidInputMapper.AXIS_RSTICK_X,
                    centeredAxis(event, MotionEvent.AXIS_Z));
            NativeBridge.nativeSetAxis(AndroidInputMapper.AXIS_RSTICK_Y,
                    -centeredAxis(event, MotionEvent.AXIS_RZ));
            updateHatDpad(event);
            return true;
        }
        return super.onGenericMotionEvent(event);
    }

    private static boolean isGameControllerSource(int source) {
        return (source & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                (source & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
    }

    private static float triggerValue(MotionEvent event, int primaryAxis, int fallbackAxis) {
        float primary = Math.max(0.0f, event.getAxisValue(primaryAxis));
        float fallback = Math.max(0.0f, event.getAxisValue(fallbackAxis));
        return Math.max(primary, fallback);
    }

    private static float centeredAxis(MotionEvent event, int axis) {
        InputDevice device = event.getDevice();
        float value = event.getAxisValue(axis);
        float flat = 0.08f;
        if (device != null) {
            InputDevice.MotionRange range = device.getMotionRange(axis, event.getSource());
            if (range != null) {
                flat = Math.max(flat, range.getFlat());
            }
        }
        return Math.abs(value) <= flat ? 0.0f : value;
    }

    private void updateHatDpad(MotionEvent event) {
        float hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X);
        float hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y);
        NativeBridge.nativeSetButton(AndroidInputMapper.BTN_DPAD_LEFT, hatX < -0.5f);
        NativeBridge.nativeSetButton(AndroidInputMapper.BTN_DPAD_RIGHT, hatX > 0.5f);
        NativeBridge.nativeSetButton(AndroidInputMapper.BTN_DPAD_UP, hatY < -0.5f);
        NativeBridge.nativeSetButton(AndroidInputMapper.BTN_DPAD_DOWN, hatY > 0.5f);
    }

    @Override
    public void onTouchButton(int button, boolean pressed) {
        if (emulationPaused) {
            showPauseOverlay();
            return;
        }
        noteGameInputActivity();
        NativeBridge.nativeSetButton(button, pressed);
    }

    @Override
    public void onTouchAxis(int axis, float value) {
        if (emulationPaused) {
            showPauseOverlay();
            return;
        }
        noteGameInputActivity();
        NativeBridge.nativeSetAxis(axis, value);
    }

    private interface SpinnerSelectionHandler {
        void onSelected(int position);
    }

    private final class SimpleItemSelectedListener implements AdapterView.OnItemSelectedListener {
        private final SpinnerSelectionHandler handler;

        SimpleItemSelectedListener(SpinnerSelectionHandler handler) {
            this.handler = handler;
        }

        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            if (!settingsBinding) {
                handler.onSelected(position);
            }
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
        }
    }

    private static final class DiscSelection {
        final Uri uri;
        final String name;
        final long size;

        DiscSelection(Uri uri, String name, long size) {
            this.uri = uri;
            this.name = name;
            this.size = size;
        }
    }

    private static final class RawgGameInfo {
        String name;
        String released;
        String description;
        String coverUrl;
        Bitmap cover;
        List<String> genres = new ArrayList<>();
        List<String> publishers = new ArrayList<>();
        List<String> developers = new ArrayList<>();
    }

    private static final class XdvdfsFile {
        final long offset;
        final long size;

        XdvdfsFile(long offset, long size) {
            this.offset = offset;
            this.size = size;
        }
    }

    private static final class LibraryGame {
        final Uri uri;
        final String name;
        final long size;

        LibraryGame(Uri uri, String name, long size) {
            this.uri = uri;
            this.name = name;
            this.size = size;
        }
    }
}
