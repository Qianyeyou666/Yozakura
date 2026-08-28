package gq.yozakura.club;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import gq.yozakura.k.B;
import gq.yozakura.core.YozakuraClientState;
import gq.yozakura.manager.FileManager;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public final class ClubService {
    private static final ClubService INSTANCE = createDefault();

    private final ClubApiClient api;
    private final ClubSessionStore sessionStore;
    private final ExecutorService executor;
    private final Object lock = new Object();

    private volatile ClubViewState state = ClubViewState.guest(
            Collections.<ClubConfigSummary>emptyList(), "正在加载配置大厅...", false, false);
    private volatile ClubSession session;
    private ClubConfig pendingDownload;
    private ClubConfig pendingUse;

    public ClubService(ClubApiClient api, ClubSessionStore sessionStore) {
        if (api == null || sessionStore == null) {
            throw new IllegalArgumentException("Club API and session store are required");
        }
        this.api = api;
        this.sessionStore = sessionStore;
        this.executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "Yozakura Club API");
                thread.setDaemon(true);
                return thread;
            }
        });
        boolean restoring = restoreSession();
        if (!restoring) {
            refreshHallConfigs();
        }
    }

    public static ClubService getInstance() {
        return INSTANCE;
    }

    public ClubViewState getState() {
        return state;
    }

    public boolean isAuthenticated() {
        return session != null;
    }

    public void ensureVerifiedSession() {
        if (session != null || state.isBusy()) {
            return;
        }
        final String proof = B.getVerifiedSessionProof();
        if (proof == null || proof.trim().isEmpty()) {
            state = state.status("上传配置需要已验证的客户端会话");
            return;
        }
        state = state.busy("正在验证上传身份...");
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    ClubAuthResult result = api.exchangeVerifiedClient(proof);
                    ClubSession authenticated = new ClubSession(result.getToken(),
                            result.getUser().getUsername());
                    sessionStore.save(authenticated);
                    session = authenticated;
                    List<ClubConfigSummary> configs = api.listHallConfigs();
                    List<ClubConfigSummary> ownedConfigs = api.listConfigs(authenticated.getToken());
                    state = ClubViewState.signedIn(authenticated.getUsername(), configs,
                            ownedConfigIds(ownedConfigs), configs.size() + " 个大厅配置", false);
                } catch (Exception exception) {
                    session = null;
                    clearStoredSession();
                    state = state.error(message("客户端上传身份验证失败", exception));
                    FileManager.logConfigFailure("Club client exchange failed", exception);
                }
            }
        });
    }

    public void refreshHallConfigs() {
        if (state.isBusy()) {
            return;
        }
        state = state.busy("正在刷新配置大厅...");
        executor.execute(new Runnable() {
            @Override
            public void run() {
                ClubSession current = session;
                try {
                    List<ClubConfigSummary> configs = api.listHallConfigs();
                    if (current == null) {
                        state = ClubViewState.guest(configs, configs.size() + " 个大厅配置",
                                false, false);
                    } else {
                        List<ClubConfigSummary> ownedConfigs = api.listConfigs(current.getToken());
                        state = ClubViewState.signedIn(current.getUsername(), configs,
                                ownedConfigIds(ownedConfigs), configs.size() + " 个大厅配置", false);
                    }
                } catch (IOException exception) {
                    if (current != null) {
                        failAuthenticatedRequest(current, "刷新配置大厅失败", exception);
                    } else {
                        state = state.error(message("刷新配置大厅失败", exception));
                        FileManager.logConfigFailure("Refresh config hall failed", exception);
                    }
                }
            }
        });
    }

    public void uploadConfigToHall(final String name, String snapshot) {
        final ClubSession current = requireUploadSession();
        if (current == null) {
            return;
        }
        final JsonObject payload;
        try {
            payload = parseSnapshot(snapshot);
        } catch (IOException exception) {
            state = state.error("本地配置不是有效 JSON 对象");
            return;
        }
        state = state.busy("正在上传到配置大厅...");
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    api.saveHallConfig(current.getToken(), name, payload);
                    List<ClubConfigSummary> configs = api.listHallConfigs();
                    List<ClubConfigSummary> ownedConfigs = api.listConfigs(current.getToken());
                    if (session == current) {
                        state = ClubViewState.signedIn(current.getUsername(), configs,
                                ownedConfigIds(ownedConfigs), "已上传 " + name, false);
                    }
                } catch (IOException exception) {
                    failAuthenticatedRequest(current, "上传配置大厅失败", exception);
                }
            }
        });
    }

    public void downloadHallConfig(final String id) {
        fetchHallConfig(id, false);
    }

    public void useHallConfig(final String id) {
        fetchHallConfig(id, true);
    }

    public void deleteHallConfig(final ClubConfigSummary config) {
        if (state.isBusy()) {
            return;
        }
        final ClubSession current = session;
        if (current == null) {
            state = state.status("删除配置需要已验证的客户端会话");
            return;
        }
        if (config == null || !state.ownsConfig(config.getId())) {
            state = state.error("只能删除自己上传的配置");
            return;
        }
        state = state.busy("正在删除大厅配置...");
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    api.deleteConfig(current.getToken(), config.getId());
                    List<ClubConfigSummary> configs = api.listHallConfigs();
                    List<ClubConfigSummary> ownedConfigs = api.listConfigs(current.getToken());
                    if (session == current) {
                        state = ClubViewState.signedIn(current.getUsername(), configs,
                                ownedConfigIds(ownedConfigs), "已删除 " + config.getName(), false);
                    }
                } catch (IOException exception) {
                    failAuthenticatedRequest(current, "删除配置大厅条目失败", exception);
                }
            }
        });
    }

    private void fetchHallConfig(final String id, final boolean use) {
        if (state.isBusy()) {
            return;
        }
        state = state.busy(use ? "正在读取大厅配置..." : "正在下载大厅配置...");
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    ClubConfig config = api.getHallConfig(id);
                    synchronized (lock) {
                        if (use) {
                            pendingUse = config;
                        } else {
                            pendingDownload = config;
                        }
                    }
                    state = state.status((use ? "已读取 " : "已下载 ")
                            + config.getSummary().getName());
                } catch (IOException exception) {
                    state = state.error(message(use ? "使用大厅配置失败" : "下载大厅配置失败",
                            exception));
                    FileManager.logConfigFailure("Fetch config hall entry failed", exception);
                }
            }
        });
    }

    public ClubConfig consumePendingDownload() {
        synchronized (lock) {
            ClubConfig result = pendingDownload;
            pendingDownload = null;
            return result;
        }
    }

    public ClubConfig consumePendingUse() {
        synchronized (lock) {
            ClubConfig result = pendingUse;
            pendingUse = null;
            return result;
        }
    }

    public void reportResult(String message, boolean error) {
        state = error ? state.error(message) : state.status(message);
    }

    private boolean restoreSession() {
        if (B.getVerifiedSessionProof() == null) {
            clearStoredSession();
            return false;
        }
        final ClubSession restored = sessionStore.load();
        if (restored == null) {
            return false;
        }
        session = restored;
        state = ClubViewState.signedIn(restored.getUsername(), state.getConfigs(),
                Collections.<String>emptySet(), "正在恢复上传身份...", true);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    ClubUser user = api.getCurrentUser(restored.getToken());
                    if (session == restored) {
                        session = new ClubSession(restored.getToken(), user.getUsername());
                        sessionStore.save(session);
                    }
                } catch (IOException exception) {
                    if (session == restored) {
                        session = null;
                        clearStoredSession();
                    }
                } finally {
                    refreshHallConfigsAfterRestore();
                }
            }
        });
        return true;
    }

    private void refreshHallConfigsAfterRestore() {
        ClubSession current = session;
        try {
            List<ClubConfigSummary> configs = api.listHallConfigs();
            if (current == null) {
                state = ClubViewState.guest(configs, configs.size() + " 个大厅配置",
                        false, false);
            } else {
                List<ClubConfigSummary> ownedConfigs = api.listConfigs(current.getToken());
                state = ClubViewState.signedIn(current.getUsername(), configs,
                        ownedConfigIds(ownedConfigs), configs.size() + " 个大厅配置", false);
            }
        } catch (IOException exception) {
            if (current != null) {
                failAuthenticatedRequest(current, "刷新配置大厅失败", exception);
            } else {
                state = state.error(message("刷新配置大厅失败", exception));
            }
        }
    }

    private ClubSession requireUploadSession() {
        ClubSession current = session;
        if (current == null) {
            state = state.status("上传配置需要已验证的客户端会话");
        }
        return current;
    }

    private void failAuthenticatedRequest(ClubSession expected, String prefix,
                                          IOException exception) {
        if (session != expected) {
            return;
        }
        if (exception instanceof ClubApiClient.ClubApiException
                && ((ClubApiClient.ClubApiException) exception).getStatusCode() == 401) {
            session = null;
            clearStoredSession();
            state = state.status("上传身份已失效，请重新验证");
        } else {
            state = state.error(message(prefix, exception));
        }
        FileManager.logConfigFailure(prefix, exception);
    }

    private void clearStoredSession() {
        try {
            sessionStore.clear();
        } catch (IOException exception) {
            FileManager.logConfigFailure("Unable to clear Club session", exception);
        }
    }

    private static Set<String> ownedConfigIds(List<ClubConfigSummary> configs) {
        Set<String> ids = new HashSet<String>();
        if (configs != null) {
            for (ClubConfigSummary config : configs) {
                if (config != null && config.getId() != null && !config.getId().isEmpty()) {
                    ids.add(config.getId());
                }
            }
        }
        return ids;
    }

    private static JsonObject parseSnapshot(String snapshot) throws IOException {
        try {
            JsonElement element = new JsonParser().parse(snapshot == null ? "" : snapshot);
            if (element == null || !element.isJsonObject()) {
                throw new IOException("Config snapshot root must be a JSON object");
            }
            return element.getAsJsonObject();
        } catch (IOException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IOException("Config snapshot was not valid JSON", exception);
        }
    }

    private static ClubService createDefault() {
        String appData = System.getenv("APPDATA");
        File root = new File(appData == null || appData.trim().isEmpty()
                ? System.getProperty("user.home", ".") : appData, YozakuraClientState.getName());
        return new ClubService(new ClubApiClient(),
                new ClubSessionStore(new File(root, "club-session.json")));
    }

    private static String message(String prefix, Exception exception) {
        String detail = exception.getMessage();
        if (detail == null || detail.trim().isEmpty()) {
            return prefix;
        }
        detail = detail.replace('\n', ' ').replace('\r', ' ').trim();
        if (detail.length() > 96) {
            detail = detail.substring(0, 93) + "...";
        }
        return prefix + ": " + detail;
    }

    public static final class ClubViewState {
        private final boolean authenticated;
        private final String username;
        private final List<ClubConfigSummary> configs;
        private final Set<String> ownedConfigIds;
        private final String status;
        private final boolean busy;
        private final boolean error;

        private ClubViewState(boolean authenticated, String username,
                              List<ClubConfigSummary> configs, Set<String> ownedConfigIds,
                              String status, boolean busy, boolean error) {
            this.authenticated = authenticated;
            this.username = username == null ? "" : username;
            this.configs = Collections.unmodifiableList(
                    new java.util.ArrayList<ClubConfigSummary>(configs));
            this.ownedConfigIds = Collections.unmodifiableSet(new HashSet<String>(ownedConfigIds));
            this.status = status == null ? "" : status;
            this.busy = busy;
            this.error = error;
        }

        private static ClubViewState guest(List<ClubConfigSummary> configs, String status,
                                           boolean busy, boolean error) {
            return new ClubViewState(false, "", configs, Collections.<String>emptySet(),
                    status, busy, error);
        }

        private static ClubViewState signedIn(String username, List<ClubConfigSummary> configs,
                                              Set<String> ownedConfigIds, String status,
                                              boolean busy) {
            return new ClubViewState(true, username, configs, ownedConfigIds, status, busy, false);
        }

        private ClubViewState busy(String nextStatus) {
            return new ClubViewState(authenticated, username, configs, ownedConfigIds,
                    nextStatus, true, false);
        }

        private ClubViewState error(String nextStatus) {
            return new ClubViewState(authenticated, username, configs, ownedConfigIds,
                    nextStatus, false, true);
        }

        private ClubViewState status(String nextStatus) {
            return new ClubViewState(authenticated, username, configs, ownedConfigIds,
                    nextStatus, false, false);
        }

        public boolean isAuthenticated() {
            return authenticated;
        }

        public String getUsername() {
            return username;
        }

        public List<ClubConfigSummary> getConfigs() {
            return configs;
        }

        public boolean ownsConfig(String configId) {
            return authenticated && configId != null && ownedConfigIds.contains(configId);
        }

        public String getStatus() {
            return status;
        }

        public boolean isBusy() {
            return busy;
        }

        public boolean isError() {
            return error;
        }
    }
}
