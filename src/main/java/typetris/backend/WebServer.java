package typetris.backend;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal local HTTP server for static web UI and authentication API
 * 
 * @author CS2212 Group 26, Western University
 */
public final class WebServer {
    private static final String ADMIN_PIN = "2212";

    private final int port;
    private final Path webRoot;
    private final AuthRepository authRepository;
    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer server;

    /**
     * @param port listen port (for example 8080)
     * @param webRoot directory for {@link StaticHandler} (HTML/JS/CSS)
     * @param dbPath SQLite file path passed to {@link AuthRepository}
     */
    public WebServer(int port, Path webRoot, Path dbPath) {
        this.port = port;
        this.webRoot = webRoot;
        this.authRepository = new AuthRepository(dbPath);
    }

    /**
     * Starts the server and initializes schema
     *
     * <p>
     * Routes include {@code POST /api/register}, {@code POST /api/login}, parent
     * admin
     * POST endpoints, {@code GET /api/personal-stats}, {@code GET /api/leaderboard},
     * and {@code POST /api/game-result}
     */
    public void start() throws IOException, SQLException {
        authRepository.init();
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/register", new RegisterHandler());
        server.createContext("/api/login", new LoginHandler());
        server.createContext("/api/parent/sessions", new ParentSessionsHandler());
        server.createContext("/api/parent/accounts/list", new ParentAccountsListHandler());
        server.createContext("/api/parent/accounts/create", new ParentAccountsCreateHandler());
        server.createContext("/api/parent/accounts/delete", new ParentAccountsDeleteHandler());
        server.createContext("/api/parent/accounts/reset-password", new ParentAccountsResetPasswordHandler());
        server.createContext("/api/personal-stats", new PersonalStatsHandler());
        server.createContext("/api/leaderboard", new LeaderboardHandler());
        server.createContext("/api/game-result", new GameResultHandler());
        server.createContext("/", new StaticHandler());
        server.setExecutor(null);
        server.start();
    }

    /** Stops the HTTP server if {@link #start()} was called. */
    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    /** {@code POST /api/register} — self-service account creation from JSON body. */
    private final class RegisterHandler implements HttpHandler {
        /** {@inheritDoc} */
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, Map.of("ok", false, "error", "Method not allowed"));
                return;
            }
            Map<String, String> body = readJsonBody(exchange);
            String email = trim(body.get("email"));
            String username = trim(body.get("username"));
            String password = trim(body.get("password"));
            String confirm = trim(body.get("confirmPassword"));

            if (email.isEmpty() || username.isEmpty() || password.isEmpty() || confirm.isEmpty()) {
                sendJson(exchange, 400, Map.of("ok", false, "error", "All fields are required."));
                return;
            }
            if (!password.equals(confirm)) {
                sendJson(exchange, 400, Map.of("ok", false, "error", "Passwords do not match."));
                return;
            }
            if (password.length() < 8) {
                sendJson(exchange, 400, Map.of("ok", false, "error", "Password must be at least 8 characters."));
                return;
            }

            String salt = PasswordUtil.newSaltBase64();
            String hash = PasswordUtil.hashBase64(password, salt);
            try {
                AuthRepository.CreateUserResult created = authRepository.createUser(email, username, hash, salt);
                if (created == AuthRepository.CreateUserResult.EMAIL_TAKEN) {
                    sendJson(exchange, 409, Map.of("ok", false, "error", "Email is already taken."));
                    return;
                }
                if (created == AuthRepository.CreateUserResult.USERNAME_TAKEN) {
                    sendJson(exchange, 409, Map.of("ok", false, "error", "Username is already taken."));
                    return;
                }
                if (created == AuthRepository.CreateUserResult.DUPLICATE_UNKNOWN) {
                    sendJson(exchange, 409, Map.of("ok", false, "error", "Username or email is already taken."));
                    return;
                }
                sendJson(exchange, 201, Map.of("ok", true, "message", "Account created."));
            } catch (SQLException e) {
                sendJson(exchange, 500, Map.of("ok", false, "error", "Database error."));
            }
        }
    }

    /** {@code POST /api/login} — verifies password and records a session start. */
    private final class LoginHandler implements HttpHandler {
        /** {@inheritDoc} */
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, Map.of("ok", false, "error", "Method not allowed"));
                return;
            }
            Map<String, String> body = readJsonBody(exchange);
            String username = trim(body.get("username"));
            String password = trim(body.get("password"));
            if (username.isEmpty() || password.isEmpty()) {
                sendJson(exchange, 400, Map.of("ok", false, "error", "Username and password are required."));
                return;
            }
            try {
                AuthRepository.UserCredentials creds = authRepository.findByUsername(username);
                if (creds == null || !PasswordUtil.verify(password, creds.salt(), creds.passwordHash())) {
                    sendJson(exchange, 401, Map.of("ok", false, "error", "Invalid username or password."));
                    return;
                }
                authRepository.recordSessionStart(username);
                sendJson(exchange, 200, Map.of("ok", true, "message", "Login successful."));
            } catch (SQLException e) {
                sendJson(exchange, 500, Map.of("ok", false, "error", "Database error."));
            }
        }
    }

    /** {@code POST /api/parent/sessions} — PIN-guarded session history list. */
    private final class ParentSessionsHandler implements HttpHandler {
        /** {@inheritDoc} */
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, Map.of("ok", false, "error", "Method not allowed"));
                return;
            }
            Map<String, String> body = readJsonBody(exchange);
            String pin = trim(body.get("pin"));
            if (!ADMIN_PIN.equals(pin)) {
                sendJson(exchange, 401, Map.of("ok", false, "error", "Invalid administrative PIN."));
                return;
            }
            try {
                List<AuthRepository.SessionHistory> rows = authRepository.listSessionHistory();
                sendJson(exchange, 200, Map.of("ok", true, "sessions", rows));
            } catch (SQLException e) {
                sendJson(exchange, 500, Map.of("ok", false, "error", "Database error."));
            }
        }
    }

    /** {@code POST /api/parent/accounts/list} — PIN-guarded user directory for admin UI. */
    private final class ParentAccountsListHandler implements HttpHandler {
        /** {@inheritDoc} */
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, Map.of("ok", false, "error", "Method not allowed"));
                return;
            }
            Map<String, String> body = readJsonBody(exchange);
            String pin = trim(body.get("pin"));
            if (!ADMIN_PIN.equals(pin)) {
                sendJson(exchange, 401, Map.of("ok", false, "error", "Invalid administrative PIN."));
                return;
            }
            try {
                List<AuthRepository.UserAccount> users = authRepository.listUserAccounts();
                sendJson(exchange, 200, Map.of("ok", true, "accounts", users));
            } catch (SQLException e) {
                sendJson(exchange, 500, Map.of("ok", false, "error", "Database error."));
            }
        }
    }

    /** {@code POST /api/parent/accounts/create} — PIN-guarded account creation. */
    private final class ParentAccountsCreateHandler implements HttpHandler {
        /** {@inheritDoc} */
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, Map.of("ok", false, "error", "Method not allowed"));
                return;
            }
            Map<String, String> body = readJsonBody(exchange);
            String pin = trim(body.get("pin"));
            if (!ADMIN_PIN.equals(pin)) {
                sendJson(exchange, 401, Map.of("ok", false, "error", "Invalid administrative PIN."));
                return;
            }
            String email = trim(body.get("email"));
            String username = trim(body.get("username"));
            String password = trim(body.get("password"));
            if (email.isEmpty() || username.isEmpty() || password.isEmpty()) {
                sendJson(exchange, 400, Map.of("ok", false, "error", "Email, username, and password are required."));
                return;
            }
            if (password.length() < 8) {
                sendJson(exchange, 400, Map.of("ok", false, "error", "Password must be at least 8 characters."));
                return;
            }
            String salt = PasswordUtil.newSaltBase64();
            String hash = PasswordUtil.hashBase64(password, salt);
            try {
                AuthRepository.CreateUserResult created = authRepository.createUser(email, username, hash, salt);
                if (created == AuthRepository.CreateUserResult.EMAIL_TAKEN) {
                    sendJson(exchange, 409, Map.of("ok", false, "error", "Email is already taken."));
                    return;
                }
                if (created == AuthRepository.CreateUserResult.USERNAME_TAKEN) {
                    sendJson(exchange, 409, Map.of("ok", false, "error", "Username is already taken."));
                    return;
                }
                if (created == AuthRepository.CreateUserResult.DUPLICATE_UNKNOWN) {
                    sendJson(exchange, 409, Map.of("ok", false, "error", "Username or email is already taken."));
                    return;
                }
                sendJson(exchange, 201, Map.of("ok", true, "message", "Student account created."));
            } catch (SQLException e) {
                sendJson(exchange, 500, Map.of("ok", false, "error", "Database error."));
            }
        }
    }

    /** {@code POST /api/parent/accounts/delete} — PIN-guarded account removal. */
    private final class ParentAccountsDeleteHandler implements HttpHandler {
        /** {@inheritDoc} */
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, Map.of("ok", false, "error", "Method not allowed"));
                return;
            }
            Map<String, String> body = readJsonBody(exchange);
            String pin = trim(body.get("pin"));
            if (!ADMIN_PIN.equals(pin)) {
                sendJson(exchange, 401, Map.of("ok", false, "error", "Invalid administrative PIN."));
                return;
            }
            String username = trim(body.get("username"));
            if (username.isEmpty()) {
                sendJson(exchange, 400, Map.of("ok", false, "error", "Username is required."));
                return;
            }
            try {
                boolean deleted = authRepository.deleteUserByUsername(username);
                if (!deleted) {
                    sendJson(exchange, 404, Map.of("ok", false, "error", "Account not found."));
                    return;
                }
                sendJson(exchange, 200, Map.of("ok", true, "message", "Account deleted."));
            } catch (SQLException e) {
                sendJson(exchange, 500, Map.of("ok", false, "error", "Database error."));
            }
        }
    }

    /** {@code GET /api/personal-stats?username=} — JSON aggregates for one player. */
    private final class PersonalStatsHandler implements HttpHandler {
        /** {@inheritDoc} */
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, Map.of("ok", false, "error", "Method not allowed"));
                return;
            }

            Map<String, String> query = parseQueryParams(exchange.getRequestURI().getQuery());
            String username = trim(query.get("username"));
            if (username.isEmpty()) {
                sendJson(exchange, 400, Map.of("ok", false, "error", "Missing username."));
                return;
            }

            try {
                PlayerAccount player = authRepository.findPlayerAccount(username);
                if (player == null) {
                    sendJson(exchange, 404, Map.of("ok", false, "error", "Player not found."));
                    return;
                }
                sendJson(exchange, 200, Map.of("ok", true, "player", player));
            } catch (SQLException e) {
                sendJson(exchange, 500, Map.of("ok", false, "error", "Database error."));
            }
        }
    }

    /** {@code POST /api/parent/accounts/reset-password} — PIN-guarded password reset. */
    private final class ParentAccountsResetPasswordHandler implements HttpHandler {
        /** {@inheritDoc} */
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, Map.of("ok", false, "error", "Method not allowed"));
                return;
            }
            Map<String, String> body = readJsonBody(exchange);
            String pin = trim(body.get("pin"));
            if (!ADMIN_PIN.equals(pin)) {
                sendJson(exchange, 401, Map.of("ok", false, "error", "Invalid administrative PIN."));
                return;
            }
            String username = trim(body.get("username"));
            String newPassword = trim(body.get("newPassword"));
            if (username.isEmpty() || newPassword.isEmpty()) {
                sendJson(exchange, 400, Map.of("ok", false, "error", "Username and new password are required."));
                return;
            }
            if (newPassword.length() < 8) {
                sendJson(exchange, 400, Map.of("ok", false, "error", "Password must be at least 8 characters."));
                return;
            }
            String salt = PasswordUtil.newSaltBase64();
            String hash = PasswordUtil.hashBase64(newPassword, salt);
            try {
                boolean reset = authRepository.resetPasswordByUsername(username, hash, salt);
                if (!reset) {
                    sendJson(exchange, 404, Map.of("ok", false, "error", "Account not found."));
                    return;
                }
                sendJson(exchange, 200, Map.of("ok", true, "message", "Password reset."));
            } catch (SQLException e) {
                sendJson(exchange, 500, Map.of("ok", false, "error", "Database error."));
            }
        }
    }

    /** {@code GET /api/leaderboard?limit=} — top players by high score. */
    private final class LeaderboardHandler implements HttpHandler {
        /** {@inheritDoc} */
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, Map.of("ok", false, "error", "Method not allowed"));
                return;
            }

            Map<String, String> query = parseQueryParams(exchange.getRequestURI().getQuery());
            int limit = 10;
            String rawLimit = trim(query.get("limit"));
            if (!rawLimit.isEmpty()) {
                try {
                    limit = Integer.parseInt(rawLimit);
                } catch (NumberFormatException e) {
                    sendJson(exchange, 400, Map.of("ok", false, "error", "Invalid limit."));
                    return;
                }
            }

            try {
                Leaderboard leaderboard = new Leaderboard(authRepository);
                List<PlayerAccount> top = leaderboard.getTopPlayers(limit);

                List<Map<String, Object>> out = new ArrayList<>();
                for (int i = 0; i < top.size(); i++) {
                    PlayerAccount p = top.get(i);
                    out.add(Map.of(
                            "rank", i + 1,
                            "username", p.username(),
                            "highScore", p.highScore(),
                            "bestWPM", p.bestWPM(),
                            "avgAccuracy", p.avgAccuracy(),
                            "gamesPlayed", p.gamesPlayed()));
                }

                sendJson(exchange, 200, Map.of("ok", true, "leaderboard", out));
            } catch (SQLException e) {
                sendJson(exchange, 500, Map.of("ok", false, "error", "Database error."));
            }
        }
    }

    /**
     * Persists score / WPM / accuracy for the logged-in player (identified by username)
     * Not cryptographically authenticated; suitable for local / coursework use
     */
    private final class GameResultHandler implements HttpHandler {
        /** {@inheritDoc} */
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, Map.of("ok", false, "error", "Method not allowed"));
                return;
            }
            Map<String, String> body = readJsonBody(exchange);
            String username = trim(body.get("username"));
            if (username.isEmpty()) {
                sendJson(exchange, 400, Map.of("ok", false, "error", "Username is required."));
                return;
            }
            int score;
            int wpm;
            double accuracy;
            try {
                score = Integer.parseInt(trim(body.get("score")));
                wpm = Integer.parseInt(trim(body.get("wpm")));
                accuracy = Double.parseDouble(trim(body.get("accuracy")));
            } catch (NumberFormatException e) {
                sendJson(exchange, 400, Map.of("ok", false, "error", "score, wpm, and accuracy must be numbers."));
                return;
            }
            int wrongStrokes = parseOptionalNonNegativeInt(body.get("wrongStrokes"), 0);
            long activeMs = parseOptionalNonNegativeLong(body.get("activePlayTimeMs"), 0L);
            int maxLevel = parseOptionalNonNegativeInt(body.get("maxLevel"), 0);
            int wordsTyped = parseOptionalNonNegativeInt(body.get("wordsTyped"), 0);
            int peakWpm = parseOptionalNonNegativeInt(body.get("peakWpm"), wpm);
            CompletedRunStats run = new CompletedRunStats(
                    score, wpm, accuracy, wrongStrokes, activeMs, maxLevel, wordsTyped, peakWpm);
            try {
                authRepository.recordGameResult(username, run);
                sendJson(exchange, 200, Map.of("ok", true, "message", "Stats saved."));
            } catch (SQLException e) {
                sendJson(exchange, 500, Map.of("ok", false, "error", "Database error."));
            }
        }
    }

    /** Serves files under {@link #webRoot} for {@code GET /} and asset paths. */
    private final class StaticHandler implements HttpHandler {
        /** {@inheritDoc} */
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String uriPath = exchange.getRequestURI().getPath();
            String relative = "/".equals(uriPath) ? "index.html" : uriPath.substring(1);
            Path target = webRoot.resolve(relative).normalize();
            if (!target.startsWith(webRoot.normalize()) || Files.isDirectory(target) || !Files.exists(target)) {
                sendText(exchange, 404, "Not Found", "text/plain");
                return;
            }
            byte[] data = Files.readAllBytes(target);
            String type = contentType(target.getFileName().toString());
            exchange.getResponseHeaders().set("Content-Type", type);
            exchange.sendResponseHeaders(200, data.length);
            exchange.getResponseBody().write(data);
            exchange.close();
        }
    }

    /** Parses JSON object body into string map; empty map on parse failure. */
    private Map<String, String> readJsonBody(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            Map<String, Object> raw = mapper.readValue(in, new TypeReference<>() {
            });
            Map<String, String> out = new HashMap<>();
            for (Map.Entry<String, Object> e : raw.entrySet()) {
                out.put(e.getKey(), e.getValue() == null ? "" : String.valueOf(e.getValue()));
            }
            return out;
        } catch (Exception e) {
            return Map.of();
        }
    }

    /** Writes UTF-8 JSON response and closes the exchange. */
    private void sendJson(HttpExchange exchange, int status, Map<String, ?> payload) throws IOException {
        byte[] data = mapper.writeValueAsBytes(payload);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, data.length);
        exchange.getResponseBody().write(data);
        exchange.close();
    }

    /** Plain-text (or other) small error bodies. */
    private void sendText(HttpExchange exchange, int status, String body, String contentType) throws IOException {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=utf-8");
        exchange.sendResponseHeaders(status, data.length);
        exchange.getResponseBody().write(data);
        exchange.close();
    }

    /** URL-decodes {@code application/x-www-form-urlencoded} style query pairs. */
    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> out = new HashMap<>();
        if (query == null || query.isBlank()) {
            return out;
        }
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            if (pair.isBlank()) {
                continue;
            }
            String[] kv = pair.split("=", 2);
            String key = decode(kv[0]);
            String value = kv.length > 1 ? decode(kv[1]) : "";
            out.put(key, value);
        }
        return out;
    }

    /** @param s percent-encoded fragment */
    private static String decode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    /** @return trimmed string or empty when {@code null} */
    private static String trim(String v) {
        return v == null ? "" : v.trim();
    }

    /** @param raw optional numeric string from JSON */
    private static int parseOptionalNonNegativeInt(String raw, int defaultValue) {
        String t = trim(raw);
        if (t.isEmpty()) {
            return defaultValue;
        }
        try {
            return Math.max(0, Integer.parseInt(t));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /** @param raw optional numeric string from JSON */
    private static long parseOptionalNonNegativeLong(String raw, long defaultValue) {
        String t = trim(raw);
        if (t.isEmpty()) {
            return defaultValue;
        }
        try {
            long v = Long.parseLong(t);
            return Math.max(0L, v);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /** Guesses {@code Content-Type} from file extension. */
    private static String contentType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".html")) {
            return "text/html; charset=utf-8";
        }
        if (lower.endsWith(".js")) {
            return "application/javascript; charset=utf-8";
        }
        if (lower.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (lower.endsWith(".json")) {
            return "application/json; charset=utf-8";
        }
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".svg")) {
            return "image/svg+xml";
        }
        return "application/octet-stream";
    }
}
