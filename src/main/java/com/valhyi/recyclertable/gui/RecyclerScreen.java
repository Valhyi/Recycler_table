package com.valhyi.recyclertable.gui;

import com.valhyi.recyclertable.RecyclerTable;
import com.valhyi.recyclertable.network.RecyclerButtonPayload;
import com.valhyi.recyclertable.network.RecyclerGroupPreferencePayload;
import com.valhyi.recyclertable.network.RecyclerPreferencePayload;
import com.valhyi.recyclertable.recipe.MultiRecipeScanner;
import com.valhyi.recyclertable.recipe.RecyclerPreferences;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class RecyclerScreen extends AbstractContainerScreen<RecyclerMenu> {

    private static final Identifier TEXTURE = RecyclerTable.resLoc("textures/gui/recycler_gui.png");
    private static final Identifier TAG_TEXTURE = RecyclerTable.resLoc("textures/gui/tag_gui.png");

    private static final int TAG_PANEL_GAP = 4;
    private static final int TAG_PANEL_WIDTH = 120;
    private static final int TAG_PANEL_HEIGHT = 166;

    private static final int PANEL_PAD_X = 4;
    private static final int PANEL_PAD_TOP = 8;
    private static final int PANEL_PAD_BOTTOM = 6;

    private static final int LIST_COLUMN_WIDTH = 36; // 2 iconos de 16px (item + preferencia)
    private static final int LIST_GRID_GAP = 6;
    private static final int ROW_HEIGHT = 18;
    private static final int VISIBLE_ROWS = 8;

    private static final int DETAIL_LABEL_HEIGHT = 10;
    private static final int DETAIL_GRID_GAP = 4;

    private static final int VARIANT_ICON_SIZE = 18;
    private static final int VARIANT_COLS = 3;
    private static final int VARIANT_ROWS = 7;
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
    private StringWidget detailLabel;
    private final ConflictRowButton[] rowButtons = new ConflictRowButton[VISIBLE_ROWS];
    private final ItemIconButton[] variantButtons = new ItemIconButton[MAX_VARIANT_SLOTS];

    private boolean showingTagsPanel = false;

    /**
     * ES: Una fila de la columna de conflictos. groupKey == null significa
     * "conflicto normal" (una sola firma completa de ingredientes, ej.
     * mossy_cobblestone - comportamiento de siempre, sin cambios). Un
     * groupKey no-nulo significa que esta fila representa UN grupo
     * independiente de un item con 2+ grupos sin material en común (ej.
     * fogata: una fila para "logs", otra para "coals" - ver
     * MultiRecipeScanner.hasUnlinkedGroups).
     */
    private record ConflictRow(Item target, String groupKey) {}

    /**
     * ES: Un tramo del grid de variantes asignado a UN grupo en particular
     * cuando el target seleccionado tiene grupos sin material en común.
     * startRow/allocatedRows son filas dentro del grid de VARIANT_ROWS
     * filas totales (ver allocateRows). items() es la lista completa de
     * variantes de ese grupo (ya resueltas al item que realmente varía,
     * ver updateVariantButtonsGrouped).
     */
    private record Segment(String groupKey, List<Item> items, int startRow, int allocatedRows) {}

    private List<ConflictRow> conflictRows = new ArrayList<>();
    private int scrollOffset = 0;

    // ES: Item actualmente "abierto" en el panel. A diferencia de antes, ya
    // NO es un índice de fila: un mismo target puede tener varias filas
    // (una por grupo) y todas se resaltan juntas al seleccionar cualquiera
    // de ellas (ver updateRowButtons) - así se ve en el mockup del usuario,
    // ambas filas de la fogata en verde a la vez.
    private Item selectedTarget = null;

    // ES: Solo se usan en modo "conflicto normal" (selectedTarget sin
    // grupos independientes) - comportamiento idéntico al de siempre.
    private int selectedVariantIndex = 0;
    private int variantScrollOffset = 0;

    // ES: Solo se usan en modo "grupos independientes". Scroll (en filas)
    // por grupo, para cuando un grupo tiene más variantes de las que caben
    // en las filas que le tocaron (ver allocateRows / scrollSegmentBy).
    private final Map<String, Integer> groupScrollOffsets = new HashMap<>();

    // ES: Último layout de grupos calculado por updateVariantButtonsGrouped,
    // reutilizado por selectGroupVariant y el manejo de scroll para no
    // recalcular la asignación de filas dos veces por click/scroll.
    private List<Segment> currentSegments = List.of();

    private int panelX;
    private int panelY;

    private int listX;
    private int listY;
    private int gridX;
    private int gridY;
    private int detailY;
    private int detailLabelWidth;

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

        this.panelX = this.leftPos + this.imageWidth + TAG_PANEL_GAP;
        this.panelY = this.topPos;

        this.listX = panelX + PANEL_PAD_X;
        this.listY = panelY + PANEL_PAD_TOP;
        this.gridX = listX + LIST_COLUMN_WIDTH + LIST_GRID_GAP;
        this.detailY = panelY + PANEL_PAD_TOP;
        this.gridY = detailY + DETAIL_LABEL_HEIGHT + DETAIL_GRID_GAP;
        this.detailLabelWidth = TAG_PANEL_WIDTH - (gridX - panelX) - PANEL_PAD_X;

        for (int i = 0; i < VISIBLE_ROWS; i++) {
            final int rowOffset = i;
            this.rowButtons[i] = this.addRenderableWidget(new ConflictRowButton(
                    listX, listY + i * ROW_HEIGHT, LIST_COLUMN_WIDTH, ROW_HEIGHT - 1,
                    button -> selectRow(rowOffset)
            ));
        }

        for (int i = 0; i < MAX_VARIANT_SLOTS; i++) {
            final int slotIndex = i;
            int col = i % VARIANT_COLS;
            int row = i / VARIANT_COLS;
            this.variantButtons[i] = this.addRenderableWidget(new ItemIconButton(
                    gridX + col * VARIANT_ICON_SIZE, gridY + row * VARIANT_ICON_SIZE, 16,
                    button -> selectVariant(slotIndex)
            ));
        }

        this.detailLabel = this.addRenderableWidget(new StringWidget(
                gridX, detailY, detailLabelWidth, DETAIL_LABEL_HEIGHT, Component.empty(), this.font));

        this.conflictRows = buildConflictRows();
        this.selectedTarget = conflictRows.isEmpty() ? null : conflictRows.get(0).target();
        loadCurrentPreferenceIndex();

        updateButtonStates();
    }

    /**
     * ES: Arma la columna de conflictos. Para un item normal (sin grupos
     * independientes), una sola fila (comportamiento de siempre). Para un
     * item con 2+ grupos sin material en común (ver
     * MultiRecipeScanner.hasUnlinkedGroups), una fila POR GRUPO en vez de
     * una sola fila mezclando todo (esa mezcla era justo el bug del ícono
     * repetido / cuenta inflada que se veía antes con la fogata).
     */
    private List<ConflictRow> buildConflictRows() {
        List<Item> allTargets = new ArrayList<>(MultiRecipeScanner.getMultiRecipeItems().keySet());
        allTargets.sort(Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString()));

        List<ConflictRow> rows = new ArrayList<>();
        for (Item target : allTargets) {
            if (MultiRecipeScanner.hasUnlinkedGroups(target)) {
                List<MultiRecipeScanner.VariantGroup> groups = new ArrayList<>(MultiRecipeScanner.getUnlinkedGroupsFor(target));
                groups.sort(Comparator.comparing(MultiRecipeScanner.VariantGroup::key));
                for (MultiRecipeScanner.VariantGroup group : groups) {
                    rows.add(new ConflictRow(target, group.key()));
                }
            } else {
                rows.add(new ConflictRow(target, null));
            }
        }
        return rows;
    }

    @Override
    public void containerTick() {
        super.containerTick();
        updateButtonStates();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (showingTagsPanel && !conflictRows.isEmpty() && scrollY != 0) {
            int direction = scrollY < 0 ? 1 : -1;

            int listRight = listX + LIST_COLUMN_WIDTH;
            int listBottom = listY + VISIBLE_ROWS * ROW_HEIGHT;
            if (mouseX >= listX && mouseX < listRight && mouseY >= listY && mouseY < listBottom) {
                scrollListBy(direction);
                return true;
            }

            int gridRight = gridX + VARIANT_COLS * VARIANT_ICON_SIZE;
            int gridBottom = gridY + VARIANT_ROWS * VARIANT_ICON_SIZE;
            if (mouseX >= gridX && mouseX < gridRight && mouseY >= gridY && mouseY < gridBottom) {
                if (selectedTarget != null && MultiRecipeScanner.hasUnlinkedGroups(selectedTarget)) {
                    int row = (int) ((mouseY - gridY) / VARIANT_ICON_SIZE);
                    Segment segment = findSegmentForRow(currentSegments, row);
                    if (segment != null) {
                        scrollSegmentBy(segment, direction);
                    }
                } else {
                    scrollVariantsBy(direction);
                }
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void updateButtonStates() {
        boolean autoActive = this.menu.isAutoActive();
        boolean processing = this.menu.isProcessing();

        if (this.playIdleButton != null) this.playIdleButton.visible = !processing;
        if (this.playActiveButton != null) this.playActiveButton.visible = processing;
        if (this.autoOffButton != null) this.autoOffButton.visible = !autoActive;
        if (this.autoOnButton != null) this.autoOnButton.visible = autoActive;

        updateRowButtons();
        updateVariantButtons();
        updateLabels();
    }

    private void updateLabels() {
        if (detailLabel == null) return;

        detailLabel.visible = showingTagsPanel && selectedTarget != null;
        if (!showingTagsPanel || selectedTarget == null) {
            detailLabel.setMessage(Component.empty());
            return;
        }

        if (MultiRecipeScanner.hasUnlinkedGroups(selectedTarget)) {
            int groupCount = MultiRecipeScanner.getUnlinkedGroupsFor(selectedTarget).size();
            String text = groupCount + " grupos";
            detailLabel.setMessage(Component.literal(this.font.plainSubstrByWidth(text, detailLabelWidth)));
            return;
        }

        List<MultiRecipeScanner.RecipeVariant> variants = MultiRecipeScanner.getVariantsFor(selectedTarget);
        String text = variants.isEmpty()
                ? "(-)"
                : "(" + (selectedVariantIndex + 1) + "/" + variants.size() + ")";
        detailLabel.setMessage(Component.literal(this.font.plainSubstrByWidth(text, detailLabelWidth)));
    }

    private void updateRowButtons() {
        for (int i = 0; i < VISIBLE_ROWS; i++) {
            ConflictRowButton rowButton = this.rowButtons[i];
            if (rowButton == null) continue;

            int itemIndex = scrollOffset + i;
            if (showingTagsPanel && itemIndex < conflictRows.size()) {
                ConflictRow row = conflictRows.get(itemIndex);
                ItemStack targetIcon = new ItemStack(row.target());
                ItemStack prefIcon = getPreferredIconForRow(row);
                // ES: Se resalta CUALQUIER fila que pertenezca al target
                // actualmente abierto, no solo una - así ambas filas de la
                // fogata (logs y coals) quedan en verde juntas, igual que
                // en el mockup del usuario.
                boolean highlighted = row.target().equals(selectedTarget);
                rowButton.setContent(targetIcon, prefIcon, highlighted);
                rowButton.visible = true;
            } else {
                rowButton.visible = false;
            }
        }
    }

    private void updateVariantButtons() {
        if (selectedTarget == null) {
            currentSegments = List.of();
            for (ItemIconButton button : variantButtons) {
                if (button != null) button.visible = false;
            }
            return;
        }

        if (MultiRecipeScanner.hasUnlinkedGroups(selectedTarget)) {
            updateVariantButtonsGrouped();
        } else {
            updateVariantButtonsFlat();
        }
    }

    /**
     * ES: Comportamiento de siempre para un conflicto normal (una sola
     * lista plana de variantes, un solo scroll) - sin cambios de lógica,
     * solo usa selectedTarget en vez de conflictItems.get(selectedIndex).
     */
    private void updateVariantButtonsFlat() {
        currentSegments = List.of();

        List<MultiRecipeScanner.RecipeVariant> variants = MultiRecipeScanner.getVariantsFor(selectedTarget);
        int startIndex = variantScrollOffset * VARIANT_COLS;

        for (int i = 0; i < MAX_VARIANT_SLOTS; i++) {
            ItemIconButton variantButton = this.variantButtons[i];
            if (variantButton == null) continue;

            int variantIndex = startIndex + i;
            if (variantIndex < variants.size()) {
                MultiRecipeScanner.RecipeVariant variant = variants.get(variantIndex);
                ItemStack icon = new ItemStack(MultiRecipeScanner.getDisplayItem(selectedTarget, variant));
                variantButton.setContent(icon, variantIndex == selectedVariantIndex);
                variantButton.visible = true;
            } else {
                variantButton.visible = false;
            }
        }
    }

    /**
     * ES: Modo "grupos independientes" (ej. fogata). Reparte las
     * VARIANT_ROWS filas del grid entre los grupos del target seleccionado
     * proporcionalmente a cuántas filas necesita cada uno (ver
     * allocateRows), y dibuja cada grupo en su propio tramo de filas, con
     * scroll independiente si le tocaron menos filas de las que necesita.
     */
    private void updateVariantButtonsGrouped() {
        List<MultiRecipeScanner.VariantGroup> groups = new ArrayList<>(MultiRecipeScanner.getUnlinkedGroupsFor(selectedTarget));
        groups.sort(Comparator.comparing(MultiRecipeScanner.VariantGroup::key));

        List<Integer> neededRows = new ArrayList<>();
        for (MultiRecipeScanner.VariantGroup group : groups) {
            int count = group.variants().size();
            neededRows.add((int) Math.ceil(count / (double) VARIANT_COLS));
        }

        int[] allocation = allocateRows(neededRows, VARIANT_ROWS);

        List<Segment> segments = new ArrayList<>();
        int cursor = 0;
        for (int i = 0; i < groups.size(); i++) {
            MultiRecipeScanner.VariantGroup group = groups.get(i);

            // ES: Resolver cada variante del grupo al item que REALMENTE
            // varía (la posición del grupo dentro de la firma completa),
            // no la firma entera - eso es lo que va en el ícono.
            int posIndex = group.positions().isEmpty() ? -1 : group.positions().get(0);
            List<Item> items = new ArrayList<>();
            for (MultiRecipeScanner.RecipeVariant variant : group.variants()) {
                List<Item> signature = variant.ingredientItems();
                items.add(posIndex >= 0 && posIndex < signature.size() ? signature.get(posIndex) : selectedTarget);
            }

            segments.add(new Segment(group.key(), items, cursor, allocation[i]));
            cursor += allocation[i];
        }
        currentSegments = segments;

        var server = net.minecraft.client.Minecraft.getInstance().getSingleplayerServer();
        RecyclerPreferences prefs = server != null ? RecyclerPreferences.get(server) : null;

        for (int idx = 0; idx < MAX_VARIANT_SLOTS; idx++) {
            ItemIconButton button = variantButtons[idx];
            if (button == null) continue;

            int row = idx / VARIANT_COLS;
            int col = idx % VARIANT_COLS;

            Segment segment = findSegmentForRow(segments, row);
            if (segment == null) {
                button.visible = false;
                continue;
            }

            int scrollRows = groupScrollOffsets.getOrDefault(segment.groupKey(), 0);
            int relativeRow = row - segment.startRow() + scrollRows;
            int variantIndex = relativeRow * VARIANT_COLS + col;

            if (variantIndex < 0 || variantIndex >= segment.items().size()) {
                button.visible = false;
                continue;
            }

            Item variantItem = segment.items().get(variantIndex);
            boolean highlighted = isGroupPreferenceSelected(prefs, segment, variantItem);
            button.setContent(new ItemStack(variantItem), highlighted);
            button.visible = true;
        }
    }

    private boolean isGroupPreferenceSelected(RecyclerPreferences prefs, Segment segment, Item candidate) {
        if (prefs != null) {
            Optional<Item> preferred = prefs.getGroupPreference(selectedTarget, segment.groupKey());
            if (preferred.isPresent()) {
                return preferred.get() == candidate;
            }
        }
        // ES: sin preferencia guardada, el "elegido" es el primer item de la
        // lista del grupo (el mismo item base que usa RecyclerLogic cuando
        // no hay preferencia configurada).
        return !segment.items().isEmpty() && segment.items().get(0) == candidate;
    }

    private static Segment findSegmentForRow(List<Segment> segments, int row) {
        for (Segment segment : segments) {
            if (row >= segment.startRow() && row < segment.startRow() + segment.allocatedRows()) {
                return segment;
            }
        }
        return null;
    }

    /**
     * ES: Reparte "totalRows" filas entre los grupos en proporción a cuántas
     * filas necesita cada uno (neededRowsList), con un mínimo de 1 fila por
     * grupo. Si la suma de filas necesitadas ya entra en totalRows, cada
     * grupo recibe exactamente lo que necesita (sin split, filas de sobra
     * simplemente no se usan). Si no entra, se reparte proporcionalmente y
     * el redondeo se ajusta dándole/quitándole 1 fila primero a los grupos
     * más grandes (donde se nota menos).
     */
    private static int[] allocateRows(List<Integer> neededRowsList, int totalRows) {
        int n = neededRowsList.size();
        int[] alloc = new int[n];
        if (n == 0) return alloc;

        int sumNeeded = 0;
        for (int v : neededRowsList) sumNeeded += v;

        if (sumNeeded <= totalRows) {
            for (int i = 0; i < n; i++) alloc[i] = neededRowsList.get(i);
            return alloc;
        }

        for (int i = 0; i < n; i++) {
            alloc[i] = Math.max(1, (int) Math.floor(neededRowsList.get(i) * (double) totalRows / sumNeeded));
        }

        int allocatedSum = 0;
        for (int v : alloc) allocatedSum += v;
        int diff = totalRows - allocatedSum;

        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        Arrays.sort(order, (a, b) -> neededRowsList.get(b) - neededRowsList.get(a));

        int orderIdx = 0;
        int guard = 0;
        while (diff != 0 && guard < n * 8 + 8) {
            int i = order[orderIdx % n];
            if (diff > 0) {
                alloc[i]++;
                diff--;
            } else if (alloc[i] > 1) {
                alloc[i]--;
                diff++;
            }
            orderIdx++;
            guard++;
        }

        return alloc;
    }

    private void scrollSegmentBy(Segment segment, int delta) {
        int neededRows = (int) Math.ceil(segment.items().size() / (double) VARIANT_COLS);
        int maxOffset = Math.max(0, neededRows - segment.allocatedRows());
        int current = groupScrollOffsets.getOrDefault(segment.groupKey(), 0);
        int updated = Math.max(0, Math.min(maxOffset, current + delta));
        groupScrollOffsets.put(segment.groupKey(), updated);
        updateVariantButtons();
    }

    private void scrollListBy(int delta) {
        int maxOffset = Math.max(0, conflictRows.size() - VISIBLE_ROWS);
        scrollOffset = Math.max(0, Math.min(maxOffset, scrollOffset + delta));
        updateRowButtons();
    }

    private void scrollVariantsBy(int delta) {
        if (selectedTarget == null) return;
        List<MultiRecipeScanner.RecipeVariant> variants = MultiRecipeScanner.getVariantsFor(selectedTarget);
        int totalRows = (variants.size() + VARIANT_COLS - 1) / VARIANT_COLS;
        int maxOffsetRows = Math.max(0, totalRows - VARIANT_ROWS);
        variantScrollOffset = Math.max(0, Math.min(maxOffsetRows, variantScrollOffset + delta));
        updateVariantButtons();
    }

    private void selectRow(int rowOffset) {
        int itemIndex = scrollOffset + rowOffset;
        if (itemIndex >= 0 && itemIndex < conflictRows.size()) {
            selectedTarget = conflictRows.get(itemIndex).target();
            loadCurrentPreferenceIndex();
            updateButtonStates();
        }
    }

    private void selectVariant(int slotIndex) {
        if (selectedTarget == null) return;

        if (MultiRecipeScanner.hasUnlinkedGroups(selectedTarget)) {
            selectGroupVariant(slotIndex);
            return;
        }

        List<MultiRecipeScanner.RecipeVariant> variants = MultiRecipeScanner.getVariantsFor(selectedTarget);
        int variantIndex = variantScrollOffset * VARIANT_COLS + slotIndex;
        if (variantIndex < 0 || variantIndex >= variants.size()) return;

        selectedVariantIndex = variantIndex;
        List<Item> chosen = variants.get(variantIndex).ingredientItems();

        if (this.minecraft != null && this.minecraft.player != null) {
            ClientPacketDistributor.sendToServer(new RecyclerPreferencePayload(selectedTarget, chosen));
        }
        updateButtonStates();
    }

    private void selectGroupVariant(int slotIndex) {
        int row = slotIndex / VARIANT_COLS;
        int col = slotIndex % VARIANT_COLS;

        Segment segment = findSegmentForRow(currentSegments, row);
        if (segment == null) return;

        int scrollRows = groupScrollOffsets.getOrDefault(segment.groupKey(), 0);
        int relativeRow = row - segment.startRow() + scrollRows;
        int variantIndex = relativeRow * VARIANT_COLS + col;
        if (variantIndex < 0 || variantIndex >= segment.items().size()) return;

        Item chosen = segment.items().get(variantIndex);

        if (this.minecraft != null && this.minecraft.player != null) {
            ClientPacketDistributor.sendToServer(new RecyclerGroupPreferencePayload(selectedTarget, segment.groupKey(), chosen));
        }
        updateButtonStates();
    }

    private void sendButtonPacket(RecyclerButtonPayload.ButtonType type) {
        if (this.minecraft != null && this.minecraft.player != null) {
            ClientPacketDistributor.sendToServer(new RecyclerButtonPayload(this.menu.getBlockPos(), type));
        }
    }

    private void loadCurrentPreferenceIndex() {
        selectedVariantIndex = 0;
        variantScrollOffset = 0;
        groupScrollOffsets.clear();

        if (selectedTarget == null || MultiRecipeScanner.hasUnlinkedGroups(selectedTarget)) {
            return;
        }

        List<MultiRecipeScanner.RecipeVariant> variants = MultiRecipeScanner.getVariantsFor(selectedTarget);

        var server = net.minecraft.client.Minecraft.getInstance().getSingleplayerServer();
        if (server == null) return;

        RecyclerPreferences prefs = RecyclerPreferences.get(server);
        prefs.getPreference(selectedTarget).ifPresent(signature -> {
            for (int i = 0; i < variants.size(); i++) {
                if (variants.get(i).ingredientItems().equals(signature)) {
                    selectedVariantIndex = i;
                    variantScrollOffset = i / VARIANT_COLS;
                    return;
                }
            }
        });
    }

    /**
     * ES: Ícono de preferencia para una fila de la columna de conflictos.
     * Para una fila normal (groupKey == null), comportamiento de siempre.
     * Para una fila de grupo, la preferencia guardada de ESE grupo en
     * particular (o el ítem base del grupo si no hay ninguna).
     */
    private ItemStack getPreferredIconForRow(ConflictRow row) {
        if (row.groupKey() == null) {
            return getPreferredIcon(row.target());
        }

        List<MultiRecipeScanner.VariantGroup> groups = MultiRecipeScanner.getUnlinkedGroupsFor(row.target());
        MultiRecipeScanner.VariantGroup group = null;
        for (MultiRecipeScanner.VariantGroup candidate : groups) {
            if (candidate.key().equals(row.groupKey())) {
                group = candidate;
                break;
            }
        }
        if (group == null) return ItemStack.EMPTY;

        var server = net.minecraft.client.Minecraft.getInstance().getSingleplayerServer();
        if (server != null) {
            RecyclerPreferences prefs = RecyclerPreferences.get(server);
            Optional<Item> preferred = prefs.getGroupPreference(row.target(), row.groupKey());
            if (preferred.isPresent()) {
                return new ItemStack(preferred.get());
            }
        }

        Item display = MultiRecipeScanner.getGroupDisplayItem(group);
        return display != null ? new ItemStack(display) : ItemStack.EMPTY;
    }

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
                        return new ItemStack(MultiRecipeScanner.getDisplayItem(target, variant));
                    }
                }
            }
        }

        // ES: Sin preferencia guardada -> primera variante (comportamiento por defecto)
        return new ItemStack(MultiRecipeScanner.getDisplayItem(target, variants.get(0)));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
        super.extractBackground(guiGraphics, mouseX, mouseY, partialTicks);
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.leftPos, this.topPos, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);

        if (showingTagsPanel) {
            guiGraphics.blit(RenderPipelines.GUI_TEXTURED, TAG_TEXTURE, panelX, panelY, 0.0F, 0.0F, TAG_PANEL_WIDTH, TAG_PANEL_HEIGHT, 256, 256);
        }
    }
}
