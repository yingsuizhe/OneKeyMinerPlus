package com.ying.fabric;

import net.fabricmc.api.ModInitializer;

import com.ying.ExampleMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xiyu.onekeyminer.api.event.ChainEvents;

public final class ExampleModFabric implements ModInitializer {
    private static final Logger log = LoggerFactory.getLogger(ExampleModFabric.class);

    @Override
    public void onInitialize() {
        // This code runs as soon as Minecraft is in a mod-load-ready state.
        // However, some things (like resources) may still be uninitialized.
        // Proceed with mild caution.

        // Run our common setup.


    }
}
