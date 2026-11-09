package dev.desiderium.ordinatio.client;

import dev.desiderium.ordinatio.config.ConfigDocument.Value;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import static dev.desiderium.ordinatio.client.ConfigScreens.tr;

final class ValueChoices extends GuiScreen {
    private static final int FIRST_CHOICE = 3;
    private static final int CUSTOM_VALUE = -1;
    private static final int ROW_HEIGHT = 24;
    private final GuiScreen parent;
    private final Value value;
    private final String current;
    private final Selection selection;
    private int offset;
    private int rows;

    ValueChoices(GuiScreen parent, Value value, String current, Selection selection) {
        this.parent = parent;
        this.value = value;
        this.current = current;
        this.selection = selection;
        this.offset = Math.max(0, value.allowedValues.indexOf(current));
    }

    @Override
    public void initGui() {
        rows = Math.max(1, (height - 96) / ROW_HEIGHT);
        showChoices(offset);
    }

    private void showChoices(int requestedOffset) {
        offset = Math.max(0, Math.min(requestedOffset, value.allowedValues.size() - rows));
        buttonList.clear();
        int center = width / 2;
        int choiceWidth = Math.min(300, width - 16);
        for (int row = 0; row < rows && offset + row < value.allowedValues.size(); row++) {
            String option = value.allowedValues.get(offset + row);
            String name = (current.equals(option) ? "> " : "") + label(option);
            buttonList.add(new GuiButton(FIRST_CHOICE + row, center - choiceWidth / 2, 34 + row * ROW_HEIGHT,
                    choiceWidth, 20, fontRenderer.trimStringToWidth(name, choiceWidth - 10)));
        }
        GuiButton previous = new GuiButton(1, center - 130, height - 30, 32, 20, "<");
        GuiButton next = new GuiButton(2, center + 98, height - 30, 32, 20, ">");
        previous.enabled = offset > 0;
        next.enabled = offset + rows < value.allowedValues.size();
        buttonList.add(previous);
        buttonList.add(next);
        buttonList.add(new GuiButton(CUSTOM_VALUE, center - 75, height - 54, 150, 20, tr("customValue")));
        buttonList.add(new GuiButton(0, center - 75, height - 30, 150, 20, tr("back")));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == CUSTOM_VALUE) {
            mc.displayGuiScreen(new EditValueScreen(this, value, current, new Selection() {
                @Override
                public void select(String option) {
                    applyChoice(option);
                }
            }));
            return;
        }
        if (button.id == 1 || button.id == 2) {
            showChoices(offset + (button.id == 1 ? -rows : rows));
            return;
        }
        if (button.id >= FIRST_CHOICE) {
            applyChoice(value.allowedValues.get(offset + button.id - FIRST_CHOICE));
        } else {
            mc.displayGuiScreen(parent);
        }
    }

    private void applyChoice(String option) {
        selection.select(option);
        mc.displayGuiScreen(parent);
    }

    @Override
    protected void keyTyped(char character, int key) {
        if (key == Keyboard.KEY_ESCAPE) mc.displayGuiScreen(parent);
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) showChoices(offset + (wheel > 0 ? -1 : 1));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTick) {
        drawDefaultBackground();
        drawCenteredString(fontRenderer, tr("chooseValue"), width / 2, 8, 0xFFFFFF);
        String title = fontRenderer.trimStringToWidth(value.category + " / " + value.key, width - 16);
        drawCenteredString(fontRenderer, title, width / 2, 20, 0xCCCCCC);
        super.drawScreen(mouseX, mouseY, partialTick);
    }

    static String label(String option) {
        return option.isEmpty() ? tr("emptyValue") : option;
    }

    interface Selection {
        void select(String option);
    }
}
