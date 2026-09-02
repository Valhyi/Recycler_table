package com.valhyi.recyclertable.block.entity;

import com.valhyi.recyclertable.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

/**
 * Sistema de tolvas integrado para RecyclerBlockEntity
 * - Entrada: Tolvas pueden poner items en el grid de entrada (slots 0-8)
 * - Salida: Tolvas pueden extraer items SOLO del grid de salida (slots 12-20)
 */
public class HopperIntegration {

    /**
     * Verifica si una tolva puede colocar un item en el Recycler
     * Solo permite inserción en slots 0-8 (input grid)
     */
    public static boolean canInsert(RecyclerBlockEntity recycler, ItemStack itemStack, @Nullable Direction side) {
        if (itemStack.isEmpty() || recycler.getContainer() == null) {
            return false;
        }

        // Buscar slot vacío en el input grid (0-8)
        for (int i = 0; i < 9; i++) {
            ItemStack slotItem = recycler.getContainer().getItem(i);
            if (slotItem.isEmpty()) {
                return true;
            } else if (ItemStack.isSameItemSameComponents(slotItem, itemStack) && slotItem.getCount() < slotItem.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Inserta un item desde una tolva al Recycler
     * Solo en los slots de entrada (0-8)
     */
    public static ItemStack insertItem(RecyclerBlockEntity recycler, ItemStack itemStack, @Nullable Direction side) {
        if (itemStack.isEmpty() || recycler.getContainer() == null) {
            return itemStack;
        }

        ItemStack remaining = itemStack.copy();

        // Intentar insertar en slots de entrada (0-8)
        for (int i = 0; i < 9; i++) {
            ItemStack slotItem = recycler.getContainer().getItem(i);
            
            if (slotItem.isEmpty()) {
                recycler.getContainer().setItem(i, remaining);
                return ItemStack.EMPTY;
            } else if (ItemStack.isSameItemSameComponents(slotItem, remaining)) {
                int maxStack = Math.min(remaining.getMaxStackSize(), 64);
                int space = maxStack - slotItem.getCount();
                
                if (space > 0) {
                    int transfer = Math.min(space, remaining.getCount());
                    slotItem.grow(transfer);
                    remaining.shrink(transfer);
                    
                    if (remaining.isEmpty()) {
                        return ItemStack.EMPTY;
                    }
                }
            }
        }

        return remaining;
    }

    /**
     * Verifica si una tolva puede extraer un item del Recycler
     * Solo permite extracción del output grid (12-20)
     */
    public static boolean canExtract(RecyclerBlockEntity recycler, @Nullable Direction side) {
        if (recycler.getContainer() == null) {
            return false;
        }

        // Verificar si hay items en el output grid (12-20)
        for (int i = 12; i <= 20; i++) {
            if (!recycler.getContainer().getItem(i).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Extrae un item desde el Recycler (solo del output grid 12-20)
     */
    public static ItemStack extractItem(RecyclerBlockEntity recycler, @Nullable Direction side) {
        if (recycler.getContainer() == null) {
            return ItemStack.EMPTY;
        }

        // Buscar primer item en output grid (12-20)
        for (int i = 12; i <= 20; i++) {
            ItemStack slotItem = recycler.getContainer().getItem(i);
            if (!slotItem.isEmpty()) {
                ItemStack extracted = slotItem.split(1);
                if (slotItem.isEmpty()) {
                    recycler.getContainer().setItem(i, ItemStack.EMPTY);
                }
                return extracted;
            }
        }

        return ItemStack.EMPTY;
    }
}
