package com.valhyi.recyclertable.gui;

import com.valhyi.recyclertable.block.entity.RecyclerBlockEntity;
import com.valhyi.recyclertable.init.ModBlocks;
import com.valhyi.recyclertable.init.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;

public class RecyclerMenu extends AbstractContainerMenu {
    private final Container container;
    private final ContainerData data;
    private final BlockPos blockPos;

    // ES: Cantidad de valores del ContainerData: 0 = auto, 1 = procesando, 2 = aparcado.
    public static final int DATA_COUNT = 3;

    // ================= INDICES DE SLOTS (fuente unica de verdad) =================
    // ES: RecyclerBlockEntity y RecyclerScreen usan estas constantes. Para
    // cambiar el tamano de los grids solo hay que tocar GRID_COLS / GRID_ROWS.
    public static final int GRID_COLS = 3;
    public static final int GRID_ROWS = 6;
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

    // ================= LAYOUT (pixeles EXACTOS de recycler_gui.png) =================
    private static final int SLOT_SIZE = 18;

    // Tamano total del GUI (la textura mide 176 x 220)
    public static final int IMAGE_WIDTH = 176;
    public static final int IMAGE_HEIGHT = 220;

    // Grid de entrada (izquierda): 3 columnas x 6 filas
    public static final int INPUT_X = 8;
    public static final int GRID_Y = 17;

    // Grid de salida (derecha): 3 columnas x 6 filas
    public static final int OUTPUT_X = 116;

    // Slot de proceso (arriba al centro)
    public static final int PROCESS_X = 80;
    public static final int PROCESS_Y = 17;

    // Botella (izquierda) y libro (derecha), debajo del slot de proceso
    public static final int BOTTLE_X = 68;
    public static final int BOTTLE_Y = 48;
    public static final int BOOK_X = 92;
    public static final int BOOK_Y = 48;

    // Botones (18x18 px)
    public static final int BUTTON_SIZE = 18;
    public static final int PLAY_BUTTON_X = 66;
    public static final int PLAY_BUTTON_Y = 76;
    public static final int AUTO_BUTTON_X = 92;
    public static final int AUTO_BUTTON_Y = 76;
    public static final int CONFIG_BUTTON_X = 79;
    public static final int CONFIG_BUTTON_Y = 100;

    // Inventario del jugador (3 filas) y hotbar
    public static final int PLAYER_INV_X = 8;
    public static final int PLAYER_INV_Y = 138;
    public static final int HOTBAR_Y = 196;

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
        return blockEntity instanceof RecyclerBlockEntity recycler ? recycler.getDataAccess() : new SimpleContainerData(DATA_COUNT);
    }

    public RecyclerMenu(int containerId, Inventory playerInventory, Container container) {
        this(containerId, playerInventory, BlockPos.ZERO, container, new SimpleContainerData(DATA_COUNT));
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

        // 2. Zona Central -> Proceso (variable), botella, libro
        // ES: this.data ya esta asignado arriba; el BooleanSupplier se evalua
        // de forma perezosa, asi que es seguro referenciar isParked() aqui.
        this.addSlot(new RecyclerSlots.ProcessSlot(container, PROCESSING_SLOT, PROCESS_X, PROCESS_Y, this::isParked));
        this.addSlot(new RecyclerSlots.RestrictedSlot(container, BOTTLE_SLOT, BOTTLE_X, BOTTLE_Y, new ItemStack(Items.GLASS_BOTTLE)));
        this.addSlot(new RecyclerSlots.RestrictedSlot(container, BOOK_SLOT, BOOK_X, BOOK_Y, new ItemStack(Items.BOOK)));

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

    /**
     * ES: true si el item del slot de proceso esta aparcado (el ciclo termino
     * pero el output no tenia espacio). En ese estado el slot es recogible.
     */
    public boolean isParked() {
        return this.data.get(2) == 1;
    }

    /**
     * ES: "Procesando" = hay un item en el slot de proceso Y no esta aparcado.
     * Un item aparcado deja el boton play libre (sirve para reiniciar la espera).
     */
    public boolean isProcessing() {
        return this.getSlot(PROCESSING_SLOT).hasItem() && !isParked();
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
            } else if (slotIndex == PROCESSING_SLOT) {
                // ES: solo se puede sacar con shift-clic si esta aparcado.
                if (!isParked()) {
                    return ItemStack.EMPTY;
                }
                if (!this.moveItemStackTo(slotStack, PLAYER_INV_START, PLAYER_HOTBAR_END, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (slotIndex == BOTTLE_SLOT || slotIndex == BOOK_SLOT) {
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

    /**
     * ES: SimpleContainer.stillValid siempre devuelve true, asi que el menu
     * nunca se cerraba al alejarse ni al romper la mesa con la GUI abierta.
     * Ahora se comprueba que el bloque en blockPos siga siendo la mesa de
     * reciclaje y que el jugador este a distancia de interaccion.
     */
    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(player.level(), this.blockPos),
                player, ModBlocks.RECYCLER_TABLE.get());
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.container.stopOpen(player);
    }
}
