package dev.desiderium.ordinatio;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.network.NetworkMod;

@Mod(modid = BuildConstants.MOD_ID, name = BuildConstants.MOD_NAME,
        version = BuildConstants.MOD_VERSION, acceptedMinecraftVersions = "[1.6.4]")
@NetworkMod(clientSideRequired = false, serverSideRequired = false)
public final class Ordinatio {
}
