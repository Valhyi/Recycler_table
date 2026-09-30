package com.valhyi.recyclertable.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

/**
 * ES: Fila de la lista de conflictos. Dibuja el icono del item objetivo y,
 * al lado, el icono de la preferencia actualmente elegida. Toda la fila es
 * clickeable y selecciona ese item para mostrar sus variantes abajo.
 * Solo la fila seleccionada se marca (verde).
 *
 * ES: Ya NO define su propio tooltip. RecyclerScreen dibuja el tooltip
 * vanilla del icono bajo el cursor usando getTargetIcon() (izquierda) o
 * getPreferenceIcon() (derecha, x+19).
 */
public class ConflictRowButton extends AbstractButton {
    private ItemStack targetIcon = ItemStack.EMPTY;
    private ItemStack preferenceIcon = ItemStack.EMPTY;
    private boolean selected = false;
    private final Consumer<ConflictRowButton> onPress;

    public ConflictRowButton(int x, int y, int width, int height, Consumer<ConflictRowButton> onPress) {
        super(x, y, width, height, Component.empty());
        this.onPress = onPress;
    }

    public void setContent(ItemStack targetIcon, ItemStack preferenceIcon, boolean selected) {
        this.targetIcon = targetIcon;
        this.preferenceIcon = preferenceIcon;
        this.selected = selected;
    }

    public ItemStack getTargetIcon() {
        return targetIcon;
    }

    public ItemStack getPreferenceIcon() {
        return preferenceIcon;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        if (onPress != null) {
            onPress.accept(this);
        }
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        int x = this.getX();
        int y = this.getY();
        int w = this.getWidth();
        int h = this.getHeight();

        if (selected) {
            guiGraphics.fill(x, y, x + w, y + h, 0xFF3C6E27);
        }

        if (!targetIcon.isEmpty()) {
            guiGraphics.item(targetIcon, x + 1, y + 1);
        }
        if (!preferenceIcon.isEmpty()) {
            guiGraphics.item(preferenceIcon, x + 19, y + 1);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        this.defaultButtonNarrationText(narrationElementOutput);
    }
}
