package com.example.modernandroidtemplate;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * GameView: Manages rendering loops, custom player vehicles, dynamic laser patterns,
 * power-up items, bosses (Dreadnought and Segmented slithering Dragon), and visual particle feedback.
 */
public class GameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {

    public interface GameListener {
        void onScoreChanged(int score, int highScore);
        void onStatusChanged(float health, float shield);
        void onGameStateChanged(MainActivity.GameState state);
        void onTriggerHaptic(int msDuration);
        void onBossStatusChanged(boolean isActive, String name, float healthPercent);
        void onGameFinishedAndGainedPoints(int gainedPoints);
    }

    // Config enums for weapons and ships
    public enum ShipType { SPACESHIP, CAR, JETPACK }
    public enum GunType { PLASMA, DOUBLE_BOLT, SPREAD_FIRE, COSMIC_RAY }

    // Selected Loadout
    private ShipType currentShipType = ShipType.SPACESHIP;
    private GunType currentGunType = GunType.PLASMA;

    // Entity Classes
    private static class Star {
        float x, y, speed, size;
        int layer;
        Star(float x, float y, float speed, float size, int layer) {
            this.x = x; this.y = y; this.speed = speed; this.size = size; this.layer = layer;
        }
    }

    private static class Laser {
        float x, y;
        float vx = 0f;
        float vy = -25f;
        Laser(float x, float y) { this.x = x; this.y = y; }
        Laser(float x, float y, float vx, float vy) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy;
        }
    }

    private static class Enemy {
        float x, y, radius, speed;
        int color;
        int points;
        float phaseOffset;
        Enemy(float x, float y, float radius, float speed, int color, int points) {
            this.x = x; this.y = y; this.radius = radius; this.speed = speed; this.color = color; this.points = points;
            this.phaseOffset = new Random().nextFloat() * 100f;
        }
    }

    private static class DragonSegment {
        float x, y;
        DragonSegment(float x, float y) { this.x = x; this.y = y; }
    }

    // Boss Class
    private static class Boss {
        float x, y, radius;
        float maxHealth, health;
        int type; // 0 = Dreadnought, 1 = Dragon
        float angle;
        float dx = 3.5f;
        List<DragonSegment> segments = new ArrayList<>();

        Boss(float x, float y, float radius, float health, int type) {
            this.x = x; this.y = y; this.radius = radius;
            this.maxHealth = health; this.health = health;
            this.type = type;
            this.angle = 0f;

            if (type == 1) { // Dragon body segments
                for (int i = 0; i < 12; i++) {
                    segments.add(new DragonSegment(x, y));
                }
            }
        }
    }

    private static class BossProjectile {
        float x, y, vx, vy, radius;
        int color;
        BossProjectile(float x, float y, float vx, float vy, float radius, int color) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy; this.radius = radius; this.color = color;
        }
    }

    private static class PowerUp {
        float x, y, radius, speed;
        int type; // 0 = Shield, 1 = Overdrive Double Shot
        PowerUp(float x, float y, float radius, float speed, int type) {
            this.x = x; this.y = y; this.radius = radius; this.speed = speed; this.type = type;
        }
    }

    private static class Particle {
        float x, y, vx, vy;
        int color;
        float alpha;
        int life, maxLife;
        Particle(float x, float y, float vx, float vy, int color, int life) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy; this.color = color;
            this.alpha = 1.0f; this.life = life; this.maxLife = life;
        }
    }

    // Threading
    private Thread gameThread = null;
    private final SurfaceHolder surfaceHolder;
    private volatile boolean isRunning = false;
    private final Random random = new Random();

    // Game Variables
    private MainActivity.GameState gameState = MainActivity.GameState.START;
    private int score = 0;
    private int highScore = 0;
    private float playerHealth = 100f;
    private float playerShield = 0f;
    private float playerX = 0f;
    private float targetPlayerX = 0f;
    private float canvasWidth = 0f;
    private float canvasHeight = 0f;

    // Power-Up Mechanics
    private int weaponPowerUpTimer = 0; // Temporary double-shot override active

    // Boss State
    private Boss activeBoss = null;
    private int lastBossThreshold = 0;

    // Camera Shake
    private int shakeDuration = 0;
    private float shakeIntensity = 0f;

    // Game Lists
    private final List<Star> stars = new ArrayList<>();
    private final List<Laser> lasers = new ArrayList<>();
    private final List<Enemy> enemies = new ArrayList<>();
    private final List<BossProjectile> bossProjectiles = new ArrayList<>();
    private final List<PowerUp> powerups = new ArrayList<>();
    private final List<Particle> particles = new ArrayList<>();

    // Physics
    private int frameCounter = 0;
    private int enemySpawnInterval = 60;
    private float speedMultiplier = 1.0f;

    // Brushes
    private Paint paintStar;
    private Paint paintLaser;
    private Paint paintLaserGlow;
    private Paint paintEnemyFill;
    private Paint paintEnemyStroke;
    private Paint paintPlayerHull;
    private Paint paintPlayerOutline;
    private Paint paintCockpit;
    private Paint paintFlame;
    private Paint paintParticle;
    private Paint paintPowerUp;

    private GameListener gameListener;

    public GameView(Context context, AttributeSet attrs) {
        super(context, attrs);
        surfaceHolder = getHolder();
        surfaceHolder.addCallback(this);
        initPaints();
    }

    private void initPaints() {
        paintStar = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintStar.setColor(Color.WHITE);

        paintLaser = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintLaser.setColor(Color.parseColor("#00FFCC"));

        paintLaserGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintLaserGlow.setColor(Color.parseColor("#3300FFCC"));

        paintEnemyFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintEnemyFill.setStyle(Paint.Style.FILL);

        paintEnemyStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintEnemyStroke.setStyle(Paint.Style.STROKE);
        paintEnemyStroke.setStrokeWidth(5f);

        paintPlayerHull = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintPlayerHull.setStyle(Paint.Style.FILL);

        paintPlayerOutline = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintPlayerOutline.setStyle(Paint.Style.STROKE);
        paintPlayerOutline.setStrokeWidth(6f);

        paintCockpit = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintCockpit.setColor(Color.argb(200, 100, 220, 255));
        paintCockpit.setStyle(Paint.Style.FILL);

        paintFlame = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintFlame.setStyle(Paint.Style.FILL);

        paintParticle = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintParticle.setStyle(Paint.Style.FILL);

        paintPowerUp = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintPowerUp.setStyle(Paint.Style.FILL);
    }

    public void setGameListener(GameListener listener) {
        this.gameListener = listener;
    }

    public void setShipType(ShipType shipType) {
        this.currentShipType = shipType;
    }

    public void setGunType(GunType gunType) {
        this.currentGunType = gunType;
    }

    public void startNewGame() {
        synchronized (this) {
            score = 0;
            playerHealth = 100f;
            playerShield = 0f;
            weaponPowerUpTimer = 0;
            activeBoss = null;
            lastBossThreshold = 0;

            lasers.clear();
            enemies.clear();
            bossProjectiles.clear();
            powerups.clear();
            particles.clear();

            speedMultiplier = 1.0f;
            enemySpawnInterval = 65;
            shakeDuration = 0;
            gameState = MainActivity.GameState.PLAYING;

            if (gameListener != null) {
                gameListener.onScoreChanged(score, highScore);
                gameListener.onStatusChanged(playerHealth, playerShield);
                gameListener.onGameStateChanged(gameState);
                gameListener.onBossStatusChanged(false, "", 0f);
            }
        }
    }

    private void triggerCameraShake(int duration, float intensity) {
        shakeDuration = duration;
        shakeIntensity = intensity;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        canvasWidth = getWidth();
        canvasHeight = getHeight();
        if (playerX == 0f) {
            playerX = canvasWidth / 2f;
            targetPlayerX = playerX;
        }

        stars.clear();
        for (int i = 0; i < 45; i++) {
            int layer = random.nextInt(3);
            float size = (layer + 1) * 2f;
            float speed = (layer + 1) * 1.5f;
            stars.add(new Star(
                    random.nextFloat() * canvasWidth,
                    random.nextFloat() * canvasHeight,
                    speed, size, layer
            ));
        }

        isRunning = true;
        gameThread = new Thread(this);
        gameThread.start();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        canvasWidth = width;
        canvasHeight = height;
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        boolean retry = true;
        isRunning = false;
        while (retry) {
            try {
                gameThread.join();
                retry = false;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (gameState == MainActivity.GameState.PLAYING) {
            int action = event.getAction();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                targetPlayerX = Math.max(45f, Math.min(event.getX(), canvasWidth - 45f));
                return true;
            }
        }
        return super.onTouchEvent(event);
    }

    @Override
    public void run() {
        while (isRunning) {
            if (gameState == MainActivity.GameState.PLAYING) {
                updateGame();
            } else {
                updateStartMenuBackground();
            }
            drawGame();

            try {
                Thread.sleep(16);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void updateStartMenuBackground() {
        synchronized (this) {
            for (Star star : stars) {
                star.y += star.speed * 0.4f;
                if (star.y > canvasHeight) {
                    star.y = 0;
                    star.x = random.nextFloat() * canvasWidth;
                }
            }
        }
    }

    private void updateGame() {
        synchronized (this) {
            frameCounter++;

            // 1. Lerp Player Position
            playerX += (targetPlayerX - playerX) * 0.18f;

            // 2. Adjust timers
            if (weaponPowerUpTimer > 0) {
                weaponPowerUpTimer--;
            }

            // 3. Difficulty scale
            if (frameCounter % 600 == 0) {
                speedMultiplier += 0.12f;
                enemySpawnInterval = Math.max(30, (int) (enemySpawnInterval * 0.92));
            }

            // 4. Parallax starfield updating
            for (Star star : stars) {
                star.y += star.speed;
                if (star.y > canvasHeight) {
                    star.y = 0;
                    star.x = random.nextFloat() * canvasWidth;
                }
            }

            // 5. Boss Spawning Engine
            int scoreLevel = score / 250; // Spawns Boss every 250 points
            if (scoreLevel > lastBossThreshold && activeBoss == null) {
                lastBossThreshold = scoreLevel;
                int type = (scoreLevel % 2 == 1) ? 0 : 1; // Alternates between Dreadnought (0) and Dragon (1)
                float health = 150f + (scoreLevel * 60f);
                float radius = (type == 1) ? 55f : 85f;
                activeBoss = new Boss(canvasWidth / 2f, -120f, radius, health, type);
                triggerCameraShake(25, 20f);
                if (gameListener != null) {
                    gameListener.onTriggerHaptic(200);
                }
            }

            // 6. Projectile Laser Management
            handleWeaponFire();

            // 7. Update Lasers
            Iterator<Laser> laserIterator = lasers.iterator();
            while (laserIterator.hasNext()) {
                Laser l = laserIterator.next();
                l.x += l.vx;
                l.y += l.vy;
                if (l.y < 0 || l.x < 0 || l.x > canvasWidth) {
                    laserIterator.remove();
                }
            }

            // 8. Update Active Boss
            if (activeBoss != null) {
                updateBossLogic();
            }

            // 9. Update Boss Projectiles
            Iterator<BossProjectile> bpIterator = bossProjectiles.iterator();
            while (bpIterator.hasNext()) {
                BossProjectile bp = bpIterator.next();
                bp.x += bp.vx;
                bp.y += bp.vy;
                if (bp.y > canvasHeight || bp.x < 0 || bp.x > canvasWidth) {
                    bpIterator.remove();
                }
            }

            // 10. Update standard Enemies (Only spawn if boss is not active)
            if (activeBoss == null) {
                if (frameCounter % enemySpawnInterval == 0 && canvasWidth > 0) {
                    float radius = random.nextFloat() * 25f + 25f;
                    float xPos = random.nextFloat() * (canvasWidth - radius * 2) + radius;
                    float speed = (random.nextFloat() * 4f + 3f) * speedMultiplier;

                    int seedType = random.nextInt(3);
                    int color;
                    int points;
                    if (seedType == 0) {
                        color = Color.parseColor("#FFFF4136"); // Red
                        points = 10;
                    } else if (seedType == 1) {
                        color = Color.parseColor("#FFFFDC00"); // Yellow
                        points = 20;
                    } else {
                        color = Color.parseColor("#B10DC9"); // Purple
                        points = 35;
                    }
                    enemies.add(new Enemy(xPos, -radius, radius, speed, color, points));
                }
            }

            // Update Standard Enemy Movement
            for (Enemy enemy : enemies) {
                enemy.y += enemy.speed;
                if (enemy.color == Color.parseColor("#FFFFDC00")) {
                    enemy.x += Math.sin((enemy.y / 40f) + enemy.phaseOffset) * 4f;
                    enemy.x = Math.max(enemy.radius, Math.min(enemy.x, canvasWidth - enemy.radius));
                }
            }

            // 11. Update active power-ups
            for (PowerUp pu : powerups) {
                pu.y += pu.speed;
            }
            Iterator<PowerUp> puIterator = powerups.iterator();
            while (puIterator.hasNext()) {
                PowerUp pu = puIterator.next();
                if (pu.y > canvasHeight) {
                    puIterator.remove();
                }
            }

            // 12. Collisions: Lasers vs Boss or Enemies
            List<Laser> destroyedLasers = new ArrayList<>();
            List<Enemy> destroyedEnemies = new ArrayList<>();

            for (Laser laser : lasers) {
                // Boss Collision Check
                if (activeBoss != null) {
                    float dx = laser.x - activeBoss.x;
                    float dy = laser.y - activeBoss.y;
                    double distance = Math.sqrt(dx * dx + dy * dy);

                    if (distance < activeBoss.radius + 15f) {
                        destroyedLasers.add(laser);
                        activeBoss.health -= 5f; // Raw weapon damage

                        // Debris explosion feedback
                        particles.add(new Particle(laser.x, laser.y,
                                (random.nextFloat() - 0.5f) * 10f,
                                (random.nextFloat() - 0.5f) * 10f,
                                Color.parseColor("#FFDC00"), 8));

                        if (gameListener != null) {
                            gameListener.onBossStatusChanged(true,
                                    activeBoss.type == 1 ? "COSMIC DRAGON" : "DREADNOUGHT CARRIER",
                                    (activeBoss.health / activeBoss.maxHealth) * 100f);
                        }

                        // Check Boss defeat
                        if (activeBoss.health <= 0f) {
                            score += 150; // Mass reward
                            if (score > highScore) highScore = score;

                            if (gameListener != null) {
                                gameListener.onScoreChanged(score, highScore);
                                gameListener.onBossStatusChanged(false, "", 0f);
                            }

                            // Big boss explosions
                            for (int i = 0; i < 45; i++) {
                                float vx = (random.nextFloat() - 0.5f) * 22f;
                                float vy = (random.nextFloat() - 0.5f) * 22f;
                                particles.add(new Particle(activeBoss.x, activeBoss.y, vx, vy,
                                        activeBoss.type == 1 ? Color.parseColor("#FF851B") : Color.parseColor("#00FFCC"),
                                        35));
                            }

                            // Guaranteed shield/double shot reward drop
                            powerups.add(new PowerUp(activeBoss.x, activeBoss.y, 25f, 4f, random.nextBoolean() ? 0 : 1));

                            activeBoss = null;
                            triggerCameraShake(35, 25f);
                            break;
                        }
                    }
                }

                // Standard Enemy Check
                for (Enemy enemy : enemies) {
                    float dx = laser.x - enemy.x;
                    float dy = laser.y - enemy.y;
                    double distance = Math.sqrt(dx * dx + dy * dy);

                    if (distance < enemy.radius + 10f) {
                        destroyedLasers.add(laser);
                        destroyedEnemies.add(enemy);
                        score += enemy.points;

                        if (score > highScore) {
                            highScore = score;
                        }

                        if (gameListener != null) {
                            gameListener.onScoreChanged(score, highScore);
                        }

                        if (random.nextFloat() < 0.18f) {
                            powerups.add(new PowerUp(enemy.x, enemy.y, 25f, 4f, random.nextBoolean() ? 0 : 1));
                        }

                        for (int i = 0; i < 15; i++) {
                            particles.add(new Particle(
                                    enemy.x, enemy.y,
                                    (random.nextFloat() - 0.5f) * 14f,
                                    (random.nextFloat() - 0.5f) * 14f,
                                    enemy.color,
                                    random.nextInt(15) + 12
                            ));
                        }
                    }
                }
            }
            lasers.removeAll(destroyedLasers);
            enemies.removeAll(destroyedEnemies);

            // 13. PowerUp Item vs Player
            float playerY = canvasHeight - 120f;
            float playerRadius = 45f;
            puIterator = powerups.iterator();
            while (puIterator.hasNext()) {
                PowerUp pu = puIterator.next();
                float dx = playerX - pu.x;
                float dy = playerY - pu.y;
                double dist = Math.sqrt(dx * dx + dy * dy);

                if (dist < playerRadius + pu.radius) {
                    puIterator.remove();
                    if (gameListener != null) {
                        gameListener.onTriggerHaptic(45);
                    }
                    if (pu.type == 0) {
                        playerShield = Math.min(100f, playerShield + 50f);
                    } else {
                        weaponPowerUpTimer = 350; // Active double shot bonus
                    }
                    if (gameListener != null) {
                        gameListener.onStatusChanged(playerHealth, playerShield);
                    }

                    for (int i = 0; i < 10; i++) {
                        particles.add(new Particle(
                                playerX, playerY,
                                (random.nextFloat() - 0.5f) * 10f,
                                (random.nextFloat() - 0.5f) * 10f,
                                pu.type == 0 ? Color.parseColor("#0074D9") : Color.parseColor("#FF851B"),
                                12
                        ));
                    }
                }
            }

            // 14. Collisions: Enemies/Boss Projectiles vs Player
            List<Enemy> handledEnemies = new ArrayList<>();
            for (Enemy enemy : enemies) {
                float dx = playerX - enemy.x;
                float dy = playerY - enemy.y;
                double dist = Math.sqrt(dx * dx + dy * dy);

                if (dist < (enemy.radius + playerRadius)) {
                    handledEnemies.add(enemy);
                    deductProtection(25f);
                    triggerCameraShake(12, 18f);

                    if (gameListener != null) {
                        gameListener.onTriggerHaptic(120);
                    }
                } else if (enemy.y > canvasHeight) {
                    handledEnemies.add(enemy);
                    deductProtection(10f);
                    triggerCameraShake(8, 8f);
                }
            }
            enemies.removeAll(handledEnemies);

            // Boss Projectiles vs Player
            Iterator<BossProjectile> bpCheck = bossProjectiles.iterator();
            while (bpCheck.hasNext()) {
                BossProjectile bp = bpCheck.next();
                float dx = playerX - bp.x;
                float dy = playerY - bp.y;
                double dist = Math.sqrt(dx * dx + dy * dy);

                if (dist < playerRadius + bp.radius) {
                    bpCheck.remove();
                    deductProtection(20f);
                    triggerCameraShake(10, 15f);

                    if (gameListener != null) {
                        gameListener.onTriggerHaptic(100);
                    }
                }
            }

            // Direct contact with Boss body
            if (activeBoss != null) {
                float dx = playerX - activeBoss.x;
                float dy = playerY - activeBoss.y;
                double dist = Math.sqrt(dx * dx + dy * dy);
                if (dist < playerRadius + activeBoss.radius) {
                    deductProtection(1f); // Quick frame erosion
                    triggerCameraShake(3, 10f);
                }
            }

            // 15. Particles Decay
            for (Particle p : particles) {
                p.x += p.vx;
                p.y += p.vy;
                p.life--;
                p.alpha = Math.max(0f, (float) p.life / p.maxLife);
            }
            Iterator<Particle> particleIterator = particles.iterator();
            while (particleIterator.hasNext()) {
                if (particleIterator.next().life <= 0) {
                    particleIterator.remove();
                }
            }

            // Defeat Criteria
            if (playerHealth <= 0f) {
                playerHealth = 0f;
                gameState = MainActivity.GameState.GAME_OVER;

                if (gameListener != null) {
                    gameListener.onGameStateChanged(gameState);
                    // Award player all points earned during run to their permanent wallet
                    gameListener.onGameFinishedAndGainedPoints(score);
                }
            }
        }
    }

    private void handleWeaponFire() {
        // Standard high speed, distinct fire patterns
        if (frameCounter % 11 == 0) {
            float fireY = canvasHeight - 165f;

            // Check override bonus, else default gun selected
            if (weaponPowerUpTimer > 0) {
                // Double Laser Override
                lasers.add(new Laser(playerX - 22f, fireY));
                lasers.add(new Laser(playerX + 22f, fireY));
                return;
            }

            switch (currentGunType) {
                case PLASMA:
                    // Single High Impact Plasma
                    lasers.add(new Laser(playerX, fireY));
                    break;
                case DOUBLE_BOLT:
                    // Twin Dual lasers
                    lasers.add(new Laser(playerX - 18f, fireY));
                    lasers.add(new Laser(playerX + 18f, fireY));
                    break;
                case SPREAD_FIRE:
                    // Triple 3-Way angled lasers
                    lasers.add(new Laser(playerX, fireY, 0f, -25f));
                    lasers.add(new Laser(playerX - 10f, fireY, -5f, -24f));
                    lasers.add(new Laser(playerX + 10f, fireY, 5f, -24f));
                    break;
                case COSMIC_RAY:
                    // Rapid Continuous heavy lasers (runs twice as fast)
                    lasers.add(new Laser(playerX - 8f, fireY, 0, -28f));
                    lasers.add(new Laser(playerX + 8f, fireY, 0, -28f));
                    break;
            }
        } else if (currentGunType == GunType.COSMIC_RAY && frameCounter % 5 == 0) {
            // Cosmic Ray fires at hyper-frequency frame rates
            float fireY = canvasHeight - 165f;
            lasers.add(new Laser(playerX, fireY, 0, -32f));
        }
    }

    private void updateBossLogic() {
        // Entering/centering logic
        if (activeBoss.y < 220f) {
            activeBoss.y += 3.5f;
        }

        // Floating/Slithering motion
        activeBoss.angle += 0.05f;

        if (activeBoss.type == 0) { // Dreadnought: standard floating movement
            activeBoss.x += activeBoss.dx;
            if (activeBoss.x < activeBoss.radius || activeBoss.x > canvasWidth - activeBoss.radius) {
                activeBoss.dx *= -1;
            }

            // Dreadnought shoot pattern (radial pulse bursts)
            if (frameCounter % 45 == 0) {
                for (int i = 0; i < 8; i++) {
                    double angle = (i * Math.PI / 4) + (activeBoss.angle * 0.5f);
                    float vx = (float) Math.cos(angle) * 7f;
                    float vy = (float) Math.sin(angle) * 7f;
                    bossProjectiles.add(new BossProjectile(activeBoss.x, activeBoss.y, vx, vy, 15f, Color.parseColor("#FFFF4136")));
                }
            }
        } else if (activeBoss.type == 1) { // Cosmic Dragon
            // Move Dragon Head in beautiful dynamic sine-wave pathing
            float targetX = (canvasWidth / 2f) + (float) Math.sin(activeBoss.angle) * (canvasWidth * 0.35f);
            activeBoss.x += (targetX - activeBoss.x) * 0.12f;

            // Slither bodies trailing dynamics
            float prevX = activeBoss.x;
            float prevY = activeBoss.y;
            for (DragonSegment segment : activeBoss.segments) {
                float dx = prevX - segment.x;
                float dy = prevY - segment.y;
                float distance = (float) Math.sqrt(dx * dx + dy * dy);
                float minDistance = 35f; // Link separation

                if (distance > minDistance) {
                    segment.x = prevX - (dx / distance) * minDistance;
                    segment.y = prevY - (dy / distance) * minDistance;
                }
                prevX = segment.x;
                prevY = segment.y;
            }

            // Dragon Fire breath!
            if (frameCounter % 28 == 0) {
                // Spews orange/red fireballs downward with random velocity spread
                float vx = (random.nextFloat() - 0.5f) * 6f;
                float vy = 8f + random.nextFloat() * 5f;
                bossProjectiles.add(new BossProjectile(activeBoss.x, activeBoss.y + 10f, vx, vy, 18f, Color.parseColor("#FFFF851B")));
            }
        }
    }

    private void deductProtection(float amount) {
        if (playerShield > 0) {
            playerShield -= amount;
            if (playerShield < 0) {
                playerHealth += playerShield;
                playerShield = 0f;
            }
        } else {
            playerHealth -= amount;
        }

        if (gameListener != null) {
            gameListener.onStatusChanged(playerHealth, playerShield);
        }
    }

    // ==========================================
    // CANVAS RENDERING & CUSTOM STYLING
    // ==========================================
    private void drawGame() {
        if (surfaceHolder.getSurface().isValid()) {
            Canvas canvas = surfaceHolder.lockCanvas();
            if (canvas == null) return;

            // Render Space Void Background
            canvas.drawColor(Color.parseColor("#050510"));

            synchronized (this) {
                if (shakeDuration > 0) {
                    float currentShakeX = (random.nextFloat() - 0.5f) * shakeIntensity;
                    float currentShakeY = (random.nextFloat() - 0.5f) * shakeIntensity;
                    canvas.translate(currentShakeX, currentShakeY);
                    shakeDuration--;
                }

                // Star layers
                for (Star star : stars) {
                    if (star.layer == 0) paintStar.setAlpha(80);
                    else if (star.layer == 1) paintStar.setAlpha(160);
                    else paintStar.setAlpha(255);
                    canvas.drawCircle(star.x, star.y, star.size, paintStar);
                }

                // Laser Projectiles
                for (Laser laser : lasers) {
                    int laserColor = getLaserColorByWeapon();
                    paintLaser.setColor(laserColor);
                    paintLaserGlow.setColor(Color.argb(50, Color.red(laserColor), Color.green(laserColor), Color.blue(laserColor)));

                    canvas.drawRect(laser.x - 3.5f, laser.y, laser.x + 3.5f, laser.y + 35f, paintLaser);
                    canvas.drawRect(laser.x - 9f, laser.y - 4f, laser.x + 9f, laser.y + 39f, paintLaserGlow);
                }

                // Collectables
                for (PowerUp pu : powerups) {
                    drawPowerUpItem(canvas, pu);
                }

                // Enemies
                for (Enemy enemy : enemies) {
                    drawEnemyUnit(canvas, enemy);
                }

                // Bosses
                if (activeBoss != null) {
                    drawBoss(canvas);
                }

                // Boss Projectiles
                Paint paintBP = new Paint(Paint.ANTI_ALIAS_FLAG);
                for (BossProjectile bp : bossProjectiles) {
                    paintBP.setColor(bp.color);
                    canvas.drawCircle(bp.x, bp.y, bp.radius, paintBP);

                    // Core bright spark glow
                    paintBP.setColor(Color.WHITE);
                    canvas.drawCircle(bp.x, bp.y, bp.radius * 0.5f, paintBP);
                }

                // Spark debris
                for (Particle p : particles) {
                    paintParticle.setColor(p.color);
                    paintParticle.setAlpha((int) (p.alpha * 255));
                    canvas.drawCircle(p.x, p.y, 6f, paintParticle);
                }

                // Render Active Spacecraft Skin
                if (gameState != MainActivity.GameState.START) {
                    drawPlayerSpaceship(canvas, playerX, canvasHeight - 120f);
                }
            }

            surfaceHolder.unlockCanvasAndPost(canvas);
        }
    }

    private int getLaserColorByWeapon() {
        if (weaponPowerUpTimer > 0) return Color.parseColor("#FF851B"); // Golden Overdrive lasers

        switch (currentGunType) {
            case DOUBLE_BOLT: return Color.parseColor("#0074D9"); // Deep Blue
            case SPREAD_FIRE: return Color.parseColor("#B10DC9"); // Dark Purple
            case COSMIC_RAY: return Color.parseColor("#FF4136");  // Neon Red
            case PLASMA:
            default:
                return Color.parseColor("#00FFCC"); // Classic Cyan
        }
    }

    private void drawPlayerSpaceship(Canvas canvas, float centerX, float centerY) {
        // Draw Flame Thrusters
        drawVehicleThrusters(canvas, centerX, centerY);

        // Shield Bubble
        if (playerShield > 0f) {
            Paint shieldPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            shieldPaint.setStyle(Paint.Style.STROKE);
            shieldPaint.setStrokeWidth(5f + (float) Math.sin(frameCounter * 0.15f) * 2f);
            shieldPaint.setColor(Color.argb(180, 0, 116, 217));
            canvas.drawCircle(centerX, centerY, 62f, shieldPaint);

            Paint shieldGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
            shieldGlow.setStyle(Paint.Style.FILL);
            shieldGlow.setColor(Color.argb(30, 0, 116, 217));
            canvas.drawCircle(centerX, centerY, 62f, shieldGlow);
        }

        // Hull Colors
        int accentOutlineColor = getLaserColorByWeapon();
        paintPlayerHull.setColor(Color.parseColor("#05162D"));
        paintPlayerOutline.setColor(accentOutlineColor);

        // Conditional Drawing depending on Unlocked selection
        if (currentShipType == ShipType.CAR) {
            // CYBER CAR MODEL
            // Body Chassis (Futuristic Roadster)
            Path carBody = new Path();
            carBody.moveTo(centerX - 18f, centerY - 45f); // Front bumper
            carBody.lineTo(centerX + 18f, centerY - 45f);
            carBody.lineTo(centerX + 26f, centerY - 20f); // Hood
            carBody.lineTo(centerX + 32f, centerY + 30f); // Right Fender
            carBody.lineTo(centerX - 32f, centerY + 30f); // Left Fender
            carBody.close();
            canvas.drawPath(carBody, paintPlayerHull);
            canvas.drawPath(carBody, paintPlayerOutline);

            // Glowing Cyber-wheels
            Paint wheelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            wheelPaint.setColor(accentOutlineColor);
            wheelPaint.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(centerX - 38f, centerY - 15f, centerX - 30f, centerY + 5f, 5f, 5f, wheelPaint); // Left Wheel
            canvas.drawRoundRect(centerX + 30f, centerY - 15f, centerX + 38f, centerY + 5f, 5f, 5f, wheelPaint); // Right Wheel

            // Sports spoiler
            canvas.drawRect(centerX - 35f, centerY + 24f, centerX + 35f, centerY + 32f, paintPlayerOutline);

            // Windshield
            Path cockpit = new Path();
            cockpit.moveTo(centerX - 12f, centerY - 15f);
            cockpit.lineTo(centerX + 12f, centerY - 15f);
            cockpit.lineTo(centerX + 16f, centerY + 5f);
            cockpit.lineTo(centerX - 16f, centerY + 5f);
            cockpit.close();
            canvas.drawPath(cockpit, paintCockpit);

        } else if (currentShipType == ShipType.JETPACK) {
            // ROCKET JETPACK SUIT MODEL
            // Central Suit Armor Body
            canvas.drawCircle(centerX, centerY, 22f, paintPlayerHull);
            canvas.drawCircle(centerX, centerY, 22f, paintPlayerOutline);

            // Left Booster Tank
            canvas.drawRoundRect(centerX - 32f, centerY - 25f, centerX - 18f, centerY + 25f, 8f, 8f, paintPlayerHull);
            canvas.drawRoundRect(centerX - 32f, centerY - 25f, centerX - 18f, centerY + 25f, 8f, 8f, paintPlayerOutline);

            // Right Booster Tank
            canvas.drawRoundRect(centerX + 18f, centerY - 25f, centerX + 32f, centerY + 25f, 8f, 8f, paintPlayerHull);
            canvas.drawRoundRect(centerX + 18f, centerY - 25f, centerX + 32f, centerY + 25f, 8f, 8f, paintPlayerOutline);

            // Connector Pipes
            canvas.drawRect(centerX - 22f, centerY - 4f, centerX + 22f, centerY + 4f, paintPlayerOutline);

            // Helmet Visor
            canvas.drawCircle(centerX, centerY - 6f, 10f, paintCockpit);

        } else {
            // CLASSIC SPACESHIP
            Path shipPath = new Path();
            shipPath.moveTo(centerX, centerY - 45f);        // Nose
            shipPath.lineTo(centerX - 35f, centerY + 20f);   // Left Wing Engine
            shipPath.lineTo(centerX - 12f, centerY + 12f);   // Wing Joint Left
            shipPath.lineTo(centerX + 12f, centerY + 12f);   // Wing Joint Right
            shipPath.lineTo(centerX + 35f, centerY + 20f);   // Right Wing Engine
            shipPath.close();

            canvas.drawPath(shipPath, paintPlayerHull);
            canvas.drawPath(shipPath, paintPlayerOutline);

            // Cockpit Glass Hatch
            Path cockpitPath = new Path();
            cockpitPath.moveTo(centerX, centerY - 22f);
            cockpitPath.lineTo(centerX - 8f, centerY + 4f);
            cockpitPath.lineTo(centerX + 8f, centerY + 4f);
            cockpitPath.close();
            canvas.drawPath(cockpitPath, paintCockpit);
        }
    }

    private void drawVehicleThrusters(Canvas canvas, float centerX, float centerY) {
        if (currentShipType == ShipType.JETPACK) {
            // Dual booster exhausts
            drawFlamePlume(canvas, centerX - 25f, centerY + 25f, 8f);
            drawFlamePlume(canvas, centerX + 25f, centerY + 25f, 8f);
        } else {
            // Main center jet stream
            drawFlamePlume(canvas, centerX, centerY + 20f, 15f);
        }
    }

    private void drawFlamePlume(Canvas canvas, float x, float y, float widthHalf) {
        Path flamePath = new Path();
        flamePath.moveTo(x - widthHalf, y);
        flamePath.lineTo(x, y + 25f + random.nextInt(20));
        flamePath.lineTo(x + widthHalf, y);
        flamePath.close();

        paintFlame.setShader(new LinearGradient(
                x, y,
                x, y + 40f,
                Color.parseColor("#FFFF851B"),
                Color.argb(0, 255, 220, 0),
                Shader.TileMode.CLAMP
        ));
        canvas.drawPath(flamePath, paintFlame);
        paintFlame.setShader(null);
    }

    private void drawBoss(Canvas canvas) {
        Paint paintBoss = new Paint(Paint.ANTI_ALIAS_FLAG);

        if (activeBoss.type == 0) {
            // DREADNOUGHT RENDERING (Big Mechanical Core Carrier)
            // Outer Hull Shielding
            paintBoss.setStyle(Paint.Style.FILL);
            paintBoss.setColor(Color.parseColor("#1C1C3A"));
            canvas.drawCircle(activeBoss.x, activeBoss.y, activeBoss.radius, paintBoss);

            paintBoss.setStyle(Paint.Style.STROKE);
            paintBoss.setStrokeWidth(10f);
            paintBoss.setColor(Color.parseColor("#B10DC9"));
            canvas.drawCircle(activeBoss.x, activeBoss.y, activeBoss.radius, paintBoss);

            // Neon Spiked edges
            paintBoss.setStyle(Paint.Style.FILL);
            paintBoss.setColor(Color.parseColor("#00FFCC"));
            canvas.drawRect(activeBoss.x - activeBoss.radius - 12f, activeBoss.y - 15f,
                    activeBoss.x - activeBoss.radius + 12f, activeBoss.y + 15f, paintBoss);
            canvas.drawRect(activeBoss.x + activeBoss.radius - 12f, activeBoss.y - 15f,
                    activeBoss.x + activeBoss.radius + 12f, activeBoss.y + 15f, paintBoss);

            // Glowing Eye core
            paintBoss.setColor(Color.RED);
            canvas.drawCircle(activeBoss.x, activeBoss.y, 25f + (float) Math.sin(frameCounter * 0.1f) * 6f, paintBoss);
            paintBoss.setColor(Color.WHITE);
            canvas.drawCircle(activeBoss.x, activeBoss.y, 8f, paintBoss);

        } else if (activeBoss.type == 1) {
            // COSMIC DRAGON (Slithering Multi-Segment creature)
            Paint segmentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            segmentPaint.setStyle(Paint.Style.FILL);

            // Draw Body trailing segments backwards so head draws on top
            for (int i = activeBoss.segments.size() - 1; i >= 0; i--) {
                DragonSegment segment = activeBoss.segments.get(i);
                float radiusSize = activeBoss.radius * (1.0f - (i * 0.05f));

                // Alternate Scale colors (Crimson and Neon Orange)
                if (i % 2 == 0) {
                    segmentPaint.setColor(Color.parseColor("#D50000")); // Crimson
                } else {
                    segmentPaint.setColor(Color.parseColor("#FF6D00")); // Deep Orange
                }
                canvas.drawCircle(segment.x, segment.y, radiusSize, segmentPaint);

                // Spikes details
                segmentPaint.setColor(Color.parseColor("#FFFFDC00"));
                canvas.drawCircle(segment.x, segment.y - radiusSize, radiusSize * 0.25f, segmentPaint);
            }

            // Head segment drawing
            segmentPaint.setColor(Color.parseColor("#FF1744"));
            canvas.drawCircle(activeBoss.x, activeBoss.y, activeBoss.radius + 5f, segmentPaint);

            // Angry glowing eyes
            segmentPaint.setColor(Color.YELLOW);
            canvas.drawCircle(activeBoss.x - 18f, activeBoss.y - 8f, 10f, segmentPaint);
            canvas.drawCircle(activeBoss.x + 18f, activeBoss.y - 8f, 10f, segmentPaint);

            segmentPaint.setColor(Color.BLACK);
            canvas.drawCircle(activeBoss.x - 18f, activeBoss.y - 8f, 4f, segmentPaint);
            canvas.drawCircle(activeBoss.x + 18f, activeBoss.y - 8f, 4f, segmentPaint);

            // Fire emission mouth sparks
            if (frameCounter % 3 == 0) {
                particles.add(new Particle(activeBoss.x, activeBoss.y + 12f,
                        (random.nextFloat() - 0.5f) * 12f,
                        random.nextFloat() * 10f + 5f,
                        Color.parseColor("#FF5722"), 8));
            }
        }
    }

    private void drawEnemyUnit(Canvas canvas, Enemy enemy) {
        Path path = new Path();
        if (enemy.color == Color.parseColor("#FFFF4136")) { // Dreadnought Minion
            path.moveTo(enemy.x, enemy.y + enemy.radius);
            path.lineTo(enemy.x - enemy.radius, enemy.y - enemy.radius);
            path.lineTo(enemy.x + enemy.radius, enemy.y - enemy.radius);
            path.close();

            paintEnemyFill.setColor(Color.argb(40, 255, 65, 54));
            paintEnemyStroke.setColor(enemy.color);

            canvas.drawPath(path, paintEnemyFill);
            canvas.drawPath(path, paintEnemyStroke);

        } else if (enemy.color == Color.parseColor("#FFFFDC00")) { // Swerving Diamond
            path.moveTo(enemy.x, enemy.y - enemy.radius);
            path.lineTo(enemy.x - enemy.radius, enemy.y);
            path.lineTo(enemy.x, enemy.y + enemy.radius);
            path.lineTo(enemy.x + enemy.radius, enemy.y);
            path.close();

            paintEnemyFill.setColor(Color.argb(40, 255, 220, 0));
            paintEnemyStroke.setColor(enemy.color);

            canvas.drawPath(path, paintEnemyFill);
            canvas.drawPath(path, paintEnemyStroke);

        } else { // Hex Interceptor
            paintEnemyFill.setColor(Color.argb(40, 177, 13, 201));
            paintEnemyStroke.setColor(enemy.color);

            canvas.drawCircle(enemy.x, enemy.y, enemy.radius, paintEnemyFill);
            canvas.drawCircle(enemy.x, enemy.y, enemy.radius, paintEnemyStroke);

            paintEnemyFill.setColor(enemy.color);
            canvas.drawCircle(enemy.x, enemy.y, enemy.radius / 2.5f, paintEnemyFill);
        }
    }

    private void drawPowerUpItem(Canvas canvas, PowerUp pu) {
        int alphaValue = 120 + (int) (Math.sin(frameCounter * 0.2f) * 80);

        if (pu.type == 0) {
            paintPowerUp.setColor(Color.argb(alphaValue, 0, 116, 217));
            canvas.drawCircle(pu.x, pu.y, pu.radius, paintPowerUp);

            paintPowerUp.setColor(Color.WHITE);
            paintPowerUp.setTextSize(26f);
            paintPowerUp.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("S", pu.x, pu.y + 9f, paintPowerUp);
        } else {
            paintPowerUp.setColor(Color.argb(alphaValue, 255, 133, 27));
            canvas.drawCircle(pu.x, pu.y, pu.radius, paintPowerUp);

            paintPowerUp.setColor(Color.WHITE);
            paintPowerUp.setTextSize(24f);
            paintPowerUp.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("W", pu.x, pu.y + 8f, paintPowerUp);
        }
    }
}