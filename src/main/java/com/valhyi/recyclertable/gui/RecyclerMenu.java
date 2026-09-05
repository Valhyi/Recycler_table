package com.valhyi.recyclertable.gui;

import com.valhyi.recyclertable.block.entity.RecyclerBlockEntity;
import com.valhyi.recyclertable.init.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;

public class RecyclerMenu extends AbstractContainerMenu {
    private final Container container;

    // Constantes de slots
    private static final int CONTAINER_SIZE = 21;
    private static final int INPUT_SLOTS_START = 0;   // 0-8
    private static final int INPUT_SLOTS_END = 9;
    private static final int PROCESSING_SLOT = 9;
    private static final int BOTTLE_SLOT = 10;
    private static final int BOOK_SLOT = 11;
    private static final int OUTPUT_SLOTS_START = 12;  // 12-20
    private static final int OUTPUT_SLOTS_END = 21;
    private static final int PLAYER_INV_START = 21;
    private static final int PLAYER_INV_END = 48;
    private static final int PLAYER_HOTBAR_START = 48;
    private static final int PLAYER_HOTBAR_END = 57;

    public RecyclerMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory, extraData.readBlockPos());
    }

    public RecyclerMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        this(containerId, playerInventory, getBlockEntity(playerInventory, pos));
    }

    private static BlockEntity getBlockEntity(Inventory playerInventory, BlockPos pos) {
        return playerInventory.player.level().getBlockEntity(pos);
    }

    public RecyclerMenu(int containerId, Inventory playerInventory, BlockEntity blockEntity) {
        this(containerId, playerInventory, blockEntity instanceof RecyclerBlockEntity recycler ? recycler.getContainer() : new SimpleContainer(21));
    }

    public RecyclerMenu(int containerId, Inventory playerInventory, Container container) {
        super(ModMenuTypes.RECYCLER_MENU.get(), containerId);
        this.container = container;
        checkContainerSize(container, 21);
        container.startOpen(playerInventory.player);

        // 1. Input Grid (3x3) - Izquierda -> Índices 0 al 8
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                this.addSlot(new Slot(container, j + i * 3, 8 + j * 18, 17 + i * 18));
            }
        }

        // 2. Zona Central -> Índices 9, 10 y 11
        // Slot 9: Item en proceso (solo lectura)
        this.addSlot(new RecyclerSlots.ProcessSlot(container, 9, 80, 17));

        // Slot 10: Botella vacía (bloqueado para solo botellas vacías)
        this.addSlot(new RecyclerSlots.RestrictedSlot(container, 10, 71, 35, new ItemStack(Items.GLASS_BOTTLE)));

        // Slot 11: Libro (bloqueado para solo libros)
        this.addSlot(new RecyclerSlots.RestrictedSlot(container, 11, 89, 35, new ItemStack(Items.BOOK)));

        // 3. Output Grid (3x3) - Derecha -> Índices 12 al 20
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                this.addSlot(new RecyclerSlots.OutputSlot(container, 12 + j + i * 3, 116 + j * 18, 17 + i * 18));
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
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack itemStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(slotIndex);

        if (slot != null && slot.hasItem()) {
            ItemStack slotStack = slot.getItem();
            itemStack = slotStack.copy();

            // Si es del inventario del jugador o hotbar -> mover al input de la mesa
            if (slotIndex >= PLAYER_INV_START) {
                if (!this.moveItemStackTo(slotStack, INPUT_SLOTS_START, INPUT_SLOTS_END, false)) {
                    return ItemStack.EMPTY;
                }
            }
            // Si es del output de la mesa -> mover al inventario del jugador
            else if (slotIndex >= OUTPUT_SLOTS_START && slotIndex < OUTPUT_SLOTS_END) {
                if (!this.moveItemStackTo(slotStack, PLAYER_INV_START, PLAYER_HOTBAR_END, true)) {
                    return ItemStack.EMPTY;
                }
            }
            // Si es del input de la mesa -> mover al inventario del jugador
            else if (slotIndex >= INPUT_SLOTS_START && slotIndex < INPUT_SLOTS_END) {
                if (!this.moveItemStackTo(slotStack, PLAYER_INV_START, PLAYER_HOTBAR_END, true)) {
                    return ItemStack.EMPTY;
                }
            }
            // Slots centrales (9, 10, 11) no se pueden mover con shift-click
            else {
                return ItemStack.EMPTY;
            }

            if (slotStack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }

        return itemStack;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.container.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.container.stopOpen(player);
    }
}
