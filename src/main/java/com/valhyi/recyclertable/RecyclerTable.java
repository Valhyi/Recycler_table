package com.valhyi.recyclertable;

import com.valhyi.recyclertable.block.entity.RecyclerBlockEntity;
import com.valhyi.recyclertable.init.ModBlocks;
import com.valhyi.recyclertable.init.ModBlockEntities;
import com.valhyi.recyclertable.init.ModMenuTypes;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.TickEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;

@Mod(RecyclerTable.MOD_ID)
public class RecyclerTable {
    public static final String MOD_ID = "recyclertable";

    public RecyclerTable(IEventBus modEventBus, ModContainer modContainer) {
        // Registra tus menús y componentes aquí
        ModMenuTypes.MENUS.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlocks.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);

        // Registrar el evento para la pestaña creativa
        modEventBus.addListener(this::addCreative);
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ModBlocks.RECYCLER_TABLE_ITEM);
        }
    }

    /**
     * Event handler para ticks del servidor
     */
    @EventBusSubscriber(modid = MOD_ID, bus = EventBusSubscriber.Bus.FORGE)
    public static class ServerEvents {
        @net.neoforged.neoforge.api.distmarker.OnlyIn(Dist.DEDICATED_SERVER)
        public static void onServerTick(TickEvent.LevelTickEvent event) {
            if (event.phase == TickEvent.Phase.END && !event.level.isClientSide()) {
                // Iterar sobre todos los block entities de RecyclerBlockEntity
                event.level.getBlockEntities().forEach(blockEntity -> {
                    if (blockEntity instanceof RecyclerBlockEntity recyclerEntity) {
                        recyclerEntity.tick();
                    }
                });
            }
        }
    }
}
