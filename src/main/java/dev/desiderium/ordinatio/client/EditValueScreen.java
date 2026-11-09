package dev.desiderium.ordinatio.client;

import dev.desiderium.ordinatio.config.ConfigDocument.Value;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraftforge.common.Property;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static dev.desiderium.ordinatio.client.ConfigScreens.tr;

final class EditValueScreen extends GuiScreen {
    private final GuiScreen parent;
    private final Value value;
    private final List<String> draft;
    private final String manualInitial;
    private final ValueChoices.Selection manualSelection;
    private GuiTextField field;
    private GuiButton apply;
    private GuiButton previous;
    private GuiButton next;
    private GuiButton remove;
    private GuiButton choose;
    private int index;

    EditValueScreen(GuiScreen parent, Value value) {
        this(parent, value, null, null);
    }

    EditValueScreen(GuiScreen parent, Value value, String current, ValueChoices.Selection selection) {
        this.parent = parent;
        this.value = value;
        this.manualInitial = current;
        this.manualSelection = selection;
        this.draft = new ArrayList<String>(Arrays.asList(selection == null ? value.get() : new String[] { current }));
    }

    private boolean editingList() {
        return value.list && manualSelection == null;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        int center = width / 2;
        int y = height / 2;
        if (manualSelection != null || value.allowedValues.isEmpty()) {
            field = new GuiTextField(fontRenderer, center - 120, y - 20, 240, 20);
        } else {
            choose = new GuiButton(6, center - 120, y - 20, 240, 20, "");
            buttonList.add(choose);
        }
        apply = new GuiButton(0, center - 102, height - 30, 100, 20, tr("apply"));
        buttonList.add(apply);
        buttonList.add(new GuiButton(1, center + 2, height - 30, 100, 20, tr("cancel")));
        if (editingList()) {
            previous = new GuiButton(2, center - 120, y + 8, 28, 20, "<");
            next = new GuiButton(3, center + 92, y + 8, 28, 20, ">");
            remove = new GuiButton(5, center + 4, y + 8, 82, 20, tr("remove"));
            buttonList.add(previous);
            buttonList.add(next);
            buttonList.add(new GuiButton(4, center - 86, y + 8, 82, 20, tr("add")));
            buttonList.add(remove);
        }
        showItem();
    }

    private void showItem() {
        boolean available = !draft.isEmpty();
        if (field != null) {
            field.setVisible(available);
            field.setFocused(available);
            if (available) {
                String text = draft.get(index);
                field.setMaxStringLength(Math.max(32767, text.length()));
                field.setText(text);
            }
        } else {
            choose.drawButton = available;
            if (available) {
                choose.displayString = fontRenderer.trimStringToWidth(ValueChoices.label(draft.get(index)), 215)
                        + " >";
            }
        }
        updateButtons();
    }

    private void updateButtons() {
        apply.enabled = true;
        for (String text : draft) {
            if (!value.isRepresentable(text)) {
                apply.enabled = false;
                break;
            }
        }
        if (editingList()) {
            previous.enabled = index > 0;
            next.enabled = index + 1 < draft.size();
            remove.enabled = !draft.isEmpty();
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case 0:
                apply();
                return;
            case 1:
                mc.displayGuiScreen(parent);
                return;
            case 2:
                index--;
                break;
            case 3:
                index++;
                break;
            case 4:
                String initial = value.type == Property.Type.BOOLEAN ? "false" : "";
                draft.add(value.allowedValues.isEmpty() ? initial : value.allowedValues.get(0));
                index = draft.size() - 1;
                break;
            case 5:
                draft.remove(index);
                index = Math.max(0, Math.min(index, draft.size() - 1));
                break;
            case 6:
                mc.displayGuiScreen(new ValueChoices(this, value, draft.get(index), new ValueChoices.Selection() {
                    @Override
                    public void select(String option) {
                        draft.set(index, option);
                    }
                }));
                return;
            default:
                return;
        }
        showItem();
    }

    private void apply() {
        if (manualSelection == null) {
            // List items can contain an existing unlisted value or one confirmed by the manual editor.
            value.setAllowingUnlisted(draft.toArray(new String[draft.size()]));
            mc.displayGuiScreen(parent);
            return;
        }

        final String text = draft.get(0);
        if (!text.equals(manualInitial) && value.isUnlisted(text)) {
            mc.displayGuiScreen(new ConfirmActionScreen(this, tr("unlistedValueTitle"),
                    tr("unlistedValueWarning"), tr("continue"), new Runnable() {
                        @Override
                        public void run() {
                            manualSelection.select(text);
                        }
                    }));
        } else {
            manualSelection.select(text);
        }
    }

    @Override
    protected void keyTyped(char character, int key) {
        if (key == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parent);
        } else if (key == Keyboard.KEY_RETURN && apply.enabled) {
            actionPerformed(apply);
        } else if (field != null && !draft.isEmpty() && field.textboxKeyTyped(character, key)) {
            draft.set(index, field.getText());
            updateButtons();
        }
    }

    @Override
    protected void mouseClicked(int x, int y, int button) {
        if (field != null && !draft.isEmpty()) field.mouseClicked(x, y, button);
        super.mouseClicked(x, y, button);
    }

    @Override
    public void updateScreen() {
        if (field != null) field.updateCursorCounter();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTick) {
        drawDefaultBackground();
        drawCenteredString(fontRenderer, fontRenderer.trimStringToWidth(value.category + " / " + value.key,
                width - 16), width / 2, 14, 0xFFFFFF);
        if (editingList()) {
            String position = draft.isEmpty() ? tr("emptyList") : (index + 1) + " / " + draft.size();
            drawCenteredString(fontRenderer, position, width / 2, height / 2 - 34, 0xCCCCCC);
        }
        if (field != null) field.drawTextBox();
        if (!apply.enabled) {
            drawCenteredString(fontRenderer, tr("invalid"), width / 2, height - 45, 0xFF7777);
        }
        super.drawScreen(mouseX, mouseY, partialTick);
    }
}
