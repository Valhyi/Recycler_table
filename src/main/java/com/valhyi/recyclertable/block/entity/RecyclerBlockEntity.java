package com.valhyi.recyclertable.block.entity;

import com.valhyi.recyclertable.gui.RecyclerMenu;
import com.valhyi.recyclertable.init.ModBlockEntities;
import com.valhyi.recyclertable.recipe.RecyclerLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
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

import static com.valhyi.recyclertable.gui.RecyclerMenu.BOOK_SLOT;
import static com.valhyi.recyclertable.gui.RecyclerMenu.BOTTLE_SLOT;
import static com.valhyi.recyclertable.gui.RecyclerMenu.CONTAINER_SIZE;
import static com.valhyi.recyclertable.gui.RecyclerMenu.GRID_SIZE;
import static com.valhyi.recyclertable.gui.RecyclerMenu.INPUT_SLOTS_END;
import static com.valhyi.recyclertable.gui.RecyclerMenu.INPUT_SLOTS_START;
import static com.valhyi.recyclertable.gui.RecyclerMenu.OUTPUT_SLOTS_END;
import static com.valhyi.recyclertable.gui.RecyclerMenu.OUTPUT_SLOTS_START;
import static com.valhyi.recyclertable.gui.RecyclerMenu.PROCESSING_SLOT;

public class RecyclerBlockEntity extends BlockEntity implements MenuProvider {
    // ES: SimpleContainer llama a setChanged() cada vez que cambia un slot
    // (jugador en la GUI, tolvas, tuberias). Se sobreescribe para avisar al
    // BlockEntity; sin esto el chunk puede no guardarse y se pierden items.
    // (En esta version SimpleContainer ya no tiene addListener.)
    private final SimpleContainer container = new SimpleContainer(CONTAINER_SIZE) {
        @Override
        public void setChanged() {
            super.setChanged();
            RecyclerBlockEntity.this.setChanged();
        }
    };

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
                // o un item aparcado en el slot de proceso esperando espacio en el output.
                case 1 -> (processingTicks > 0 || singleShotPending || !container.getItem(PROCESSING_SLOT).isEmpty()) ? 1 : 0;
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
        if (processingTicks == 0 && container.getItem(PROCESSING_SLOT).isEmpty()) {
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
     * - Input slots (0-17): insert only
     * - Processing/bottle/book (18-20): no hopper interaction
     * - Output slots (21-38): extract only
     */
    public static ResourceHandler<ItemResource> getCapability(RecyclerBlockEntity blockEntity, @Nullable Direction direction) {
        return new RestrictedRecyclerItemHandler(VanillaContainerWrapper.of(blockEntity.container));
    }

    /**
     * Custom wrapper that restricts hopper interaction based on slot types.
     * Expone 2 * GRID_SIZE slots virtuales: los primeros GRID_SIZE son el
     * input (mismos indices reales), los siguientes GRID_SIZE son el output
     * (desplazados hasta OUTPUT_SLOTS_START).
     */
    private static class RestrictedRecyclerItemHandler implements ResourceHandler<ItemResource> {
        private static final int VIRTUAL_SIZE = GRID_SIZE * 2;
        private final ResourceHandler<ItemResource> baseHandler;

        RestrictedRecyclerItemHandler(ResourceHandler<ItemResource> baseHandler) {
            this.baseHandler = baseHandler;
        }

        @Override
        public int size() {
            return VIRTUAL_SIZE;
        }

        @Override
        public ItemResource getResource(int index) {
            return baseHandler.getResource(mapVirtualToActualSlot(index));
        }

        @Override
        public long getAmountAsLong(int index) {
            return baseHandler.getAmountAsLong(mapVirtualToActualSlot(index));
        }

        @Override
        public long getCapacityAsLong(int index, ItemResource resource) {
            return baseHandler.getCapacityAsLong(mapVirtualToActualSlot(index), resource);
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            return baseHandler.isValid(mapVirtualToActualSlot(index), resource);
        }

        @Override
        public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
            // Solo se puede insertar en los slots virtuales de input
            if (index < 0 || index >= GRID_SIZE) {
                return 0;
            }
            return baseHandler.insert(mapVirtualToActualSlot(index), resource, amount, transaction);
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
            // Solo se puede extraer de los slots virtuales de output
            if (index < GRID_SIZE || index >= VIRTUAL_SIZE) {
                return 0;
            }
            return baseHandler.extract(mapVirtualToActualSlot(index), resource, amount, transaction);
        }

        /**
         * Virtual 0..GRID_SIZE-1 -> real INPUT_SLOTS_START..
         * Virtual GRID_SIZE..2*GRID_SIZE-1 -> real OUTPUT_SLOTS_START..
         */
        private int mapVirtualToActualSlot(int virtualSlot) {
            if (virtualSlot < 0 || virtualSlot >= VIRTUAL_SIZE) {
                throw new IndexOutOfBoundsException("Virtual slot " + virtualSlot + " is out of bounds [0, " + VIRTUAL_SIZE + ")");
            }
            return virtualSlot < GRID_SIZE
                    ? INPUT_SLOTS_START + virtualSlot
                    : OUTPUT_SLOTS_START + (virtualSlot - GRID_SIZE);
        }
    }

    /**
     * Called by BlockEntityTicker every server tick
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, RecyclerBlockEntity entity) {
        entity.tick(level, pos, state);
    }

    /**
     * Mueve botellas de vidrio y libros del grid de entrada a sus espacios
     * restringidos (BOTTLE_SLOT / BOOK_SLOT) antes de procesar cualquier item.
     */
    private void moveRestrictedItemsFromInput() {
        for (int i = INPUT_SLOTS_START; i < INPUT_SLOTS_END; i++) {
            ItemStack inputItem = container.getItem(i);
            if (inputItem.isEmpty()) continue;

            if (inputItem.is(net.minecraft.world.item.Items.GLASS_BOTTLE)) {
                ItemStack bottleSlot = container.getItem(BOTTLE_SLOT);
                int space = bottleSlot.isEmpty() ? 64 : (ItemStack.isSameItemSameComponents(bottleSlot, inputItem) ? bottleSlot.getMaxStackSize() - bottleSlot.getCount() : 0);
                if (space > 0) {
                    int transfer = Math.min(space, inputItem.getCount());
                    if (bottleSlot.isEmpty()) {
                        container.setItem(BOTTLE_SLOT, inputItem.copyWithCount(transfer));
                    } else {
                        bottleSlot.grow(transfer);
                    }
                    inputItem.shrink(transfer);
                    this.setChanged();
                }
            } else if (inputItem.is(net.minecraft.world.item.Items.BOOK)) {
                ItemStack bookSlot = container.getItem(BOOK_SLOT);
                int space = bookSlot.isEmpty() ? 64 : (ItemStack.isSameItemSameComponents(bookSlot, inputItem) ? bookSlot.getMaxStackSize() - bookSlot.getCount() : 0);
                if (space > 0) {
                    int transfer = Math.min(space, inputItem.getCount());
                    if (bookSlot.isEmpty()) {
                        container.setItem(BOOK_SLOT, inputItem.copyWithCount(transfer));
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
     * ES: Mientras hay un item en proceso, absorbe del input cualquier item
     * identico (mismo item + mismos componentes) hasta llenar el slot de
     * proceso hasta su stack maximo. Se llama cada tick durante el ciclo.
     */
    private void accumulateMatchingItems() {
        ItemStack template = container.getItem(PROCESSING_SLOT);
        if (template.isEmpty()) return;

        int maxStack = template.getMaxStackSize();
        if (template.getCount() >= maxStack) return;

        for (int i = INPUT_SLOTS_START; i < INPUT_SLOTS_END; i++) {
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
        ItemStack[] tempSlots = new ItemStack[GRID_SIZE];
        for (int i = 0; i < GRID_SIZE; i++) {
            tempSlots[i] = container.getItem(OUTPUT_SLOTS_START + i).copy();
        }

        for (ItemStack result : results) {
            boolean placed = false;
            ItemStack toPlace = result.copy();

            for (int i = 0; i < GRID_SIZE; i++) {
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
                return false;
            }
        }

        return true;
    }

    /**
     * ES: Coloca un ItemStack resultante en el grid de output, apilando sobre
     * stacks existentes compatibles o usando un slot vacio.
     */
    private void placeInOutput(ItemStack result) {
        ItemStack toPlace = result.copy();

        for (int slot = OUTPUT_SLOTS_START; slot < OUTPUT_SLOTS_END && !toPlace.isEmpty(); slot++) {
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

        ItemStack processStack = container.getItem(PROCESSING_SLOT);

        // Si hay algo en el slot de proceso, gestionarlo (contando o aparcado)
        if (!processStack.isEmpty()) {
            if (processingTicks > 0) {
                accumulateMatchingItems();
                processingTicks--;
                if (processingTicks == 0) {
                    attemptResolveProcessing();
                }
            } else {
                // Aparcado: el ciclo ya termino pero no habia espacio en el output.
                attemptResolveProcessing();
            }
            return;
        }

        // Slot de proceso vacio: solo buscar nuevo item si Auto esta activo o se presiono Play
        if (!autoMode && !singleShotPending) {
            return;
        }

        // Buscar item reciclable en el grid de entrada
        for (int i = INPUT_SLOTS_START; i < INPUT_SLOTS_END; i++) {
            ItemStack inputItem = container.getItem(i);
            if (!inputItem.isEmpty() && RecyclerLogic.canRecycle(inputItem, level)) {
                ItemStack singleItem = inputItem.copyWithCount(1);
                inputItem.shrink(1);

                container.setItem(PROCESSING_SLOT, singleItem);
                processingTicks = PROCESSING_TIME;

                accumulateMatchingItems();

                singleShotPending = false; // Play solo inicia 1 ciclo y se apaga
                this.setChanged();
                return;
            }
        }

        // ES: No hay nada que reciclar en el input. Play (un solo ciclo) se
        // cancela, pero Auto se MANTIENE encendido: asi una tolva/tuberia que
        // entrega items con pausas no obliga a reactivarlo a mano. Auto solo
        // se apaga cuando el output esta lleno (ver attemptResolveProcessing).
        if (singleShotPending) {
            singleShotPending = false;
            this.setChanged();
        }
    }

    /**
     * ES: Intenta resolver el contenido del slot de proceso (llamado al llegar a 0
     * ticks, o cada tick mientras esta aparcado esperando espacio). Si el resultado
     * cabe en el output, lo coloca y limpia el slot. Si no cabe, detiene el modo
     * automatico pero deja los items aparcados para reintentar en el proximo tick.
     */
    private void attemptResolveProcessing() {
        ItemStack processStack = container.getItem(PROCESSING_SLOT);
        if (processStack.isEmpty() || this.level == null) {
            return;
        }

        ItemStack emptyBottle = container.getItem(BOTTLE_SLOT);
        ItemStack book = container.getItem(BOOK_SLOT);

        RecyclerLogic.RecyclingOutput output = RecyclerLogic.processRecycling(processStack, emptyBottle, book, this.level);

        List<ItemStack> results = output.results();
        if (results.isEmpty()) {
            results = new ArrayList<>();
            results.add(processStack.copy());
        }

        if (!canFitAllResults(results)) {
            autoMode = false;
            singleShotPending = false;
            this.setChanged();
            return;
        }

        for (ItemStack result : results) {
            placeInOutput(result);
        }

        if (output.bottlesConsumed() > 0) {
            emptyBottle.shrink(output.bottlesConsumed());
        }
        if (output.booksConsumed() > 0) {
            book.shrink(output.booksConsumed());
        }

        container.setItem(PROCESSING_SLOT, ItemStack.EMPTY);

        this.setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), 2);
        }
    }

    /**
     * ES: Soltar el inventario al romper el bloque (desde 1.21.5 se maneja
     * aqui, no en el Block). No se dispara por recarga de chunk.
     */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (this.level != null && !this.level.isClientSide()) {
            Containers.dropContents(this.level, pos, this.container);
        }
    }

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
        output.putBoolean("single_shot_pending", this.singleShotPending);
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
        this.singleShotPending = input.getBooleanOr("single_shot_pending", false);
    }
}
