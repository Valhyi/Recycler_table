package com.valhyi.recyclertable.block.entity;

import com.valhyi.recyclertable.gui.RecyclerMenu;
import com.valhyi.recyclertable.init.ModBlockEntities;
import com.valhyi.recyclertable.recipe.RecyclerLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class RecyclerBlockEntity extends BlockEntity implements MenuProvider {
    private final SimpleContainer container = new SimpleContainer(21);

    private int processingTicks = 0;
    private static final int PROCESSING_TIME = 60; // Ticks que dura un ciclo de proceso

    private boolean autoMode = false;
    private boolean singleShotPending = false;

    private final net.minecraft.world.inventory.ContainerData dataAccess = new net.minecraft.world.inventory.ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> autoMode ? 1 : 0;
                // ES: "Procesando" incluye: contando ticks, single-shot pendiente,
                // o un item aparcado en el slot 9 esperando espacio en el output.
                case 1 -> (processingTicks > 0 || singleShotPending || !container.getItem(9).isEmpty()) ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            if (index == 0) {
                autoMode = value != 0;
            }
            // index 1 (processing) is read-only on the client; no-op
        }

        @Override
        public int getCount() {
            return 2;
        }
    };

    public net.minecraft.world.inventory.ContainerData getDataAccess() {
        return dataAccess;
    }

    public void triggerSingleShot() {
        if (processingTicks == 0 && container.getItem(9).isEmpty()) {
            singleShotPending = true;
            this.setChanged();
        }
    }

    public void toggleAutoMode() {
        autoMode = !autoMode;
        this.setChanged();
    }

    public boolean isAutoMode() {
        return autoMode;
    }

    public RecyclerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RECYCLER_BLOCK_ENTITY.get(), pos, state);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.recyclertable.recycler_table");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new RecyclerMenu(containerId, playerInventory, this.getBlockPos(), this.container, this.dataAccess);
    }

    public SimpleContainer getContainer() {
        return container;
    }

    /**
     * Hopper compatibility: Provides ItemHandler capability for hopper interaction
     * Restricts insertion/extraction based on slot groups:
     * - Slots 0-8: Input (can insert/extract)
     * - Slots 9-11: Restricted (no hopper interaction)
     * - Slots 12-20: Output (can extract only)
     */
    public static ResourceHandler<ItemResource> getCapability(RecyclerBlockEntity blockEntity, @Nullable Direction direction) {
        return new RestrictedRecyclerItemHandler(VanillaContainerWrapper.of(blockEntity.container));
    }

    /**
     * Custom wrapper that restricts hopper interaction based on slot types
     * Wraps the vanilla container handler to add slot restrictions
     */
    private static class RestrictedRecyclerItemHandler implements ResourceHandler<ItemResource> {
        private final ResourceHandler<ItemResource> baseHandler;

        RestrictedRecyclerItemHandler(ResourceHandler<ItemResource> baseHandler) {
            this.baseHandler = baseHandler;
        }

        @Override
        public int size() {
            // Total accessible slots: 9 input (0-8) + 9 output (12-20) = 18 virtual slots
            return 18;
        }

        @Override
        public ItemResource getResource(int index) {
            int actualSlot = mapVirtualToActualSlot(index);
            return baseHandler.getResource(actualSlot);
        }

        @Override
        public long getAmountAsLong(int index) {
            int actualSlot = mapVirtualToActualSlot(index);
            return baseHandler.getAmountAsLong(actualSlot);
        }

        @Override
        public long getCapacityAsLong(int index, ItemResource resource) {
            int actualSlot = mapVirtualToActualSlot(index);
            return baseHandler.getCapacityAsLong(actualSlot, resource);
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            int actualSlot = mapVirtualToActualSlot(index);
            return baseHandler.isValid(actualSlot, resource);
        }

        @Override
        public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
            // Only allow insertion into input slots (virtual 0-8 = actual 0-8)
            if (index >= 9) {
                return 0; // Cannot insert into output slots
            }
            int actualSlot = index; // Virtual 0-8 maps directly to actual 0-8
            return baseHandler.insert(actualSlot, resource, amount, transaction);
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
            // Only allow extraction from output slots (virtual 9-17 = actual 12-20)
            if (index < 9) {
                return 0; // Cannot extract from input slots
            }
            int actualSlot = index + 3; // Virtual 9 = Actual 12, Virtual 17 = Actual 20
            return baseHandler.extract(actualSlot, resource, amount, transaction);
        }

        /**
         * Maps virtual slot numbers to actual container slots
         * Virtual 0-8 -> Actual 0-8 (input)
         * Virtual 9-17 -> Actual 12-20 (output)
         */
        private int mapVirtualToActualSlot(int virtualSlot) {
            if (virtualSlot < 0 || virtualSlot >= 18) {
                throw new IndexOutOfBoundsException("Virtual slot " + virtualSlot + " is out of bounds [0, 18)");
            }
            return virtualSlot < 9 ? virtualSlot : virtualSlot + 3;
        }
    }

    /**
     * Called by BlockEntityTicker every server tick
     * Procesa el grid de entrada / slot de proceso
     * Slots 0-8: Input Grid
     * Slot 9: Item(s) en proceso (stackeable hasta el maximo del item)
     * Slot 10: Botella vacía (restringido)
     * Slot 11: Libro (restringido)
     * Slots 12-20: Output Grid
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, RecyclerBlockEntity entity) {
        entity.tick(level, pos, state);
    }

    /**
     * Mueve botellas de vidrio y libros del grid de entrada (slots 0-8)
     * a sus espacios restringidos (slot 10 = botellas, slot 11 = libros)
     * antes de procesar cualquier item.
     */
    private void moveRestrictedItemsFromInput() {
        for (int i = 0; i < 9; i++) {
            ItemStack inputItem = container.getItem(i);
            if (inputItem.isEmpty()) continue;

            if (inputItem.is(net.minecraft.world.item.Items.GLASS_BOTTLE)) {
                ItemStack bottleSlot = container.getItem(10);
                int space = bottleSlot.isEmpty() ? 64 : (ItemStack.isSameItemSameComponents(bottleSlot, inputItem) ? bottleSlot.getMaxStackSize() - bottleSlot.getCount() : 0);
                if (space > 0) {
                    int transfer = Math.min(space, inputItem.getCount());
                    if (bottleSlot.isEmpty()) {
                        container.setItem(10, inputItem.copyWithCount(transfer));
                    } else {
                        bottleSlot.grow(transfer);
                    }
                    inputItem.shrink(transfer);
                    this.setChanged();
                }
            } else if (inputItem.is(net.minecraft.world.item.Items.BOOK)) {
                ItemStack bookSlot = container.getItem(11);
                int space = bookSlot.isEmpty() ? 64 : (ItemStack.isSameItemSameComponents(bookSlot, inputItem) ? bookSlot.getMaxStackSize() - bookSlot.getCount() : 0);
                if (space > 0) {
                    int transfer = Math.min(space, inputItem.getCount());
                    if (bookSlot.isEmpty()) {
                        container.setItem(11, inputItem.copyWithCount(transfer));
                    } else {
                        bookSlot.grow(transfer);
                    }
                    inputItem.shrink(transfer);
                    this.setChanged();
                }
            }
        }
    }

    /**
     * ES: Mientras hay un item en proceso (slot 9), absorbe del input (slots 0-8)
     * cualquier item idéntico (mismo item + mismos componentes) hasta llenar el
     * slot 9 hasta su stack máximo. Se llama cada tick durante el ciclo de proceso.
     */
    private void accumulateMatchingItems() {
        ItemStack template = container.getItem(9);
        if (template.isEmpty()) return;

        int maxStack = template.getMaxStackSize();
        if (template.getCount() >= maxStack) return;

        for (int i = 0; i < 9; i++) {
            ItemStack inputItem = container.getItem(i);
            if (inputItem.isEmpty()) continue;
            if (!ItemStack.isSameItemSameComponents(inputItem, template)) continue;

            int space = maxStack - template.getCount();
            if (space <= 0) break;

            int transfer = Math.min(space, inputItem.getCount());
            template.grow(transfer);
            inputItem.shrink(transfer);
            this.setChanged();

            if (template.getCount() >= maxStack) break;
        }
    }

    /**
     * Verifica si hay espacio en el output para colocar todos los items
     */
    private boolean canFitAllResults(List<ItemStack> results) {
        // Crear una copia de los slots de output para simular la colocación
        ItemStack[] tempSlots = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            tempSlots[i] = container.getItem(12 + i).copy();
        }

        // Intentar colocar todos los resultados en los slots temporales
        for (ItemStack result : results) {
            boolean placed = false;
            ItemStack toPlace = result.copy();

            for (int i = 0; i < 9; i++) {
                if (tempSlots[i].isEmpty()) {
                    tempSlots[i] = toPlace.copy();
                    placed = true;
                    break;
                } else if (ItemStack.isSameItemSameComponents(tempSlots[i], toPlace)) {
                    int space = tempSlots[i].getMaxStackSize() - tempSlots[i].getCount();
                    if (space > 0) {
                        int transfer = Math.min(space, toPlace.getCount());
                        tempSlots[i].grow(transfer);
                        toPlace.shrink(transfer);
                        if (toPlace.isEmpty()) {
                            placed = true;
                            break;
                        }
                    }
                }
            }

            if (!placed) {
                return false; // No hay espacio para este item
            }
        }

        return true; // Hay espacio para todos
    }

    /**
     * ES: Coloca un ItemStack resultante en el grid de output (slots 12-20),
     * apilando sobre stacks existentes compatibles o usando un slot vacío.
     */
    private void placeInOutput(ItemStack result) {
        ItemStack toPlace = result.copy();

        for (int slot = 12; slot <= 20 && !toPlace.isEmpty(); slot++) {
            ItemStack existingItem = container.getItem(slot);
            if (existingItem.isEmpty()) {
                container.setItem(slot, toPlace.copy());
                toPlace = ItemStack.EMPTY;
                break;
            } else if (ItemStack.isSameItemSameComponents(existingItem, toPlace)) {
                int space = existingItem.getMaxStackSize() - existingItem.getCount();
                if (space > 0) {
                    int transfer = Math.min(space, toPlace.getCount());
                    existingItem.grow(transfer);
                    toPlace.shrink(transfer);
                }
            }
        }
    }

    public void tick(Level level, BlockPos pos, BlockState state) {
        if (level == null || level.isClientSide()) {
            return;
        }

        // Mover botellas y libros del input a los espacios restringidos
        moveRestrictedItemsFromInput();

        ItemStack processStack = container.getItem(9);

        // Si hay algo en el slot de proceso, gestionarlo (contando o aparcado)
        if (!processStack.isEmpty()) {
            if (processingTicks > 0) {
                // Mientras cuenta, sigue absorbiendo items idénticos del input
                accumulateMatchingItems();
                processingTicks--;
                if (processingTicks == 0) {
                    attemptResolveProcessing();
                }
            } else {
                // Aparcado: el ciclo ya terminó pero no había espacio en el output.
                // Reintenta cada tick automáticamente, sin necesitar el botón Play.
                attemptResolveProcessing();
            }
            return;
        }

        // Slot de proceso vacío: solo buscar nuevo item si Auto está activo o se presionó Play
        if (!autoMode && !singleShotPending) {
            return;
        }

        // Buscar item reciclable en el grid de entrada (slots 0-8)
        for (int i = 0; i < 9; i++) {
            ItemStack inputItem = container.getItem(i);
            if (!inputItem.isEmpty() && RecyclerLogic.canRecycle(inputItem, level)) {
                // Tomar 1 unidad para establecer el "molde" del slot de proceso
                ItemStack singleItem = inputItem.copyWithCount(1);
                inputItem.shrink(1);

                container.setItem(9, singleItem);
                processingTicks = PROCESSING_TIME;

                // Absorber de inmediato cualquier otra unidad idéntica ya disponible
                accumulateMatchingItems();

                singleShotPending = false; // Play solo inicia 1 ciclo y se apaga
                this.setChanged();
                return;
            }
        }

        // No se encontró ningún item reciclable en el input: detener Auto y Play
        if (autoMode || singleShotPending) {
            autoMode = false;
            singleShotPending = false;
            this.setChanged();
        }
    }

    /**
     * ES: Intenta resolver el contenido del slot de proceso (llamado al llegar a 0
     * ticks, o cada tick mientras está aparcado esperando espacio). Si el resultado
     * cabe en el output, lo coloca y limpia el slot 9. Si no cabe, detiene el modo
     * automático pero deja los items aparcados para reintentar en el próximo tick.
     */
    private void attemptResolveProcessing() {
        ItemStack processStack = container.getItem(9);
        if (processStack.isEmpty() || this.level == null) {
            return;
        }

        ItemStack emptyBottle = container.getItem(10);
        ItemStack book = container.getItem(11);

        RecyclerLogic.RecyclingOutput output = RecyclerLogic.processRecycling(processStack, emptyBottle, book, this.level);

        List<ItemStack> results = output.results();
        if (results.isEmpty()) {
            results = new ArrayList<>();
            results.add(processStack.copy());
        }

        if (!canFitAllResults(results)) {
            // No hay espacio: detener Auto (no debe reanudarse solo), pero NO perder
            // los items ya acumulados en el slot 9. Quedan aparcados para reintentar.
            autoMode = false;
            singleShotPending = false;
            this.setChanged();
            return;
        }

        // Hay espacio: colocar todos los resultados
        for (ItemStack result : results) {
            placeInOutput(result);
        }

        // Consumir botellas/libros según cuántas unidades encantadas se procesaron
        if (output.bottlesConsumed() > 0) {
            emptyBottle.shrink(output.bottlesConsumed());
        }
        if (output.booksConsumed() > 0) {
            book.shrink(output.booksConsumed());
        }

        // Limpiar slot de proceso
        container.setItem(9, ItemStack.EMPTY);

        this.setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), 2);
        }
    }

    // ES: Ya no se sobreescribe setRemoved() aquí. Soltar el inventario al romper
    // el bloque ahora se maneja en RecyclerBlock#onRemove(), porque setRemoved()
    // también se dispara al recargar el chunk (no solo al romper el bloque), lo
    // que causaba que los items se duplicaran cada vez que se entraba al mundo.

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        NonNullList<ItemStack> items = NonNullList.withSize(this.container.getContainerSize(), ItemStack.EMPTY);
        for (int i = 0; i < this.container.getContainerSize(); i++) {
            items.set(i, this.container.getItem(i));
        }
        ContainerHelper.saveAllItems(output, items);
        output.putInt("processing_ticks", this.processingTicks);
        output.putBoolean("auto_mode", this.autoMode);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        NonNullList<ItemStack> items = NonNullList.withSize(this.container.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        for (int i = 0; i < items.size(); i++) {
            this.container.setItem(i, items.get(i));
        }
        this.processingTicks = input.getIntOr("processing_ticks", 0);
        this.autoMode = input.getBooleanOr("auto_mode", false);
    }
}
