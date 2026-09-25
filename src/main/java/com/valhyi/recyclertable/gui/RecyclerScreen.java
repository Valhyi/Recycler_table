package com.valhyi.recyclertable.gui;

import com.valhyi.recyclertable.RecyclerTable;
import com.valhyi.recyclertable.network.RecyclerButtonPayload;
import com.valhyi.recyclertable.network.RecyclerPreferencePayload;
import com.valhyi.recyclertable.recipe.MultiRecipeScanner;
import com.valhyi.recyclertable.recipe.RecyclerPreferences;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.StringWidget;
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
import java.util.Optional;

public class RecyclerScreen extends AbstractContainerScreen<RecyclerMenu> {

    private static final Identifier TEXTURE = RecyclerTable.resLoc("textures/gui/recycler_gui.png");
    private static final Identifier TAG_TEXTURE = RecyclerTable.resLoc("textures/gui/tag_gui.png");

    private static final int TAG_PANEL_GAP = 4;
    private static final int TAG_PANEL_WIDTH = 120;
    private static final int TAG_PANEL_HEIGHT = 166;

    // ES: Layout vertical del panel (offsets relativos a panelY).
    private static final int LABEL_Y = 4;
    private static final int SCROLL_UP_Y = 15;
    private static final int LIST_TOP = 26;
    private static final int ROW_HEIGHT = 18; // filas mas altas: ahora llevan 2 iconos de 16px
    private static final int VISIBLE_ROWS = 4; // 26 + 4*18 = 98
    private static final int SCROLL_DOWN_Y = LIST_TOP + VISIBLE_ROWS * ROW_HEIGHT; // 98
    private static final int DETAIL_LABEL_Y = SCROLL_DOWN_Y + 14; // 112
    private static final int VARIANT_GRID_Y = DETAIL_LABEL_Y + 12; // 124
    private static final int VARIANT_ICON_SIZE = 18;
    private static final int VARIANT_COLS = 6;
    private static final int VARIANT_ROWS = 2; // 124 + 2*18 = 160, entra en 166
    private static final int MAX_VARIANT_SLOTS = VARIANT_COLS * VARIANT_ROWS;

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
    private Button scrollUpButton;
    private Button scrollDownButton;
    private StringWidget infoLabel;
    private StringWidget detailLabel;
    private final ConflictRowButton[] rowButtons = new ConflictRowButton[VISIBLE_ROWS];
    private final ItemIconButton[] variantButtons = new ItemIconButton[MAX_VARIANT_SLOTS];

    private boolean showingTagsPanel = false;

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

        int panelX = this.leftPos + this.imageWidth + TAG_PANEL_GAP;
        int panelY = this.topPos;

        this.scrollUpButton = this.addRenderableWidget(Button.builder(Component.literal("^"), b -> scrollBy(-1))
                .bounds(panelX + 2, panelY + SCROLL_UP_Y, TAG_PANEL_WIDTH - 4, 10)
                .build());

        this.scrollDownButton = this.addRenderableWidget(Button.builder(Component.literal("v"), b -> scrollBy(1))
                .bounds(panelX + 2, panelY + SCROLL_DOWN_Y, TAG_PANEL_WIDTH - 4, 10)
                .build());

        // ES: Filas de conflictos - cada una con icono objetivo + icono de
        // preferencia actual. Tocar la fila la selecciona (no cambia nada
        // por si sola; el cambio real pasa en el grid de variantes de abajo).
        for (int i = 0; i < VISIBLE_ROWS; i++) {
            final int rowOffset = i;
            this.rowButtons[i] = this.addRenderableWidget(new ConflictRowButton(
                    panelX + 2, panelY + LIST_TOP + i * ROW_HEIGHT, TAG_PANEL_WIDTH - 4, ROW_HEIGHT - 1,
                    button -> selectRow(rowOffset)
            ));
        }

        // ES: Grid de iconos de variantes - tocar un icono aplica esa
        // preferencia al instante (reemplaza al viejo boton "Cambiar" + ciclo).
        for (int i = 0; i < MAX_VARIANT_SLOTS; i++) {
            final int variantIndex = i;
            int col = i % VARIANT_COLS;
            int row = i / VARIANT_COLS;
            this.variantButtons[i] = this.addRenderableWidget(new ItemIconButton(
                    panelX + 2 + col * VARIANT_ICON_SIZE, panelY + VARIANT_GRID_Y + row * VARIANT_ICON_SIZE, 16,
                    button -> selectVariant(variantIndex)
            ));
        }

        this.infoLabel = this.addRenderableWidget(new StringWidget(
                panelX + 2, panelY + LABEL_Y, TAG_PANEL_WIDTH - 4, 10, Component.empty(), this.font));
        this.detailLabel = this.addRenderableWidget(new StringWidget(
                panelX + 2, panelY + DETAIL_LABEL_Y, TAG_PANEL_WIDTH - 4, 10, Component.empty(), this.font));

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

        boolean hasItems = !conflictItems.isEmpty();
        if (this.scrollUpButton != null) this.scrollUpButton.visible = showingTagsPanel && hasItems;
        if (this.scrollDownButton != null) this.scrollDownButton.visible = showingTagsPanel && hasItems;

        updateRowButtons();
        updateVariantButtons();
        updateLabels();
    }

    private void updateLabels() {
        if (infoLabel == null) return;

        infoLabel.visible = showingTagsPanel;
        detailLabel.visible = showingTagsPanel && !conflictItems.isEmpty();

        if (!showingTagsPanel) return;

        infoLabel.setMessage(Component.literal("Conflictos: " + conflictItems.size()));

        if (conflictItems.isEmpty()) {
            detailLabel.setMessage(Component.empty());
            return;
        }

        Item selected = conflictItems.get(selectedIndex);
        List<MultiRecipeScanner.RecipeVariant> variants = MultiRecipeScanner.getVariantsFor(selected);
        String name = new ItemStack(selected).getHoverName().getString();
        String text = variants.isEmpty()
                ? name + " (sin variantes)"
                : name + " (" + (selectedVariantIndex + 1) + "/" + variants.size() + ")";
        detailLabel.setMessage(Component.literal(this.font.plainSubstrByWidth(text, TAG_PANEL_WIDTH - 6)));
    }

    private void updateRowButtons() {
        for (int i = 0; i < VISIBLE_ROWS; i++) {
            ConflictRowButton rowButton = this.rowButtons[i];
            if (rowButton == null) continue;

            int itemIndex = scrollOffset + i;
            if (showingTagsPanel && itemIndex < conflictItems.size()) {
                Item item = conflictItems.get(itemIndex);
                ItemStack targetIcon = new ItemStack(item);
                ItemStack prefIcon = getPreferredIcon(item);
                rowButton.setContent(targetIcon, prefIcon, itemIndex == selectedIndex);
                rowButton.visible = true;
            } else {
                rowButton.visible = false;
            }
        }
    }

    private void updateVariantButtons() {
        boolean hasSelection = showingTagsPanel && !conflictItems.isEmpty();
        List<MultiRecipeScanner.RecipeVariant> variants = hasSelection
                ? MultiRecipeScanner.getVariantsFor(conflictItems.get(selectedIndex))
                : List.of();

        for (int i = 0; i < MAX_VARIANT_SLOTS; i++) {
            ItemIconButton variantButton = this.variantButtons[i];
            if (variantButton == null) continue;

            if (hasSelection && i < variants.size()) {
                ItemStack icon = new ItemStack(variants.get(i).ingredientItems().get(0));
                variantButton.setContent(icon, i == selectedVariantIndex);
                variantButton.visible = true;
            } else {
                variantButton.visible = false;
            }
        }
    }

    private void scrollBy(int delta) {
        int maxOffset = Math.max(0, conflictItems.size() - VISIBLE_ROWS);
        scrollOffset = Math.max(0, Math.min(maxOffset, scrollOffset + delta));
        updateRowButtons();
    }

    private void selectRow(int rowOffset) {
        int itemIndex = scrollOffset + rowOffset;
        if (itemIndex >= 0 && itemIndex < conflictItems.size()) {
            selectedIndex = itemIndex;
            loadCurrentPreferenceIndex();
            updateButtonStates();
        }
    }

    /**
     * ES: Se llama al tocar un icono del grid de variantes. Aplica la
     * preferencia de inmediato (manda el paquete al server) y refresca
     * tanto el grid como la fila correspondiente en la lista.
     */
    private void selectVariant(int variantIndex) {
        if (conflictItems.isEmpty()) return;

        Item target = conflictItems.get(selectedIndex);
        List<MultiRecipeScanner.RecipeVariant> variants = MultiRecipeScanner.getVariantsFor(target);
        if (variantIndex < 0 || variantIndex >= variants.size()) return;

        selectedVariantIndex = variantIndex;
        List<Item> chosen = variants.get(variantIndex).ingredientItems();

        if (this.minecraft != null && this.minecraft.player != null) {
            ClientPacketDistributor.sendToServer(new RecyclerPreferencePayload(target, chosen));
        }
        updateButtonStates();
    }

    private void sendButtonPacket(RecyclerButtonPayload.ButtonType type) {
        if (this.minecraft != null && this.minecraft.player != null) {
            ClientPacketDistributor.sendToServer(new RecyclerButtonPayload(this.menu.getBlockPos(), type));
        }
    }

    /**
     * ES: Busca la preferencia guardada para el item seleccionado y ajusta
     * selectedVariantIndex para reflejarla (o 0 = primera variante si no
     * hay preferencia guardada). Lee el server local directo (singleplayer).
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

    /**
     * ES: Calcula que icono mostrar en la fila de la lista como "preferencia
     * actual" para un item cualquiera (no necesariamente el seleccionado).
     * Mismo criterio que loadCurrentPreferenceIndex, pero sin tocar el
     * estado de seleccion (se usa para TODAS las filas visibles, no solo
     * la activa).
     */
    private ItemStack getPreferredIcon(Item target) {
        List<MultiRecipeScanner.RecipeVariant> variants = MultiRecipeScanner.getVariantsFor(target);
        if (variants.isEmpty()) return ItemStack.EMPTY;

        var server = net.minecraft.client.Minecraft.getInstance().getSingleplayerServer();
        if (server != null) {
            RecyclerPreferences prefs = RecyclerPreferences.get(server);
            Optional<List<Item>> preferred = prefs.getPreference(target);
            if (preferred.isPresent()) {
                List<Item> wanted = preferred.get();
                for (MultiRecipeScanner.RecipeVariant variant : variants) {
                    if (variant.ingredientItems().equals(wanted)) {
                        return new ItemStack(variant.ingredientItems().get(0));
                    }
                }
            }
        }

        // ES: Sin preferencia guardada -> primera variante (comportamiento por defecto)
        return new ItemStack(variants.get(0).ingredientItems().get(0));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
        super.extractBackground(guiGraphics, mouseX, mouseY, partialTicks);
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.leftPos, this.topPos, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);

        if (showingTagsPanel) {
            int panelX = this.leftPos + this.imageWidth + TAG_PANEL_GAP;
            int panelY = this.topPos;
            guiGraphics.blit(RenderPipelines.GUI_TEXTURED, TAG_TEXTURE, panelX, panelY, 0.0F, 0.0F, TAG_PANEL_WIDTH, TAG_PANEL_HEIGHT, 256, 256);
        }
    }
}
