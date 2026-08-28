package com.valhyi.recyclertable.gui;

import com.valhyi.recyclertable.init.ModBlocks;
import com.valhyi.recyclertable.init.ModMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public class RecyclerMenu extends AbstractContainerMenu {

    private final BlockPos blockPos;
    private final ItemStackHandler inventory;

    // ES: Constructor llamado desde el lado cliente
    // EN: Constructor called from client side
    public RecyclerMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory, extraData.readBlockPos(), new ItemStackHandler(18));
    }

    // ES: Constructor servidor / contenedor principal
    // EN: Server / main container constructor
    public RecyclerMenu(int containerId, Inventory playerInventory, BlockPos pos, ItemStackHandler inventory) {
        super(ModMenuTypes.RECYCLER_MENU.get(), containerId);
        this.blockPos = pos;
        this.inventory = inventory;

        // ES: Slots de la Mesa Recicladora (Entrada / Salida)
        // EN: Recycler Table slots (Input / Output)
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                this.addSlot(new SlotItemHandler(inventory, j + i * 3, 30 + j * 18, 17 + i * 18));
            }
        }

        // ES: Inventario del Jugador
        // EN: Player Inventory
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 9; ++j) {
                this.addSlot(new Slot(playerInventory, j + i * 9 + 9, 8 + j * 18, 84 + i * 18));
            }
        }

        // ES: Hotbar del Jugador
        // EN: Player Hotbar
        for (int k = 0; k < 9; ++k) {
            this.addSlot(new Slot(playerInventory, k, 8 + k * 18, 142));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();
            itemstack = itemstack1.copy();
            if (index < 18) {
                if (!this.moveItemStackTo(itemstack1, 18, 54, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(itemstack1, 0, 18, false)) {
                return ItemStack.EMPTY;
            }

            if (itemstack1.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return itemstack;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(player.level(), blockPos), player, ModBlocks.RECYCLER_TABLE.get());
    }
}
