package com.valhyi.recyclertable.gui;

import com.valhyi.recyclertable.RecyclerTable;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;

public class RecyclerScreen extends AbstractContainerScreen<RecyclerMenu> {

    public RecyclerScreen(RecyclerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }
}
