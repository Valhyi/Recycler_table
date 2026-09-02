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
        this.addSlot(new Slot(container, 9, 80, 17) {
            @Override
            public boolean mayPlace(ItemStack itemStack) {
                return false; // No se puede colocar items
            }
        });

        // Slot 10: Botella vacía (bloqueado para solo botellas vacías)
        this.addSlot(new RestrictedSlot(container, 10, 71, 35, Items.GLASS_BOTTLE));

        // Slot 11: Libro (bloqueado para solo libros)
        this.addSlot(new RestrictedSlot(container, 11, 89, 35, Items.BOOK));

        // 3. Output Grid (3x3) - Derecha -> Índices 12 al 20
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                this.addSlot(new OutputSlot(container, 12 + j + i * 3, 116 + j * 18, 17 + i * 18));
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
        return this.container.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.container.stopOpen(player);
    }

    /**
     * Slot personalizado para botellas vacías y libros
     */
    private static class RestrictedSlot extends Slot {
        private final ItemStack restrictedItem;

        public RestrictedSlot(Container container, int index, int x, int y, ItemStack restrictedItem) {
            super(container, index, x, y);
            this.restrictedItem = restrictedItem;
        }

        @Override
        public boolean mayPlace(ItemStack itemStack) {
            return itemStack.is(this.restrictedItem.getItem());
        }

        @Override
        public int getMaxStackSize() {
            return 64;
        }
    }

    /**
     * Slot de output (solo lectura)
     */
    private static class OutputSlot extends Slot {
        public OutputSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack itemStack) {
            return false; // No se puede colocar items en output
        }

        @Override
        public void set(ItemStack itemStack) {
            super.set(itemStack);
        }
    }
}
