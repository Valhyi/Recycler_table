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
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;

public class RecyclerMenu extends AbstractContainerMenu {
    private final Container container;
    private final ContainerData data;
    private final BlockPos blockPos;

    // ================= INDICES DE SLOTS (fuente unica de verdad) =================
    // ES: RecyclerBlockEntity y RecyclerScreen usan estas constantes. Para
    // cambiar el tamano de los grids solo hay que tocar GRID_COLS / GRID_ROWS.
    public static final int GRID_COLS = 6;
    public static final int GRID_ROWS = 3;
    public static final int GRID_SIZE = GRID_COLS * GRID_ROWS; // 18

    public static final int INPUT_SLOTS_START = 0;                       // 0-17
    public static final int INPUT_SLOTS_END = GRID_SIZE;                 // exclusivo
    public static final int PROCESSING_SLOT = GRID_SIZE;                 // 18
    public static final int BOTTLE_SLOT = GRID_SIZE + 1;                 // 19
    public static final int BOOK_SLOT = GRID_SIZE + 2;                   // 20
    public static final int OUTPUT_SLOTS_START = GRID_SIZE + 3;          // 21-38
    public static final int OUTPUT_SLOTS_END = OUTPUT_SLOTS_START + GRID_SIZE; // exclusivo
    public static final int CONTAINER_SIZE = OUTPUT_SLOTS_END;           // 39

    private static final int PLAYER_INV_START = CONTAINER_SIZE;
    private static final int PLAYER_INV_END = PLAYER_INV_START + 27;
    private static final int PLAYER_HOTBAR_START = PLAYER_INV_END;
    private static final int PLAYER_HOTBAR_END = PLAYER_HOTBAR_START + 9;

    // ================= LAYOUT (coordenadas relativas al GUI) =================
    private static final int SLOT_SIZE = 18;
    private static final int MARGIN = 8;
    private static final int CENTER_ZONE_WIDTH = 36;
    private static final int CENTER_GAP = 10;

    public static final int INPUT_X = MARGIN;
    public static final int GRID_Y = 17;
    public static final int CENTER_X = INPUT_X + GRID_COLS * SLOT_SIZE + CENTER_GAP;
    public static final int CENTER_MID = CENTER_X + CENTER_ZONE_WIDTH / 2;
    public static final int OUTPUT_X = CENTER_X + CENTER_ZONE_WIDTH + CENTER_GAP;

    public static final int IMAGE_WIDTH = OUTPUT_X + GRID_COLS * SLOT_SIZE + MARGIN;
    public static final int IMAGE_HEIGHT = 166;

    public static final int PLAYER_INV_X = (IMAGE_WIDTH - 9 * SLOT_SIZE) / 2;
    private static final int PLAYER_INV_Y = 84;
    private static final int HOTBAR_Y = 142;

    public RecyclerMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, playerInventory, extraData.readBlockPos());
    }

    public RecyclerMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        this(containerId, playerInventory, pos, getBlockEntity(playerInventory, pos));
    }

    private static BlockEntity getBlockEntity(Inventory playerInventory, BlockPos pos) {
        return playerInventory.player.level().getBlockEntity(pos);
    }

    private RecyclerMenu(int containerId, Inventory playerInventory, BlockPos pos, BlockEntity blockEntity) {
        this(containerId, playerInventory, pos, resolveContainer(blockEntity), resolveData(blockEntity));
    }

    private static Container resolveContainer(BlockEntity blockEntity) {
        return blockEntity instanceof RecyclerBlockEntity recycler ? recycler.getContainer() : new SimpleContainer(CONTAINER_SIZE);
    }

    private static ContainerData resolveData(BlockEntity blockEntity) {
        return blockEntity instanceof RecyclerBlockEntity recycler ? recycler.getDataAccess() : new SimpleContainerData(2);
    }

    public RecyclerMenu(int containerId, Inventory playerInventory, Container container) {
        this(containerId, playerInventory, BlockPos.ZERO, container, new SimpleContainerData(2));
    }

    public RecyclerMenu(int containerId, Inventory playerInventory, BlockPos pos, Container container, ContainerData data) {
        super(ModMenuTypes.RECYCLER_MENU.get(), containerId);
        this.container = container;
        this.blockPos = pos;
        this.data = data;
        checkContainerSize(container, CONTAINER_SIZE);
        container.startOpen(playerInventory.player);

        // 1. Input Grid - Izquierda -> Indices 0 al 17
        for (int i = 0; i < GRID_ROWS; i++) {
            for (int j = 0; j < GRID_COLS; j++) {
                this.addSlot(new Slot(container, INPUT_SLOTS_START + j + i * GRID_COLS,
                        INPUT_X + j * SLOT_SIZE, GRID_Y + i * SLOT_SIZE));
            }
        }

        // 2. Zona Central -> Proceso, botella, libro
        this.addSlot(new RecyclerSlots.ProcessSlot(container, PROCESSING_SLOT, CENTER_MID - 8, 17));
        this.addSlot(new RecyclerSlots.RestrictedSlot(container, BOTTLE_SLOT, CENTER_MID - 17, 35, new ItemStack(Items.GLASS_BOTTLE)));
        this.addSlot(new RecyclerSlots.RestrictedSlot(container, BOOK_SLOT, CENTER_MID + 1, 35, new ItemStack(Items.BOOK)));

        // 3. Output Grid - Derecha -> Indices 21 al 38
        for (int i = 0; i < GRID_ROWS; i++) {
            for (int j = 0; j < GRID_COLS; j++) {
                this.addSlot(new RecyclerSlots.OutputSlot(container, OUTPUT_SLOTS_START + j + i * GRID_COLS,
                        OUTPUT_X + j * SLOT_SIZE, GRID_Y + i * SLOT_SIZE));
            }
        }

        // 4. Inventario del jugador
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 9; ++j) {
                this.addSlot(new Slot(playerInventory, j + i * 9 + 9, PLAYER_INV_X + j * SLOT_SIZE, PLAYER_INV_Y + i * SLOT_SIZE));
            }
        }

        // 5. Hotbar del jugador
        for (int k = 0; k < 9; ++k) {
            this.addSlot(new Slot(playerInventory, k, PLAYER_INV_X + k * SLOT_SIZE, HOTBAR_Y));
        }

        this.addDataSlots(data);
    }

    public BlockPos getBlockPos() {
        return this.blockPos;
    }

    public boolean isAutoActive() {
        return this.data.get(0) == 1;
    }

    public boolean isProcessing() {
        return this.getSlot(PROCESSING_SLOT).hasItem();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack itemStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(slotIndex);

        if (slot != null && slot.hasItem()) {
            ItemStack slotStack = slot.getItem();
            itemStack = slotStack.copy();

            if (slotIndex >= PLAYER_INV_START) {
                if (!this.moveItemStackTo(slotStack, INPUT_SLOTS_START, INPUT_SLOTS_END, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (slotIndex >= OUTPUT_SLOTS_START && slotIndex < OUTPUT_SLOTS_END) {
                if (!this.moveItemStackTo(slotStack, PLAYER_INV_START, PLAYER_HOTBAR_END, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (slotIndex >= INPUT_SLOTS_START && slotIndex < INPUT_SLOTS_END) {
                if (!this.moveItemStackTo(slotStack, PLAYER_INV_START, PLAYER_HOTBAR_END, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
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
