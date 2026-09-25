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
 * Sin fondo gris/negro por defecto: solo la fila seleccionada se marca
 * (verde), igual criterio que ItemIconButton.
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
