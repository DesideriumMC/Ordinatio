package dev.desiderium.ordinatio.client;

import dev.desiderium.ordinatio.config.ConfigDocument;
import dev.desiderium.ordinatio.config.ConfigIndex.ConfigFile;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

import java.io.IOException;
import java.util.List;

public final class ConfigScreens {
    private ConfigScreens() {
    }

    public static GuiScreen open(GuiScreen parent, String modName, List<ConfigFile> files) {
        if (files.size() == 1) return openFile(parent, files.get(0));
        return new FileList(parent, modName, files);
    }

    private static GuiScreen openFile(GuiScreen parent, ConfigFile file) {
        try {
            return new ConfigScreen(parent, file.getName(), new ConfigDocument(file));
        } catch (IOException exception) {
            return new FileError(parent, file.getName(), exception.getMessage());
        }
    }

    static String tr(String key) {
        return I18n.getString("ordinatio." + key);
    }

    private static final class FileList extends GuiScreen {
        private static final int TOP = 28;
        private static final int ROW_HEIGHT = 24;
        private static final int FIRST_FILE = 3;

        private final GuiScreen parent;
        private final String title;
        private final List<ConfigFile> files;
        private int offset;
        private int rows;
        private int listWidth;

        private FileList(GuiScreen parent, String title, List<ConfigFile> files) {
            this.parent = parent;
            this.title = title;
            this.files = files;
        }

        @Override
        public void initGui() {
            buttonList.clear();
            rows = Math.max(1, (height - 70) / ROW_HEIGHT);
            listWidth = Math.min(420, width - 16);
            offset = Math.min(offset, Math.max(0, files.size() - rows));
            for (int row = 0; row < rows && offset + row < files.size(); row++) {
                ConfigFile file = files.get(offset + row);
                buttonList.add(new GuiButton(FIRST_FILE + row, (width - listWidth) / 2,
                        TOP + row * ROW_HEIGHT, listWidth, 20, visibleLabel(file)));
            }
            GuiButton previous = new GuiButton(1, width / 2 - 130, height - 30, 32, 20, "<");
            GuiButton next = new GuiButton(2, width / 2 + 98, height - 30, 32, 20, ">");
            previous.enabled = offset > 0;
            next.enabled = offset + rows < files.size();
            buttonList.add(previous);
            buttonList.add(next);
            buttonList.add(new GuiButton(0, width / 2 - 75, height - 30, 150, 20, tr("back")));
        }

        @Override
        protected void actionPerformed(GuiButton button) {
            if (button.id == 0) {
                mc.displayGuiScreen(parent);
            } else if (button.id == 1 || button.id == 2) {
                offset = Math.max(0, offset + (button.id == 1 ? -rows : rows));
                initGui();
            } else {
                mc.displayGuiScreen(openFile(this, files.get(offset + button.id - FIRST_FILE)));
            }
        }

        @Override
        protected void keyTyped(char character, int key) {
            if (key == 1) mc.displayGuiScreen(parent);
        }

        @Override
        public void drawScreen(int mouseX, int mouseY, float partialTick) {
            drawDefaultBackground();
            drawCenteredString(fontRenderer, title, width / 2, 12, 0xFFFFFF);
            super.drawScreen(mouseX, mouseY, partialTick);
            for (Object item : buttonList) {
                GuiButton button = (GuiButton) item;
                if (button.id < FIRST_FILE || mouseX < button.xPosition || mouseX >= button.xPosition + listWidth
                        || mouseY < button.yPosition || mouseY >= button.yPosition + 20) continue;

                ConfigFile file = files.get(offset + button.id - FIRST_FILE);
                String fullLabel = file.getRelativePath() + ownerLabel(file);
                if (!button.displayString.equals(fullLabel)) drawFullLabel(fullLabel, mouseY);
                break;
            }
        }

        private String ownerLabel(ConfigFile file) {
            return "  [" + file.getModId() + "]";
        }

        private String visibleLabel(ConfigFile file) {
            String path = file.getRelativePath();
            String owner = ownerLabel(file);
            String fullLabel = path + owner;
            int available = listWidth - 10;
            if (fontRenderer.getStringWidth(fullLabel) <= available) return fullLabel;

            String ellipsis = "...";
            int remaining = available - fontRenderer.getStringWidth(ellipsis + owner);
            if (remaining <= 0) {
                return fontRenderer.trimStringToWidth(fullLabel, available - fontRenderer.getStringWidth(ellipsis))
                        + ellipsis;
            }
            return fontRenderer.trimStringToWidth(path, remaining) + ellipsis + owner;
        }

        private void drawFullLabel(String label, int mouseY) {
            List<?> lines = fontRenderer.listFormattedStringToWidth(label, listWidth - 10);
            int count = Math.min(lines.size(), (height - 12) / 10);
            int x = (width - listWidth) / 2;
            int y = Math.max(4, Math.min(mouseY + 14, height - count * 10 - 8));
            drawGradientRect(x, y - 4, x + listWidth, y + count * 10 + 2, 0xF0100010, 0xF0100010);
            for (int index = 0; index < count; index++) {
                drawString(fontRenderer, (String) lines.get(index), x + 5, y + index * 10, 0xFFFFFF);
            }
        }
    }

    private static final class FileError extends GuiScreen {
        private final GuiScreen parent;
        private final String file;
        private final String message;

        private FileError(GuiScreen parent, String file, String message) {
            this.parent = parent;
            this.file = file;
            this.message = message;
        }

        @Override
        public void initGui() {
            buttonList.add(new GuiButton(0, width / 2 - 75, height - 30, 150, 20, tr("back")));
        }

        @Override
        protected void actionPerformed(GuiButton button) {
            mc.displayGuiScreen(parent);
        }

        @Override
        protected void keyTyped(char character, int key) {
            if (key == 1) mc.displayGuiScreen(parent);
        }

        @Override
        public void drawScreen(int mouseX, int mouseY, float partialTick) {
            drawDefaultBackground();
            drawCenteredString(fontRenderer, tr("openFailed"), width / 2, 20, 0xFF7777);
            drawCenteredString(fontRenderer, file, width / 2, 34, 0xFFFFFF);
            fontRenderer.drawSplitString(message == null ? "" : message, 12, 52, width - 24, 0xDDDDDD);
            super.drawScreen(mouseX, mouseY, partialTick);
        }
    }
}
