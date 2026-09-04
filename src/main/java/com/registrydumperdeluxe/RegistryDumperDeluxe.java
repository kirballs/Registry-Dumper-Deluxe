package com.registrydumperdeluxe;

import com.registrydumperdeluxe.config.DumpConfig;
import com.registrydumperdeluxe.dump.RegistryDumper;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

@Mod(RegistryDumperDeluxe.MOD_ID)
public class RegistryDumperDeluxe {

    public static final String MOD_ID = "registrydumperdeluxe";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public RegistryDumperDeluxe() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, DumpConfig.SPEC);
        MinecraftForge.EVENT_BUS.register(this);
        LOGGER.info("Registry Dumper Deluxe initialized");
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        LOGGER.info("Registry Dumper Deluxe: starting dump...");
        try {
            DumpConfig.load();
            Path dir = DumpConfig.outputFolder;
            Files.createDirectories(dir);
            ResourceManager resourceManager = event.getServer().getResourceManager();
            LOGGER.info("Using ResourceManager (namespaces: {})", resourceManager.getNamespaces());
            RegistryDumper.dumpAll(event.getServer(), resourceManager, dir);
            LOGGER.info("Registry Dumper Deluxe: dump complete!");
        } catch (Exception e) {
            LOGGER.error("Registry Dumper Deluxe: error during dump!", e);
        }
    }
}
