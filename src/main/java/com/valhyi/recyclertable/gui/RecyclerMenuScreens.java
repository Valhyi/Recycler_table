package com.valhyi.recyclertable.gui;

import com.valhyi.recyclertable.RecyclerTable;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

@EventBusSubscriber(modid = RecyclerTable.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class RecyclerMenuScreens {

    @OnlyIn(Dist.CLIENT)
    public static void registerScreens() {
        MenuScreens.register(com.valhyi.recyclertable.init.ModMenuTypes.RECYCLER_MENU.get(), RecyclerScreen::new);
    }
}
