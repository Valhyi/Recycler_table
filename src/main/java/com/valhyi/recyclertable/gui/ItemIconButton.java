package com.valhyi.recyclertable.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

/**
 * ES: Boton cuadrado que dibuja un ItemStack en vez de texto. Usado para
 * cada variante disponible en el grid de seleccion del panel de tags:
 * tocar el icono aplica esa preferencia de inmediato (sin boton "Cambiar").
 * Ya no pinta un fondo gris/negro por defecto: solo el slot con la
 * preferencia actual se marca (verde).
 *
 * ES: getDisplayStack() expone el stack actualmente dibujado (por si algo
 * mas lo necesita). El nombre en hover se resuelve con
 * AbstractWidget.setTooltip(Tooltip) en setContent(): es el mecanismo base
 * de Minecraft para tooltips de widgets, el propio Screen lo dibuja solo
 * al detectar el hover, sin depender de metodos renombrados/inciertos de
 * GuiGraphicsExtractor.
 */
public class ItemIconButton extends AbstractButton {
    private ItemStack displayStack = ItemStack.EMPTY;
    private boolean highlighted = false;
    private final Consumer<ItemIconButton> onPress;

    public ItemIconButton(int x, int y, int size, Consumer<ItemIconButton> onPress) {
        super(x, y, size, size, Component.empty());
        this.onPress = onPress;
    }

    public void setContent(ItemStack displayStack, boolean highlighted) {
        this.displayStack = displayStack;
        this.highlighted = highlighted;
        this.setTooltip(displayStack.isEmpty() ? null : Tooltip.create(displayStack.getHoverName()));
    }

    public ItemStack getDisplayStack() {
        return displayStack;
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

        if (highlighted) {
            guiGraphics.fill(x, y, x + w, y + h, 0xFF3C8527);
        }

        if (!displayStack.isEmpty()) {
            guiGraphics.item(displayStack, x + Math.max(0, (w - 16) / 2), y + Math.max(0, (h - 16) / 2));
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        this.defaultButtonNarrationText(narrationElementOutput);
    }
}
