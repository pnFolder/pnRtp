package ru.privatenull.integration;

import org.bukkit.plugin.java.JavaPlugin;
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticConfiguration;
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticContainer;
import org.pnfolder.pnlibrary.api.logging.MessageBox;
import org.pnfolder.pnlibrary.api.plugin.ModuleContext;
import org.pnfolder.pnlibrary.api.plugin.PluginRegistration;
import org.pnfolder.pnlibrary.api.remote.PolicyErrorAction;
import org.pnfolder.pnlibrary.api.remote.RemotePolicySettings;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryProvider;
import org.pnfolder.pnlibrary.api.updates.UpdateChannel;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

/** Подключение pnRtp к общему рантайму pnLibrary; загружается только после его включения. */
public final class PnLibraryRuntime {

    public static final String MINIMUM_VERSION = "0.0.1-dev.6";
    private static final String MODULE_ID = "pnrtp";
    private static final int BSTATS_PROJECT_ID = 35049;
    private static final String FASTSTATS_TOKEN = "34ab5a660dc9e25408c107d9dffb7448";
    private static final String POLICY_SOURCE =
            "https://raw.githubusercontent.com/pnFolder/pnlibrary-policy/main/.pnlibrary/policies/PnRtpPolicy.java";
    private static final String UPDATE_OWNER = "pnFolder";
    private static final String UPDATE_REPOSITORY = "pnRtp";

    private final JavaPlugin owner;
    private final PnLibrary runtime;
    private PluginRegistration registration;
    private ModuleContext module;

    private PnLibraryRuntime(JavaPlugin owner, PnLibrary runtime) {
        this.owner = owner;
        this.runtime = runtime;
    }

    public static PnLibraryRuntime connect(JavaPlugin owner) {
        PnLibrary runtime = PnLibraryProvider.get();
        if (!runtime.isAtLeastVersion(MINIMUM_VERSION)) {
            throw new IllegalStateException("pnRtp требует pnLibrary " + MINIMUM_VERSION
                    + " или новее, установлена " + runtime.getVersion());
        }
        return new PnLibraryRuntime(owner, runtime);
    }

    public void register() {
        if (registration != null) throw new IllegalStateException("pnRtp уже зарегистрирован в pnLibrary");
        registration = runtime.getPlugins().register(owner);
        try {
            module = registration.registerModule(MODULE_ID, builder -> builder
                    .metrics(metrics -> metrics.bStats(BSTATS_PROJECT_ID).fastStats(FASTSTATS_TOKEN))
                    .diagnostics(owner.getDataFolder().toPath(), DiagnosticContainer.builder("pnrtp")
                            .snapshot(this::diagnostics)
                            .configuration(DiagnosticConfiguration.builder("config.yml").build())
                            .build())
                    // Недоступная политика (нет сети, JRE без компилятора) не останавливает плагин,
                    // только явный запрет от самой политики.
                    .remotePolicy(RemotePolicySettings.builder()
                            .source(POLICY_SOURCE)
                            .onError(PolicyErrorAction.KEEP_RUNNING)
                            .support(PnLibraryIdentity.SUPPORT_URL)
                            .build())
                    .updates(UPDATE_OWNER, UPDATE_REPOSITORY, updater -> updater
                            .channel(UpdateChannel.STABLE)
                            .automaticDownload(false)
                            .configurationBackup(owner.getDataFolder().toPath(), "config.yml")
                            .artifact("(?i)^pnRtp-[0-9].*\\.jar$", 8)));
        } catch (RuntimeException failure) {
            closeRegistration();
            throw failure;
        }
    }

    public void started(String targetWorld, boolean targetWorldLoaded, int modes,
                        boolean effects, boolean titles, boolean worldBorderCheck) {
        if (module == null) return;
        MessageBox block = module.getLifecycle().enabled()
                .ok("Режимы", modes + " (near, default, far)");
        block = targetWorldLoaded ? block.ok("Мир телепортации", targetWorld)
                : block.warn("Мир телепортации", targetWorld + " не загружен");
        block = effects ? block.ok("Эффекты", "частицы и звуки") : block.skip("Эффекты", "выключены");
        block = titles ? block.ok("Титры", "включены") : block.skip("Титры", "выключены");
        block = worldBorderCheck ? block.ok("Граница мира", "проверяется") : block.skip("Граница мира", "не проверяется");
        block.ok("Диагностика", "/pn debug pnRtp").show();
    }

    public void stopped() {
        ModuleContext closing = module;
        if (closing == null || closing.isClosed()) return;
        try {
            stoppedOn(closing);
        } catch (RuntimeException failure) {
            owner.getLogger().log(Level.FINE, "Не удалось вывести сообщение об остановке pnRtp", failure);
        }
    }

    private Map<String, Object> diagnostics() {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("version", owner.getDescription().getVersion());
        report.put("server", owner.getServer().getVersion());
        report.put("minecraft", owner.getServer().getBukkitVersion());
        report.put("java", System.getProperty("java.version"));
        return report;
    }

    public void close() {
        module = null;
        closeRegistration();
    }

    private void stoppedOn(ModuleContext closing) {
        closing.getLifecycle().disabled()
                .ok("Кулдауны", "сброшены")
                .ok("Диагностика", "/pn debug pnRtp")
                .show();
    }

    private void closeRegistration() {
        PluginRegistration closing = registration;
        registration = null;
        if (closing != null && !closing.isClosed()) closing.close();
    }
}
