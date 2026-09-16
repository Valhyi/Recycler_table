package com.valhyi.recyclertable.gui;

import com.valhyi.recyclertable.RecyclerTable;
import com.valhyi.recyclertable.network.RecyclerButtonPayload;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public class RecyclerScreen extends AbstractContainerScreen<RecyclerMenu> {

    private static final Identifier TEXTURE = RecyclerTable.resLoc("textures/gui/recycler_gui.png");

    // ES: Textura del panel de tags. Por ahora usa las mismas dimensiones
    // (176x166) que el panel principal para no complicar el layout de slots.
    private static final Identifier TAG_TEXTURE = RecyclerTable.resLoc("textures/gui/tag_gui.png");

    private static final WidgetSprites PLAY_SPRITES = new WidgetSprites(
            RecyclerTable.resLoc("widget/play_button"),
            RecyclerTable.resLoc("widget/play_button_highlighted")
    );

    private static final WidgetSprites PLAY_ACTIVE_SPRITES = new WidgetSprites(
            RecyclerTable.resLoc("widget/play_button_highlighted"),
            RecyclerTable.resLoc("widget/play_button_highlighted")
    );

    private static final WidgetSprites AUTO_OFF_SPRITES = new WidgetSprites(
            RecyclerTable.resLoc("widget/auto_button"),
            RecyclerTable.resLoc("widget/auto_button_highlighted")
    );

    private static final WidgetSprites AUTO_ON_SPRITES = new WidgetSprites(
            RecyclerTable.resLoc("widget/auto_button_active"),
            RecyclerTable.resLoc("widget/auto_button_active_highlighted")
    );

    // ES: Sprite provisional del botón de configuración/tags.
    // Ajusta los nombres si tus archivos en assets/.../textures/gui/sprites/widget/
    // se llaman distinto.
    private static final WidgetSprites CONFIG_SPRITES = new WidgetSprites(
            RecyclerTable.resLoc("widget/config_button"),
            RecyclerTable.resLoc("widget/config_button_highlighted")
    );

    private ImageButton playIdleButton;
    private ImageButton playActiveButton;
    private ImageButton autoOffButton;
    private ImageButton autoOnButton;
    private ImageButton configButton;
    private ImageButton backButton;

    // ES: true cuando se está mostrando el panel de tags en vez del panel normal
    private boolean showingTagsPanel = false;

    public RecyclerScreen(RecyclerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, 176, 166);
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = this.imageWidth / 2 - this.font.width(this.title) / 2;

        int playX = this.leftPos + 65;
        int autoX = this.leftPos + 97;
        int buttonY = this.topPos + 55;

        this.playIdleButton = this.addRenderableWidget(new ImageButton(
                playX, buttonY, 14, 14, PLAY_SPRITES,
                button -> sendButtonPacket(RecyclerButtonPayload.ButtonType.PLAY),
                Component.translatable("gui.recyclertable.play_button")
        ));

        this.playActiveButton = this.addRenderableWidget(new ImageButton(
                playX, buttonY, 14, 14, PLAY_ACTIVE_SPRITES,
                button -> {},
                Component.translatable("gui.recyclertable.play_button")
        ));

        this.autoOffButton = this.addRenderableWidget(new ImageButton(
                autoX, buttonY, 14, 14, AUTO_OFF_SPRITES,
                button -> sendButtonPacket(RecyclerButtonPayload.ButtonType.AUTO),
                Component.translatable("gui.recyclertable.auto_button")
        ));

        this.autoOnButton = this.addRenderableWidget(new ImageButton(
                autoX, buttonY, 14, 14, AUTO_ON_SPRITES,
                button -> sendButtonPacket(RecyclerButtonPayload.ButtonType.AUTO),
                Component.translatable("gui.recyclertable.auto_button")
        ));

        // ES: Botón de config, arriba a la derecha del panel
        this.configButton = this.addRenderableWidget(new ImageButton(
                this.leftPos + this.imageWidth - 20, this.topPos + 4, 14, 14, CONFIG_SPRITES,
                button -> setTagsPanelVisible(true),
                Component.translatable("gui.recyclertable.config_button")
        ));

        // ES: Botón para volver, mismo lugar, solo visible en el panel de tags.
        // Reusa los sprites de config por ahora; cámbialos si quieres uno distinto.
        this.backButton = this.addRenderableWidget(new ImageButton(
                this.leftPos + this.imageWidth - 20, this.topPos + 4, 14, 14, CONFIG_SPRITES,
                button -> setTagsPanelVisible(false),
                Component.translatable("gui.recyclertable.config_button")
        ));

        updateButtonStates();
    }

    private void setTagsPanelVisible(boolean visible) {
        this.showingTagsPanel = visible;
        updateButtonStates();
    }

    @Override
    public void containerTick() {
        super.containerTick();
        updateButtonStates();
    }

    private void updateButtonStates() {
        boolean autoActive = this.menu.isAutoActive();
        boolean processing = this.menu.isProcessing();

        // ES: Mientras se muestra el panel de tags, se ocultan los controles
        // del reciclador normal (play/auto/config) y solo se ve el botón de volver.
        if (this.playIdleButton != null) this.playIdleButton.visible = !showingTagsPanel && !processing;
        if (this.playActiveButton != null) this.playActiveButton.visible = !showingTagsPanel && processing;
        if (this.autoOffButton != null) this.autoOffButton.visible = !showingTagsPanel && !autoActive;
        if (this.autoOnButton != null) this.autoOnButton.visible = !showingTagsPanel && autoActive;
        if (this.configButton != null) this.configButton.visible = !showingTagsPanel;
        if (this.backButton != null) this.backButton.visible = showingTagsPanel;
    }

    private void sendButtonPacket(RecyclerButtonPayload.ButtonType type) {
        if (this.minecraft != null && this.minecraft.player != null) {
            ClientPacketDistributor.sendToServer(new RecyclerButtonPayload(this.menu.getBlockPos(), type));
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
        super.extractBackground(guiGraphics, mouseX, mouseY, partialTicks);

        Identifier textureToDraw = showingTagsPanel ? TAG_TEXTURE : TEXTURE;
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, textureToDraw, this.leftPos, this.topPos, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);
    }
}
