package com.valhyi.recyclertable;

import com.valhyi.recyclertable.block.entity.CapabilityHandler;
import com.valhyi.recyclertable.init.ModBlocks;
import com.valhyi.recyclertable.init.ModBlockEntities;
import com.valhyi.recyclertable.init.ModMenuTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

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
        
        // Registrar capacidades para tolvas
        modEventBus.addListener(CapabilityHandler::registerCapabilities);
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ModBlocks.RECYCLER_TABLE_ITEM);
        }
    }

    /**
     * Creates an Identifier for this mod's resources
     */
    public static Identifier resLoc(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
