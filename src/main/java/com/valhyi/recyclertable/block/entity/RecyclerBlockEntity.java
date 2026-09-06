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
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.Nullable;

public class RecyclerBlockEntity extends BlockEntity implements MenuProvider {
    private final SimpleContainer container = new SimpleContainer(21);

    private int processingTicks = 0;
    private static final int PROCESSING_TIME = 6; // Cada 6 ticks se procesa 1 item

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
        return new RecyclerMenu(containerId, playerInventory, this.container);
    }

    public SimpleContainer getContainer() {
        return container;
    }

    /**
     * Hopper compatibility: Provides ItemHandler capability for hopper interaction
     * Slots 0-8: Input Grid (hoppers can insert)
     * Slots 9-11: Processing/Resources (restricted, hoppers cannot access)
     * Slots 12-20: Output Grid (hoppers can extract)
     */
    public static ResourceHandler<ItemResource> getCapability(RecyclerBlockEntity blockEntity, Direction direction) {
        return new ItemHandlerAdapter(blockEntity.container);
    }

    /**
     * Adapter that restricts hopper access to specific slots only
     */
    private static class ItemHandlerAdapter implements IItemHandler {
        private final SimpleContainer container;

        ItemHandlerAdapter(SimpleContainer container) {
            this.container = container;
        }

        @Override
        public int getSlots() {
            // Only expose input (0-8) and output (12-20) slots = 18 slots
            return 18;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            // Map virtual slots to actual container slots
            // Virtual 0-8 -> Container 0-8 (input)
            // Virtual 9-17 -> Container 12-20 (output)
            int actualSlot = slot < 9 ? slot : slot + 3;
            if (actualSlot >= 0 && actualSlot < container.getContainerSize()) {
                return container.getItem(actualSlot);
            }
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            // Only allow insertion into input slots (virtual 0-8 = actual 0-8)
            if (slot >= 9) {
                return stack; // Cannot insert into output slots
            }

            ItemStack toInsert = stack.copy();
            int actualSlot = slot;

            // Try to insert into the specified slot
            ItemStack existing = container.getItem(actualSlot);
            if (existing.isEmpty()) {
                if (!simulate) {
                    ItemStack placed = toInsert.copy();
                    placed.setCount(Math.min(toInsert.getCount(), placed.getMaxStackSize()));
                    container.setItem(actualSlot, placed);
                    toInsert.shrink(placed.getCount());
                }
            } else if (ItemStack.isSameItemSameComponents(existing, toInsert)) {
                int space = existing.getMaxStackSize() - existing.getCount();
                if (space > 0) {
                    int transfer = Math.min(space, toInsert.getCount());
                    if (!simulate) {
                        existing.grow(transfer);
                        toInsert.shrink(transfer);
                    } else {
                        toInsert.shrink(transfer);
                    }
                }
            }

            return toInsert;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            // Only allow extraction from output slots (virtual 9-17 = actual 12-20)
            if (slot < 9) {
                return ItemStack.EMPTY; // Cannot extract from input slots
            }

            int actualSlot = slot + 3; // Virtual 9 = Actual 12, Virtual 17 = Actual 20
            ItemStack existing = container.getItem(actualSlot);

            if (existing.isEmpty() || amount <= 0) {
                return ItemStack.EMPTY;
            }

            int canExtract = Math.min(amount, existing.getCount());
            if (!simulate) {
                ItemStack extracted = existing.split(canExtract);
                if (existing.isEmpty()) {
                    container.setItem(actualSlot, ItemStack.EMPTY);
                }
                return extracted;
            } else {
                return existing.copy().withCount(canExtract);
            }
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            // All input and output slots accept any item
            return slot >= 0 && slot < getSlots();
        }
    }

    /**
     * Called by BlockEntityTicker every server tick
     * Procesa un item del grid de entrada
     * Slots 0-8: Input Grid
     * Slot 9: Item en proceso (lectura)
     * Slot 10: Botella vacía (restringido)
     * Slot 11: Libro (restringido)
     * Slots 12-20: Output Grid
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, RecyclerBlockEntity entity) {
        entity.tick(level, pos, state);
    }

    /**
     * Verifica si hay espacio en el output para colocar todos los items
     */
    private boolean canFitAllResults(java.util.List<ItemStack> results) {
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

    public void tick(Level level, BlockPos pos, BlockState state) {
        if (level == null || level.isClientSide()) {
            return;
        }

        // Si está procesando, decrementar contador
        if (processingTicks > 0) {
            processingTicks--;
            if (processingTicks == 0) {
                completeRecycling();
            }
            return;
        }

        // Si hay un item en slot 9, NO hacer nada (esperar a que se procese)
        if (!container.getItem(9).isEmpty()) {
            return;
        }

        // Buscar item reciclable en el grid de entrada (slots 0-8)
        for (int i = 0; i < 9; i++) {
            ItemStack inputItem = container.getItem(i);
            if (!inputItem.isEmpty() && RecyclerLogic.canRecycle(inputItem, level)) {
                // Crear una copia de solo 1 item
                ItemStack singleItem = inputItem.copy();
                singleItem.setCount(1);
                
                // Obtener botellas vacías y libros
                ItemStack emptyBottle = container.getItem(10);
                ItemStack book = container.getItem(11);

                // Procesar para obtener los resultados
                java.util.List<ItemStack> results = RecyclerLogic.processRecycling(singleItem, emptyBottle, book, level);
                
                // Si no hay resultados, usar el item original
                if (results.isEmpty()) {
                    results = new java.util.ArrayList<>();
                    results.add(singleItem.copy());
                }

                // VERIFICAR SI CABE ANTES DE CONSUMIR EL ITEM
                if (!canFitAllResults(results)) {
                    // No hay espacio, NO hacer nada (pausa automática)
                    return;
                }

                // HAY ESPACIO: Reducir el item del input
                inputItem.shrink(1);
                
                // Guardar el item en proceso en el slot central (slot 9)
                container.setItem(9, singleItem);
                
                // Iniciar procesamiento
                processingTicks = PROCESSING_TIME;
                this.setChanged();
                return;
            }
        }
    }

    /**
     * Completa el reciclaje y coloca los resultados en el output
     */
    private void completeRecycling() {
        ItemStack itemInProcess = container.getItem(9);
        if (itemInProcess.isEmpty() || this.level == null) {
            return;
        }

        // Obtener botellas vacías y libros de los slots centrales
        ItemStack emptyBottle = container.getItem(10);
        ItemStack book = container.getItem(11);

        // Procesar el reciclaje (solo 1 item)
        java.util.List<ItemStack> results = RecyclerLogic.processRecycling(itemInProcess, emptyBottle, book, this.level);

        // Si no hay resultados, devolver el item original al output (item sin receta)
        if (results.isEmpty()) {
            results = new java.util.ArrayList<>();
            results.add(itemInProcess.copy());
        }

        // Colocar resultados en el grid de output (slots 12-20)
        for (ItemStack result : results) {
            boolean placed = false;
            
            // Intentar colocar en un slot vacío o stackeable
            for (int slot = 12; slot <= 20; slot++) {
                ItemStack existingItem = container.getItem(slot);
                if (existingItem.isEmpty()) {
                    container.setItem(slot, result.copy());
                    placed = true;
                    break;
                } else if (ItemStack.isSameItemSameComponents(existingItem, result)) {
                    int space = existingItem.getMaxStackSize() - existingItem.getCount();
                    if (space > 0) {
                        int transfer = Math.min(space, result.getCount());
                        existingItem.grow(transfer);
                        result.shrink(transfer);
                        if (result.isEmpty()) {
                            placed = true;
                            break;
                        }
                    }
                }
            }
        }

        // Consumir recursos si fue encantado
        ItemEnchantments enchantments = itemInProcess.get(net.minecraft.core.component.DataComponents.ENCHANTMENTS);
        if (enchantments != null && !enchantments.isEmpty()) {
            if (!emptyBottle.isEmpty()) {
                emptyBottle.shrink(1);
            }
            if (!book.isEmpty()) {
                book.shrink(1);
            }
        }

        // Limpiar slot de proceso
        container.setItem(9, ItemStack.EMPTY);

        this.setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), 2);
        }
    }

    /**
     * Llamado cuando el bloque es destruido
     * Devuelve todos los items de los contenedores
     */
    @Override
    public void setRemoved() {
        // Soltar todos los items del contenedor
        if (this.level != null && !this.level.isClientSide()) {
            // Slots 0-8: Input Grid
            for (int i = 0; i < 9; i++) {
                Containers.dropItemStack(this.level, this.worldPosition.getX(), this.worldPosition.getY(), this.worldPosition.getZ(), container.getItem(i));
            }
            
            // Slot 9: Item en proceso
            Containers.dropItemStack(this.level, this.worldPosition.getX(), this.worldPosition.getY(), this.worldPosition.getZ(), container.getItem(9));
            
            // Slot 10: Botella vacía
            Containers.dropItemStack(this.level, this.worldPosition.getX(), this.worldPosition.getY(), this.worldPosition.getZ(), container.getItem(10));
            
            // Slot 11: Libro
            Containers.dropItemStack(this.level, this.worldPosition.getX(), this.worldPosition.getY(), this.worldPosition.getZ(), container.getItem(11));
            
            // Slots 12-20: Output Grid
            for (int i = 12; i < 21; i++) {
                Containers.dropItemStack(this.level, this.worldPosition.getX(), this.worldPosition.getY(), this.worldPosition.getZ(), container.getItem(i));
            }
        }
        
        super.setRemoved();
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
    }
}
