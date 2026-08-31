package com.valhyi.recyclertable.gui;

import com.valhyi.recyclertable.init.ModBlocks;
import com.valhyi.recyclertable.init.ModMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public class RecyclerMenu extends AbstractContainerMenu {
    private final ContainerLevelAccess access;
    private final IItemHandler inventory;

    // Constructor para el cliente
    public RecyclerMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory, playerInventory.player.level().getBlockEntity(extraData.readBlockPos()), ContainerLevelAccess.NULL);
    }

    // Constructor para el servidor
    public RecyclerMenu(int containerId, Inventory playerInventory, BlockEntity entity, ContainerLevelAccess access) {
        super(ModMenuTypes.RECYCLER_MENU.get(), containerId);
        this.access = access;

        // Si la entidad tiene un inventario, lo usamos; si no, creamos uno vacío temporal
        if (entity instanceof com.valhyi.recyclertable.block.entity.RecyclerBlockEntity recyclerEntity) {
            this.inventory = recyclerEntity.getItemHandler();
        } else {
            this.inventory = new ItemStackHandler(21);
        }

        // Inventario Izquierdo (3x3)
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                this.addSlot(new SlotItemHandler(inventory, j + i * 3, 8 + j * 18, 17 + i * 18));
            }
        }

        // Centro (Pirámide de 3 slots)
        this.addSlot(new SlotItemHandler(inventory, 9, 80, 17));
        this.addSlot(new SlotItemHandler(inventory, 10, 71, 35));
        this.addSlot(new SlotItemHandler(inventory, 11, 89, 35));

        // Inventario Derecho (3x3)
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                this.addSlot(new SlotItemHandler(inventory, 12 + j + i * 3, 116 + j * 18, 17 + i * 18));
            }
        }

        // Inventario del Jugador (27 slots)
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 9; ++j) {
                this.addSlot(new Slot(playerInventory, j + i * 9 + 9, 8 + j * 18, 84 + i * 18));
            }
        }

        // Hotbar del Jugador (9 slots)
        for (int k = 0; k < 9; ++k) {
            this.addSlot(new Slot(playerInventory, k, 8 + k * 18, 142));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(this.access, player, ModBlocks.RECYCLER_TABLE.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot != null && slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();
            itemstack = itemstack1.copy();

            // Lógica de Shift-Click: Si hace click en la mesa (índices 0-20), mueve al jugador (21-56)
            if (index < 21) {
                if (!this.moveItemStackTo(itemstack1, 21, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(itemstack1, 0, 21, false)) { // Del jugador a la mesa
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
}
