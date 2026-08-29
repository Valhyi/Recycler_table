package com.valhyi.recyclertable.gui;

import com.valhyi.recyclertable.init.ModBlocks;
import com.valhyi.recyclertable.init.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public class RecyclerMenu extends AbstractContainerMenu {
    private final ItemStackHandler inventory;
    private final ContainerLevelAccess levelAccess;

    public RecyclerMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory, extraData.readBlockPos(), new ItemStackHandler(18));
    }

    public RecyclerMenu(int containerId, Inventory playerInventory, BlockPos pos, ItemStackHandler inventory) {
        super(ModMenuTypes.RECYCLER_MENU.get(), containerId);
        this.inventory = inventory;
        this.levelAccess = ContainerLevelAccess.create(playerInventory.player.level(), pos);

        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 9; j++) {
                this.addSlot(new SlotItemHandler(inventory, j + i * 9, 8 + j * 18, 18 + i * 18));
            }
        }

        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 9; ++j) {
                this.addSlot(new Slot(playerInventory, j + i * 9 + 9, 8 + j * 18, 84 + i * 18));
            }
        }

        for (int k = 0; k < 9; ++k) {
            this.addSlot(new Slot(playerInventory, k, 8 + k * 18, 142));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        // Asegúrate de usar el nombre correcto con el que registraste tu bloque en ModBlocks
        return stillValid(levelAccess, player, ModBlocks.RECYCLER_TABLE.get());
    }
}
