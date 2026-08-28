package com.valhyi.recyclertable.init;

import com.valhyi.recyclertable.RecyclerTable;
import com.valhyi.recyclertable.gui.RecyclerMenu;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(BuiltInRegistries.MENU, RecyclerTable.MOD_ID);

    // ES: Registro del tipo de menú/contenedor GUI
    // EN: GUI Menu/Container type registration
    public static final DeferredHolder<MenuType<?>, MenuType<RecyclerMenu>> RECYCLER_MENU =
            MENUS.register("recycler_menu", () -> IMenuTypeExtension.create(RecyclerMenu::new));

    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }
}
