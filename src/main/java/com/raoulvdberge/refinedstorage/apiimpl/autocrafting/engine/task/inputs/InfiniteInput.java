package com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task.inputs;

import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;

import javax.annotation.Nonnull;

/**
 * Represents an Input that is infinite and only needs to be extracted once. Only allowed for crafting tasks currently.
 */
public class InfiniteInput extends Input {

    /**
     * Whether or not this infinite input actually extracted any item
     */
    private boolean actuallyExtracted;

    public InfiniteInput(@Nonnull ItemStack itemStack, boolean oredict) {
        super(NonNullList.from(ItemStack.EMPTY, itemStack), 1, oredict);
    }

    @Override
    public long getAmountNeeded() {
        return 1;
    }

    @Override
    public void setAmountNeeded(long amountNeeded) {
        //NO OP
    }

    @Override
    public int getQuantityPerCraft() {
        return 0;
    }

    public boolean hasActuallyExtracted() {
        return actuallyExtracted;
    }

    public void setActuallyExtracted(boolean actuallyExtracted) {
        this.actuallyExtracted = actuallyExtracted;
    }
}
