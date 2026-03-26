package typetris.backend;

import java.sql.SQLException;
import java.util.List;

/**
 * Provides global high-score ordering for the UI.
 *
 * <p>This is currently backed by the `users` table and returns a list of
 * lightweight `PlayerAccount` views suitable for displaying a leaderboard.
 */
public final class Leaderboard {

    private final AuthRepository authRepository;

    public Leaderboard(AuthRepository authRepository) {
        this.authRepository = authRepository;
    }

    /**
     * @param limit max number of players to return
     * @return top players ordered by global `high_score` desc
     */
    public List<PlayerAccount> getTopPlayers(int limit) throws SQLException {
        return authRepository.fetchLeaderboard(limit);
    }
}

