package com.raoulvdberge.refinedstorage.api.autocrafting;

import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import javax.annotation.Nonnull;

/**
 * Implement this interface on crafting pattern items.
 * When this interface is implemented on the item in question, they will be insertable in crafters.
 */
public interface ICraftingPatternProvider {
    /**
     * Creates a crafting pattern.
     *
     * @param world     the world
     * @param stack     the pattern stack, the implementor needs to copy it
     * @param container unused, patterns are not bound to containers anymore; pass {@code null} here
     * @return the crafting pattern
     */
    @Deprecated
    @Nonnull
    default ICraftingPattern create(World world, ItemStack stack, ICraftingPatternContainer container) {
        return create(world, stack);
    }

    /**
     * Creates a crafting pattern.
     *
     * @param world     the world
     * @param stack     the pattern stack, the implementor needs to copy it
     * @return the crafting pattern
     */
    @Nonnull
    ICraftingPattern create(World world, ItemStack stack);
}
