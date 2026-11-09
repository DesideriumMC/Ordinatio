package dev.desiderium.ordinatio.client;

import dev.desiderium.ordinatio.config.ConfigDocument;
import dev.desiderium.ordinatio.config.ConfigDocument.Value;
import dev.desiderium.ordinatio.config.ConfigIndex.RegistryId;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.common.Property;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

import static dev.desiderium.ordinatio.client.ConfigScreens.tr;

final class ConfigScreen extends GuiScreen {
    private static final int TOP = 54;
    private static final int ROW_HEIGHT = 24;
    private static final int SCROLLBAR_WIDTH = 8;
    private static final int SCROLLBAR_GUTTER = 12;
    private static final int FIRST_ENTRY = 10;
    private static final int CONFIRM_DISCARD = 0;

    private final GuiScreen parent;
    private final String title;
    private final ConfigDocument document;
    private final List<Value> matches = new ArrayList<Value>();
    private GuiTextField search;
    private int offset;
    private int rows;
    private int scrollGrabOffset = -1;
    private String query = "";
    private String error;

    ConfigScreen(GuiScreen parent, String title, ConfigDocument document) {
        this.parent = parent;
        this.title = title;
        this.document = document;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        search = new GuiTextField(fontRenderer, width / 2 - 90, 24, 180, 18);
        search.setMaxStringLength(128);
        search.setText(query);
        rows = Math.max(1, (height - TOP - 54) / ROW_HEIGHT);
        scrollGrabOffset = -1;
        refreshMatches();
    }

    private void refreshMatches() {
        matches.clear();
        String needle = query.toLowerCase(Locale.ROOT);
        for (Value value : document.values) {
            String label = value.category + " / " + value.key;
            if (label.toLowerCase(Locale.ROOT).contains(needle)
                    || value.comment != null && value.comment.toLowerCase(Locale.ROOT).contains(needle)) {
                matches.add(value);
            }
        }
        offset = Math.min(offset, maxOffset());
        refreshRows();
    }

    private void refreshRows() {
        buttonList.clear();
        buttonList.add(new GuiButton(0, width / 2 - 92, height - 28, 90, 20, tr("done")));
        buttonList.add(new GuiButton(1, width / 2 + 2, height - 28, 90, 20, tr("cancel")));
        GuiButton previous = new GuiButton(2, 8, height - 28, 26, 20, "<");
        GuiButton next = new GuiButton(3, width - 34, height - 28, 26, 20, ">");
        previous.enabled = offset > 0;
        next.enabled = offset < maxOffset();
        buttonList.add(previous);
        buttonList.add(next);
        int right = listRight();
        for (int row = 0; row < rows && offset + row < matches.size(); row++) {
            Value value = matches.get(offset + row);
            int y = TOP + row * ROW_HEIGHT;
            int id = FIRST_ENTRY + row * 2;
            GuiButton edit = new GuiButton(id, right - 134, y, 88, 20, summarize(value));
            GuiButton reset = new GuiButton(id + 1, right - 42, y, 34, 20, tr("reset"));
            edit.enabled = value.editable;
            reset.enabled = value.hasDefault();
            buttonList.add(edit);
            buttonList.add(reset);
        }
    }

    private String summarize(Value value) {
        String[] current = value.get();
        String text = value.list ? "[" + current.length + "] " + tr("items") : current[0];
        return fontRenderer.trimStringToWidth(text, 80);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case 0:
                confirmSave();
                return;
            case 1:
                discard();
                return;
            case 2:
                scroll(-rows);
                return;
            case 3:
                scroll(rows);
                return;
            default:
                break;
        }

        int entry = button.id - FIRST_ENTRY;
        final Value value = matches.get(offset + entry / 2);
        boolean reset = entry % 2 == 1;
        if (reset && value.hasUnlistedDefault()) {
            mc.displayGuiScreen(new ConfirmActionScreen(this, tr("confirmChange"),
                    tr("unlistedDefaultWarning"), tr("continue"),
                    new Runnable() {
                        @Override
                        public void run() {
                            edit(value, true);
                        }
                    }));
        } else {
            edit(value, reset);
        }
    }

    private void edit(final Value value, boolean reset) {
        if (reset) {
            value.restoreDefault();
        } else if (!value.list && !value.allowedValues.isEmpty()) {
            mc.displayGuiScreen(new ValueChoices(this, value, value.get()[0], new ValueChoices.Selection() {
                @Override
                public void select(String option) {
                    value.setAllowingUnlisted(new String[] { option });
                }
            }));
            return;
        } else if (!value.list && value.type == Property.Type.BOOLEAN) {
            value.set(new String[] { Boolean.toString(!Boolean.parseBoolean(value.get()[0])) });
        } else {
            mc.displayGuiScreen(new EditValueScreen(this, value));
            return;
        }
        if (mc.currentScreen == this) refreshRows();
        else mc.displayGuiScreen(this);
    }

    private void discard() {
        if (document.isDirty()) {
            mc.displayGuiScreen(new GuiYesNo(this, tr("discardTitle"), tr("discardPrompt"), CONFIRM_DISCARD));
        } else {
            mc.displayGuiScreen(parent);
        }
    }

    private static String registryIdReason(Value value) {
        RegistryId id = value.registryId;
        String reasonKey = "ordinatio.registryIdReason." + id.source.name().toLowerCase(Locale.ROOT);
        return I18n.getStringParams(reasonKey, registryIdKind(id.kind));
    }

    private static String registryIdKind(RegistryId.Kind kind) {
        return tr("registryIdKind." + kind.name().toLowerCase(Locale.ROOT));
    }

    private void confirmSave() {
        EnumSet<RegistryId.Kind> kinds = document.changedRegistryIdKinds();
        if (kinds.isEmpty()) {
            save();
            return;
        }
        StringBuilder labels = new StringBuilder();
        for (RegistryId.Kind kind : kinds) {
            if (labels.length() > 0) labels.append(", ");
            labels.append(registryIdKind(kind));
        }
        String summary = I18n.getStringParams("ordinatio.registryIdSummary", labels.toString());
        mc.displayGuiScreen(new ConfirmActionScreen(this, tr("registryIdTitle"),
                summary + "\n" + tr("registryIdWarning"), tr("saveAnyway"), new Runnable() {
                    @Override
                    public void run() {
                        save();
                    }
                }));
    }

    private void save() {
        try {
            document.save();
            mc.displayGuiScreen(parent);
        } catch (IOException exception) {
            error = exception.getMessage();
            if (mc.currentScreen != this) mc.displayGuiScreen(this);
        }
    }

    @Override
    public void confirmClicked(boolean confirmed, int id) {
        mc.displayGuiScreen(id == CONFIRM_DISCARD && confirmed ? parent : this);
    }

    private int maxOffset() {
        return Math.max(0, matches.size() - rows);
    }

    private int listRight() {
        return width - (maxOffset() > 0 ? SCROLLBAR_GUTTER : 0);
    }

    private int trackLeft() {
        return width - SCROLLBAR_WIDTH - 4;
    }

    private int trackHeight() {
        return rows * ROW_HEIGHT - 4;
    }

    private int thumbHeight() {
        return Math.max(12, (int) ((long) trackHeight() * rows / matches.size()));
    }

    private int thumbTop() {
        return TOP + Math.round((trackHeight() - thumbHeight()) * (offset / (float) maxOffset()));
    }

    private void setOffset(int requestedOffset) {
        int nextOffset = Math.max(0, Math.min(requestedOffset, maxOffset()));
        if (offset != nextOffset) {
            offset = nextOffset;
            refreshRows();
        }
    }

    private void scroll(int delta) {
        setOffset(offset + delta);
    }

    private void dragScrollbar(int mouseY) {
        int travel = trackHeight() - thumbHeight();
        int position = Math.max(0, Math.min(mouseY - TOP - scrollGrabOffset, travel));
        setOffset(Math.round(maxOffset() * (position / (float) travel)));
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) scroll(wheel > 0 ? -1 : 1);
    }

    @Override
    protected void mouseClicked(int x, int y, int button) {
        search.mouseClicked(x, y, button);
        int left = trackLeft();
        if (button == 0 && maxOffset() > 0 && x >= left && x < left + SCROLLBAR_WIDTH
                && y >= TOP && y < TOP + trackHeight()) {
            int thumb = thumbTop();
            int height = thumbHeight();
            if (y >= thumb && y < thumb + height) {
                scrollGrabOffset = y - thumb;
            } else {
                scrollGrabOffset = height / 2;
                dragScrollbar(y);
            }
            return;
        }
        super.mouseClicked(x, y, button);
    }

    @Override
    protected void mouseClickMove(int x, int y, int button, long elapsed) {
        if (button == 0 && scrollGrabOffset >= 0) dragScrollbar(y);
        else super.mouseClickMove(x, y, button, elapsed);
    }

    @Override
    protected void mouseMovedOrUp(int x, int y, int button) {
        if (button == 0 && scrollGrabOffset >= 0) scrollGrabOffset = -1;
        else super.mouseMovedOrUp(x, y, button);
    }

    @Override
    protected void keyTyped(char character, int key) {
        if (key == Keyboard.KEY_ESCAPE) {
            discard();
        } else if (search.textboxKeyTyped(character, key)) {
            query = search.getText();
            offset = 0;
            scrollGrabOffset = -1;
            refreshMatches();
        }
    }

    @Override
    public void updateScreen() {
        search.updateCursorCounter();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTick) {
        drawDefaultBackground();
        drawCenteredString(fontRenderer, title, width / 2, 10, 0xFFFFFF);
        drawString(fontRenderer, tr("search"), width / 2 - 96 - fontRenderer.getStringWidth(tr("search")),
                29, 0xCCCCCC);
        search.drawTextBox();
        if (matches.isEmpty()) drawCenteredString(fontRenderer, tr("noResults"), width / 2, TOP, 0xCCCCCC);
        Value hovered = null;
        int right = listRight();
        for (int row = 0; row < rows && offset + row < matches.size(); row++) {
            Value value = matches.get(offset + row);
            int y = TOP + row * ROW_HEIGHT;
            String label = value.category + " / " + value.key;
            int color = 0xAAAAAA;
            if (value.editable) color = value.registryId != null ? 0xFFCC66 : 0xFFFFFF;
            drawString(fontRenderer, fontRenderer.trimStringToWidth(label, right - 150), 10, y + 6, color);
            if (mouseX >= 8 && mouseX < right - 138 && mouseY >= y && mouseY < y + 20) hovered = value;
        }
        String status = error == null ? tr("restart") : error;
        drawCenteredString(fontRenderer, fontRenderer.trimStringToWidth(status, width - 16),
                width / 2, height - 43, error == null ? 0xCCCCCC : 0xFF7777);
        super.drawScreen(mouseX, mouseY, partialTick);
        if (maxOffset() > 0) drawScrollbar();
        if (hovered != null && scrollGrabOffset < 0) drawDetails(hovered, mouseY);
    }

    private void drawScrollbar() {
        int left = trackLeft();
        int right = left + SCROLLBAR_WIDTH;
        int top = thumbTop();
        int bottom = top + thumbHeight();
        drawRect(left, TOP, right, TOP + trackHeight(), 0xFF202020);
        drawRect(left, top, right, bottom, scrollGrabOffset < 0 ? 0xFF808080 : 0xFFA0A0A0);
        drawRect(left, top, right - 1, top + 1, 0xFFC0C0C0);
    }

    private void drawDetails(Value value, int mouseY) {
        String details = value.category + " / " + value.key;
        if (value.registryId != null) {
            details += "\n" + EnumChatFormatting.YELLOW + registryIdReason(value)
                    + "\n" + EnumChatFormatting.YELLOW + tr("registryIdWarning") + EnumChatFormatting.RESET;
        }
        if (value.comment != null && !value.comment.isEmpty()) details += "\n" + value.comment;
        if (!value.editable) details += "\n" + tr("readOnly");
        int boxWidth = Math.min(width - 20, 300);
        List<?> lines = fontRenderer.listFormattedStringToWidth(details, boxWidth - 10);
        int lineCount = Math.min(lines.size(), (height - 12) / 10);
        int y = Math.max(4, Math.min(mouseY + 14, height - lineCount * 10 - 8));
        drawGradientRect(8, y - 4, 8 + boxWidth, y + lineCount * 10 + 2, 0xF0100010, 0xF0100010);
        for (int index = 0; index < lineCount; index++) {
            drawString(fontRenderer, (String) lines.get(index), 13, y + index * 10, 0xFFFFFF);
        }
    }
}
