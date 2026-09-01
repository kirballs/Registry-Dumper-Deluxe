package com.registrydumper3000;

import com.registrydumper3000.config.DumpConfig;
import com.registrydumper3000.dump.*;
import com.registrydumper3000.persistent.PersistentRegistryTracker;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.resource.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

@Mod(RegistryDumper3000.MOD_ID)
public class RegistryDumper3000 {

    public static final String MOD_ID = "registrydumper3000";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /**
     * Captured ResourceManager from AddReloadListenerEvent.
     * Used for raw resource/recipe/tag file access.
     */
    private static ResourceManager resourceManager;

    public RegistryDumper3000() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, DumpConfig.SPEC);
        MinecraftForge.EVENT_BUS.register(this);
        LOGGER.info("Registry Dumper 3000 initialized");
    }

    // ------------------------------------------------------------------
    //  Capture the server-side ResourceManager as early as possible
    // ------------------------------------------------------------------
    @SubscribeEvent
    public void onAddReloadListener(AddReloadListenerEvent event) {
        resourceManager = event.getResourceManager();
        LOGGER.debug("Registry Dumper 3000: captured ResourceManager");
    }

    // ------------------------------------------------------------------
    //  Main dump trigger — runs once the server is fully started
    // ------------------------------------------------------------------
    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        LOGGER.info("Registry Dumper 3000: starting dump …");

        try {
            DumpConfig.load();
            MinecraftServer server = event.getServer();
            Path dumpRoot = DumpConfig.outputFolder;

            Files.createDirectories(dumpRoot);

            // 1. Built-in registries
            if (DumpConfig.dumpBuiltinRegistriesVal) {
                BuiltinRegistryDumper.dump(dumpRoot);
            }

            // 2. Runtime registries
            if (DumpConfig.dumpRuntimeRegistriesVal) {
                RuntimeRegistryDumper.dump(dumpRoot, server);
            }

            // 3. Tags (raw + expanded)
            if (DumpConfig.dumpTagsVal) {
                TagDumper.dump(dumpRoot, server, resourceManager);
            }

            // 4. Recipes
            if (DumpConfig.dumpRecipesVal) {
                RecipeDumper.dump(dumpRoot, server);
            }

            // 5. Generic resource folders
            if (DumpConfig.dumpResourceFoldersVal) {
                ResourceFolderDumper.dump(dumpRoot, resourceManager);
            }

            // 6. Persistent mod tracking (the extra feature)
            if (DumpConfig.persistentTrackingVal) {
                PersistentRegistryTracker.trackAndSave(dumpRoot, server);
            }

            LOGGER.info("Registry Dumper 3000: dump complete!  Output → {}",
                    dumpRoot.toAbsolutePath());
        } catch (Exception e) {
            LOGGER.error("Registry Dumper 3000: error during dump!", e);
        }
    }
}
