package com.valhyi.recyclertable.gui;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Clases de slots personalizados para validación en RecyclerMenu
 * - RestrictedSlot: Botella vacía (slot 10) y Libro (slot 11)
 * - OutputSlot: No permite colocar items (slots 12-20)
 * - ProcessSlot: Solo lectura (slot 9)
 */
public class RecyclerSlots {

    /**
     * Slot personalizado para botellas vacías y libros
     * Slot 10: Botella vacía
     * Slot 11: Libro
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
     * Slots 12-20: Grid de output
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

        @Override
        public void set(ItemStack itemStack) {
            super.set(itemStack);
        }
    }

    /**
     * Slot de proceso (solo lectura)
     * Slot 9: Muestra el item siendo procesado
     */
    public static class ProcessSlot extends Slot {
        public ProcessSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack itemStack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public void set(ItemStack itemStack) {
            super.set(itemStack);
        }
    }
}
