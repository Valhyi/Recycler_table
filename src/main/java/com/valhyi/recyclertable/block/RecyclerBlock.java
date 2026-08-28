package com.valhyi.recyclertable.block;

import com.valhyi.recyclertable.block.entity.RecyclerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class RecyclerBlock extends Block implements EntityBlock {

    public RecyclerBlock() {
        super(BlockBehaviour.Properties.of()
                .strength(3.5f, 6.0f)
                .sound(SoundType.WOOD)
                .requiresCorrectToolForDrops());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RecyclerBlockEntity(pos, state);
    }

    // ES: Abrir la interfaz al hacer clic derecho sobre la mesa
    // EN: Open GUI upon right-clicking the table block
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof RecyclerBlockEntity recyclerBE && player instanceof ServerPlayer serverPlayer) {
                serverPlayer.openMenu(recyclerBE, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
