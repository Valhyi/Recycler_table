package com.valhyi.recyclertable.block.entity;

import com.valhyi.recyclertable.gui.RecyclerMenu;
import com.valhyi.recyclertable.init.ModBlockEntities;
import com.valhyi.recyclertable.recipe.RecyclerLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
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
    private static final int PROCESSING_TIME = 3; // 3 ticks como especificaste

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
     * Procesa un item del grid de entrada
     */
    public void tick() {
        if (this.level == null || this.level.isClientSide()) {
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
            if (!inputItem.isEmpty() && RecyclerLogic.canRecycle(inputItem, this.level)) {
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

        // Colocar resultados en el grid de output (slots 12-20)
        int outputSlot = 12;
        for (ItemStack result : results) {
            if (outputSlot > 20) {
                // Si el output está lleno, devolver item al input
                container.setItem(9, itemInProcess);
                return;
            }

            ItemStack existingItem = container.getItem(outputSlot);
            if (existingItem.isEmpty()) {
                container.setItem(outputSlot, result);
            } else if (ItemStack.isSameItemSameComponents(existingItem, result) && existingItem.getCount() < existingItem.getMaxStackSize()) {
                existingItem.grow(result.getCount());
            } else {
                outputSlot++;
                if (outputSlot <= 20) {
                    container.setItem(outputSlot, result);
                }
            }
            outputSlot++;
        }

        // Limpiar slots de proceso
        container.setItem(9, ItemStack.EMPTY);
        
        // Consumir recursos si fue encantado
        if (itemInProcess.hasEnchantments()) {
            if (!emptyBottle.isEmpty()) {
                emptyBottle.shrink(1);
            }
            if (!book.isEmpty()) {
                book.shrink(1);
            }
        }

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
