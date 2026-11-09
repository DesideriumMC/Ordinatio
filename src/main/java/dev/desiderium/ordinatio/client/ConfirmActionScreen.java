package dev.desiderium.ordinatio.client;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiYesNo;
import org.lwjgl.input.Keyboard;

import static dev.desiderium.ordinatio.client.ConfigScreens.tr;

final class ConfirmActionScreen extends GuiYesNo {
    private final String message;
    private final Runnable action;

    ConfirmActionScreen(GuiScreen parent, String title, String message, String proceed, Runnable action) {
        super(parent, title, "", proceed, tr("back"), 0);
        this.message = message;
        this.action = action;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) action.run();
        else mc.displayGuiScreen(parentScreen);
    }

    @Override
    protected void keyTyped(char character, int key) {
        if (key == Keyboard.KEY_ESCAPE) mc.displayGuiScreen(parentScreen);
        else super.keyTyped(character, key);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTick) {
        super.drawScreen(mouseX, mouseY, partialTick);
        int y = 86;
        for (Object line : fontRenderer.listFormattedStringToWidth(message, width - 24)) {
            drawCenteredString(fontRenderer, (String) line, width / 2, y, 0xFFFFFF);
            y += 10;
        }
    }
}
