package gq.yozakura.club;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ClubServiceContractTest {
    @Test
    public void networkWorkRunsOnDedicatedDaemonExecutor() throws IOException {
        String source = source("src/main/java/gq/yozakura/club/ClubService.java");

        assertTrue(source.contains("Executors.newSingleThreadExecutor"));
        assertTrue(source.contains("thread.setDaemon(true)"));
        assertTrue(source.contains("executor.execute("));
    }

    @Test
    public void renderFacingStateDoesNotContainPasswords() throws IOException {
        String source = source("src/main/java/gq/yozakura/club/ClubService.java");

        assertFalse(source.contains("private volatile String password"));
        assertFalse(source.contains("this.password"));
    }

    @Test
    public void panelSessionComesFromNativeVerificationProof() throws IOException {
        String source = source("src/main/java/gq/yozakura/club/ClubService.java");

        assertTrue(source.contains("public void ensureVerifiedSession()"));
        assertTrue(source.contains("B.getVerifiedSessionProof()"));
        assertTrue(source.contains("api.exchangeVerifiedClient(proof)"));
        assertFalse(source.contains("public void login("));
        assertFalse(source.contains("public void register("));
    }

    @Test
    public void deletingAHallConfigRequiresOwnershipAndRefreshesThePublicList() throws IOException {
        String source = source("src/main/java/gq/yozakura/club/ClubService.java");

        assertTrue(source.contains("public void deleteHallConfig(final ClubConfigSummary config)"));
        assertTrue(source.contains("state.ownsConfig(config.getId())"));
        assertFalse(source.contains("current.getUsername().equalsIgnoreCase(config.getOwner())"));
        assertTrue(source.contains("api.deleteConfig(current.getToken(), config.getId())"));
        assertTrue(source.contains("List<ClubConfigSummary> ownedConfigs = api.listConfigs(current.getToken())"));
        assertTrue(source.contains("List<ClubConfigSummary> configs = api.listHallConfigs()"));
        assertTrue(source.contains("failAuthenticatedRequest(current, \"删除配置大厅条目失败\", exception)"));
    }

    private static String source(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
    }
}
