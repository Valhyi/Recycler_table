package com.valhyi.recyclertable.event;

import com.valhyi.recyclertable.RecyclerTable;
import com.valhyi.recyclertable.gui.RecyclerScreen;
import com.valhyi.recyclertable.init.ModMenuTypes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = RecyclerTable.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientEvents {

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.RECYCLER_MENU.get(), RecyclerScreen::new); //[cite: 1]
    }
}
