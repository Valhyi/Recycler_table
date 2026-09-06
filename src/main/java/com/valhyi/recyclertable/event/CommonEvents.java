package com.valhyi.recyclertable.event;

import com.valhyi.recyclertable.RecyclerTable;
import com.valhyi.recyclertable.block.entity.RecyclerBlockEntity;
import com.valhyi.recyclertable.init.ModBlockEntities;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

@EventBusSubscriber(modid = RecyclerTable.MOD_ID)
public class CommonEvents {

    @SubscribeEvent
    static void registerCapabilities(RegisterCapabilitiesEvent event) {
        // Registrar capability de Item para que los hoppers puedan interactuar
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModBlockEntities.RECYCLER_BLOCK_ENTITY.get(), RecyclerBlockEntity::getCapability);
    }
}
