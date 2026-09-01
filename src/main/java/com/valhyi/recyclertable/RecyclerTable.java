package com.valhyi.recyclertable;

import com.valhyi.recyclertable.init.ModBlocks;
import com.valhyi.recyclertable.init.ModBlockEntities;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

@Mod(RecyclerTable.MOD_ID)
public class RecyclerTable {
    public static final String MOD_ID = "recyclertable";

public RecyclerTable(IEventBus modEventBus, ModContainer modContainer) {
    // Registra tus menús aquí
    ModMenuTypes.MENUS.register(modEventBus);
    
    // (Asegúrate de que también tengas registrados tus bloques y entidades de bloques si no lo has hecho)
    ModBlocks.BLOCKS.register(modEventBus);
    ModBlocks.ITEMS.register(modEventBus);
    ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
}
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ModBlocks.RECYCLER_TABLE_ITEM);
        }
    }
}
