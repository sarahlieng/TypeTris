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
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;

/**
 * Minimal local HTTP server for static web UI + auth API.
 */
public final class WebServer {

    private final int port;
    private final Path webRoot;
    private final AuthRepository authRepository;
    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer server;

    public WebServer(int port, Path webRoot, Path dbPath) {
        this.port = port;
        this.webRoot = webRoot;
        this.authRepository = new AuthRepository(dbPath);
    }

    /**
     * Starts the server and initializes schema.
     *
     * <p>Routes:
     * <ul>
     *   <li>`POST /api/register`</li>
     *   <li>`POST /api/login`</li>
     *   <li>`GET /api/personal-stats?username=...`</li>
     *   <li>`GET /api/leaderboard?limit=...`</li>
     * </ul>
     */
    public void start() throws IOException, SQLException {
        authRepository.init();
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/register", new RegisterHandler());
        server.createContext("/api/login", new LoginHandler());
        server.createContext("/api/personal-stats", new PersonalStatsHandler());
        server.createContext("/api/leaderboard", new LeaderboardHandler());
        server.createContext("/", new StaticHandler());
        server.setExecutor(null);
        server.start();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private final class RegisterHandler implements HttpHandler {
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

    private final class LoginHandler implements HttpHandler {
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
                sendJson(exchange, 200, Map.of("ok", true, "message", "Login successful."));
            } catch (SQLException e) {
                sendJson(exchange, 500, Map.of("ok", false, "error", "Database error."));
            }
        }
    }

    private final class PersonalStatsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, Map.of("ok", false, "error", "Method not allowed"));
                return;
            }

            // Expected query: ?username=<player username>
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

    private final class LeaderboardHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, Map.of("ok", false, "error", "Method not allowed"));
                return;
            }

            // Optional query: ?limit=<n> (defaults to 10)
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
                            "avgAccuracy", p.avgAccuracy()
                    ));
                }

                sendJson(exchange, 200, Map.of("ok", true, "leaderboard", out));
            } catch (SQLException e) {
                sendJson(exchange, 500, Map.of("ok", false, "error", "Database error."));
            }
        }
    }

    private final class StaticHandler implements HttpHandler {
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

    private Map<String, String> readJsonBody(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            Map<String, Object> raw = mapper.readValue(in, new TypeReference<>() {});
            Map<String, String> out = new HashMap<>();
            for (Map.Entry<String, Object> e : raw.entrySet()) {
                out.put(e.getKey(), e.getValue() == null ? "" : String.valueOf(e.getValue()));
            }
            return out;
        } catch (Exception e) {
            return Map.of();
        }
    }

    private void sendJson(HttpExchange exchange, int status, Map<String, ?> payload) throws IOException {
        byte[] data = mapper.writeValueAsBytes(payload);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, data.length);
        exchange.getResponseBody().write(data);
        exchange.close();
    }

    private void sendText(HttpExchange exchange, int status, String body, String contentType) throws IOException {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=utf-8");
        exchange.sendResponseHeaders(status, data.length);
        exchange.getResponseBody().write(data);
        exchange.close();
    }

    private Map<String, String> parseQueryParams(String query) {
        Map<String, String> out = new HashMap<>();
        if (query == null || query.isBlank()) {
            return out;
        }
        // Very small query parser: splits on '&' and then '=' (no support for repeated keys).
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

    private static String decode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    private static String trim(String v) {
        return v == null ? "" : v.trim();
    }

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
