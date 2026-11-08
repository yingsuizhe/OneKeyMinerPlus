package com.ying.neoforge;

import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;

import com.ying.ExampleMod;

@Mod(ExampleMod.MOD_ID)
public final class ExampleModNeoForge {
    public ExampleModNeoForge() {
        // Run our common setup.
        if (ModList.get().isLoaded("onekeyminer")) {
            ExampleMod.init();
        }

    }
}
