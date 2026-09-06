package com.valhyi.recyclertable.event;

import com.valhyi.recyclertable.RecyclerTable;
import com.valhyi.recyclertable.block.entity.RecyclerBlockEntity;
import com.valhyi.recyclertable.init.ModBlockEntities;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Registers capabilities for hopper and pipe interaction with the Recycler Table
 */
@EventBusSubscriber(modid = RecyclerTable.MOD_ID)
public class CommonEvents {

    @SubscribeEvent
    static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // Register Item capability for hopper interaction
        // This allows hoppers, pipes, and other automation to extract/insert items
        event.registerBlockEntity(
            Capabilities.Item.BLOCK,
            ModBlockEntities.RECYCLER_BLOCK_ENTITY.get(),
            RecyclerBlockEntity::getCapability
        );
    }
}
