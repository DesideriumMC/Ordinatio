package dev.desiderium.ordinatio.mixin;

import net.minecraftforge.common.ConfigCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ConfigCategory.class, remap = false)
public interface ConfigCategoryAccessor {
    @Accessor("comment")
    String ordinatio$getComment();
}
