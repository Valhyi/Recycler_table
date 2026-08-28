package com.valhyi.recyclertable.init;

import com.valhyi.recyclertable.RecyclerTable;
import com.valhyi.recyclertable.block.entity.RecyclerBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, RecyclerTable.MOD_ID);

    // ES: Registro corregido de la entidad de bloque (BlockEntity) para NeoForge 26.2
    // EN: Corrected registration of the BlockEntity for NeoForge 26.2
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RecyclerBlockEntity>> RECYCLER_BE =
            BLOCK_ENTITIES.register("recycler_be", () ->
                    new BlockEntityType<>(RecyclerBlockEntity::new, java.util.Set.of(ModBlocks.RECYCLER_TABLE.get())));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
