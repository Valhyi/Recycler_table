package com.valhyi.recyclertable.gui;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.function.BooleanSupplier;

/**
 * Clases de slots personalizados para validación en RecyclerMenu
 * - RestrictedSlot: Botella vacía y Libro
 * - OutputSlot: No permite colocar items
 * - ProcessSlot: Solo lectura mientras procesa; recogible cuando está aparcado
 */
public class RecyclerSlots {

    /**
     * Slot personalizado para botellas vacías y libros
     */
    public static class RestrictedSlot extends Slot {
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
     * Slot de output (solo lectura para el usuario)
     * Las tolvas pueden extraer items de aquí
     */
    public static class OutputSlot extends Slot {
        public OutputSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack itemStack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return true;
        }
    }

    /**
     * ES: Slot de proceso VARIABLE. Nunca se puede colocar nada a mano.
     * Mientras la mesa esta procesando es de solo lectura; cuando el item
     * queda "aparcado" (el ciclo termino pero el output no tenia espacio) el
     * jugador puede recogerlo. El estado "aparcado" viene del ContainerData
     * del menu (sincronizado servidor -> cliente), por eso se recibe como
     * BooleanSupplier.
     */
    public static class ProcessSlot extends Slot {
        private final BooleanSupplier parked;

        public ProcessSlot(Container container, int index, int x, int y, BooleanSupplier parked) {
            super(container, index, x, y);
            this.parked = parked;
        }

        @Override
        public boolean mayPlace(ItemStack itemStack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return parked.getAsBoolean();
        }
    }
}
