package pitiupi.flappy.GUI;

import pitiupi.flappy.control.FlappyController;
import pitiupi.flappy.control.GhostState;
import pitiupi.presentation.shell.PluginRoutingPanel;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.Toolkit;
import java.awt.Transparency;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.VolatileImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Locale;
import java.util.Random;
import java.util.function.Supplier;

public class FlappyCanvas extends JPanel {

    public enum GamePhase {
        IDLE,
        READY,
        COUNTDOWN,
        PLAYING,
        GAME_OVER
    }

    public static final int DESIGN_WIDTH = 640;
    public static final int DESIGN_HEIGHT = 440;
    public static final int GROUND_Y = 390;

    private static final float BIRD_SCREEN_X = 130f;
    private static final float BIRD_RADIUS = 14f;
    private static final float HITBOX_RADIUS = 7.5f;
    private static final float GRAVITY = 0.38f;
    private static final float JUMP_VELOCITY = -6.9f;
    private static final float SPEED_X = 2.9f;

    private static final int PIPE_WIDTH = 56;
    private static final int PIPE_GAP = 132;
    private static final int PIPE_SPACING = 230;
    private static final int FIRST_PIPE_X = 420;

    private static final Color BG_BLACK = new Color(10, 10, 12);
    private static final Color NODE_BG = new Color(18, 18, 22);
    private static final Color BUS_BG = new Color(16, 16, 20);
    private static final Color GRID_DARK = new Color(28, 28, 32);
    private static final Color WAVE_GRAY = new Color(50, 50, 58);
    private static final Color LINK_GRAY = new Color(55, 55, 62);
    private static final Color NODE_BORDER = new Color(65, 65, 74);
    private static final Color WIRE_DIM = new Color(75, 75, 82);
    private static final Color SLOT_GUIDE = new Color(90, 90, 100);
    private static final Color NODE_TEXT = new Color(110, 110, 122);
    private static final Color BUS_TEXT = new Color(120, 120, 130);
    private static final Color WIRE_MID = new Color(145, 145, 155);
    private static final Color WIRE_BRIGHT = new Color(245, 245, 245);

    private static final Font FONT_MONO_10 = new Font(Font.MONOSPACED, Font.PLAIN, 10);
    private static final Font FONT_MONO_10_BOLD = new Font(Font.MONOSPACED, Font.BOLD, 10);
    private static final Font FONT_MONO_11_BOLD = new Font(Font.MONOSPACED, Font.BOLD, 11);
    private static final Font FONT_MONO_12 = new Font(Font.MONOSPACED, Font.PLAIN, 12);
    private static final Font FONT_MONO_12_BOLD = new Font(Font.MONOSPACED, Font.BOLD, 12);
    private static final Font FONT_MONO_13_BOLD = new Font(Font.MONOSPACED, Font.BOLD, 13);
    private static final Font FONT_MONO_14_BOLD = new Font(Font.MONOSPACED, Font.BOLD, 14);
    private static final Font FONT_MONO_15_BOLD = new Font(Font.MONOSPACED, Font.BOLD, 15);
    private static final Font FONT_MONO_16_BOLD = new Font(Font.MONOSPACED, Font.BOLD, 16);
    private static final Font FONT_MONO_18_BOLD = new Font(Font.MONOSPACED, Font.BOLD, 18);
    private static final Font FONT_MONO_26_BOLD = new Font(Font.MONOSPACED, Font.BOLD, 26);
    private static final Font FONT_MONO_30_BOLD = new Font(Font.MONOSPACED, Font.BOLD, 30);

    private static final Stroke STROKE_1 = new BasicStroke(1f);
    private static final Stroke STROKE_1_5 = new BasicStroke(1.5f);
    private static final Stroke STROKE_1_8 = new BasicStroke(1.8f);
    private static final Stroke STROKE_2 = new BasicStroke(2f);
    private static final Stroke STROKE_DASHED_LINK = new BasicStroke(
            1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{4f, 4f}, 0);
    private static final Stroke STROKE_DASHED_SLOT = new BasicStroke(
            1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{3f, 5f}, 0);

    private static final Composite ALPHA_GHOST_ALIVE = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.50f);
    private static final Composite ALPHA_GHOST_DROP = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.30f);
    private static final Composite ALPHA_OPAQUE = AlphaComposite.SrcOver;

    private final FlappyController controller;
    private final Supplier<PluginRoutingPanel.RoutingSnapshot> routingSupplier;
    private final Runnable onStateChanged;

    private static final boolean IS_WINDOWS = System.getProperty("os.name", "")
            .toLowerCase(Locale.ROOT)
            .contains("win");

    private final BufferedImage frameBuffer = new BufferedImage(
            DESIGN_WIDTH, DESIGN_HEIGHT, BufferedImage.TYPE_INT_RGB);
    private VolatileImage volatileBuffer;
    private final BufferedImage localPacketSprite;
    private final BufferedImage localDroppedSprite;
    private final BufferedImage ghostPacketSprite;
    private final BufferedImage ghostDroppedSprite;
    private final BufferedImage bgGridSprite;
    private final BufferedImage nodesFarSprite;
    private final BufferedImage nodesSprite;
    private final BufferedImage waveSprite;
    private final BufferedImage firewallBodySprite;
    private final BufferedImage firewallHeadSprite;
    private final BufferedImage busSprite;
    private final BufferedImage bottomTextSprite;

    private final Timer gameLoopTimer;
    private GamePhase phase = GamePhase.IDLE;
    private boolean localSoloMode = true;
    private boolean deadReckoningEnabled = true;

    private long countdownEndMillis = 0L;
    private float birdY = 200f;
    private float birdVelocityY = 0f;
    private float worldX = 0f;
    private int score = 0;
    private int bestScore = 0;
    private int frameCounter = 0;

    private long lastTickNanos = System.nanoTime();
    private long lastNetTickMillis = 0L;
    private long fpsWindowStartMs = System.currentTimeMillis();
    private int fpsFrames = 0;
    private int currentFps = 60;

    private boolean flapKeyHeld = false;
    private long lastFlapPressMs = 0L;

    private static final int PARTICLE_COUNT = 14;
    private final float[] partX = new float[PARTICLE_COUNT];
    private final float[] partY = new float[PARTICLE_COUNT];
    private final float[] partVx = new float[PARTICLE_COUNT];
    private final float[] partVy = new float[PARTICLE_COUNT];
    private final float[] partLife = new float[PARTICLE_COUNT];
    private final int[] partKind = new int[PARTICLE_COUNT];

    private static final int IO_SAMPLES = 26;
    private final int[] ioSamples = new int[IO_SAMPLES];
    private int ioWriteIdx = 0;
    private int lastIoPacketsTotal = 0;
    private int pendingFlapSpike = 0;
    private long lastIoSampleMs = 0L;

    public FlappyCanvas(
            FlappyController controller,
            Supplier<PluginRoutingPanel.RoutingSnapshot> routingSupplier,
            Runnable onStateChanged
    ) {
        this.controller = controller;
        this.routingSupplier = routingSupplier;
        this.onStateChanged = onStateChanged;

        BufferedImage loadedLocal = loadCompatibleSprite("package_model_1.png", false);
        BufferedImage loadedDead = loadCompatibleSprite("package_model_1_dead.png", false);

        this.localPacketSprite = loadedLocal != null ? loadedLocal : createPacketSpriteImage(false, false);
        this.localDroppedSprite = loadedDead != null ? loadedDead : createPacketSpriteImage(true, false);
        this.ghostPacketSprite = loadedLocal != null
                ? createGhostVariant(loadedLocal, 0.52f)
                : createPacketSpriteImage(false, true);
        this.ghostDroppedSprite = loadedDead != null
                ? createGhostVariant(loadedDead, 0.35f)
                : createPacketSpriteImage(true, true);

        this.bgGridSprite = loadCompatibleSprite("Background_tiles_1.png", true);
        this.nodesFarSprite = loadCompatibleSprite("nodes_but_far.png", false);
        this.nodesSprite = loadCompatibleSprite("nodes.png", false);
        this.waveSprite = loadCompatibleSprite("wave.png", false);
        this.firewallBodySprite = loadCompatibleSprite("firewalls.png", false);
        this.firewallHeadSprite = loadCompatibleSprite("firewall_head.png", false);
        this.busSprite = loadCompatibleSprite("bus.png", false);
        this.bottomTextSprite = loadCompatibleSprite("bottom_text.png", false);

        setPreferredSize(new Dimension(DESIGN_WIDTH, DESIGN_HEIGHT));
        setMinimumSize(new Dimension(480, 360));
        setFocusable(true);
        setOpaque(true);
        setBackground(BG_BLACK);

        setupInputBindings();
        this.gameLoopTimer = new Timer(10, e -> onTick());
        this.gameLoopTimer.setCoalesce(true);
        this.gameLoopTimer.start();
    }

    private static BufferedImage createGhostVariant(BufferedImage src, float alpha) {
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage out;
        if (!GraphicsEnvironment.isHeadless()) {
            GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice()
                    .getDefaultConfiguration();
            out = gc.createCompatibleImage(w, h, Transparency.TRANSLUCENT);
        } else {
            out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        }
        Graphics2D g2 = out.createGraphics();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        g2.drawImage(src, 0, 0, null);
        g2.dispose();
        return out;
    }

    private static BufferedImage loadCompatibleSprite(String resourceName, boolean opaque) {
        String path = "/pitiupi/flappy/assets/" + resourceName;
        try (InputStream in = FlappyCanvas.class.getResourceAsStream(path)) {
            if (in == null) {
                return null;
            }
            BufferedImage raw = ImageIO.read(in);
            if (raw == null) {
                return null;
            }
            BufferedImage compat;
            if (!GraphicsEnvironment.isHeadless()) {
                GraphicsConfiguration gc = GraphicsEnvironment.getLocalGraphicsEnvironment()
                        .getDefaultScreenDevice()
                        .getDefaultConfiguration();
                compat = gc.createCompatibleImage(
                        raw.getWidth(),
                        raw.getHeight(),
                        opaque ? Transparency.OPAQUE : Transparency.BITMASK
                );
            } else {
                compat = new BufferedImage(
                        raw.getWidth(),
                        raw.getHeight(),
                        opaque ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB
                );
            }
            Graphics2D g2 = compat.createGraphics();
            g2.drawImage(raw, 0, 0, null);
            g2.dispose();
            return compat;
        } catch (IOException ex) {
            return null;
        }
    }

    private static BufferedImage createPacketSpriteImage(boolean dropped, boolean ghostWireframe) {
        int imgW = 36;
        int imgH = 28;
        BufferedImage img = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

        int w = 30;
        int h = 22;
        int x = (imgW - w) / 2;
        int y = (imgH - h) / 2;

        if (ghostWireframe) {
            g2.setColor(new Color(20, 20, 24, 190));
            g2.fillRect(x, y, w, h);
            g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{4f, 3f}, 0f));
            g2.setColor(WIRE_MID);
            g2.drawRect(x, y, w, h);
            if (dropped) {
                g2.drawLine(x + 4, y + 4, x + w - 4, y + h - 4);
                g2.drawLine(x + w - 4, y + 4, x + 4, y + h - 4);
            }
        } else if (dropped) {
            g2.setColor(new Color(35, 35, 40));
            g2.fillRect(x, y, w, h);
            g2.setColor(WIRE_BRIGHT);
            g2.setStroke(STROKE_2);
            g2.drawRect(x, y, w, h);
            g2.drawLine(x, y, x + w, y + h);
            g2.drawLine(x + w, y, x, y + h);
        } else {
            g2.setColor(WIRE_BRIGHT);
            g2.fillRect(x, y, w, h);
            g2.setColor(BG_BLACK);
            g2.fillRect(x + 2, y + 7, w - 4, h - 9);

            g2.setColor(WIRE_BRIGHT);
            g2.setStroke(STROKE_1_5);
            g2.drawLine(x + 3, y + 7, x + w / 2, y + 15);
            g2.drawLine(x + w - 3, y + 7, x + w / 2, y + 15);

            g2.setColor(BG_BLACK);
            g2.fillRect(x + 3, y + 2, 4, 3);
            g2.fillRect(x + 9, y + 2, 4, 3);
            g2.fillRect(x + 15, y + 2, 8, 3);
        }

        g2.dispose();
        return img;
    }

    private void setupInputBindings() {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                handleJumpOrStart();
            }
        });

        int[] flapKeys = {KeyEvent.VK_SPACE, KeyEvent.VK_UP, KeyEvent.VK_W};
        for (int keyCode : flapKeys) {
            getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                    KeyStroke.getKeyStroke(keyCode, 0, false), "flapPress");
            getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                    KeyStroke.getKeyStroke(keyCode, 0, true), "flapRelease");
        }

        getActionMap().put("flapPress", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                long now = System.currentTimeMillis();
                if (!flapKeyHeld || (now - lastFlapPressMs) > 350L) {
                    flapKeyHeld = true;
                    lastFlapPressMs = now;
                    handleJumpOrStart();
                }
            }
        });

        getActionMap().put("flapRelease", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                flapKeyHeld = false;
            }
        });

        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_R, 0), "restart");
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "restart");
        getActionMap().put("restart", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (phase == GamePhase.READY) {
                    toggleLocalReady();
                } else if (phase == GamePhase.IDLE) {
                    startLocalSoloRun(controller.getCurrentSeed());
                    flap();
                } else if (phase == GamePhase.GAME_OVER) {
                    if (localSoloMode) {
                        startLocalSoloRun(controller.getCurrentSeed());
                        flap();
                    } else {
                        enterReadyScreen(true);
                    }
                }
            }
        });
    }

    private void handleJumpOrStart() {
        if (phase == GamePhase.READY) {
            toggleLocalReady();
        } else if (phase == GamePhase.IDLE) {
            startLocalSoloRun(controller.getCurrentSeed());
            flap();
        } else if (phase == GamePhase.GAME_OVER && !localSoloMode) {
            enterReadyScreen(true);
        } else if (phase == GamePhase.PLAYING) {
            flap();
        }
    }

    private void toggleLocalReady() {
        controller.setLocalReady(routingSupplier.get(), !controller.isLocalReady());
        notifyChanged();
        repaint();
    }

    public void enterReadyScreen(boolean markReady) {
        this.localSoloMode = false;
        resetLocalBird();
        this.phase = GamePhase.READY;
        controller.setLocalReady(routingSupplier.get(), markReady);
        requestFocusInWindow();
        notifyChanged();
        repaint();
    }

    public void enterIdleSoloMode() {
        this.localSoloMode = true;
        resetLocalBird();
        this.phase = GamePhase.IDLE;
        requestFocusInWindow();
        notifyChanged();
        repaint();
    }

    private void flap() {
        birdVelocityY = JUMP_VELOCITY;
        pendingFlapSpike = 14;
    }

    public void startSynchronizedCountdown(String matchId, long seed) {
        this.localSoloMode = false;
        controller.setMatchContext(matchId, seed);
        resetLocalBird();
        this.phase = GamePhase.COUNTDOWN;
        this.countdownEndMillis = System.currentTimeMillis() + 3000L;
        requestFocusInWindow();
        notifyChanged();
        repaint();
    }

    public void startLocalSoloRun(long seed) {
        this.localSoloMode = true;
        controller.setMatchContext("solo", seed);
        resetLocalBird();
        this.phase = GamePhase.PLAYING;
        this.lastTickNanos = System.nanoTime();
        requestFocusInWindow();
        notifyChanged();
        repaint();
    }

    public void startQuickRun(long seed) {
        startLocalSoloRun(seed);
    }

    public void stopGameLoop() {
        if (gameLoopTimer != null && gameLoopTimer.isRunning()) {
            gameLoopTimer.stop();
        }
    }

    public void ensureGameLoopRunning() {
        this.lastTickNanos = System.nanoTime();
        if (gameLoopTimer != null && !gameLoopTimer.isRunning()) {
            gameLoopTimer.start();
        }
    }

    public void setDeadReckoningEnabled(boolean enabled) {
        this.deadReckoningEnabled = enabled;
        repaint();
    }

    public boolean isDeadReckoningEnabled() {
        return deadReckoningEnabled;
    }

    public GamePhase getPhase() {
        return phase;
    }

    public int getScore() {
        return score;
    }

    public int getBestScore() {
        return bestScore;
    }

    private void resetLocalBird() {
        this.birdY = 195f;
        this.birdVelocityY = 0f;
        this.worldX = 0f;
        this.score = 0;
        this.frameCounter = 0;
        this.lastTickNanos = System.nanoTime();
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            partLife[i] = 0f;
        }
    }

    private void spawnDropParticles(float originX, float originY) {
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            double angle = (Math.PI * 2.0 * i) / PARTICLE_COUNT + ((i % 3) - 1) * 0.18;
            float speed = 1.6f + (i % 4) * 0.75f;
            partX[i] = originX;
            partY[i] = originY;
            partVx[i] = (float) (Math.cos(angle) * speed);
            partVy[i] = (float) (Math.sin(angle) * speed - 1.4);
            partLife[i] = 1.0f;
            partKind[i] = i % 4;
        }
    }

    private void updateDropParticles(float dtScale) {
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            if (partLife[i] <= 0f) {
                continue;
            }
            partX[i] += partVx[i] * dtScale;
            partY[i] += partVy[i] * dtScale;
            partVy[i] += 0.22f * dtScale;
            if (partY[i] >= GROUND_Y - 4f) {
                partY[i] = GROUND_Y - 4f;
                partVy[i] = -Math.abs(partVy[i]) * 0.45f;
                partVx[i] *= 0.8f;
            }
            partLife[i] -= 0.016f * dtScale;
            if (partLife[i] < 0f) {
                partLife[i] = 0f;
            }
        }
    }

    private void sampleIoActivity(long nowMillis) {
        if (nowMillis - lastIoSampleMs < 90L) {
            return;
        }
        lastIoSampleMs = nowMillis;
        int totalNow = controller.getTotalPacketsSent() + controller.getTotalPacketsReceived();
        int deltaPackets = Math.max(0, totalNow - lastIoPacketsTotal);
        lastIoPacketsTotal = totalNow;

        int barHeight = 2 + Math.min(14, deltaPackets * 3 + pendingFlapSpike);
        pendingFlapSpike = 0;
        ioSamples[ioWriteIdx] = barHeight;
        ioWriteIdx = (ioWriteIdx + 1) % IO_SAMPLES;
    }

    private void onTick() {
        long nowNanos = System.nanoTime();
        float dtScale = (nowNanos - lastTickNanos) / 16_666_667f;
        lastTickNanos = nowNanos;
        if (dtScale < 0.25f) {
            dtScale = 0.25f;
        } else if (dtScale > 2.5f) {
            dtScale = 2.5f;
        }

        frameCounter++;
        long nowMillis = System.currentTimeMillis();
        fpsFrames++;
        if (nowMillis - fpsWindowStartMs >= 500L) {
            currentFps = (int) Math.round(fpsFrames * 1000.0 / (nowMillis - fpsWindowStartMs));
            fpsFrames = 0;
            fpsWindowStartMs = nowMillis;
        }

        updateDropParticles(dtScale);
        sampleIoActivity(nowMillis);

        for (GhostState ghost : controller.getGhosts()) {
            ghost.stepDeadReckoning(GRAVITY, SPEED_X, BIRD_RADIUS, GROUND_Y - BIRD_RADIUS, dtScale);
        }

        if (phase == GamePhase.COUNTDOWN) {
            long remaining = countdownEndMillis - nowMillis;
            if (remaining <= 0) {
                phase = GamePhase.PLAYING;
                flap();
                lastNetTickMillis = nowMillis;
                if (!localSoloMode) {
                    sendNetworkTick(true);
                }
                notifyChanged();
            }
        } else if (phase == GamePhase.PLAYING) {
            worldX += SPEED_X * dtScale;
            birdY += birdVelocityY * dtScale;
            birdVelocityY += GRAVITY * dtScale;

            int passedPipes = 0;
            if (worldX + BIRD_SCREEN_X > FIRST_PIPE_X + PIPE_WIDTH / 2f) {
                passedPipes = 1 + (int) ((worldX + BIRD_SCREEN_X - FIRST_PIPE_X - PIPE_WIDTH / 2f) / PIPE_SPACING);
            }
            if (passedPipes > score) {
                score = passedPipes;
                if (score > bestScore) {
                    bestScore = score;
                }
                notifyChanged();
            }

            if (checkCollision()) {
                phase = GamePhase.GAME_OVER;
                spawnDropParticles(BIRD_SCREEN_X, birdY);
                if (!localSoloMode) {
                    sendNetworkTick(false);
                    PluginRoutingPanel.RoutingSnapshot snap = routingSupplier.get();
                    controller.sendGameOver(snap, score);
                }
                notifyChanged();
            } else if (!localSoloMode && nowMillis - lastNetTickMillis >= 48L) {
                lastNetTickMillis = nowMillis;
                sendNetworkTick(true);
            }
        }

        repaint();
    }

    private void sendNetworkTick(boolean alive) {
        PluginRoutingPanel.RoutingSnapshot snap = routingSupplier.get();
        controller.sendStateTick(snap, birdY, birdVelocityY, worldX, score, alive);
    }

    private boolean checkCollision() {
        if (birdY - BIRD_RADIUS <= 0) {
            birdY = BIRD_RADIUS;
            birdVelocityY = 0;
        }
        if (birdY + BIRD_RADIUS >= GROUND_Y) {
            birdY = GROUND_Y - BIRD_RADIUS;
            return true;
        }

        long seed = controller.getCurrentSeed();
        float birdWorldX = worldX + BIRD_SCREEN_X;
        int firstIndex = Math.max(0, (int) Math.floor((birdWorldX - FIRST_PIPE_X - PIPE_WIDTH) / PIPE_SPACING));

        for (int i = firstIndex; i <= firstIndex + 2; i++) {
            float pipeStartX = FIRST_PIPE_X + i * PIPE_SPACING;
            float pipeEndX = pipeStartX + PIPE_WIDTH;

            if (birdWorldX + HITBOX_RADIUS > pipeStartX && birdWorldX - HITBOX_RADIUS < pipeEndX) {
                int gapCenterY = getPipeGapCenterY(i, seed);
                int gapTop = gapCenterY - PIPE_GAP / 2;
                int gapBottom = gapCenterY + PIPE_GAP / 2;

                if (birdY - HITBOX_RADIUS < gapTop || birdY + HITBOX_RADIUS > gapBottom) {
                    return true;
                }
            }
        }
        return false;
    }

    public static int getPipeGapCenterY(int pipeIndex, long seed) {
        long mixedSeed = seed ^ ((pipeIndex + 1L) * 0x9E3779B97F4A7C15L);
        Random r = new Random(mixedSeed);
        int minCenter = 85;
        int maxCenter = GROUND_Y - 85;
        return minCenter + r.nextInt(maxCenter - minCenter + 1);
    }

    private void notifyChanged() {
        if (onStateChanged != null) {
            onStateChanged.run();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        GraphicsConfiguration gc = getGraphicsConfiguration();
        Graphics2D screen = (Graphics2D) g;
        int w = getWidth();
        int h = getHeight();

        if (gc == null) {
            Graphics2D g2 = frameBuffer.createGraphics();
            renderScene(g2);
            g2.dispose();
            blitToScreen(screen, frameBuffer, w, h);
            return;
        }

        if (volatileBuffer == null
                || volatileBuffer.validate(gc) == VolatileImage.IMAGE_INCOMPATIBLE) {
            volatileBuffer = gc.createCompatibleVolatileImage(DESIGN_WIDTH, DESIGN_HEIGHT);
        }

        do {
            int val = volatileBuffer.validate(gc);
            if (val == VolatileImage.IMAGE_INCOMPATIBLE) {
                volatileBuffer = gc.createCompatibleVolatileImage(DESIGN_WIDTH, DESIGN_HEIGHT);
            }
            Graphics2D g2 = volatileBuffer.createGraphics();
            renderScene(g2);
            g2.dispose();
            blitToScreen(screen, volatileBuffer, w, h);
        } while (volatileBuffer.contentsLost());

        if (!IS_WINDOWS) {
            Toolkit.getDefaultToolkit().sync();
        }
    }

    private void renderScene(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        float cameraWorldX = resolveCameraWorldX();

        drawNetworkBackground(g2, cameraWorldX);
        drawPipes(g2, cameraWorldX, controller.getCurrentSeed());
        drawGround(g2, cameraWorldX);
        drawGhosts(g2, cameraWorldX);
        drawLocalBird(g2, cameraWorldX);
        drawDropParticles(g2);
        drawOscilloscopeFrame(g2);
        drawHud(g2);
        drawOverlayBanner(g2);
    }

    private static void blitToScreen(Graphics2D screen, java.awt.Image img, int w, int h) {
        if (w == DESIGN_WIDTH && h == DESIGN_HEIGHT) {
            screen.drawImage(img, 0, 0, null);
        } else {
            screen.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
            );
            screen.drawImage(img, 0, 0, w, h, null);
        }
    }

    private float resolveCameraWorldX() {
        if (localSoloMode || phase == GamePhase.PLAYING || phase == GamePhase.COUNTDOWN) {
            return worldX;
        }
        long now = System.currentTimeMillis();
        float maxGhostWorldX = -1f;
        for (GhostState ghost : controller.getGhosts()) {
            if (ghost.isAlive() && (now - ghost.getLastUpdateMillis()) < 1500) {
                float gx = ghost.getWorldX(deadReckoningEnabled);
                if (gx > maxGhostWorldX) {
                    maxGhostWorldX = gx;
                }
            }
        }
        return maxGhostWorldX >= 0 ? maxGhostWorldX : worldX;
    }

    private static void drawHorizontalLoop(Graphics2D g2, BufferedImage img, float offsetPx, int y) {
        int w = img.getWidth();
        if (w <= 0) {
            return;
        }
        int shift = ((int) offsetPx) % w;
        if (shift < 0) {
            shift += w;
        }
        for (int x = -shift; x < DESIGN_WIDTH; x += w) {
            g2.drawImage(img, x, y, null);
        }
    }

    private void drawNetworkBackground(Graphics2D g2, float cameraX) {
        g2.setColor(BG_BLACK);
        g2.fillRect(0, 0, DESIGN_WIDTH, GROUND_Y);

        if (bgGridSprite != null) {
            drawHorizontalLoop(g2, bgGridSprite, cameraX * 0.2f, 0);
        } else {
            g2.setColor(GRID_DARK);
            g2.setStroke(STROKE_1);
            int gridOffset = (int) ((cameraX * 0.2f) % 40);
            for (int x = -gridOffset; x < DESIGN_WIDTH; x += 40) {
                g2.drawLine(x, 0, x, GROUND_Y);
            }
            for (int y = 40; y < GROUND_Y; y += 40) {
                g2.drawLine(0, y, DESIGN_WIDTH, y);
            }
        }

        if (nodesFarSprite != null) {
            drawHorizontalLoop(g2, nodesFarSprite, cameraX * 0.14f, 185);
        }

        if (nodesSprite != null) {
            drawHorizontalLoop(g2, nodesSprite, cameraX * 0.30f, 65);
        } else {
            int nodeOffset = (int) ((cameraX * 0.30f) % 280);
            g2.setFont(FONT_MONO_10);

            for (int i = -1; i < 4; i++) {
                int nx = i * 280 - nodeOffset + 50;
                int ny1 = 78;
                int ny2 = 135;

                g2.setColor(LINK_GRAY);
                g2.setStroke(STROKE_DASHED_LINK);
                g2.drawLine(nx + 28, ny1 + 10, nx + 140, ny2 + 10);
                g2.drawLine(nx + 140, ny2 + 10, nx + 280 + 28, ny1 + 10);

                g2.setStroke(STROKE_1);
                drawTopologyNode(g2, nx, ny1, "NODE::0x" + Integer.toHexString(Math.abs(i + 10)).toUpperCase());
                drawTopologyNode(g2, nx + 130, ny2, "UDP::224.0.0.3");
            }
        }

        if (waveSprite != null) {
            drawHorizontalLoop(g2, waveSprite, cameraX * 0.55f, GROUND_Y - waveSprite.getHeight() - 18);
        } else {
            g2.setColor(WAVE_GRAY);
            g2.setStroke(STROKE_1_5);
            int waveOffset = (int) ((cameraX * 0.55f) % 32);
            int baseWaveY = GROUND_Y - 18;
            int prevY = baseWaveY;
            for (int x = -waveOffset; x < DESIGN_WIDTH + 32; x += 16) {
                int stepIndex = (x + waveOffset) / 16;
                int highLow = ((stepIndex * 37) & 1) == 0 ? (baseWaveY - 14) : baseWaveY;
                g2.drawLine(x, prevY, x, highLow);
                g2.drawLine(x, highLow, x + 16, highLow);
                prevY = highLow;
            }
        }
    }

    private void drawTopologyNode(Graphics2D g2, int x, int y, String label) {
        g2.setColor(NODE_BG);
        g2.fillRect(x, y, 96, 20);
        g2.setColor(NODE_BORDER);
        g2.drawRect(x, y, 96, 20);
        g2.fillRect(x - 3, y + 7, 5, 5);
        g2.fillRect(x + 94, y + 7, 5, 5);
        g2.setColor(NODE_TEXT);
        g2.drawString(label, x + 6, y + 14);
    }

    private void drawPipes(Graphics2D g2, float cameraX, long seed) {
        int firstIdx = Math.max(0, (int) Math.floor((cameraX - FIRST_PIPE_X - PIPE_WIDTH - 20) / PIPE_SPACING));
        int lastIdx = Math.max(0, (int) Math.ceil((cameraX - FIRST_PIPE_X + DESIGN_WIDTH + PIPE_SPACING) / PIPE_SPACING));

        for (int i = firstIdx; i <= lastIdx; i++) {
            int screenX = Math.round(FIRST_PIPE_X + i * PIPE_SPACING - cameraX);
            if (screenX + PIPE_WIDTH < -10 || screenX > DESIGN_WIDTH + 10) {
                continue;
            }
            int gapCenterY = getPipeGapCenterY(i, seed);
            int topPipeHeight = gapCenterY - PIPE_GAP / 2;
            int bottomPipeY = gapCenterY + PIPE_GAP / 2;
            int bottomPipeHeight = GROUND_Y - bottomPipeY;

            drawFirewallBarrier(g2, screenX, 0, PIPE_WIDTH, topPipeHeight, true, i + 1);
            drawFirewallBarrier(g2, screenX, bottomPipeY, PIPE_WIDTH, bottomPipeHeight, false, i + 1);

            g2.setColor(SLOT_GUIDE);
            g2.setStroke(STROKE_1);
            int lineX = screenX + PIPE_WIDTH / 2;
            for (int dy = topPipeHeight + 4; dy < bottomPipeY - 4; dy += 10) {
                g2.drawLine(lineX, dy, lineX, Math.min(dy + 4, bottomPipeY - 2));
            }
        }
    }

    private void drawFirewallBarrier(
            Graphics2D g2,
            int x,
            int y,
            int width,
            int height,
            boolean topBarrier,
            int hopNumber
    ) {
        if (height <= 0) {
            return;
        }
        if (firewallBodySprite != null) {
            int imgW = firewallBodySprite.getWidth();
            int imgH = firewallBodySprite.getHeight();
            int srcH = Math.min(height, imgH);
            int srcY = topBarrier ? (imgH - srcH) : 0;
            g2.drawImage(
                    firewallBodySprite,
                    x, y, x + width, y + height,
                    0, srcY, imgW, srcY + srcH,
                    null
            );
        } else {
            g2.setColor(NODE_BG);
            g2.fillRect(x, y, width, height);
            g2.setColor(GRID_DARK);
            g2.setStroke(STROKE_1);
            for (int sy = y + 10; sy < y + height - 4; sy += 14) {
                g2.drawLine(x + 2, sy, x + width - 2, sy);
            }

            g2.setColor(WIRE_MID);
            g2.setStroke(STROKE_1_8);
            g2.drawRect(x, y, width, height);
        }

        int rimHeight = 20;
        int rimY = topBarrier ? (y + height - rimHeight) : y;
        if (firewallHeadSprite != null) {
            g2.drawImage(firewallHeadSprite, x - 4, rimY, null);
        } else {
            g2.setColor(BG_BLACK);
            g2.fillRect(x - 4, rimY, width + 8, rimHeight);
            g2.setColor(WIRE_BRIGHT);
            g2.setStroke(STROKE_2);
            g2.drawRect(x - 4, rimY, width + 8, rimHeight);
        }

        g2.setColor(WIRE_BRIGHT);
        g2.setFont(FONT_MONO_10_BOLD);
        String tag = topBarrier ? ("HOP#" + hopNumber) : "[ACL]";
        FontMetrics fm = g2.getFontMetrics();
        int tx = x + (width - fm.stringWidth(tag)) / 2;
        g2.drawString(tag, tx, rimY + 14);
    }

    private void drawGround(Graphics2D g2, float cameraX) {
        int busHeight = DESIGN_HEIGHT - GROUND_Y;
        g2.setColor(BUS_BG);
        g2.fillRect(0, GROUND_Y, DESIGN_WIDTH, busHeight);

        if (busSprite != null) {
            drawHorizontalLoop(g2, busSprite, cameraX, GROUND_Y);
        } else {
            g2.setColor(WIRE_BRIGHT);
            g2.setStroke(STROKE_2);
            g2.drawLine(0, GROUND_Y, DESIGN_WIDTH, GROUND_Y);
            g2.setColor(WIRE_DIM);
            g2.drawLine(0, GROUND_Y + 16, DESIGN_WIDTH, GROUND_Y + 16);

            int tickOffset = (int) (cameraX % 32);
            g2.setFont(FONT_MONO_10);
            g2.setStroke(STROKE_1);
            for (int x = -32; x < DESIGN_WIDTH + 32; x += 32) {
                int sx = x - tickOffset;
                g2.setColor(WIRE_MID);
                g2.drawLine(sx, GROUND_Y, sx, GROUND_Y + 16);
                g2.setColor(WIRE_DIM);
                g2.drawString("01", sx + 9, GROUND_Y + 12);
            }
        }

        if (bottomTextSprite != null) {
            g2.drawImage(bottomTextSprite, 7, GROUND_Y + 30, null);
        } else {
            g2.setColor(BUS_TEXT);
            g2.setFont(FONT_MONO_11_BOLD);
            g2.drawString("PHYSICAL MEDIUM // IEEE 802.3 / UDP MULTICAST BUS", 14, GROUND_Y + 34);
        }
    }

    private void drawGhosts(Graphics2D g2, float cameraX) {
        if (localSoloMode) {
            return;
        }
        Collection<GhostState> ghosts = controller.getGhosts();
        long now = System.currentTimeMillis();

        for (GhostState ghost : ghosts) {
            if (!ghost.isParticipating() || now - ghost.getLastUpdateMillis() > 15_000L) {
                continue;
            }
            float ghostWorldX = ghost.getWorldX(deadReckoningEnabled);
            float ghostY = ghost.getBirdY(deadReckoningEnabled);
            float ghostVy = ghost.getVelocityY(deadReckoningEnabled);

            float rawScreenX = BIRD_SCREEN_X + (ghostWorldX - cameraX);
            float drawX;
            if (ghost.isAlive()) {
                drawX = Math.max(40f, Math.min(DESIGN_WIDTH - 65f, rawScreenX));
            } else {
                if (rawScreenX < -50f || rawScreenX > DESIGN_WIDTH + 60f) {
                    continue;
                }
                drawX = rawScreenX;
            }

            drawCachedPacket(
                    g2,
                    drawX,
                    ghostY,
                    ghostVy,
                    !ghost.isAlive(),
                    true
            );

            String tag = ghost.getPlayerName() + " [" + ghost.getScore() + "]";
            if (!ghost.isAlive()) {
                tag += " DROP";
            }
            g2.setFont(FONT_MONO_11_BOLD);
            FontMetrics fm = g2.getFontMetrics();
            int tw = fm.stringWidth(tag);
            int tx = Math.round(drawX - tw / 2f);
            int ty = Math.round(ghostY - BIRD_RADIUS - 10);

            g2.setColor(BG_BLACK);
            g2.fillRect(tx - 4, ty - 11, tw + 8, 14);
            g2.setColor(WIRE_MID);
            g2.setStroke(STROKE_1);
            g2.drawRect(tx - 4, ty - 11, tw + 8, 14);
            g2.setColor(WIRE_BRIGHT);
            g2.drawString(tag, tx, ty);
        }
    }

    private void drawLocalBird(Graphics2D g2, float cameraX) {
        float screenX = BIRD_SCREEN_X + (worldX - cameraX);
        if (screenX < -30 || screenX > DESIGN_WIDTH + 30) {
            return;
        }
        drawCachedPacket(
                g2,
                screenX,
                birdY,
                birdVelocityY,
                phase == GamePhase.GAME_OVER,
                false
        );

        g2.setFont(FONT_MONO_10_BOLD);
        String label = "PDU::LOCAL";
        FontMetrics fm = g2.getFontMetrics();
        int lw = fm.stringWidth(label);
        int lx = Math.round(screenX - lw / 2f);
        int ly = Math.round(birdY - BIRD_RADIUS - 10);

        g2.setColor(WIRE_BRIGHT);
        g2.fillRect(lx - 4, ly - 10, lw + 8, 13);
        g2.setColor(BG_BLACK);
        g2.drawString(label, lx, ly);
    }

    private void drawCachedPacket(
            Graphics2D g2,
            float cx,
            float cy,
            float vy,
            boolean dropped,
            boolean ghostWireframe
    ) {
        if (!dropped) {
            g2.setFont(FONT_MONO_10_BOLD);
            g2.setColor(ghostWireframe ? WIRE_DIM : WIRE_MID);
            String bits = ((frameCounter / 3) % 2 == 0) ? "101" : "010";
            int pulseY = ((frameCounter / 2) % 2 == 0) ? -2 : 2;
            g2.drawString(bits, Math.round(cx - 38), Math.round(cy + 4 + pulseY));
            g2.setStroke(STROKE_1);
            g2.drawLine(Math.round(cx - 18), Math.round(cy), Math.round(cx - 14), Math.round(cy));
        }

        BufferedImage sprite;
        if (ghostWireframe) {
            sprite = dropped ? ghostDroppedSprite : ghostPacketSprite;
        } else {
            sprite = dropped ? localDroppedSprite : localPacketSprite;
        }

        AffineTransform oldTransform = g2.getTransform();
        double angle = Math.max(-0.48, Math.min(0.85, vy * 0.065));
        g2.translate(cx, cy);
        g2.rotate(angle);
        g2.drawImage(sprite, -sprite.getWidth() / 2, -sprite.getHeight() / 2, null);

        if (!dropped && !ghostWireframe && vy < -1.5f) {
            g2.setColor(WIRE_BRIGHT);
            g2.setStroke(STROKE_1_5);
            g2.drawLine(18, -5, 22, 0);
            g2.drawLine(22, 0, 18, 5);
        }

        g2.setTransform(oldTransform);
    }

    private void drawDropParticles(Graphics2D g2) {
        g2.setFont(FONT_MONO_10_BOLD);
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            float life = partLife[i];
            if (life <= 0f) {
                continue;
            }
            int px = Math.round(partX[i]);
            int py = Math.round(partY[i]);
            g2.setColor(life > 0.45f ? WIRE_BRIGHT : WIRE_MID);
            int kind = partKind[i];
            if (kind == 0) {
                g2.drawString("0", px, py);
            } else if (kind == 1) {
                g2.drawString("1", px, py);
            } else if (kind == 2) {
                g2.fillRect(px, py, 4, 4);
            } else {
                g2.fillRect(px, py, 3, 3);
            }
        }
    }

    private void drawOscilloscopeFrame(Graphics2D g2) {
        int c = 12;
        int topY = 42;
        int botY = GROUND_Y - 6;
        g2.setColor(WIRE_BRIGHT);
        g2.setStroke(STROKE_2);
        g2.drawLine(8, topY + c, 8, topY);
        g2.drawLine(8, topY, 8 + c, topY);
        g2.drawLine(DESIGN_WIDTH - 8 - c, topY, DESIGN_WIDTH - 8, topY);
        g2.drawLine(DESIGN_WIDTH - 8, topY, DESIGN_WIDTH - 8, topY + c);
        g2.drawLine(8, botY - c, 8, botY);
        g2.drawLine(8, botY, 8 + c, botY);
        g2.drawLine(DESIGN_WIDTH - 8 - c, botY, DESIGN_WIDTH - 8, botY);
        g2.drawLine(DESIGN_WIDTH - 8, botY, DESIGN_WIDTH - 8, botY - c);

        boolean blink = (System.currentTimeMillis() / 450L) % 2L == 0L;
        g2.setColor(phase == GamePhase.GAME_OVER ? WIRE_DIM : (blink ? WIRE_BRIGHT : WIRE_DIM));
        g2.fillRect(16, 48, 6, 6);
        g2.setColor(WIRE_MID);
        g2.setFont(FONT_MONO_10_BOLD);
        g2.drawString(phase == GamePhase.GAME_OVER ? "CAPTURE: DROP" : "CAPTURE: LIVE", 27, 55);
    }

    private void drawHud(Graphics2D g2) {
        g2.setColor(BG_BLACK);
        g2.fillRect(10, 8, DESIGN_WIDTH - 20, 28);
        g2.setColor(WIRE_BRIGHT);
        g2.setStroke(STROKE_1_5);
        g2.drawRect(10, 8, DESIGN_WIDTH - 20, 28);

        g2.setFont(FONT_MONO_12_BOLD);
        String hudText = String.format(
                "SEED:#%d | FPS:%d | TX:%d RX:%d LOSS:%d OOO:%d | PEERS:%d",
                controller.getCurrentSeed() % 100000,
                currentFps,
                controller.getTotalPacketsSent(),
                controller.getTotalPacketsReceived(),
                controller.getTotalPacketsLost(),
                controller.getTotalPacketsOutOfOrder(),
                controller.getGhosts().size()
        );
        g2.drawString(hudText, 18, 26);

        int gx = DESIGN_WIDTH - 98;
        int gy = 11;
        int gw = 82;
        int gh = 22;
        g2.setColor(NODE_BG);
        g2.fillRect(gx, gy, gw, gh);
        g2.setColor(WIRE_DIM);
        g2.setStroke(STROKE_1);
        g2.drawRect(gx, gy, gw, gh);
        for (int i = 0; i < IO_SAMPLES; i++) {
            int idx = (ioWriteIdx + i) % IO_SAMPLES;
            int barH = ioSamples[idx];
            if (barH <= 0) {
                barH = 2;
            }
            g2.setColor(barH >= 10 ? WIRE_BRIGHT : WIRE_MID);
            g2.fillRect(gx + 3 + i * 3, gy + gh - 2 - barH, 2, barH);
        }

        String scoreStr = "HOP " + score;
        g2.setFont(FONT_MONO_26_BOLD);
        FontMetrics fm = g2.getFontMetrics();
        int sw = fm.stringWidth(scoreStr);
        int sx = (DESIGN_WIDTH - sw) / 2;

        g2.setColor(BG_BLACK);
        g2.fillRect(sx - 10, 44, sw + 20, 32);
        g2.setColor(WIRE_MID);
        g2.drawRect(sx - 10, 44, sw + 20, 32);
        g2.setColor(WIRE_BRIGHT);
        g2.drawString(scoreStr, sx, 68);
    }

    private void drawOverlayBanner(Graphics2D g2) {
        if (phase == GamePhase.PLAYING) {
            return;
        }
        if (phase == GamePhase.READY) {
            drawReadyOverlay(g2);
            return;
        }
        int boxW = 430;
        int boxH = 160;
        int bx = (DESIGN_WIDTH - boxW) / 2;
        int by = (GROUND_Y - boxH) / 2 + 16;

        g2.setColor(BG_BLACK);
        g2.fillRect(bx, by, boxW, boxH);
        g2.setColor(WIRE_BRIGHT);
        g2.setStroke(STROKE_2);
        g2.drawRect(bx, by, boxW, boxH);
        g2.setColor(WIRE_DIM);
        g2.setStroke(STROKE_1);
        g2.drawRect(bx + 4, by + 4, boxW - 8, boxH - 8);

        if (phase == GamePhase.COUNTDOWN) {
            long remMs = Math.max(0, countdownEndMillis - System.currentTimeMillis());
            int seconds = (int) Math.ceil(remMs / 1000.0);
            drawCenteredString(g2, "[ SYN / ACK — HANDSHAKE P2P ]", bx, by + 36, boxW,
                    FONT_MONO_15_BOLD, WIRE_MID);
            drawCenteredString(g2, "TX START IN " + seconds + "s...", bx, by + 88, boxW,
                    FONT_MONO_30_BOLD, WIRE_BRIGHT);
            drawCenteredString(g2, "ROUTING TABLE SEED #" + controller.getCurrentSeed(), bx, by + 132, boxW,
                    FONT_MONO_12, WIRE_MID);
        } else if (phase == GamePhase.IDLE) {
            drawCenteredString(g2, "[ PACKET RUNNER P2P // GHOST TRACE ]", bx, by + 36, boxW,
                    FONT_MONO_16_BOLD, WIRE_BRIGHT);
            drawCenteredString(g2, "Pressione ESPAÇO ou Clique para Voo Local (Solo)", bx, by + 76, boxW,
                    FONT_MONO_14_BOLD, WIRE_BRIGHT);
            drawCenteredString(g2, "Use 'Iniciar Corrida Sincronizada' na barra superior", bx, by + 112, boxW,
                    FONT_MONO_12, WIRE_MID);
            drawCenteredString(g2, "para abrir a sala de Pronto e jogar em rede", bx, by + 134, boxW,
                    FONT_MONO_12, WIRE_MID);
        } else if (phase == GamePhase.GAME_OVER) {
            drawCenteredString(g2, "[ PACKET DROPPED // COLLISION ]", bx, by + 36, boxW,
                    FONT_MONO_18_BOLD, WIRE_BRIGHT);
            drawCenteredString(
                    g2,
                    "HOPS: " + score + "   |   MAX HOPS: " + bestScore,
                    bx,
                    by + 74,
                    boxW,
                    FONT_MONO_15_BOLD,
                    WIRE_BRIGHT
            );
            String retryPrompt = localSoloMode
                    ? "Pressione R ou ENTER para retransmitir (Voo Local)"
                    : "Pressione R, ENTER ou Clique para Tela de PRONTO";
            drawCenteredString(g2, retryPrompt, bx, by + 112, boxW,
                    FONT_MONO_13_BOLD, WIRE_BRIGHT);
            drawCenteredString(g2, "ou 'Iniciar Corrida Sincronizada' para novo Handshake", bx, by + 136, boxW,
                    FONT_MONO_11_BOLD, WIRE_MID);
        }
    }

    private void drawReadyOverlay(Graphics2D g2) {
        Collection<GhostState> ghosts = controller.getGhosts();
        int visibleRows = Math.min(5, 1 + ghosts.size());
        int boxW = 470;
        int boxH = 152 + visibleRows * 22;
        int bx = (DESIGN_WIDTH - boxW) / 2;
        int by = Math.max(46, (GROUND_Y - boxH) / 2 + 12);

        g2.setColor(BG_BLACK);
        g2.fillRect(bx, by, boxW, boxH);
        g2.setColor(WIRE_BRIGHT);
        g2.setStroke(STROKE_2);
        g2.drawRect(bx, by, boxW, boxH);
        g2.setColor(WIRE_DIM);
        g2.setStroke(STROKE_1);
        g2.drawRect(bx + 4, by + 4, boxW - 8, boxH - 8);

        drawCenteredString(g2, "[ SYN-READY // SALA DE ESPERA P2P ]", bx, by + 30, boxW,
                FONT_MONO_15_BOLD, WIRE_BRIGHT);

        int readyCount = controller.getReadyPlayersCount();
        int totalCount = controller.getTotalPlayersCount();
        int requiredCount = controller.getRequiredReadyCount();
        boolean quorum = controller.isQuorumReached();

        String quorumText = String.format(
                "QUÓRUM: %d / %d PRONTOS  (MAIORIA MÍN.: %d)",
                readyCount, totalCount, requiredCount
        );
        drawCenteredString(g2, quorumText, bx, by + 56, boxW,
                FONT_MONO_12_BOLD, quorum ? WIRE_BRIGHT : WIRE_MID);

        g2.setColor(NODE_BORDER);
        g2.drawLine(bx + 18, by + 68, bx + boxW - 18, by + 68);

        int rowY = by + 90;
        drawReadyRow(g2, bx + 22, rowY, boxW - 44, "VOCÊ (Local)", controller.isLocalReady(), true);
        rowY += 22;

        int drawn = 1;
        for (GhostState ghost : ghosts) {
            if (drawn >= 5) {
                break;
            }
            String label = ghost.getPlayerName() + " (" + ghost.getPlayerIp() + ")";
            drawReadyRow(g2, bx + 22, rowY, boxW - 44, label, ghost.isReady(), false);
            rowY += 22;
            drawn++;
        }

        g2.setColor(NODE_BORDER);
        g2.drawLine(bx + 18, by + boxH - 54, bx + boxW - 18, by + boxH - 54);

        String footerPrimary;
        if (quorum) {
            footerPrimary = "Quórum atingido! Sincronizando handshake...";
        } else if (controller.isLocalReady()) {
            footerPrimary = "Aguardando demais peers... (ESPAÇO/R para cancelar)";
        } else {
            footerPrimary = "Pressione ESPAÇO, R, ENTER ou Clique para dar PRONTO";
        }
        drawCenteredString(g2, footerPrimary, bx, by + boxH - 30, boxW,
                FONT_MONO_12_BOLD, WIRE_BRIGHT);
        drawCenteredString(g2, "Todos devem confirmar (ou maioria em salas >= 3)", bx, by + boxH - 12, boxW,
                FONT_MONO_10, WIRE_MID);
    }

    private static void drawReadyRow(
            Graphics2D g2,
            int x,
            int y,
            int width,
            String name,
            boolean ready,
            boolean isLocal
    ) {
        String truncated = name.length() > 28 ? name.substring(0, 27) + "~" : name;
        String badge = ready ? "[ PRONTO ]" : "[ AGUARDANDO ]";
        g2.setFont(isLocal ? FONT_MONO_12_BOLD : FONT_MONO_12);
        g2.setColor(ready ? WIRE_BRIGHT : WIRE_MID);
        g2.drawString((isLocal ? "* " : "  ") + truncated, x, y);

        FontMetrics fm = g2.getFontMetrics();
        int bw = fm.stringWidth(badge);
        g2.drawString(badge, x + width - bw, y);
    }

    private static void drawCenteredString(
            Graphics2D g2,
            String text,
            int boxX,
            int y,
            int boxWidth,
            Font font,
            Color color
    ) {
        g2.setFont(font);
        g2.setColor(color);
        FontMetrics fm = g2.getFontMetrics();
        int x = boxX + (boxWidth - fm.stringWidth(text)) / 2;
        g2.drawString(text, x, y);
    }
}
