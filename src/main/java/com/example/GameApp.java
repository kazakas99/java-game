package com.example;

import com.almasb.fxgl.app.GameApplication;
import com.almasb.fxgl.app.GameSettings;
import com.almasb.fxgl.app.scene.Viewport;
import com.almasb.fxgl.entity.Entity;
import com.almasb.fxgl.texture.AnimatedTexture;
import com.almasb.fxgl.texture.AnimationChannel;
import com.almasb.fxgl.texture.Texture;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.Group;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.util.Duration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static com.almasb.fxgl.dsl.FXGL.*;

public class GameApp extends GameApplication {

    // ==================== LAUKAI ====================

    // Zaidejas ir zemelapis
    private Entity player;
    private int[][] map;

    // Animacija: kanalai vaiksciojimui ir stovejimui kiekviena kryptimi
    private AnimatedTexture texture;
    private AnimationChannel animDown, animLeft, animRight, animUp;
    private AnimationChannel idleDown, idleLeft, idleRight, idleUp;
    private AnimationChannel idleChannel;
    private boolean moving;

    // Redaktorius (F1): rezimas, plyteliu palete, pasirinkta plytele
    private boolean editMode = false;
    private final int[] palette = { 817, 1138, 719, 974, 107, 413, 11, 1127, 1433 };
    private int selectedGid = 817;
    private Group paletteUI;
    private Rectangle selectionBox;
    private String status = "";

    // Zemelapio piesimas i viena Canvas
    private Canvas mapCanvas;
    private GraphicsContext gc;
    private Image tilesetImage;

    // Monetos, HUD ir lygiu eiga
    private List<Entity> coins = new ArrayList<>();
    private int coinsCollected = 0;
    private boolean levelComplete = false;
    private Text hud;
    private int currentLevel = 1;
    private int totalCoins = 0;
    private static final int MAX_LEVELS = 3;

    // ==================== PALEIDIMAS IR LYGIU UZKROVIMAS ====================

    // Lango dydis ir pavadinimas
    @Override
    protected void initSettings(GameSettings settings) {
        settings.setWidth(800);
        settings.setHeight(600);
        settings.setTitle("zaidimas");
    }

    // Animacijos kanalai sukuriami viena karta, po to uzkraunamas 1 lygis
    @Override
    protected void initGame() {
        Image sheet = image("player_sheet.png");

        animDown  = new AnimationChannel(sheet, 4, 16, 16, Duration.seconds(0.5), 0, 3);
        animLeft  = new AnimationChannel(sheet, 4, 16, 16, Duration.seconds(0.5), 4, 7);
        animRight = new AnimationChannel(sheet, 4, 16, 16, Duration.seconds(0.5), 8, 11);
        animUp    = new AnimationChannel(sheet, 4, 16, 16, Duration.seconds(0.5), 12, 15);

        idleDown  = new AnimationChannel(sheet, 4, 16, 16, Duration.seconds(1), 0, 0);
        idleLeft  = new AnimationChannel(sheet, 4, 16, 16, Duration.seconds(1), 4, 4);
        idleRight = new AnimationChannel(sheet, 4, 16, 16, Duration.seconds(1), 8, 8);
        idleUp    = new AnimationChannel(sheet, 4, 16, 16, Duration.seconds(1), 12, 12);

        loadLevel(1);
    }

    // Uzkrauna lygi: isvalo pasauli, nuskaito CSV, piesia zemelapi,
    // sukuria zaideja bei monetas ir pririsa kamera
    private void loadLevel(int n) {
        currentLevel = n;
        levelComplete = false;
        coinsCollected = 0;

        getGameWorld().removeEntities(new ArrayList<>(getGameWorld().getEntities()));
        coins.clear();

        map = loadMap("level" + n + ".csv");
        tilesetImage = image("terrain_tileset.png");
        renderMap();

        idleChannel = idleDown;
        texture = new AnimatedTexture(idleDown);
        texture.loop();

        player = entityBuilder()
                .at(0, 0)
                .view(texture)
                .buildAndAttach();

        loadItems("level" + n + "_items.csv");
        totalCoins = coins.size();

        getGameScene().getViewport().setBounds(0, 0, map[0].length * 16, map.length * 16);
        getGameScene().getViewport().bindToEntity(player, 400, 300);
    }

    // ==================== VARTOTOJO SASAJA (HUD IR PALETE) ====================

    // HUD tekstas virsuje ir plyteliu palete apacioje (matoma tik redaguojant)
    @Override
    protected void initUI() {
        hud = new Text();
        hud.setTranslateX(20);
        hud.setTranslateY(30);
        hud.setFont(Font.font(20));
        hud.setFill(Color.WHITE);
        addUINode(hud);

        paletteUI = new Group();

        for (int i = 0; i < palette.length; i++) {
            final int gid = palette[i];

            Texture t = tileTexture(gid);
            t.setFitWidth(32);
            t.setFitHeight(32);
            t.setTranslateX(20 + i * 38);
            t.setTranslateY(540);
            t.setOnMouseClicked(e -> selectTile(gid));

            paletteUI.getChildren().add(t);
        }

        selectionBox = new Rectangle(36, 36);
        selectionBox.setFill(Color.TRANSPARENT);
        selectionBox.setStroke(Color.WHITE);
        selectionBox.setStrokeWidth(2);
        selectionBox.setTranslateX(18);
        selectionBox.setTranslateY(538);
        paletteUI.getChildren().add(selectionBox);

        paletteUI.setVisible(false);
        addUINode(paletteUI);
    }

    // GID -> iskirpta 16x16 plytele is tileset paveiksliuko.
    // Stulpelis = index % 51, eilute = index / 51 (lape 51 plyteles per eilute)
    private Texture tileTexture(int gid) {
        int index = gid - 1;
        int sx = (index % 51) * 16;
        int sy = (index / 51) * 16;

        return texture("terrain_tileset.png")
                .subTexture(new Rectangle2D(sx, sy, 16, 16));
    }

    // Pasirenka plytele paletes paspaudimu ir perkelia balta remeli
    private void selectTile(int gid) {
        selectedGid = gid;

        for (int i = 0; i < palette.length; i++) {
            if (palette[i] == gid) {
                selectionBox.setTranslateX(18 + i * 38);
            }
        }
    }

    // ==================== PAGRINDINIS CIKLAS (KAS KADRA) ====================

    // Atnaujina HUD, renka monetas, tikrina laimejima ir stovejimo animacija
    @Override
    protected void onUpdate(double tpf) {
        hud.setText("Level " + currentLevel
                + "     Coins: " + coinsCollected + " / " + totalCoins
                + (editMode ? "     [EDIT] tile " + selectedGid : "")
                + (status.isEmpty() ? "" : "     " + status));

        for (int i = coins.size() - 1; i >= 0; i--) {
            Entity coin = coins.get(i);

            if (coin.distance(player) < 12) {
                coin.removeFromWorld();
                coins.remove(i);
                coinsCollected++;
            }
        }

        if (!levelComplete && totalCoins > 0 && coins.isEmpty()) {
            levelComplete = true;

            if (currentLevel >= MAX_LEVELS) {
                showMessage("You finished the game!");
            } else {
                runOnce(() -> loadLevel(currentLevel + 1), Duration.seconds(1));
            }
        }

        if (!moving) {
            setAnim(idleChannel);
        }

        moving = false;
    }

    // ==================== VALDYMAS ====================

    // WASD - judejimas (redaguojant - kameros stumimas),
    // F1 - redaktorius, F5 - issaugoti, F9 - perkrauti, peles kairys - piesti
    @Override
    protected void initInput() {
        onKey(KeyCode.W, () -> tryMove(0, -2));
        onKey(KeyCode.S, () -> tryMove(0, 2));
        onKey(KeyCode.A, () -> tryMove(-2, 0));
        onKey(KeyCode.D, () -> tryMove(2, 0));

        onKeyDown(KeyCode.F1, () -> toggleEditMode());
        onKeyDown(KeyCode.F5, () -> saveMap("level" + currentLevel + ".csv"));
        onKeyDown(KeyCode.F9, () -> loadLevel(currentLevel));

        onBtn(MouseButton.PRIMARY, () -> {
            if (!editMode) return;

            Point2D ui = getInput().getMousePositionUI();
            if (ui.getY() > 530) return;

            Point2D world = getInput().getMousePositionWorld();
            paintTile((int) (world.getX() / 16), (int) (world.getY() / 16));
        });
    }

    // ==================== REDAKTORIUS: PIESIMAS IR ZEMELAPIO BRAIZYMAS ====================

    // Nupiesia plytele ir tuoj pat pakeicia kliuciu masyva (map)
    private void paintTile(int col, int row) {
        if (col < 0 || row < 0 || row >= map.length || col >= map[0].length) {
            return;
        }

        map[row][col] = selectedGid;
        drawTile(col, row);
    }

    // Nupiesia viena langeli i Canvas
    private void drawTile(int col, int row) {
        int gid = map[row][col];
        if (gid <= 0) return;

        int index = gid - 1;
        int sx = (index % 51) * 16;
        int sy = (index / 51) * 16;

        gc.drawImage(tilesetImage, sx, sy, 16, 16, col * 16, row * 16, 16, 16);
    }

    // Nupiesia visa zemelapi i viena Canvas (zIndex -100 - fonas)
    private void renderMap() {
        int w = map[0].length * 16;
        int h = map.length * 16;

        mapCanvas = new Canvas(w, h);
        gc = mapCanvas.getGraphicsContext2D();

        for (int r = 0; r < map.length; r++) {
            for (int c = 0; c < map[r].length; c++) {
                drawTile(c, r);
            }
        }

        entityBuilder()
                .at(0, 0)
                .view(mapCanvas)
                .zIndex(-100)
                .buildAndAttach();
    }

    // ==================== ISSAUGOJIMAS I DISKA ====================

    // Failai rasomi i levels/ salia projekto
    private Path levelPath(String fileName) {
        return Paths.get("levels", fileName);
    }

    // Issaugo zemelapi kaip CSV (-1, kad liktu Tiled formatas)
    private void saveMap(String fileName) {
        StringBuilder sb = new StringBuilder();

        for (int r = 0; r < map.length; r++) {
            for (int c = 0; c < map[r].length; c++) {
                if (c > 0) sb.append(",");
                sb.append(map[r][c] - 1);
            }
            sb.append("\n");
        }

        try {
            Path p = levelPath(fileName);
            Files.createDirectories(p.getParent());
            Files.writeString(p, sb.toString());
            status = "Saved";
            runOnce(() -> status = "", Duration.seconds(2));
        } catch (IOException e) {
            showMessage("Save failed: " + e.getMessage());
        }
    }

    // Ijungia/isjungia redaktoriu: atrisa arba vel pririsa kamera
    private void toggleEditMode() {
        editMode = !editMode;

        paletteUI.setVisible(editMode);

        if (editMode) {
            getGameScene().getViewport().unbind();
        } else {
            getGameScene().getViewport().bindToEntity(player, 400, 300);
        }
    }

    // ==================== JUDEJIMAS, ANIMACIJA IR SUSIDURIMAI ====================

    // Bando pajudeti: pasuka veikeja, patikrina 4 kampus ir tik tada juda
    private void tryMove(double dx, double dy) {
        if (editMode) {
            Viewport vp = getGameScene().getViewport();
            vp.setX(vp.getX() + dx * 3);
            vp.setY(vp.getY() + dy * 3);
            return;
        }

        if (dx < 0)      { setAnim(animLeft);  idleChannel = idleLeft;  }
        else if (dx > 0) { setAnim(animRight); idleChannel = idleRight; }
        else if (dy < 0) { setAnim(animUp);    idleChannel = idleUp;    }
        else if (dy > 0) { setAnim(animDown);  idleChannel = idleDown;  }

        moving = true;

        double newX = player.getX() + dx;
        double newY = player.getY() + dy;

        if (canMoveTo(newX, newY)
                && canMoveTo(newX + 15, newY)
                && canMoveTo(newX, newY + 15)
                && canMoveTo(newX + 15, newY + 15)) {
            player.setPosition(newX, newY);
        }
    }

    // Sukuria moneta pagal langelio koordinates
    private void spawnCoin(int col, int row) {
        Entity coin = entityBuilder()
                .at(col * 16, row * 16)
                .view("coin.png")
                .buildAndAttach();

        coins.add(coin);
    }

    // Perjungia animacija tik jei kryptis pasikeite (kitaip strigtu 0 kadre)
    private void setAnim(AnimationChannel channel) {
        if (texture.getAnimationChannel() != channel) {
            texture.loopAnimationChannel(channel);
        }
    }

    // ==================== FAILU SKAITYMAS ====================

    // Nuskaito CSV (pirmiausia is disko, jei nera - is resursu) ir prideda +1 -> GID
    private int[][] loadMap(String fileName) {
        List<String> lines;
        Path p = levelPath(fileName);

        if (Files.exists(p)) {
            try {
                lines = Files.readAllLines(p);
            } catch (IOException e) {
                lines = getAssetLoader().loadText("levels/" + fileName);
            }
        } else {
            lines = getAssetLoader().loadText("levels/" + fileName);
        }

        List<int[]> rows = new ArrayList<>();

        for (String line : lines) {
            if (line.isBlank()) continue;

            String[] parts = line.split(",");
            int[] row = new int[parts.length];

            for (int c = 0; c < parts.length; c++) {
                row[c] = Integer.parseInt(parts[c].trim()) + 1;
            }

            rows.add(row);
        }

        return rows.toArray(new int[0][]);
    }

    // Nuskaito zaidejo starta ir monetu vietas: tipas,stulpelis,eilute
    private void loadItems(String fileName) {
        var lines = getAssetLoader().loadText("levels/" + fileName);

        for (String line : lines) {
            if (line.isBlank()) continue;

            String[] parts = line.split(",");
            String type = parts[0].trim();
            int col = Integer.parseInt(parts[1].trim());
            int row = Integer.parseInt(parts[2].trim());

            if (type.equals("player")) {
                player.setPosition(col * 16, row * 16);
            } else if (type.equals("coin")) {
                spawnCoin(col, row);
            }
        }
    }

    // ==================== KLIUTYS ====================

    // Kurios plyteles nepraleidzia zaidejo
    private boolean isSolid(int gid) {
        // grass biome (level 1): tree, rock, water
        return gid == 719 || gid == 974 || gid == 1138
                // snow biome (level 2): tree, rock, frozen water
                || gid == 107 || gid == 413 || gid == 11
                // desert biome (level 3): cactus, rock
                || gid == 1127 || gid == 1433;
    }

    // Pikseliai -> langeliai (/16) + zemelapio ribu patikra
    private boolean canMoveTo(double x, double y) {
        if (x < 0 || y < 0) {
            return false;
        }

        int col = (int) (x / 16);
        int row = (int) (y / 16);

        if (row >= map.length || col >= map[0].length) {
            return false;
        }

        return !isSolid(map[row][col]);
    }

    // ==================== STARTAS ====================

    public static void main(String[] args) {
        launch(args);
    }
}