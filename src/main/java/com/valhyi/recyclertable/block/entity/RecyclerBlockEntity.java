package com.valhyi.recyclertable.block.entity;

import com.valhyi.recyclertable.gui.RecyclerMenu;
import com.valhyi.recyclertable.init.ModBlockEntities;
import com.valhyi.recyclertable.recipe.RecyclerLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
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
import org.jetbrains.annotations.Nullable;

public class RecyclerBlockEntity extends BlockEntity implements MenuProvider {
    private final SimpleContainer container = new SimpleContainer(21) {
        @Override
        public void setChanged() {
            super.setChanged();
            RecyclerBlockEntity.this.setChanged();
        }
    };

    private int processingTicks = 0;
    private static final int PROCESSING_TIME = 3;

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

        // Buscar item reciclable en el grid de entrada (slots 0-8)
        for (int i = 0; i < 9; i++) {
            ItemStack inputItem = container.getItem(i);
            if (!inputItem.isEmpty() && RecyclerLogic.canRecycle(inputItem, level)) {
                // Iniciar procesamiento
                processingTicks = PROCESSING_TIME;
                // Guardar el item en proceso en el slot central (slot 9)
                container.setItem(9, inputItem.copy());
                container.setItem(i, ItemStack.EMPTY);
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

        // Procesar el reciclaje
        java.util.List<ItemStack> results = RecyclerLogic.processRecycling(itemInProcess, emptyBottle, book, this.level);

        // Si no hay resultados, devolver el item original al output (item sin receta)
        if (results.isEmpty()) {
            results = new java.util.ArrayList<>();
            results.add(itemInProcess.copy());
        }

        // Colocar resultados en el grid de output (slots 12-20)
        boolean allResultsPlaced = true;
        
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
            
            if (!placed) {
                allResultsPlaced = false;
                break;
            }
        }

        // Si no se colocaron todos los resultados, devolver item al input
        if (!allResultsPlaced) {
            container.setItem(9, itemInProcess);
            return;
        }

        // Consumir recursos si fue encantado
        ItemEnchantments enchantments = itemInProcess.get(DataComponents.ENCHANTMENTS);
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
