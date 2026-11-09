package dev.desiderium.ordinatio.mixin;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import dev.desiderium.ordinatio.config.ConfigFileAccess;
import dev.desiderium.ordinatio.config.ConfigIndex;
import dev.desiderium.ordinatio.config.ConfigIndex.RegistryId;
import net.minecraftforge.common.Configuration;
import net.minecraftforge.common.Property;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;
import java.util.Locale;

@Mixin(value = Configuration.class, remap = false)
public abstract class ConfigurationMixin implements ConfigFileAccess {
    @Shadow private boolean caseSensitiveCustomCategories;
    @Shadow private static Configuration PARENT;
    @Shadow File file;

    @Override
    public void ordinatio$setFile(File file) {
        this.file = file;
    }

    @Override
    public boolean ordinatio$isGlobalConfig() {
        return PARENT != null;
    }

    @Inject(method = "<init>(Ljava/io/File;)V", at = @At("RETURN"))
    private void ordinatio$trackFile(File file, CallbackInfo ci) {
        if (PARENT != null) return;
        Loader loader = Loader.instance();
        ModContainer owner = loader.activeModContainer();
        if (owner != null) {
            ConfigIndex.track(owner.getModId(), loader.getConfigDir(), file, (Configuration) (Object) this);
        }
    }

    @Inject(method = "get(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lnet/minecraftforge/common/Property$Type;)Lnet/minecraftforge/common/Property;", at = @At("RETURN"))
    private void ordinatio$trackScalar(String category, String key, String defaultValue, String comment,
                                       Property.Type type, CallbackInfoReturnable<Property> cir) {
        ordinatio$track(category, key, defaultValue == null ? null : new String[] { defaultValue },
                type, cir.getReturnValue());
    }

    @Inject(method = "get(Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;Ljava/lang/String;Lnet/minecraftforge/common/Property$Type;)Lnet/minecraftforge/common/Property;", at = @At("RETURN"))
    private void ordinatio$trackList(String category, String key, String[] defaults, String comment,
                                     Property.Type type, CallbackInfoReturnable<Property> cir) {
        ordinatio$track(category, key, defaults, type, cir.getReturnValue());
    }

    @Inject(method = "getBlockInternal", at = @At("RETURN"))
    private void ordinatio$markBlockId(String category, String key, int defaultId, String comment,
                                          int lower, int upper, CallbackInfoReturnable<Property> cir) {
        ordinatio$markRegistryId(category, key, defaultId, RegistryId.FORGE_BLOCK);
    }

    @Inject(method = "getItem(Ljava/lang/String;Ljava/lang/String;ILjava/lang/String;)Lnet/minecraftforge/common/Property;", at = @At("RETURN"))
    private void ordinatio$markItemId(String category, String key, int defaultId, String comment,
                                         CallbackInfoReturnable<Property> cir) {
        ordinatio$markRegistryId(category, key, defaultId, RegistryId.FORGE_ITEM);
    }

    private void ordinatio$track(String category, String key, String[] defaults, Property.Type type,
                                 Property property) {
        ConfigIndex.trackEntry((Configuration) (Object) this, ordinatio$category(category), key,
                defaults, type, property);
    }

    private void ordinatio$markRegistryId(String category, String key, int defaultId, RegistryId registryId) {
        ConfigIndex.markRegistryId((Configuration) (Object) this, ordinatio$category(category), key,
                defaultId, registryId);
    }

    private String ordinatio$category(String category) {
        return caseSensitiveCustomCategories ? category : category.toLowerCase(Locale.ENGLISH);
    }
}
