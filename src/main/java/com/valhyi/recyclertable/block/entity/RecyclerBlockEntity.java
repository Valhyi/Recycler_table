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

    // ES: Con Auto encendido y el input vacio, Auto sigue activo este numero de
    // ticks (240 = 12 s) esperando mas items (ej. de una tolva). Si en ese lapso
    // no llega nada, se apaga solo. No se guarda en NBT (es transitorio).
    private static final int AUTO_IDLE_TIMEOUT = 240;
    private int autoIdleTicks = 0;

    // ================= ESTADO "APARCADO" =================
    // ES: Un item queda aparcado cuando el ciclo termino pero el resultado no
    // cabe en el output. Mientras esta aparcado el slot de proceso es
    // recogible por el jugador (ver RecyclerSlots.ProcessSlot).
    // - parked: hay un item aparcado (se guarda en NBT).
    // - parkedWaiting: todavia se reintenta colocar el resultado. Se corta
    //   tras PARKED_WAIT_TIMEOUT ticks; el item sigue aparcado y recogible,
    //   y Play (o encender Auto) reinicia la espera.
    private static final int PARKED_WAIT_TIMEOUT = 240;
    private boolean parked = false;
    private boolean parkedWaiting = false;
    private int parkedTicks = 0;

    // ================= RESULTADO CACHEADO (transitorio, sin NBT) =================
    // ES: El resultado del reciclaje se calcula UNA vez por ciclo y se guarda
    // aqui; mientras el item esta aparcado solo se repite la comprobacion
    // barata canFitAllResults. Se recalcula si cambia el item del slot de
    // proceso (ej. el jugador saco la mitad) o la cantidad de botellas/libros.
    private List<ItemStack> pendingResults = null;
    private int pendingBottlesConsumed = 0;
    private int pendingBooksConsumed = 0;
    private ItemStack pendingSource = ItemStack.EMPTY;
    private int pendingBottleCount = 0;
    private int pendingBookCount = 0;

    private final net.minecraft.world.inventory.ContainerData dataAccess = new net.minecraft.world.inventory.ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> autoMode ? 1 : 0;
                // ES: "Procesando" = contando ticks o single-shot pendiente.
                case 1 -> (processingTicks > 0 || singleShotPending) ? 1 : 0;
                // ES: "Aparcado" = el slot de proceso es recogible.
                case 2 -> parked ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            if (index == 0) {
                autoMode = value != 0;
            }
            // index 1 y 2 son de solo lectura en el cliente; no-op
        }

        @Override
        public int getCount() {
            return RecyclerMenu.DATA_COUNT;
        }
    };

    public net.minecraft.world.inventory.ContainerData getDataAccess() {
        return dataAccess;
    }

    public void triggerSingleShot() {
        // ES: Con un item aparcado, Play reinicia la espera de espacio.
        if (parked) {
            restartParkedWait();
            return;
        }
        if (processingTicks == 0 && container.getItem(PROCESSING_SLOT).isEmpty()) {
            singleShotPending = true;
            this.setChanged();
        }
    }

    public void toggleAutoMode() {
        autoMode = !autoMode;
        autoIdleTicks = 0;
        if (autoMode && parked) {
            restartParkedWait();
        }
        this.setChanged();
    }

    public boolean isAutoMode() {
        return autoMode;
    }

    private void restartParkedWait() {
        if (!parked) return;
        parkedWaiting = true;
        parkedTicks = 0;
        this.setChanged();
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

    /**
     * ES: Limpia TODO el estado del ciclo actual (ticks, aparcado y
     * resultado cacheado). Se usa al terminar un ciclo con exito, o cuando
     * el slot de proceso quedo vacio (el jugador recogio el item aparcado).
     */
    private void resetProcessingState() {
        processingTicks = 0;
        parked = false;
        parkedWaiting = false;
        parkedTicks = 0;
        pendingResults = null;
        pendingBottlesConsumed = 0;
        pendingBooksConsumed = 0;
        pendingSource = ItemStack.EMPTY;
        pendingBottleCount = 0;
        pendingBookCount = 0;
    }

    public void tick(Level level, BlockPos pos, BlockState state) {
        if (level == null || level.isClientSide()) {
            return;
        }

        // Mover botellas y libros del input a los espacios restringidos
        moveRestrictedItemsFromInput();

        ItemStack processStack = container.getItem(PROCESSING_SLOT);

        if (processStack.isEmpty()) {
            // ES: Slot vacio pero quedaba estado de un ciclo (el jugador
            // recogio el item aparcado, o fue extraido por otra via): se
            // cancela todo para no dejar contadores desfasados.
            if (processingTicks > 0 || parked || pendingResults != null) {
                resetProcessingState();
                this.setChanged();
            }
        } else {
            if (parked) {
                tickParked();
            } else if (processingTicks > 0) {
                accumulateMatchingItems();
                processingTicks--;
                if (processingTicks == 0) {
                    attemptResolveProcessing();
                }
            } else {
                // ES: Item en el slot sin ciclo activo (ej. mundo guardado con
                // una version anterior): se intenta resolver directamente.
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
                parked = false;
                parkedWaiting = false;
                parkedTicks = 0;
                pendingResults = null;

                accumulateMatchingItems();

                singleShotPending = false; // Play solo inicia 1 ciclo y se apaga
                autoIdleTicks = 0;
                this.setChanged();
                return;
            }
        }

        // ES: No hay nada que reciclar en el input. Play (un solo ciclo) se
        // cancela. Auto sigue encendido AUTO_IDLE_TIMEOUT ticks esperando mas
        // items (tolvas con pausas entre entregas); pasado ese tiempo sin que
        // llegue nada, se apaga solo. Tambien se apaga si el output esta lleno
        // (ver attemptResolveProcessing).
        if (singleShotPending) {
            singleShotPending = false;
            this.setChanged();
        }
        if (autoMode) {
            autoIdleTicks++;
            if (autoIdleTicks >= AUTO_IDLE_TIMEOUT) {
                autoMode = false;
                autoIdleTicks = 0;
                this.setChanged();
            }
        }
    }

    /**
     * ES: Calcula (si hace falta) el resultado del reciclaje del slot de
     * proceso y lo deja en pendingResults. Si ya hay un resultado calculado
     * para el MISMO item/cantidad y las MISMAS botellas/libros, no hace nada
     * (esto es lo que evita recorrer todas las recetas en cada tick mientras
     * el item esta aparcado).
     */
    private void ensurePending(ItemStack processStack) {
        ItemStack emptyBottle = container.getItem(BOTTLE_SLOT);
        ItemStack book = container.getItem(BOOK_SLOT);

        if (pendingResults != null
                && pendingBottleCount == emptyBottle.getCount()
                && pendingBookCount == book.getCount()
                && ItemStack.matches(pendingSource, processStack)) {
            return;
        }

        RecyclerLogic.RecyclingOutput output = RecyclerLogic.processRecycling(processStack, emptyBottle, book, this.level);

        List<ItemStack> results = new ArrayList<>(output.results());
        if (results.isEmpty()) {
            results.add(processStack.copy());
        }

        pendingResults = results;
        pendingBottlesConsumed = output.bottlesConsumed();
        pendingBooksConsumed = output.booksConsumed();
        pendingSource = processStack.copy();
        pendingBottleCount = emptyBottle.getCount();
        pendingBookCount = book.getCount();
    }

    /**
     * ES: Intenta colocar pendingResults en el output. Si caben, los coloca,
     * consume botellas/libros, vacia el slot de proceso y limpia el estado.
     * Devuelve false (sin tocar nada) si no hay espacio.
     */
    private boolean tryPlacePending() {
        if (pendingResults == null || this.level == null) {
            return false;
        }
        if (!canFitAllResults(pendingResults)) {
            return false;
        }

        for (ItemStack result : pendingResults) {
            placeInOutput(result);
        }

        ItemStack emptyBottle = container.getItem(BOTTLE_SLOT);
        ItemStack book = container.getItem(BOOK_SLOT);
        if (pendingBottlesConsumed > 0) {
            emptyBottle.shrink(pendingBottlesConsumed);
        }
        if (pendingBooksConsumed > 0) {
            book.shrink(pendingBooksConsumed);
        }

        container.setItem(PROCESSING_SLOT, ItemStack.EMPTY);
        resetProcessingState();

        this.setChanged();
        this.level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), 2);
        return true;
    }

    /**
     * ES: Llamado al llegar el ciclo a 0 ticks. Calcula el resultado una vez
     * y, si cabe en el output, lo coloca. Si no cabe, el item queda APARCADO:
     * el slot de proceso pasa a ser recogible, se detiene el modo automatico
     * y se reintenta (barato) durante PARKED_WAIT_TIMEOUT ticks.
     */
    private void attemptResolveProcessing() {
        ItemStack processStack = container.getItem(PROCESSING_SLOT);
        if (processStack.isEmpty() || this.level == null) {
            return;
        }

        ensurePending(processStack);

        if (tryPlacePending()) {
            return;
        }

        parked = true;
        parkedWaiting = true;
        parkedTicks = 0;
        processingTicks = 0;
        autoMode = false;
        singleShotPending = false;
        this.setChanged();
    }

    /**
     * ES: Un tick con un item aparcado. Solo repite la comprobacion de espacio
     * (el resultado ya esta calculado). Tras PARKED_WAIT_TIMEOUT ticks deja de
     * reintentar; el item sigue aparcado y recogible hasta que el jugador lo
     * saque o pulse Play / encienda Auto para reiniciar la espera.
     */
    private void tickParked() {
        if (!parkedWaiting) {
            return;
        }

        ItemStack processStack = container.getItem(PROCESSING_SLOT);
        ensurePending(processStack);

        if (tryPlacePending()) {
            return;
        }

        parkedTicks++;
        if (parkedTicks >= PARKED_WAIT_TIMEOUT) {
            parkedWaiting = false;
            this.setChanged();
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
        output.putBoolean("parked", this.parked);
        output.putBoolean("parked_waiting", this.parkedWaiting);
        output.putInt("parked_ticks", this.parkedTicks);
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
        this.parked = input.getBooleanOr("parked", false);
        this.parkedWaiting = input.getBooleanOr("parked_waiting", false);
        this.parkedTicks = input.getIntOr("parked_ticks", 0);

        // ES: El resultado cacheado no se guarda; se recalcula una vez.
        this.pendingResults = null;
        this.pendingSource = ItemStack.EMPTY;
    }
}
