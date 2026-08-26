package emu.xbox.og;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.List;

/** Lets the player reposition, resize and fade every touch control, live over its own preview. */
public class TouchLayoutEditorActivity extends Activity implements TouchControllerView.EditListener {
    private TouchControllerView editorTouchView;
    private TextView selectionLabel;
    private SeekBar sizeSeek;
    private TextView abxyButtonSizeLabel;
    private SeekBar abxyButtonSizeSeek;
    private SeekBar opacitySeek;
    private TextView spacingLabel;
    private SeekBar spacingSeek;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        enterImmersiveMode();
        setContentView(R.layout.activity_touch_layout_editor);

        editorTouchView = findViewById(R.id.editorTouchView);
        selectionLabel = findViewById(R.id.editorSelectionLabel);
        sizeSeek = findViewById(R.id.sizeSeek);
        abxyButtonSizeLabel = findViewById(R.id.abxyButtonSizeLabel);
        abxyButtonSizeSeek = findViewById(R.id.abxyButtonSizeSeek);
        opacitySeek = findViewById(R.id.opacitySeek);
        spacingLabel = findViewById(R.id.spacingLabel);
        spacingSeek = findViewById(R.id.spacingSeek);

        editorTouchView.setEditMode(true);
        editorTouchView.setEditListener(this);

        sizeSeek.setOnSeekBarChangeListener(simpleListener(p -> editorTouchView.setSelectionScale(p / 100f)));
        abxyButtonSizeSeek.setOnSeekBarChangeListener(simpleListener(p -> editorTouchView.setAbxyButtonScale(p / 100f)));
        opacitySeek.setOnSeekBarChangeListener(simpleListener(p -> editorTouchView.setSelectionOpacity(p / 100f)));
        spacingSeek.setOnSeekBarChangeListener(simpleListener(p -> editorTouchView.setSelectionSpacing(p / 100f)));

        Button resetButton = findViewById(R.id.resetLayoutButton);
        resetButton.setOnClickListener(v -> editorTouchView.resetLayout());

        Button doneButton = findViewById(R.id.doneEditingButton);
        doneButton.setOnClickListener(v -> finish());

        onSelectionChanged();
    }

    private interface ProgressAction {
        void apply(int progress);
    }

    private SeekBar.OnSeekBarChangeListener simpleListener(ProgressAction action) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    action.apply(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        };
    }

    @Override
    public void onSelectionChanged() {
        List<String> keys = editorTouchView.getSelectedKeys();

        if (keys.isEmpty()) {
            selectionLabel.setText(R.string.touch_editor_no_selection);
        } else if (keys.size() == 1) {
            selectionLabel.setText(getString(R.string.touch_editor_selection_one, labelFor(keys.get(0))));
        } else {
            selectionLabel.setText(getString(R.string.touch_editor_selection_many, keys.size()));
        }

        boolean hasSelection = !keys.isEmpty();
        sizeSeek.setEnabled(hasSelection);
        opacitySeek.setEnabled(hasSelection);
        if (hasSelection) {
            sizeSeek.setProgress(clampProgress(editorTouchView.getSelectionScale() * 100f));
            opacitySeek.setProgress(clampProgress(editorTouchView.getSelectionOpacity() * 100f));
        }

        boolean abxyAlone = editorTouchView.isAbxySelectedAlone();
        abxyButtonSizeLabel.setVisibility(abxyAlone ? View.VISIBLE : View.GONE);
        abxyButtonSizeSeek.setVisibility(abxyAlone ? View.VISIBLE : View.GONE);
        if (abxyAlone) {
            abxyButtonSizeSeek.setProgress(clampProgress(editorTouchView.getAbxyButtonScale() * 100f));
        }

        boolean multiSelect = keys.size() >= 2;
        spacingLabel.setVisibility(multiSelect ? View.VISIBLE : View.GONE);
        spacingSeek.setVisibility(multiSelect ? View.VISIBLE : View.GONE);
        if (multiSelect) {
            editorTouchView.beginSpacingAdjust();
            spacingSeek.setProgress(100);
        }
    }

    private static int clampProgress(float value) {
        return Math.max(0, Math.min(200, Math.round(value)));
    }

    private String labelFor(String key) {
        if (TouchControllerView.KEY_LEFT_STICK.equals(key)) {
            return "Left Stick";
        } else if (TouchControllerView.KEY_RIGHT_STICK.equals(key)) {
            return "Right Stick";
        } else if (TouchControllerView.KEY_DPAD.equals(key)) {
            return "D-Pad";
        } else if (TouchControllerView.KEY_ABXY.equals(key)) {
            return "ABXY";
        } else if (TouchControllerView.KEY_LB.equals(key)) {
            return "LB";
        } else if (TouchControllerView.KEY_RB.equals(key)) {
            return "RB";
        } else if (TouchControllerView.KEY_LT.equals(key)) {
            return "LT";
        } else if (TouchControllerView.KEY_RT.equals(key)) {
            return "RT";
        } else if (TouchControllerView.KEY_L3.equals(key)) {
            return "L3";
        } else if (TouchControllerView.KEY_R3.equals(key)) {
            return "R3";
        } else if (TouchControllerView.KEY_BACK.equals(key)) {
            return "Back";
        } else if (TouchControllerView.KEY_START.equals(key)) {
            return "Start";
        } else if (TouchControllerView.KEY_PAUSE.equals(key)) {
            return "Pause";
        }
        return key;
    }

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
}
