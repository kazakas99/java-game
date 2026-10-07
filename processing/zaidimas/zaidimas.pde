// ==================== LAUKAI ====================

// Plyteliu lapas ir is jo iskirptos plyteles
PImage tileset;
PImage[] photomas;          // visos plyteles, indeksas = GID - 1
int tSize = 16;             // plyteles dydis pikseliais
float speed = 2.5f;            // zaidejo greitis pikseliais per kadra
float panSpeed = 6;         // kameros stumimo greitis redaguojant
int tilesetCols;            // kiek plyteliu telpa i viena lapo eilute (51)

// Zemelapis: langeliu masyvas, kuriame laikomi GID numeriai
int[][] maps;
int mapX, mapY;             // zemelapio plotis ir aukstis langeliais
PGraphics mapBuffer;        // visas zemelapis nupiestas i viena paveiksliuka

// Zaidejas
PImage[] playerFrames;      // 16 kadru: 4 kryptys x 4 zingsniai
float playerX, playerY;
int dirRow = 0;             // 0 zemyn, 1 kaire, 2 desine, 3 virsus
int animFrame = 0;
int lastAnimTime = 0;
boolean moving = false;

// Kamera
float camX = 0, camY = 0;

// Monetos ir lygiu eiga
PImage coinImg;
ArrayList<PVector> coins = new ArrayList<PVector>();
int coinsCollected = 0;
int totalCoins = 0;
int currentLevel = 1;
final int MAX_LEVELS = 3;
boolean levelComplete = false;
int completeTime = 0;

// Redaktorius (E arba F1)
boolean editMode = false;
int[] palette = { 817, 1138, 719, 974, 107, 413, 11, 1127, 1433 };
int selectedGid = 817;
String status = "";
int statusTime = 0;

// Laikomi klavisai (WASD)
boolean[] keys = new boolean[256];


// ==================== PALEIDIMAS ====================

void setup() {
  size(800, 600);
  noSmooth();

  tileset = loadImage("terrain_tileset.png");
  tilesetCols = tileset.width / tSize;
  cutphoto();

  coinImg = loadImage("coin.png");
  cutPlayer();

  loadLevel(1);
}

// Supjausto plyteliu lapa i atskiras plyteles
void cutphoto() {
  int cols = tileset.width / tSize;
  int rows = tileset.height / tSize;
  photomas = new PImage[cols * rows];

  for (int j = 0; j < rows; j++) {
    for (int i = 0; i < cols; i++) {
      photomas[i + j * cols] = tileset.get(i * tSize, j * tSize, tSize, tSize);
    }
  }
}

// Supjausto zaidejo lapa: 4 eilutes (zemyn, kaire, desine, virsus) po 4 kadrus
void cutPlayer() {
  PImage sheet = loadImage("player_sheet.png");
  playerFrames = new PImage[16];

  for (int j = 0; j < 4; j++) {
    for (int i = 0; i < 4; i++) {
      playerFrames[i + j * 4] = sheet.get(i * tSize, j * tSize, tSize, tSize);
    }
  }
}


// ==================== LYGIU UZKROVIMAS ====================

void loadLevel(int n) {
  currentLevel = n;
  levelComplete = false;
  coinsCollected = 0;

  maps = loadMap("level" + n + ".csv");
  mapY = maps.length;
  mapX = maps[0].length;
  renderMap();

  loadItems("level" + n + "_items.csv");
  totalCoins = coins.size();

  dirRow = 0;
  animFrame = 0;
  camX = 0;
  camY = 0;
}

// Nuskaito CSV: pirmiausia is levels/ salia sketch'o, jei nera - is data/.
// Faile saugomi 0-iniai indeksai (kaip Tiled), todel pridedam +1 -> GID
int[][] loadMap(String fileName) {
  String[] lines = null;

  File f = new File(sketchPath("levels/" + fileName));
  if (f.exists()) {
    lines = loadStrings(f.getAbsolutePath());
  }
  if (lines == null) {
    lines = loadStrings(fileName);
  }

  ArrayList<int[]> rows = new ArrayList<int[]>();

  for (int i = 0; i < lines.length; i++) {
    String line = trim(lines[i]);
    if (line.length() == 0) continue;

    String[] parts = split(line, ',');
    int[] row = new int[parts.length];

    for (int c = 0; c < parts.length; c++) {
      row[c] = Integer.parseInt(trim(parts[c])) + 1;
    }
    rows.add(row);
  }

  int[][] result = new int[rows.size()][];
  for (int i = 0; i < rows.size(); i++) {
    result[i] = rows.get(i);
  }
  return result;
}

// Nuskaito zaidejo starta ir monetu vietas: tipas,stulpelis,eilute
void loadItems(String fileName) {
  String[] lines = loadStrings(fileName);
  coins = new ArrayList<PVector>();

  for (int i = 0; i < lines.length; i++) {
    String line = trim(lines[i]);
    if (line.length() == 0) continue;

    String[] p = split(line, ',');
    String type = trim(p[0]);
    int col = Integer.parseInt(trim(p[1]));
    int row = Integer.parseInt(trim(p[2]));

    if (type.equals("player")) {
      playerX = col * tSize;
      playerY = row * tSize;
    } else if (type.equals("coin")) {
      coins.add(new PVector(col * tSize, row * tSize));
    }
  }
}


// ==================== ZEMELAPIO PIESIMAS ====================

// Nupiesia visa zemelapi viena karta i mapBuffer
void renderMap() {
  mapBuffer = createGraphics(mapX * tSize, mapY * tSize);
  mapBuffer.beginDraw();
  mapBuffer.noSmooth();

  for (int j = 0; j < mapY; j++) {
    for (int i = 0; i < mapX; i++) {
      int gid = maps[j][i];
      if (gid > 0) {
        mapBuffer.image(photomas[gid - 1], i * tSize, j * tSize);
      }
    }
  }
  mapBuffer.endDraw();
}

// Perpiesia viena langeli (naudojama redaguojant)
void drawTile(int col, int row) {
  int gid = maps[row][col];
  if (gid <= 0) return;

  mapBuffer.beginDraw();
  mapBuffer.noSmooth();
  mapBuffer.image(photomas[gid - 1], col * tSize, row * tSize);
  mapBuffer.endDraw();
}


// ==================== PAGRINDINIS CIKLAS ====================

void draw() {
  background(0);

  handleKeys();
  if (!editMode) updateCamera();
  updateAnim();

  pushMatrix();
  translate(-camX, -camY);

  image(mapBuffer, 0, 0);

  // Zaidejas piesiamas pries monetas - tokia pat tvarka kaip Java versijoje
  image(playerFrames[dirRow * 4 + animFrame], playerX, playerY);

  for (int i = 0; i < coins.size(); i++) {
    PVector c = coins.get(i);
    image(coinImg, c.x, c.y);
  }
  popMatrix();

  checkCoins();
  checkLevelComplete();

  drawHud();
  if (editMode) drawPalette();
}

// Laikomi klavisai tikrinami kas kadra, kad judetu tolygiai
void handleKeys() {
  moving = false;
  if (keys['W']) tryMove(0, -speed);
  if (keys['S']) tryMove(0, speed);
  if (keys['A']) tryMove(-speed, 0);
  if (keys['D']) tryMove(speed, 0);
}

// Kamera seka zaideja, bet nesislenka uz zemelapio ribu
void updateCamera() {
  camX = constrain(playerX - 400, 0, max(0, mapX * tSize - width));
  camY = constrain(playerY - 300, 0, max(0, mapY * tSize - height));
}

// Vaiksciojimo kadrai keiciasi ~9 kartus per sekunde, stovint - 0 kadras
void updateAnim() {
  if (moving) {
    if (millis() - lastAnimTime > 112) {
      animFrame = (animFrame + 1) % 4;
      lastAnimTime = millis();
    }
  } else {
    animFrame = 0;
  }
}


// ==================== JUDEJIMAS IR SUSIDURIMAI ====================

// Pasuka veikeja, patikrina visus 4 sprite kampus ir tik tada juda.
// Redaguojant WASD stumia kamera, o veikejas lieka vietoje.
void tryMove(float dx, float dy) {
  if (editMode) {
    camX += Math.signum(dx) * panSpeed;
    camY += Math.signum(dy) * panSpeed;
    return;
  }

  if (dx < 0)      dirRow = 1;
  else if (dx > 0) dirRow = 2;
  else if (dy < 0) dirRow = 3;
  else if (dy > 0) dirRow = 0;

  moving = true;

  float nx = playerX + dx;
  float ny = playerY + dy;

  if (canMoveTo(nx, ny)
   && canMoveTo(nx + 15, ny)
   && canMoveTo(nx, ny + 15)
   && canMoveTo(nx + 15, ny + 15)) {
    playerX = nx;
    playerY = ny;
  }
}

// Kurios plyteles nepraleidzia zaidejo
boolean isSolid(int gid) {
  // zoles lygis: medis, akmuo, vanduo
  return gid == 719 || gid == 974 || gid == 1138
      // sniego lygis: medis, akmuo, ledas
      || gid == 107 || gid == 413 || gid == 11
      // dykumos lygis: kaktusas, akmuo
      || gid == 1127 || gid == 1433;
}

// Pikseliai -> langeliai (/16) ir zemelapio ribu patikra
boolean canMoveTo(float x, float y) {
  if (x < 0 || y < 0) return false;

  int col = floor(x / tSize);
  int row = floor(y / tSize);

  if (row >= mapY || col >= mapX) return false;

  return !isSolid(maps[row][col]);
}


// ==================== MONETOS IR LYGIO PABAIGA ====================

// Einama atbulai, nes salinant elementa likusieji pasislenka
void checkCoins() {
  for (int i = coins.size() - 1; i >= 0; i--) {
    PVector c = coins.get(i);

    if (dist(playerX + 8, playerY + 8, c.x + 8, c.y + 8) < 12) {
      coins.remove(i);
      coinsCollected++;
    }
  }
}

void checkLevelComplete() {
  if (!levelComplete && totalCoins > 0 && coins.size() == 0) {
    levelComplete = true;
    completeTime = millis();
  }

  if (levelComplete && currentLevel < MAX_LEVELS && millis() - completeTime > 1000) {
    loadLevel(currentLevel + 1);
  }
}


// ==================== VARTOTOJO SASAJA ====================

void drawHud() {
  String s = "Level " + currentLevel + "     Coins: " + coinsCollected + " / " + totalCoins;

  if (editMode) s += "     [EDIT] tile " + selectedGid;
  if (status.length() > 0) s += "     " + status;
  if (levelComplete && currentLevel >= MAX_LEVELS) s += "     You finished the game!";

  fill(255);
  textSize(20);
  text(s, 20, 30);

  if (status.length() > 0 && millis() - statusTime > 2000) {
    status = "";
  }
}

// Plyteliu palete apacioje su baltu remeliu aplink pasirinkta
void drawPalette() {
  for (int i = 0; i < palette.length; i++) {
    image(photomas[palette[i] - 1], 20 + i * 38, 540, 32, 32);
  }

  int sel = 0;
  for (int i = 0; i < palette.length; i++) {
    if (palette[i] == selectedGid) sel = i;
  }

  noFill();
  stroke(255);
  strokeWeight(2);
  rect(18 + sel * 38, 538, 36, 36);
  noStroke();
}


// ==================== VALDYMAS ====================

void keyPressed() {
  // Sistema kartoja keyPressed kol klavisas laikomas - perjungimus
  // darom tik tada, kai klavisas tikrai ka tik nuspaustas
  boolean wasDown = false;
  if (keyCode >= 0 && keyCode < 256) {
    wasDown = keys[keyCode];
    keys[keyCode] = true;
  }
  if (wasDown) return;

  // E arba F1 - redaktorius
  if (key == 'e' || key == 'E' || keyCode == 112) {
    editMode = !editMode;
  }
  // K arba F5 - issaugoti
  if (key == 'k' || key == 'K' || keyCode == 116) {
    saveMap("level" + currentLevel + ".csv");
  }
  // L arba F9 - perkrauti is disko
  if (key == 'l' || key == 'L' || keyCode == 120) {
    loadLevel(currentLevel);
  }
}

void keyReleased() {
  if (keyCode >= 0 && keyCode < 256) keys[keyCode] = false;
}

void mousePressed() {
  if (!editMode) return;

  // paspaudimai ant paletes nepiesia zemelapio po ja
  if (mouseY > 530) {
    for (int i = 0; i < palette.length; i++) {
      if (mouseX >= 20 + i * 38 && mouseX <= 20 + i * 38 + 32
       && mouseY >= 540 && mouseY <= 572) {
        selectedGid = palette[i];
      }
    }
    return;
  }
  paintAtMouse();
}

void mouseDragged() {
  if (!editMode) return;
  if (mouseY > 530) return;
  paintAtMouse();
}

// Peles vieta ekrane -> zemelapio langelis (prideda kameros poslinki)
void paintAtMouse() {
  int col = floor((mouseX + camX) / tSize);
  int row = floor((mouseY + camY) / tSize);
  paintTile(col, row);
}

// Pakeicia langeli ir tuoj pat perpiesia - kliutys veikia is karto
void paintTile(int col, int row) {
  if (col < 0 || row < 0 || row >= mapY || col >= mapX) return;

  maps[row][col] = selectedGid;
  drawTile(col, row);
}


// ==================== ISSAUGOJIMAS I DISKA ====================

// Issaugo i levels/ salia sketch'o. Atima 1, kad liktu Tiled formatas.
void saveMap(String fileName) {
  String[] lines = new String[mapY];

  for (int j = 0; j < mapY; j++) {
    String[] nums = new String[mapX];
    for (int i = 0; i < mapX; i++) {
      nums[i] = str(maps[j][i] - 1);
    }
    lines[j] = join(nums, ',');
  }

  saveStrings(sketchPath("levels/" + fileName), lines);
  status = "Saved";
  statusTime = millis();
}
