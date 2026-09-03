package com.valhyi.recyclertable.block.entity;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Manejador de capacidades para el RecyclerBlockEntity
 * Permite que las tolvas interactúen automáticamente
 */
public class CapabilityHandler {
    
    /**
     * Registra las capacidades para el RecyclerBlockEntity
     */
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
            Capabilities.ItemHandler.BLOCK,
            ModBlockEntities.RECYCLER_BLOCK_ENTITY.get(),
            (blockEntity, side) -> new RecyclerItemHandler(blockEntity, side)
        );
    }
    
    /**
     * Manejador de items para tolvas
     * - Entrada: slots 0-8 (input grid)
     * - Salida: slots 12-20 (output grid)
     */
    public static class RecyclerItemHandler extends ItemStackHandler {
        private final RecyclerBlockEntity recycler;
        @Nullable
        private final Direction side;
        
        public RecyclerItemHandler(RecyclerBlockEntity recycler, @Nullable Direction side) {
            super(21); // 21 slots
            this.recycler = recycler;
            this.side = side;
            
            // Sincronizar con el contenedor del reciclador
            for (int i = 0; i < 21; i++) {
                stacks.set(i, recycler.getContainer().getItem(i));
            }
        }
        
        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            // Solo permitir inserción en input grid (slots 0-8)
            if (stack.isEmpty()) {
                return stack;
            }
            
            // Intentar insertar en slots 0-8
            ItemStack remaining = stack.copy();
            
            // Buscar slot vacío o stackeable
            for (int i = 0; i < 9; i++) {
                ItemStack existingItem = recycler.getContainer().getItem(i);
                
                if (existingItem.isEmpty()) {
                    if (!simulate) {
                        recycler.getContainer().setItem(i, remaining.copy());
                    }
                    return ItemStack.EMPTY;
                } else if (ItemStack.isSameItemSameComponents(existingItem, remaining)) {
                    int space = existingItem.getMaxStackSize() - existingItem.getCount();
                    if (space > 0) {
                        int transfer = Math.min(space, remaining.getCount());
                        if (!simulate) {
                            existingItem.grow(transfer);
                        }
                        remaining.shrink(transfer);
                        
                        if (remaining.isEmpty()) {
                            return ItemStack.EMPTY;
                        }
                    }
                }
            }
            return remaining;
        }
        
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            // Solo permitir extracción del output grid (slots 12-20)
            if (slot < 12 || slot > 20) {
                return ItemStack.EMPTY;
            }
            
            ItemStack slotItem = recycler.getContainer().getItem(slot);
            if (slotItem.isEmpty()) {
                return ItemStack.EMPTY;
            }
            
            int toExtract = Math.min(amount, slotItem.getCount());
            ItemStack extracted = slotItem.split(toExtract);
            
            if (!simulate) {
                if (slotItem.isEmpty()) {
                    recycler.getContainer().setItem(slot, ItemStack.EMPTY);
                }
            } else {
                slotItem.grow(toExtract);
            }
            
            return extracted;
        }
        
        @Override
        public int getSlots() {
            return 21;
        }
        
        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot < 0 || slot >= 21) {
                return ItemStack.EMPTY;
            }
            return recycler.getContainer().getItem(slot);
        }
        
        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }
        
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            // Input grid (0-8) acepta cualquier cosa
            if (slot >= 0 && slot <= 8) {
                return true;
            }
            // Output grid (12-20) no acepta items (solo lectura)
            return false;
        }
    }
}
