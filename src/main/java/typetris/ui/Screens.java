package typetris.ui;

/**
 * Central constants for {@link java.awt.CardLayout} screen names when the full multi-card shell
 * (menu, gameplay, game over, logout flow, etc.) is wired up. Keeps navigation stringly-typed in one place.
 */
public final class Screens {
    public static final String MENU = "menu";
    public static final String LOGIN = "login";
    public static final String PLAYER = "player";
    public static final String DIFFICULTY = "difficulty";
    public static final String GAMEPLAY = "gameplay";
    public static final String GAME_OVER = "gameOver";
    public static final String STATS = "stats";
    public static final String LEADERBOARD = "leaderboard";
    public static final String DASHBOARD = "dashboard";
    public static final String INSTRUCTIONS = "instructions";
    public static final String PARENT = "parent";
    public static final String PARENT_DASH = "parentDash";
    public static final String LOGOUT_CONFIRM = "logoutConfirm";
    public static final String LOGOUT_SUCCESS = "logoutSuccess";

    private Screens() {
    }
}
