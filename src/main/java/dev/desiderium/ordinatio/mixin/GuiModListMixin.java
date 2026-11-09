package dev.desiderium.ordinatio.mixin;

import cpw.mods.fml.client.GuiModList;
import cpw.mods.fml.common.ModContainer;
import dev.desiderium.ordinatio.client.ConfigScreens;
import dev.desiderium.ordinatio.config.ConfigIndex;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = GuiModList.class, remap = false)
public abstract class GuiModListMixin extends GuiScreen {
    @Shadow private ModContainer selectedMod;
    @Unique private GuiButton ordinatio$configButton;

    // FML's overrides use MCP names in development and SRG names after runtime deobfuscation.
    @Inject(method = {"initGui()V", "func_73866_w_()V"}, at = @At("RETURN"))
    private void ordinatio$addConfigButton(CallbackInfo ci) {
        for (Object item : buttonList) {
            GuiButton button = (GuiButton) item;
            if (button.id == 6) {
                button.xPosition -= 42;
                break;
            }
        }
        ordinatio$configButton = new GuiButton(1964, width / 2 + 42, height - 38, 75, 20,
                I18n.getString("ordinatio.config"));
        buttonList.add(ordinatio$configButton);
        ordinatio$updateConfigButton();
    }

    @Inject(method = "selectModIndex", at = @At("RETURN"))
    private void ordinatio$selectMod(int index, CallbackInfo ci) {
        ordinatio$updateConfigButton();
    }

    @Inject(method = {"actionPerformed(Lnet/minecraft/client/gui/GuiButton;)V",
            "func_73875_a(Lnet/minecraft/client/gui/GuiButton;)V"}, at = @At("HEAD"), cancellable = true)
    private void ordinatio$openConfig(GuiButton button, CallbackInfo ci) {
        if (button != ordinatio$configButton) return;
        ci.cancel();
        if (selectedMod == null) return;

        List<ConfigIndex.ConfigFile> files = ConfigIndex.forMod(selectedMod.getModId());
        if (!files.isEmpty()) {
            mc.displayGuiScreen(ConfigScreens.open(this, selectedMod.getName(), files));
        }
    }

    @Unique
    private void ordinatio$updateConfigButton() {
        if (ordinatio$configButton != null) {
            ordinatio$configButton.enabled =
                    selectedMod != null && !ConfigIndex.forMod(selectedMod.getModId()).isEmpty();
        }
    }
}
