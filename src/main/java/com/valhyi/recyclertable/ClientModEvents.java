package com.valhyi.recyclertable;

import com.valhyi.recyclertable.gui.RecyclerScreen;
import com.valhyi.recyclertable.init.ModMenuTypes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = RecyclerTable.MOD_ID, value = Dist.CLIENT)
public class ClientModEvents {

    @SubscribeEvent
    public static void onRegisterScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.RECYCLER_MENU.get(), RecyclerScreen::new);
    }
}
