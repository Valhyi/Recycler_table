package com.valhyi.recyclertable.block.entity;

import com.valhyi.recyclertable.gui.RecyclerMenu;
import com.valhyi.recyclertable.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ValueInput;
import net.minecraft.util.ValueOutput;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;
import java.io.IOException;

public class RecyclerBlockEntity extends BlockEntity implements MenuProvider {

    private final ItemStackHandler itemHandler = new ItemStackHandler(18) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    public RecyclerBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.RECYCLER_BE.get(), pos, blockState);
    }

    public ItemStackHandler getItemHandler() {
        return itemHandler;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.recyclertable.recycler_table");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new RecyclerMenu(containerId, playerInventory, this.worldPosition, this.itemHandler);
    }

    // ES: Nuevos métodos obligatorios de persistencia binaria en Minecraft 26.2
    // EN: New mandatory binary persistence methods in Minecraft 26.2
    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        try {
            // Guardamos el tamaño y los slots del inventario directamente en el flujo binario
            output.writeInt(itemHandler.getSlots());
            for (int i = 0; i < itemHandler.getSlots(); i++) {
                net.minecraft.world.item.ItemStack.STREAM_CODEC.encode(output, itemHandler.getStackInSlot(i));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to save Recycler Inventory", e);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        try {
            int slots = input.readInt();
            for (int i = 0; i < slots; i++) {
                if (i < itemHandler.getSlots()) {
                    itemHandler.setStackInSlot(i, net.minecraft.world.item.ItemStack.STREAM_CODEC.decode(input));
                } else {
                    net.minecraft.world.item.ItemStack.STREAM_CODEC.decode(input); // Ignorar si excede
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to load Recycler Inventory", e);
        }
    }
}
