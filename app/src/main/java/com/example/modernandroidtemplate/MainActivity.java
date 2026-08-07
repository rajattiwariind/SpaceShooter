package com.example.modernandroidtemplate;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

/**
 * MainActivity: Coordinates layouts, handles shop mechanics (unlocked items, spending persistent points),
 * saves totals to SharedPreferences, and handles boss HUD elements.
 */
public class MainActivity extends AppCompatActivity implements GameView.GameListener {

    public enum GameState {
        START, PLAYING, GAME_OVER
    }

    private GameView gameView;
    private ConstraintLayout hudLayout;
    private ScrollView startScreenLayout;
    private LinearLayout gameOverScreenLayout;

    // HUD Elements
    private TextView tvScore;
    private TextView tvHighScore;
    private ProgressBar pbHealth;
    private ProgressBar pbShield;

    // Boss Bar Layout
    private LinearLayout layoutBossHealth;
    private TextView tvBossName;
    private ProgressBar pbBossHealth;

    // Game Over Panel Elements
    private TextView tvFinalScore;
    private TextView tvFinalHighScore;

    // Persistent points wallet system
    private TextView tvWalletPoints;
    private int totalWalletPoints = 0;

    // Unlock Status Indicators
    private boolean isCarUnlocked = false;
    private boolean isJetpackUnlocked = false;
    private boolean isDoubleBoltUnlocked = false;
    private boolean isSpreadShotUnlocked = false;
    private boolean isBeamUnlocked = false;

    // Active Selection trackers
    private GameView.ShipType selectedShipType = GameView.ShipType.SPACESHIP;
    private GameView.GunType selectedGunType = GameView.GunType.PLASMA;

    // UI Buttons (Shop)
    private Button btnSelectShipDefault, btnSelectShipCar, btnSelectShipJetpack;
    private Button btnSelectGunPlasma, btnSelectGunDouble, btnSelectGunSpread, btnSelectGunBeam;

    private Vibrator vibrator;
    private int activeHighScore = 0;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Access saved wallet and unlocks
        prefs = getSharedPreferences("SpaceDefenderPrefs", MODE_PRIVATE);
        loadPersistentData();

        // Bind layout system elements
        gameView = findViewById(R.id.gameView);
        hudLayout = findViewById(R.id.hudLayout);
        startScreenLayout = findViewById(R.id.startScreenLayout);
        gameOverScreenLayout = findViewById(R.id.gameOverScreenLayout);

        tvScore = findViewById(R.id.tvScore);
        tvHighScore = findViewById(R.id.tvHighScore);
        pbHealth = findViewById(R.id.pbHealth);
        pbShield = findViewById(R.id.pbShield);

        // Boss Bar
        layoutBossHealth = findViewById(R.id.layoutBossHealth);
        tvBossName = findViewById(R.id.tvBossName);
        pbBossHealth = findViewById(R.id.pbBossHealth);

        tvFinalScore = findViewById(R.id.tvFinalScore);
        tvFinalHighScore = findViewById(R.id.tvFinalHighScore);
        tvWalletPoints = findViewById(R.id.tvWalletPoints);

        Button btnLaunch = findViewById(R.id.btnLaunch);
        Button btnRelaunch = findViewById(R.id.btnRelaunch);

        // Shop Buttons Binding
        btnSelectShipDefault = findViewById(R.id.btnSelectShipDefault);
        btnSelectShipCar = findViewById(R.id.btnSelectShipCar);
        btnSelectShipJetpack = findViewById(R.id.btnSelectShipJetpack);

        btnSelectGunPlasma = findViewById(R.id.btnSelectGunPlasma);
        btnSelectGunDouble = findViewById(R.id.btnSelectGunDouble);
        btnSelectGunSpread = findViewById(R.id.btnSelectGunSpread);
        btnSelectGunBeam = findViewById(R.id.btnSelectGunBeam);

        // Initialize Shop UI Visual states
        updateShopButtonsUI();

        // Initialize system haptics
        initVibrator();

        // Connect listener interface
        gameView.setGameListener(this);

        // Setup Shop Click Listeners
        setupShopListeners();

        // UI Event triggers
        btnLaunch.setOnClickListener(v -> {
            startScreenLayout.setVisibility(View.GONE);
            hudLayout.setVisibility(View.VISIBLE);

            // Pass down customized choices
            gameView.setShipType(selectedShipType);
            gameView.setGunType(selectedGunType);

            gameView.startNewGame();
        });

        btnRelaunch.setOnClickListener(v -> {
            gameOverScreenLayout.setVisibility(View.GONE);
            startScreenLayout.setVisibility(View.VISIBLE);
            updateShopButtonsUI(); // Refresh wallet counters on return
        });
    }

    private void initVibrator() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vibratorManager = (VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            if (vibratorManager != null) {
                vibrator = vibratorManager.getDefaultVibrator();
            }
        } else {
            vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        }
    }

    private void loadPersistentData() {
        totalWalletPoints = prefs.getInt("WalletPoints", 0);
        activeHighScore = prefs.getInt("HighScore", 0);

        // Load unlocks
        isCarUnlocked = prefs.getBoolean("UnlockCar", false);
        isJetpackUnlocked = prefs.getBoolean("UnlockJetpack", false);
        isDoubleBoltUnlocked = prefs.getBoolean("UnlockDoubleBolt", false);
        isSpreadShotUnlocked = prefs.getBoolean("UnlockSpreadShot", false);
        isBeamUnlocked = prefs.getBoolean("UnlockBeam", false);
    }

    private void savePersistentData() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt("WalletPoints", totalWalletPoints);
        editor.putInt("HighScore", activeHighScore);
        editor.putBoolean("UnlockCar", isCarUnlocked);
        editor.putBoolean("UnlockJetpack", isJetpackUnlocked);
        editor.putBoolean("UnlockDoubleBolt", isDoubleBoltUnlocked);
        editor.putBoolean("UnlockSpreadShot", isSpreadShotUnlocked);
        editor.putBoolean("UnlockBeam", isBeamUnlocked);
        editor.apply();
    }

    private void setupShopListeners() {
        // --- Ships ---
        btnSelectShipDefault.setOnClickListener(v -> {
            selectedShipType = GameView.ShipType.SPACESHIP;
            updateShopButtonsUI();
        });

        btnSelectShipCar.setOnClickListener(v -> {
            if (isCarUnlocked) {
                selectedShipType = GameView.ShipType.CAR;
            } else {
                attemptPurchase(500, "Cyber Car", () -> {
                    isCarUnlocked = true;
                    selectedShipType = GameView.ShipType.CAR;
                });
            }
            updateShopButtonsUI();
        });

        btnSelectShipJetpack.setOnClickListener(v -> {
            if (isJetpackUnlocked) {
                selectedShipType = GameView.ShipType.JETPACK;
            } else {
                attemptPurchase(1500, "Rocket Jetpack", () -> {
                    isJetpackUnlocked = true;
                    selectedShipType = GameView.ShipType.JETPACK;
                });
            }
            updateShopButtonsUI();
        });

        // --- Weapons ---
        btnSelectGunPlasma.setOnClickListener(v -> {
            selectedGunType = GameView.GunType.PLASMA;
            updateShopButtonsUI();
        });

        btnSelectGunDouble.setOnClickListener(v -> {
            if (isDoubleBoltUnlocked) {
                selectedGunType = GameView.GunType.DOUBLE_BOLT;
            } else {
                attemptPurchase(300, "Double Bolt Gun", () -> {
                    isDoubleBoltUnlocked = true;
                    selectedGunType = GameView.GunType.DOUBLE_BOLT;
                });
            }
            updateShopButtonsUI();
        });

        btnSelectGunSpread.setOnClickListener(v -> {
            if (isSpreadShotUnlocked) {
                selectedGunType = GameView.GunType.SPREAD_FIRE;
            } else {
                attemptPurchase(800, "Spread Shot Gun", () -> {
                    isSpreadShotUnlocked = true;
                    selectedGunType = GameView.GunType.SPREAD_FIRE;
                });
            }
            updateShopButtonsUI();
        });

        btnSelectGunBeam.setOnClickListener(v -> {
            if (isBeamUnlocked) {
                selectedGunType = GameView.GunType.COSMIC_RAY;
            } else {
                attemptPurchase(2000, "Cosmic Ray Beam", () -> {
                    isBeamUnlocked = true;
                    selectedGunType = GameView.GunType.COSMIC_RAY;
                });
            }
            updateShopButtonsUI();
        });
    }

    private void attemptPurchase(int cost, String itemName, Runnable onPurchaseSuccess) {
        if (totalWalletPoints >= cost) {
            totalWalletPoints -= cost;
            onPurchaseSuccess.run();
            savePersistentData();
            triggerHaptic(60);
            Toast.makeText(this, itemName + " unlocked successfully!", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Insufficient Points! Need " + (cost - totalWalletPoints) + " more points.", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateShopButtonsUI() {
        tvWalletPoints.setText("WALLET: " + totalWalletPoints + " PTS");

        // Ship buttons state
        btnSelectShipDefault.setText(selectedShipType == GameView.ShipType.SPACESHIP ? "ACTIVE" : "SELECT");
        btnSelectShipDefault.setBackgroundColor(selectedShipType == GameView.ShipType.SPACESHIP ? 0xFF00FFCC : 0xFF202040);

        // Cyber Car
        if (isCarUnlocked) {
            btnSelectShipCar.setText(selectedShipType == GameView.ShipType.CAR ? "ACTIVE" : "SELECT");
            btnSelectShipCar.setBackgroundColor(selectedShipType == GameView.ShipType.CAR ? 0xFF00FFCC : 0xFF202040);
            findViewById(R.id.tvCostShipCar).setVisibility(View.GONE);
        } else {
            btnSelectShipCar.setText("BUY");
            btnSelectShipCar.setBackgroundColor(0xFFFFDC00);
        }

        // Rocket Jetpack
        if (isJetpackUnlocked) {
            btnSelectShipJetpack.setText(selectedShipType == GameView.ShipType.JETPACK ? "ACTIVE" : "SELECT");
            btnSelectShipJetpack.setBackgroundColor(selectedShipType == GameView.ShipType.JETPACK ? 0xFF00FFCC : 0xFF202040);
            findViewById(R.id.tvCostShipJetpack).setVisibility(View.GONE);
        } else {
            btnSelectShipJetpack.setText("BUY");
            btnSelectShipJetpack.setBackgroundColor(0xFFFFDC00);
        }

        // Weapon buttons state
        btnSelectGunPlasma.setText(selectedGunType == GameView.GunType.PLASMA ? "ACTIVE" : "SELECT");
        btnSelectGunPlasma.setBackgroundColor(selectedGunType == GameView.GunType.PLASMA ? 0xFF00FFCC : 0xFF202040);

        // Double Bolt
        if (isDoubleBoltUnlocked) {
            btnSelectGunDouble.setText(selectedGunType == GameView.GunType.DOUBLE_BOLT ? "ACTIVE" : "SELECT");
            btnSelectGunDouble.setBackgroundColor(selectedGunType == GameView.GunType.DOUBLE_BOLT ? 0xFF00FFCC : 0xFF202040);
            findViewById(R.id.tvCostGunDouble).setVisibility(View.GONE);
        } else {
            btnSelectGunDouble.setText("BUY");
            btnSelectGunDouble.setBackgroundColor(0xFFFFDC00);
        }

        // Spread Fire
        if (isSpreadShotUnlocked) {
            btnSelectGunSpread.setText(selectedGunType == GameView.GunType.SPREAD_FIRE ? "ACTIVE" : "SELECT");
            btnSelectGunSpread.setBackgroundColor(selectedGunType == GameView.GunType.SPREAD_FIRE ? 0xFF00FFCC : 0xFF202040);
            findViewById(R.id.tvCostGunSpread).setVisibility(View.GONE);
        } else {
            btnSelectGunSpread.setText("BUY");
            btnSelectGunSpread.setBackgroundColor(0xFFFFDC00);
        }

        // Cosmic Ray Beam
        if (isBeamUnlocked) {
            btnSelectGunBeam.setText(selectedGunType == GameView.GunType.COSMIC_RAY ? "ACTIVE" : "SELECT");
            btnSelectGunBeam.setBackgroundColor(selectedGunType == GameView.GunType.COSMIC_RAY ? 0xFF00FFCC : 0xFF202040);
            findViewById(R.id.tvCostGunBeam).setVisibility(View.GONE);
        } else {
            btnSelectGunBeam.setText("BUY");
            btnSelectGunBeam.setBackgroundColor(0xFFFFDC00);
        }
    }

    @Override
    public void onScoreChanged(int score, int highScore) {
        if (highScore > activeHighScore) {
            activeHighScore = highScore;
            savePersistentData();
        }
        runOnUiThread(() -> {
            tvScore.setText(String.format("%06d", score));
            tvHighScore.setText(String.format("%06d", activeHighScore));
        });
    }

    @Override
    public void onStatusChanged(float health, float shield) {
        runOnUiThread(() -> {
            pbHealth.setProgress((int) health);
            pbShield.setProgress((int) shield);
        });
    }

    @Override
    public void onGameStateChanged(MainActivity.GameState state) {
        runOnUiThread(() -> {
            if (state == GameState.GAME_OVER) {
                hudLayout.setVisibility(View.GONE);
                gameOverScreenLayout.setVisibility(View.VISIBLE);

                tvFinalScore.setText(tvScore.getText());
                tvFinalHighScore.setText(String.format("%06d", activeHighScore));

                triggerHaptic(300);
            }
        });
    }

    @Override
    public void onTriggerHaptic(int msDuration) {
        triggerHaptic(msDuration);
    }

    @Override
    public void onBossStatusChanged(boolean isActive, String name, float healthPercent) {
        runOnUiThread(() -> {
            if (isActive) {
                layoutBossHealth.setVisibility(View.VISIBLE);
                tvBossName.setText(name);
                pbBossHealth.setProgress((int) healthPercent);
            } else {
                layoutBossHealth.setVisibility(View.GONE);
            }
        });
    }

    @Override
    public void onGameFinishedAndGainedPoints(int gainedPoints) {
        // Accumulate running points permanently
        totalWalletPoints += gainedPoints;
        savePersistentData();
    }

    private void triggerHaptic(int ms) {
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(ms);
            }
        }
    }
}