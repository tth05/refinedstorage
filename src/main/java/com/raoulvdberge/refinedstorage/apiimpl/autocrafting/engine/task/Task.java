package com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task;

import com.raoulvdberge.refinedstorage.api.autocrafting.ICraftingPattern;
import com.raoulvdberge.refinedstorage.api.network.INetwork;
import com.raoulvdberge.refinedstorage.api.util.Action;
import com.raoulvdberge.refinedstorage.api.util.IComparer;
import com.raoulvdberge.refinedstorage.apiimpl.API;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task.inputs.DurabilityInput;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task.inputs.Input;
import com.raoulvdberge.refinedstorage.apiimpl.autocrafting.engine.task.inputs.Output;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import java.util.List;

public abstract class Task {

    protected final List<Task> parents = new ObjectArrayList<>();
    protected final List<Input> inputs = new ObjectArrayList<>();
    protected final List<Output> outputs = new ObjectArrayList<>();

    protected final ICraftingPattern pattern;
    protected final long amountNeeded;

    public Task(@Nonnull ICraftingPattern pattern, long amountNeeded, boolean isFluidRequested) {
        //merge all pattern inputs
        for (NonNullList<ItemStack> itemStacks : pattern.getInputs()) {
            if (itemStacks.isEmpty())
                continue;

            Input newInput;

            //detect re-useable items
            ItemStack itemStack = itemStacks.get(0);
            boolean isDurabilityInput = false;

            //damageable items won't be oredicted
            if (!pattern.isProcessing() && itemStacks.size() < 2 && itemStack.isItemStackDamageable()) {
                //TODO: detect infinite

                for (ItemStack remainder : pattern.getByproducts()) {
                    //find item in by products and check if one damage was used up. this means that damage = uses
                    if (API.instance().getComparer()
                            .isEqual(itemStack, remainder, IComparer.COMPARE_NBT | IComparer.COMPARE_QUANTITY) &&
                            remainder.getItemDamage() - 1 == itemStack.getItemDamage()) {
                        isDurabilityInput = true;
                    }
                }
            }
            if (isDurabilityInput)
                newInput = new DurabilityInput(itemStack, amountNeeded, pattern.isOredict());
            else
                newInput = new Input(itemStacks, amountNeeded, pattern.isOredict());

            mergeIntoList(newInput, this.inputs);
        }

        //merge all pattern fluid inputs
        for (FluidStack i : pattern.getFluidInputs()) {
            Input newInput = new Input(i, amountNeeded, pattern.isOredict());

            mergeIntoList(newInput, this.inputs);
        }

        //merge all pattern outputs
        for (ItemStack itemStack : pattern.getOutputs()) {
            Output newOutput = new Output(itemStack, itemStack.getCount());

            //lovely cast
            mergeIntoList(newOutput, (List<Input>) (List<?>) this.outputs);
        }

        //merge all pattern fluid outputs
        for (FluidStack fluidStack : pattern.getFluidOutputs()) {
            Output newOutput = new Output(fluidStack, fluidStack.amount);

            mergeIntoList(newOutput, (List<Input>) (List<?>) this.outputs);
        }

        //find smallest output counts
        int smallestOutputStackSize = Integer.MAX_VALUE;
        int smallestOutputFluidStackSize = Integer.MAX_VALUE;

        for (Output output : this.outputs) {
            if (!output.isFluid()) {
                smallestOutputStackSize = Math.min(smallestOutputStackSize, output.getQuantityPerCraft());
            } else {
                smallestOutputFluidStackSize = Math.min(smallestOutputFluidStackSize, output.getQuantityPerCraft());
            }
        }

        //calculate actual needed amount, basically the amount of iterations that have to be run
        amountNeeded = (long) Math.ceil((double) amountNeeded / (double) (isFluidRequested ?
                smallestOutputFluidStackSize : smallestOutputStackSize));

        //set correct amount for all inputs
        for (Input input : this.inputs) {
            input.setAmountNeeded(amountNeeded * input.getQuantityPerCraft());
        }

        this.pattern = pattern;
        this.valid = true;
        this.amountNeeded = amountNeeded;
    }

    private void mergeIntoList(Input input, List<Input> list) {
        boolean merged = false;
        for (Input output : list) {
            if (input.equals(output)) {
                output.merge(input);
                merged = true;
            }
        }

        if (!merged)
            list.add(input);
    }

    @Nonnull
    public CalculationResult calculate(@Nonnull INetwork network) {
        CalculationResult result = new CalculationResult();

        inputLoop:
        for (Input input : this.inputs) {
            //first search for missing amount in network
            if (!input.isFluid()) { //extract items
                //handle durability input
                if(input instanceof DurabilityInput) {
                    DurabilityInput durabilityInput = (DurabilityInput) input;
                    //always only extract 1 item
                    ItemStack extracted = network.extractItem(durabilityInput.getCompareableItemStack(), 1,
                            IComparer.COMPARE_NBT, Action.PERFORM);

                    //extract as many items as needed
                    while(!extracted.isEmpty() && input.getAmountMissing() > 0) {
                        durabilityInput.addDamageableItemStack(extracted);

                        extracted = network.extractItem(durabilityInput.getCompareableItemStack(), 1,
                                IComparer.COMPARE_NBT, Action.PERFORM);
                    }
                } else { //handle normal inputs
                    for (ItemStack ingredient : input.getItemStacks()) {
                        //TODO: support inserting and extracting of more than Integer.MAX_VALUE xd
                        ItemStack extracted = network.extractItem(ingredient,
                                input.getAmountMissing() > Integer.MAX_VALUE ? Integer.MAX_VALUE :
                                        (int) input.getAmountMissing(), Action.PERFORM);
                        if (extracted.isEmpty())
                            continue;

                        long remainder = input.increaseAmount(extracted, extracted.getCount());
                        //if it extracted too much, insert it back. Shouldn't happen
                        if (remainder != -1) {
                            if (remainder != 0)
                                network.insertItem(ingredient, (int) remainder, Action.PERFORM);
                            continue inputLoop;
                        }
                    }
                }
            } else { //extract fluid
                //TODO: support inserting and extracting of more than Integer.MAX_VALUE
                FluidStack extracted = network.extractFluid(input.getFluidStack(),
                        input.getAmountMissing() > Integer.MAX_VALUE ? Integer.MAX_VALUE :
                                (int) input.getAmountMissing(), Action.PERFORM);
                if (extracted != null) {
                    long remainder = input.increaseFluidStackAmount(extracted.amount);
                    //if it extracted too much, insert it back. Shouldn't happen
                    if (remainder != -1) {
                        if (remainder != 0)
                            network.insertFluid(input.getFluidStack(), (int) remainder, Action.PERFORM);
                        continue;
                    }
                }
            }

            //if input is not satisfied -> search for patterns to craft this input
            if (input.getAmountMissing() > 0) {

                //find pattern to craft more
                ICraftingPattern pattern;
                if (!input.isFluid())
                    //TODO: add possibility for oredict components to be crafted
                    pattern = network.getCraftingManager().getPattern(input.getCompareableItemStack());
                else
                    pattern = network.getCraftingManager().getPattern(input.getFluidStack());

                //add new sub task
                if (pattern != null && pattern.isValid()) {
                    Task newTask;
                    if (pattern.isProcessing())
                        newTask = new ProcessingTask(pattern, input.getAmountMissing(), input.isFluid());
                    else
                        newTask = new CraftingTask(pattern, input.getAmountMissing());
                    newTask.addParent(this);
                    CalculationResult newTaskResult = newTask.calculate(network);

                    //make sure nothing is missing for this input, missing stuff is handled by the child task
                    input.increaseToCraftAmount(input.getAmountMissing());

                    result.addNewTask(newTask);
                    //merge the calculation results
                    result.merge(newTaskResult);
                }
            }

            //if input cannot be satisfied -> add to missing
            if (input.getAmountMissing() > 0) {

                if (!input.isFluid()) { //missing itemstacks
                    ItemStack missing = input.getCompareableItemStack().copy();
                    //avoid int overflow
                    //TODO: add support for real ItemStack counts
                    if (input.getAmountMissing() > Integer.MAX_VALUE)
                        missing.setCount(Integer.MAX_VALUE);
                    else
                        missing.setCount((int) input.getAmountMissing());
                    result.getMissingItemStacks().add(missing);
                } else { //missing fluid stacks
                    FluidStack missing = input.getFluidStack();

                    //TODO: add support for real FluidStack counts
                    //avoid overflow
                    if (input.getAmountMissing() > Integer.MAX_VALUE)
                        missing.amount = Integer.MAX_VALUE;
                    else
                        missing.amount = (int) input.getAmountMissing();
                    result.getMissingFluidStacks().add(missing);
                }
            }
        }

        return result;
    }

    //TODO: is this needed?
    protected boolean valid;

    public abstract void update();

    public void addParent(Task task) {
        this.parents.add(task);
    }

    public boolean isValid() {
        return valid;
    }

    public ICraftingPattern getPattern() {
        return pattern;
    }

    public List<Task> getParents() {
        return parents;
    }

    public List<Input> getInputs() {
        return inputs;
    }

    public List<Output> getOutputs() {
        return outputs;
    }

}
