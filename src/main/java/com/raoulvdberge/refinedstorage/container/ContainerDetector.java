package com.raoulvdberge.refinedstorage.container;

import com.raoulvdberge.refinedstorage.container.slot.filter.SlotFilter;
import com.raoulvdberge.refinedstorage.container.slot.filter.SlotFilterFluid;
import com.raoulvdberge.refinedstorage.tile.TileDetector;
import net.minecraft.entity.player.EntityPlayer;

public class ContainerDetector extends ContainerBase {
    public ContainerDetector(TileDetector detector, EntityPlayer player) {
        super(detector, player);

        addSlotToContainer(new SlotFilter(detector.getNode().getConfig().getItemHandler(), 0, 107, 20).setEnableHandler(() -> detector.getNode().getConfig().isFilterTypeItem()));
        addSlotToContainer(new SlotFilterFluid(detector.getNode().getConfig().getFluidHandler(), 0, 107, 20).setEnableHandler(() -> detector.getNode().getConfig().isFilterTypeFluid()));

        addPlayerInventory(8, 55);

        transferManager.addFilterTransfer(player.inventory, detector.getNode().getConfig().getItemHandler(), detector.getNode().getConfig().getFluidHandler(), detector.getNode().getConfig()::getFilterType);
    }
}
