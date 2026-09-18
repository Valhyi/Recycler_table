package com.valhyi.recyclertable.gui;

import com.valhyi.recyclertable.RecyclerTable;
import com.valhyi.recyclertable.network.RecyclerButtonPayload;
import com.valhyi.recyclertable.network.RecyclerPreferencePayload;
import com.valhyi.recyclertable.recipe.MultiRecipeScanner;
import com.valhyi.recyclertable.recipe.RecyclerPreferences;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class RecyclerScreen extends AbstractContainerScreen<RecyclerMenu> {

    private static final Identifier TEXTURE = RecyclerTable.resLoc("textures/gui/recycler_gui.png");

    // ES: Textura del panel de tags que se dibuja PEGADO al costado derecho
    // del GUI principal (no reemplaza nada, es un panel extra).
    private static final Identifier TAG_TEXTURE = RecyclerTable.resLoc("textures/gui/tag_gui.png");

    // ES: Tamaño y separación del panel de tags respecto al GUI principal.
    private static final int TAG_PANEL_GAP = 4;
    private static final int TAG_PANEL_WIDTH = 80;
    private static final int TAG_PANEL_HEIGHT = 166;

    // ES: Zona de la lista scrolleable dentro del panel (relativo a panelY)
    private static final int LIST_TOP = 16;
    private static final int LIST_BOTTOM = 116;
    private static final int ROW_HEIGHT = 12;
    private static final int VISIBLE_ROWS = (LIST_BOTTOM - LIST_TOP) / ROW_HEIGHT;

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

    private static final WidgetSprites CONFIG_SPRITES = new WidgetSprites(
            RecyclerTable.resLoc("widget/config_button"),
            RecyclerTable.resLoc("widget/config_button_highlighted")
    );

    private ImageButton playIdleButton;
    private ImageButton playActiveButton;
    private ImageButton autoOffButton;
    private ImageButton autoOnButton;
    private ImageButton configButton;
    private Button cycleVariantButton;

    // ES: true cuando el panel de tags está expandido al costado
    private boolean showingTagsPanel = false;

    // ES: Items con 2+ recetas de crafting distintas (ver MultiRecipeScanner).
    // Se carga una sola vez al abrir la pantalla, leyendo el mapa directo
    // (funciona en singleplayer/LAN; server dedicado necesita sync por red,
    // pendiente para más adelante).
    private List<Item> conflictItems = new ArrayList<>();
    private int scrollOffset = 0;
    private int selectedIndex = 0;
    private int selectedVariantIndex = 0;

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

        this.configButton = this.addRenderableWidget(new ImageButton(
                this.leftPos + this.imageWidth - 20, this.topPos + 4, 14, 14, CONFIG_SPRITES,
                button -> this.showingTagsPanel = !this.showingTagsPanel,
                Component.translatable("gui.recyclertable.config_button")
        ));

        // ES: Botón de crafteo genérico (sin sprite custom todavía; función
        // primero). Cicla la variante seleccionada del item resaltado en la lista.
        int panelX = this.leftPos + this.imageWidth + TAG_PANEL_GAP;
        int panelY = this.topPos;
        this.cycleVariantButton = this.addRenderableWidget(Button.builder(
                        Component.literal("Cambiar receta"),
                        button -> cycleVariant())
                .bounds(panelX + 2, panelY + 122, TAG_PANEL_WIDTH - 4, 16)
                .build());

        // ES: Lectura directa del escaneo (singleplayer). Orden estable por
        // registry name para que la lista no cambie de orden entre aperturas.
        this.conflictItems = new ArrayList<>(MultiRecipeScanner.getMultiRecipeItems().keySet());
        this.conflictItems.sort(Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString()));
        loadCurrentPreferenceIndex();

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

        if (this.playIdleButton != null) this.playIdleButton.visible = !processing;
        if (this.playActiveButton != null) this.playActiveButton.visible = processing;
        if (this.autoOffButton != null) this.autoOffButton.visible = !autoActive;
        if (this.autoOnButton != null) this.autoOnButton.visible = autoActive;
        if (this.cycleVariantButton != null) this.cycleVariantButton.visible = showingTagsPanel && !conflictItems.isEmpty();
    }

    private void sendButtonPacket(RecyclerButtonPayload.ButtonType type) {
        if (this.minecraft != null && this.minecraft.player != null) {
            ClientPacketDistributor.sendToServer(new RecyclerButtonPayload(this.menu.getBlockPos(), type));
        }
    }

    /**
     * ES: Busca si ya hay una preferencia guardada para el item resaltado y
     * ajusta selectedVariantIndex para reflejarla. Lee el server local
     * directo (ver nota de la clase). Si no hay preferencia, o no hay server
     * local (dedicado), queda en 0 (primera variante = comportamiento actual).
     */
    private void loadCurrentPreferenceIndex() {
        selectedVariantIndex = 0;
        if (conflictItems.isEmpty()) return;

        Item target = conflictItems.get(selectedIndex);
        List<MultiRecipeScanner.RecipeVariant> variants = MultiRecipeScanner.getVariantsFor(target);

        var server = net.minecraft.client.Minecraft.getInstance().getSingleplayerServer();
        if (server == null) return;

        RecyclerPreferences prefs = RecyclerPreferences.get(server);
        prefs.getPreference(target).ifPresent(signature -> {
            for (int i = 0; i < variants.size(); i++) {
                if (variants.get(i).ingredientItems().equals(signature)) {
                    selectedVariantIndex = i;
                    return;
                }
            }
        });
    }

    private void cycleVariant() {
        if (conflictItems.isEmpty()) return;

        Item target = conflictItems.get(selectedIndex);
        List<MultiRecipeScanner.RecipeVariant> variants = MultiRecipeScanner.getVariantsFor(target);
        if (variants.isEmpty()) return;

        selectedVariantIndex = (selectedVariantIndex + 1) % variants.size();
        List<Item> chosen = variants.get(selectedVariantIndex).ingredientItems();

        if (this.minecraft != null && this.minecraft.player != null) {
            ClientPacketDistributor.sendToServer(new RecyclerPreferencePayload(target, chosen));
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (showingTagsPanel && isOverList(mouseX, mouseY)) {
            int maxOffset = Math.max(0, conflictItems.size() - VISIBLE_ROWS);
            scrollOffset -= (int) Math.signum(scrollY);
            scrollOffset = Math.max(0, Math.min(maxOffset, scrollOffset));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (showingTagsPanel && isOverList(mouseX, mouseY)) {
            int panelY = this.topPos;
            int row = scrollOffset + (int) ((mouseY - (panelY + LIST_TOP)) / ROW_HEIGHT);
            if (row >= 0 && row < conflictItems.size()) {
                selectedIndex = row;
                loadCurrentPreferenceIndex();
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isOverList(double mouseX, double mouseY) {
        int panelX = this.leftPos + this.imageWidth + TAG_PANEL_GAP;
        int panelY = this.topPos;
        return mouseX >= panelX && mouseX <= panelX + TAG_PANEL_WIDTH
                && mouseY >= panelY + LIST_TOP && mouseY <= panelY + LIST_BOTTOM;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
        super.extractBackground(guiGraphics, mouseX, mouseY, partialTicks);
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.leftPos, this.topPos, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);

        if (showingTagsPanel) {
            int panelX = this.leftPos + this.imageWidth + TAG_PANEL_GAP;
            int panelY = this.topPos;
            guiGraphics.blit(RenderPipelines.GUI_TEXTURED, TAG_TEXTURE, panelX, panelY, 0.0F, 0.0F, TAG_PANEL_WIDTH, TAG_PANEL_HEIGHT, 256, 256);
            renderTagPanelContent(guiGraphics, panelX, panelY);
        }
    }

    private void renderTagPanelContent(GuiGraphicsExtractor guiGraphics, int panelX, int panelY) {
        guiGraphics.drawString(this.font, "Conflictos: " + conflictItems.size(), panelX + 2, panelY + 4, 0x404040, false);

        if (conflictItems.isEmpty()) {
            guiGraphics.drawString(this.font, "(ninguno)", panelX + 2, panelY + LIST_TOP + 2, 0x808080, false);
            return;
        }

        // ES: Lista scrolleable: solo se dibujan las filas visibles según scrollOffset.
        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int itemIndex = scrollOffset + i;
            if (itemIndex >= conflictItems.size()) break;

            int rowY = panelY + LIST_TOP + i * ROW_HEIGHT;
            Item item = conflictItems.get(itemIndex);
            String name = new ItemStack(item).getHoverName().getString();
            String truncated = this.font.plainSubstrByWidth(name, TAG_PANEL_WIDTH - 6);

            if (itemIndex == selectedIndex) {
                guiGraphics.fill(panelX + 1, rowY - 1, panelX + TAG_PANEL_WIDTH - 1, rowY + ROW_HEIGHT - 2, 0x552277FF);
            }

            guiGraphics.drawString(this.font, truncated, panelX + 3, rowY, 0x202020, false);
        }

        // ES: Detalle del item resaltado + variante elegida, debajo de la lista.
        Item selected = conflictItems.get(selectedIndex);
        List<MultiRecipeScanner.RecipeVariant> variants = MultiRecipeScanner.getVariantsFor(selected);
        if (selectedVariantIndex < variants.size()) {
            List<Item> ingredients = variants.get(selectedVariantIndex).ingredientItems();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < ingredients.size(); i++) {
                if (i > 0) sb.append("+");
                sb.append(new ItemStack(ingredients.get(i)).getHoverName().getString());
            }
            String variantText = this.font.plainSubstrByWidth(sb.toString(), TAG_PANEL_WIDTH - 6);
            guiGraphics.drawString(this.font, variantText, panelX + 2, panelY + LIST_BOTTOM + 2, 0x404040, false);
            guiGraphics.drawString(this.font, "Opcion " + (selectedVariantIndex + 1) + "/" + variants.size(),
                    panelX + 2, panelY + LIST_BOTTOM + 12, 0x808080, false);
        }
    }
}
