import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


public class BulletHellGame extends JFrame {
    public BulletHellGame() {
        setTitle("Bullet Hell – Enhanced Gunegon Inspired");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        add(new GamePanel());
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new BulletHellGame());
    }
}

class GamePanel extends JPanel implements ActionListener, KeyListener {
    private javax.swing.Timer timer;
    private final int DELAY = 16; // ~60 FPS

    // Game state constants
    private static final int STATE_MENU = 0;
    private static final int STATE_PLAYING = 1;
    private static final int STATE_GAME_OVER = 3;
    private static final int STATE_VICTORY = 4;

    private int gameState = STATE_MENU;
    private boolean paused = false;

    // Game objects
    private Player player;
    private java.util.List<Enemy> enemies;
    private java.util.List<Bullet> enemyBullets;
    private java.util.List<Bullet> playerBullets;
    private java.util.List<PowerUp> powerUps;
    private java.util.List<Particle> particles;
    private java.util.List<Star> stars;

    // Game variables
    private int score = 0;
    private int wave = 1;
    private int frameCount = 0;

    public GamePanel() {
        setPreferredSize(new Dimension(600, 600));
        setBackground(Color.BLACK);
        setFocusable(true);
        addKeyListener(this);
        initGame();
        timer = new javax.swing.Timer(DELAY, this);
        timer.start();
    }

    private void initGame() {
        // Initialize game objects and variables
        player = new Player(300, 500);
        enemies = new ArrayList<>();
        enemyBullets = new ArrayList<>();
        playerBullets = new ArrayList<>();
        powerUps = new ArrayList<>();
        particles = new ArrayList<>();
        stars = new ArrayList<>();
        // Create a starfield background
        for (int i = 0; i < 100; i++) {
            stars.add(new Star((int)(Math.random()*600), (int)(Math.random()*600), (int)(Math.random()*3)+1));
        }
        score = 0;
        wave = 1;
        frameCount = 0;
        paused = false;
        spawnWave(wave);
    }

    // Spawns a wave of enemies—waves 1–3 use normal enemies; wave 4 spawns a boss.
    private void spawnWave(int wave) {
        enemies.clear();
        enemyBullets.clear();
        if (wave < 4) {
            int numEnemies = wave + 2;
            int spacing = 600 / (numEnemies + 1);
            for (int i = 1; i <= numEnemies; i++) {
                int x = i * spacing - 20;
                int y = 50;
                enemies.add(new NormalEnemy(x, y, wave));
            }
        } else {
            enemies.add(new BossEnemy(200, 50));
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (gameState == STATE_PLAYING && !paused) {
            updateGame();
        }
        repaint();
    }

    private void updateGame() {
        frameCount++;
        score++;

        // Update starfield background
        for (Star s : stars) {
            s.update();
            if (s.y > getHeight()) {
                s.y = 0;
                s.x = (int)(Math.random() * getWidth());
            }
        }

        // Update player movement and auto-fire
        player.update(getWidth(), getHeight());
        if (player.canShoot()) {
            playerBullets.add(new Bullet(player.x + player.width/2 - 4, player.y, 0, -10, false));
            player.resetShootTimer();
        }
        player.decrementShootTimer();

        // Update enemies and have them shoot
        for (Enemy enemy : enemies) {
            enemy.update(getWidth(), getHeight());
            if (enemy.canShoot()) {
                enemy.shoot(enemyBullets, frameCount);
                enemy.resetShootTimer();
            }
            enemy.decrementShootTimer();
        }

        // Update enemy bullets and check collision with player’s (small) hitbox
        Iterator<Bullet> itEnemy = enemyBullets.iterator();
        while (itEnemy.hasNext()) {
            Bullet b = itEnemy.next();
            b.update();
            if (b.x < -10 || b.x > getWidth()+10 || b.y < -10 || b.y > getHeight()+10) {
                itEnemy.remove();
                continue;
            }
            if (b.getBounds().intersects(player.getHitBox())) {
                player.health--;
                itEnemy.remove();
                if (player.health <= 0) {
                    gameState = STATE_GAME_OVER;
                    timer.stop();
                }
            }
        }

        // Update player bullets and check for hits on enemies
        Iterator<Bullet> itPlayer = playerBullets.iterator();
        while (itPlayer.hasNext()) {
            Bullet b = itPlayer.next();
            b.update();
            if (b.x < -10 || b.x > getWidth()+10 || b.y < -10 || b.y > getHeight()+10) {
                itPlayer.remove();
                continue;
            }
            Iterator<Enemy> enemyIter = enemies.iterator();
            while (enemyIter.hasNext()) {
                Enemy enemy = enemyIter.next();
                if (b.getBounds().intersects(enemy.getBounds())) {
                    enemy.health--;
                    itPlayer.remove();
                    if (enemy.health <= 0) {
                        spawnExplosion(enemy.x + enemy.width/2, enemy.y + enemy.height/2);
                        if (Math.random() < 0.3) {
                            powerUps.add(new PowerUp(enemy.x, enemy.y));
                        }
                        enemyIter.remove();
                    }
                    break;
                }
            }
        }

        // Update power-ups and check for player collection
        Iterator<PowerUp> itPower = powerUps.iterator();
        while (itPower.hasNext()) {
            PowerUp p = itPower.next();
            p.update();
            if (p.getBounds().intersects(player.getBounds())) {
                if (p.type == 0) { // Health restore
                    player.health = Math.min(player.health + 1, player.maxHealth);
                } else if (p.type == 1) { // Fire rate boost
                    player.fireRateBoostTimer = 300;
                    player.shootDelay = 5;
                }
                itPower.remove();
            } else if (p.y > getHeight()) {
                itPower.remove();
            }
        }

        // Update explosion particles
        Iterator<Particle> itParticle = particles.iterator();
        while (itParticle.hasNext()) {
            Particle p = itParticle.next();
            p.update();
            if (p.life <= 0) {
                itParticle.remove();
            }
        }

        // Update fire rate boost timer on the player
        if (player.fireRateBoostTimer > 0) {
            player.fireRateBoostTimer--;
            if (player.fireRateBoostTimer == 0) {
                player.shootDelay = player.baseShootDelay;
            }
        }

        // When all enemies are destroyed, either spawn the next wave or win if it was the boss
        if (enemies.isEmpty()) {
            if (wave < 4) {
                wave++;
                spawnWave(wave);
            } else {
                gameState = STATE_VICTORY;
                timer.stop();
            }
        }
    }

    // Spawn explosion particles at (x,y)
    private void spawnExplosion(int x, int y) {
        for (int i = 0; i < 20; i++) {
            particles.add(new Particle(x, y));
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        // Draw the starfield background
        g.setColor(Color.WHITE);
        for (Star s : stars) {
            g.fillRect(s.x, s.y, 2, 2);
        }

        if (gameState == STATE_MENU) {
            g.setColor(Color.YELLOW);
            g.setFont(new Font("Arial", Font.BOLD, 36));
            String title = "Bullet Hell – Enhanced Gunegon";
            FontMetrics fm = g.getFontMetrics();
            int titleWidth = fm.stringWidth(title);
            g.drawString(title, (getWidth()-titleWidth)/2, getHeight()/2 - 50);
            g.setFont(new Font("Arial", Font.PLAIN, 20));
            String msg = "Press ENTER to Start";
            int msgWidth = g.getFontMetrics().stringWidth(msg);
            g.drawString(msg, (getWidth()-msgWidth)/2, getHeight()/2);
        } else if (gameState == STATE_PLAYING) {
            // Draw the player
            g.setColor(Color.CYAN);
            g.fillRect(player.x, player.y, player.width, player.height);
            if (player.focusMode) {
                g.setColor(Color.WHITE);
                Rectangle hitbox = player.getHitBox();
                g.drawRect(hitbox.x, hitbox.y, hitbox.width, hitbox.height);
            }
            // Draw enemies (boss enemies are drawn in orange)
            for (Enemy enemy : enemies) {
                if (enemy instanceof BossEnemy) {
                    g.setColor(Color.ORANGE);
                } else {
                    g.setColor(Color.MAGENTA);
                }
                g.fillRect(enemy.x, enemy.y, enemy.width, enemy.height);
            }
            // Draw enemy bullets
            g.setColor(Color.RED);
            for (Bullet b : enemyBullets) {
                b.draw(g);
            }
            // Draw player bullets
            g.setColor(Color.BLUE);
            for (Bullet b : playerBullets) {
                b.draw(g);
            }
            // Draw power-ups
            for (PowerUp p : powerUps) {
                p.draw(g);
            }
            // Draw explosion particles
            for (Particle p : particles) {
                p.draw(g);
            }
            // Draw HUD
            g.setColor(Color.WHITE);
            g.setFont(new Font("Arial", Font.PLAIN, 16));
            g.drawString("Score: " + score, 10, 20);
            g.drawString("HP: " + player.health, 10, 40);
            g.drawString("Bombs: " + player.bombs, 10, 60);
            g.drawString("Wave: " + wave, 10, 80);
            if (paused) {
                g.setFont(new Font("Arial", Font.BOLD, 36));
                String pauseMsg = "PAUSED";
                int pauseWidth = g.getFontMetrics().stringWidth(pauseMsg);
                g.drawString(pauseMsg, (getWidth()-pauseWidth)/2, getHeight()/2);
            }
        } else if (gameState == STATE_GAME_OVER) {
            g.setColor(Color.RED);
            g.setFont(new Font("Arial", Font.BOLD, 48));
            String msg = "GAME OVER";
            FontMetrics fm = g.getFontMetrics();
            int msgWidth = fm.stringWidth(msg);
            g.drawString(msg, (getWidth()-msgWidth)/2, getHeight()/2);
            g.setFont(new Font("Arial", Font.PLAIN, 20));
            String restart = "Press R to Restart";
            int restartWidth = g.getFontMetrics().stringWidth(restart);
            g.drawString(restart, (getWidth()-restartWidth)/2, getHeight()/2 + 40);
        } else if (gameState == STATE_VICTORY) {
            g.setColor(Color.YELLOW);
            g.setFont(new Font("Arial", Font.BOLD, 48));
            String msg = "VICTORY!";
            FontMetrics fm = g.getFontMetrics();
            int msgWidth = fm.stringWidth(msg);
            g.drawString(msg, (getWidth()-msgWidth)/2, getHeight()/2);
            g.setFont(new Font("Arial", Font.PLAIN, 20));
            String restart = "Press R to Restart";
            int restartWidth = g.getFontMetrics().stringWidth(restart);
            g.drawString(restart, (getWidth()-restartWidth)/2, getHeight()/2 + 40);
        }
    }

    // Key events
    @Override
    public void keyPressed(KeyEvent e) {
        int key = e.getKeyCode();
        if (gameState == STATE_MENU) {
            if (key == KeyEvent.VK_ENTER) {
                gameState = STATE_PLAYING;
            }
        } else if (gameState == STATE_PLAYING) {
            if (key == KeyEvent.VK_LEFT) player.left = true;
            if (key == KeyEvent.VK_RIGHT) player.right = true;
            if (key == KeyEvent.VK_UP) player.up = true;
            if (key == KeyEvent.VK_DOWN) player.down = true;
            if (key == KeyEvent.VK_SHIFT) player.focusMode = true;
            if (key == KeyEvent.VK_SPACE) {
                // Allow manual fire in addition to auto-fire
                playerBullets.add(new Bullet(player.x + player.width/2 - 4, player.y, 0, -10, false));
            }
            if (key == KeyEvent.VK_X) {
                // Use bomb to clear enemy bullets
                if (player.bombs > 0) {
                    enemyBullets.clear();
                    player.bombs--;
                }
            }
            if (key == KeyEvent.VK_P) {
                paused = !paused;
            }
        }
        if (gameState == STATE_GAME_OVER || gameState == STATE_VICTORY) {
            if (key == KeyEvent.VK_R) {
                initGame();
                gameState = STATE_MENU;
                timer.start();
            }
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        int key = e.getKeyCode();
        if (gameState == STATE_PLAYING) {
            if (key == KeyEvent.VK_LEFT) player.left = false;
            if (key == KeyEvent.VK_RIGHT) player.right = false;
            if (key == KeyEvent.VK_UP) player.up = false;
            if (key == KeyEvent.VK_DOWN) player.down = false;
            if (key == KeyEvent.VK_SHIFT) player.focusMode = false;
        }
    }

    @Override
    public void keyTyped(KeyEvent e) {}
}

// --------------------
// Player Class
// --------------------
class Player {
    int x, y;
    int width = 20, height = 20;
    int health = 5;
    int maxHealth = 5;
    int bombs = 3;
    int speed = 5;
    int focusSpeed = 2;
    boolean up, down, left, right;
    boolean focusMode = false;

    int baseShootDelay = 10;
    int shootDelay = baseShootDelay;
    int shootTimer = 0;
    int fireRateBoostTimer = 0;

    public Player(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void update(int panelWidth, int panelHeight) {
        int currentSpeed = focusMode ? focusSpeed : speed;
        if (left) x -= currentSpeed;
        if (right) x += currentSpeed;
        if (up) y -= currentSpeed;
        if (down) y += currentSpeed;
        if (x < 0) x = 0;
        if (x > panelWidth - width) x = panelWidth - width;
        if (y < 0) y = 0;
        if (y > panelHeight - height) y = panelHeight - height;
    }

    public boolean canShoot() {
        return shootTimer <= 0;
    }

    public void resetShootTimer() {
        shootTimer = shootDelay;
    }

    public void decrementShootTimer() {
        if (shootTimer > 0) shootTimer--;
    }

    public Rectangle getBounds() {
        return new Rectangle(x, y, width, height);
    }

    // In focus mode, the hitbox is smaller for precise dodging
    public Rectangle getHitBox() {
        if (focusMode) {
            int hbSize = 8;
            return new Rectangle(x + width/2 - hbSize/2, y + height/2 - hbSize/2, hbSize, hbSize);
        } else {
            return getBounds();
        }
    }
}

// --------------------
// Abstract Enemy Class
// --------------------
abstract class Enemy {
    int x, y, width, height, health;
    int shootDelay, shootTimer;

    public boolean canShoot() {
        return shootTimer <= 0;
    }

    public void resetShootTimer() {
        shootTimer = shootDelay;
    }

    public void decrementShootTimer() {
        if (shootTimer > 0) shootTimer--;
    }

    public abstract void update(int panelWidth, int panelHeight);
    public abstract void shoot(java.util.List<Bullet> enemyBullets, int frameCount);

    public Rectangle getBounds() {
        return new Rectangle(x, y, width, height);
    }
}

// --------------------
// NormalEnemy Class
// --------------------
class NormalEnemy extends Enemy {
    int speed;
    int direction = 1;

    public NormalEnemy(int x, int y, int wave) {
        this.x = x;
        this.y = y;
        this.width = 40;
        this.height = 40;
        this.health = 2 + wave;
        this.speed = 2 + wave;
        this.shootDelay = Math.max(60 - wave * 5, 20);
        this.shootTimer = shootDelay;
    }

    public void update(int panelWidth, int panelHeight) {
        x += speed * direction;
        if (x < 0 || x + width > panelWidth) {
            direction *= -1;
        }
    }

    public void shoot(java.util.List<Bullet> enemyBullets, int frameCount) {
        int numBullets = 6;
        int startX = x + width/2;
        int startY = y + height;
        double bulletSpeed = 3.0;
        for (int i = 0; i < numBullets; i++) {
            double angle = 2 * Math.PI / numBullets * i;
            double dx = bulletSpeed * Math.cos(angle);
            double dy = bulletSpeed * Math.sin(angle);
            enemyBullets.add(new Bullet(startX, startY, dx, dy, true));
        }
    }
}

// --------------------
// BossEnemy Class
// --------------------
class BossEnemy extends Enemy {
    int speed = 2;
    double phase = 0;

    public BossEnemy(int x, int y) {
        this.x = x;
        this.y = y;
        this.width = 120;
        this.height = 60;
        this.health = 200;
        this.shootDelay = 40;
        this.shootTimer = shootDelay;
    }

    public void update(int panelWidth, int panelHeight) {
        phase += 0.05;
        x += speed;
        y = 50 + (int)(20 * Math.sin(phase));
        if (x < 0 || x + width > panelWidth) {
            speed = -speed;
        }
    }

    public void shoot(java.util.List<Bullet> enemyBullets, int frameCount) {
        int pattern = (frameCount / 120) % 2;
        int numBullets = 12;
        int startX = x + width/2;
        int startY = y + height/2;
        if (pattern == 0) {
            double bulletSpeed = 3.0;
            for (int i = 0; i < numBullets; i++) {
                double angle = 2 * Math.PI / numBullets * i;
                double dx = bulletSpeed * Math.cos(angle);
                double dy = bulletSpeed * Math.sin(angle);
                enemyBullets.add(new Bullet(startX, startY, dx, dy, true));
            }
        } else {
            double baseAngle = (frameCount % 360) * Math.PI / 180;
            double bulletSpeed = 2.5;
            for (int i = 0; i < numBullets; i++) {
                double angle = baseAngle + 2 * Math.PI / numBullets * i;
                double dx = bulletSpeed * Math.cos(angle);
                double dy = bulletSpeed * Math.sin(angle);
                enemyBullets.add(new Bullet(startX, startY, dx, dy, true));
            }
        }
    }
}

// --------------------
// Bullet Class
// --------------------
class Bullet {
    double x, y;
    double dx, dy;
    int size = 8;
    boolean isEnemyBullet;

    public Bullet(double x, double y, double dx, double dy, boolean isEnemyBullet) {
        this.x = x;
        this.y = y;
        this.dx = dx;
        this.dy = dy;
        this.isEnemyBullet = isEnemyBullet;
    }

    public void update() {
        x += dx;
        y += dy;
    }

    public void draw(Graphics g) {
        g.fillOval((int)x, (int)y, size, size);
    }

    public Rectangle getBounds() {
        return new Rectangle((int)x, (int)y, size, size);
    }
}

// --------------------
// PowerUp Class
// --------------------
class PowerUp {
    int x, y, size = 12, speed = 2;
    int type; // 0 = health, 1 = fire rate boost

    public PowerUp(int x, int y) {
        this.x = x;
        this.y = y;
        type = Math.random() < 0.5 ? 0 : 1;
    }

    public void update() {
        y += speed;
    }

    public Rectangle getBounds() {
        return new Rectangle(x, y, size, size);
    }

    public void draw(Graphics g) {
        if (type == 0) {
            g.setColor(Color.GREEN);
        } else {
            g.setColor(Color.ORANGE);
        }
        g.fillOval(x, y, size, size);
    }
}

// --------------------
// Particle Class for Explosions
// --------------------
class Particle {
    double x, y, dx, dy;
    int life;

    public Particle(double x, double y) {
        this.x = x;
        this.y = y;
        dx = (Math.random() - 0.5) * 4;
        dy = (Math.random() - 0.5) * 4;
        life = 30;
    }

    public void update() {
        x += dx;
        y += dy;
        life--;
    }

    public void draw(Graphics g) {
        g.setColor(Color.ORANGE);
        g.fillOval((int)x, (int)y, 4, 4);
    }
}

// --------------------
// Star Class for the Background
// --------------------
class Star {
    int x, y;
    int speed;

    public Star(int x, int y, int speed) {
        this.x = x;
        this.y = y;
        this.speed = speed;
    }

    public void update() {
        y += speed;
    }
}
