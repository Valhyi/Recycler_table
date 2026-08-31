package com.valhyi.recyclertable.event;

import com.valhyi.recyclertable.gui.RecyclerScreen;
import com.valhyi.recyclertable.init.ModMenuTypes;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

public class ClientEvents {
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.RECYCLER_MENU.get(), RecyclerScreen::new);
    }
}
