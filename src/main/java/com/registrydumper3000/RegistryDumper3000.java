package com.registrydumper3000;

import com.registrydumper3000.config.DumpConfig;
import com.registrydumper3000.dump.RegistryDumper;
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

    private static ResourceManager resourceManager;

    public RegistryDumper3000() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, DumpConfig.SPEC);
        MinecraftForge.EVENT_BUS.register(this);
        LOGGER.info("Registry Dumper Deluxe initialized");
    }

    @SubscribeEvent
    public void onAddReloadListener(AddReloadListenerEvent event) {
        resourceManager = event.getResourceManager();
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        LOGGER.info("Registry Dumper Deluxe: starting dump...");
        try {
            DumpConfig.load();
            Path dir = DumpConfig.outputFolder;
            Files.createDirectories(dir);
            RegistryDumper.dumpAll(event.getServer(), resourceManager, dir);
            LOGGER.info("Registry Dumper Deluxe: dump complete!");
        } catch (Exception e) {
            LOGGER.error("Registry Dumper Deluxe: error during dump!", e);
        }
    }
}
