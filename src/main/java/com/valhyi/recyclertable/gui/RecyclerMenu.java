package com.valhyi.recyclertable.gui;

import com.valhyi.recyclertable.init.ModMenuTypes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public class RecyclerMenu extends AbstractContainerMenu {
    private final IItemHandler itemHandler;

    public RecyclerMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new ItemStackHandler(21));
    }

    public RecyclerMenu(int containerId, Inventory playerInventory, IItemHandler itemHandler) {
        super(ModMenuTypes.RECYCLER_MENU.get(), containerId);
        this.itemHandler = itemHandler;

        // 1. Input Grid (3x3) - Izquierda (Verde)
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                this.addSlot(new SlotItemHandler(itemHandler, j + i * 3, 8 + j * 18, 17 + i * 18));
            }
        }

        // 2. Zona Central (Centro)
        // Slot superior: Item en proceso (Rosa) -> Índice 9
        this.addSlot(new SlotItemHandler(itemHandler, 9, 80, 17));
        // Slot inferior izquierdo: Botella vacía (Amarillo) -> Índice 10
        this.addSlot(new SlotItemHandler(itemHandler, 10, 71, 35));
        // Slot inferior derecho: Libros (Rojo) -> Índice 11
        this.addSlot(new SlotItemHandler(itemHandler, 11, 89, 35));

        // 3. Output Grid (3x3) - Derecha (Cian) -> Índices 12 a 20
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                this.addSlot(new SlotItemHandler(itemHandler, 12 + j + i * 3, 116 + j * 18, 17 + i * 18));
            }
        }

        // 4. Inventario del jugador
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 9; ++j) {
                this.addSlot(new Slot(playerInventory, j + i * 9 + 9, 8 + j * 18, 84 + i * 18));
            }
        }

        // 5. Hotbar del jugador
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
        return true;
    }
}
