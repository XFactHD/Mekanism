package mekanism.common.tile;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import mekanism.api.NBTConstants;
import mekanism.api.RelativeSide;
import mekanism.api.Upgrade;
import mekanism.api.annotations.NonNull;
import mekanism.api.chemical.gas.BasicGasTank;
import mekanism.api.chemical.gas.Gas;
import mekanism.api.chemical.gas.GasStack;
import mekanism.api.chemical.gas.IGasTank;
import mekanism.api.inventory.AutomationType;
import mekanism.api.math.FloatingLong;
import mekanism.api.math.MathUtils;
import mekanism.api.recipes.RotaryRecipe;
import mekanism.api.recipes.cache.CachedRecipe;
import mekanism.api.recipes.cache.RotaryCachedRecipe;
import mekanism.api.recipes.inputs.IInputHandler;
import mekanism.api.recipes.inputs.InputHelper;
import mekanism.api.recipes.outputs.IOutputHandler;
import mekanism.api.recipes.outputs.OutputHelper;
import mekanism.api.transmitters.TransmissionType;
import mekanism.common.capabilities.energy.MachineEnergyContainer;
import mekanism.common.capabilities.fluid.BasicFluidTank;
import mekanism.common.capabilities.holder.chemical.ChemicalTankHelper;
import mekanism.common.capabilities.holder.chemical.IChemicalTankHolder;
import mekanism.common.capabilities.holder.energy.EnergyContainerHelper;
import mekanism.common.capabilities.holder.energy.IEnergyContainerHolder;
import mekanism.common.capabilities.holder.fluid.FluidTankHelper;
import mekanism.common.capabilities.holder.fluid.IFluidTankHolder;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.slot.ContainerSlotType;
import mekanism.common.inventory.container.slot.SlotOverlay;
import mekanism.common.inventory.container.sync.SyncableBoolean;
import mekanism.common.inventory.container.sync.SyncableFloatingLong;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.inventory.slot.FluidInventorySlot;
import mekanism.common.inventory.slot.GasInventorySlot;
import mekanism.common.inventory.slot.OutputInventorySlot;
import mekanism.common.recipe.MekanismRecipeType;
import mekanism.common.registries.MekanismBlocks;
import mekanism.common.tile.component.TileComponentConfig;
import mekanism.common.tile.component.TileComponentEjector;
import mekanism.common.tile.component.config.ConfigInfo;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.component.config.slot.EnergySlotInfo;
import mekanism.common.tile.component.config.slot.FluidSlotInfo;
import mekanism.common.tile.component.config.slot.GasSlotInfo;
import mekanism.common.tile.component.config.slot.InventorySlotInfo;
import mekanism.common.tile.interfaces.IHasMode;
import mekanism.common.tile.prefab.TileEntityRecipeMachine;
import mekanism.common.util.GasUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.PipeUtils;
import net.minecraft.nbt.CompoundNBT;
import net.minecraftforge.fluids.FluidStack;

public class TileEntityRotaryCondensentrator extends TileEntityRecipeMachine<RotaryRecipe> implements IHasMode {

    private static final int CAPACITY = 10_000;

    public BasicGasTank gasTank;
    public BasicFluidTank fluidTank;
    /**
     * True: fluid -> gas
     *
     * False: gas -> fluid
     */
    public boolean mode;

    public long gasOutput = 256;
    public int fluidOutput = 256;

    private final IOutputHandler<@NonNull GasStack> gasOutputHandler;
    private final IOutputHandler<@NonNull FluidStack> fluidOutputHandler;
    private final IInputHandler<@NonNull FluidStack> fluidInputHandler;
    private final IInputHandler<@NonNull GasStack> gasInputHandler;

    public FloatingLong clientEnergyUsed = FloatingLong.ZERO;

    private MachineEnergyContainer<TileEntityRotaryCondensentrator> energyContainer;
    private GasInventorySlot gasInputSlot;
    private GasInventorySlot gasOutputSlot;
    private FluidInventorySlot fluidInputSlot;
    private OutputInventorySlot fluidOutputSlot;
    private EnergyInventorySlot energySlot;

    public TileEntityRotaryCondensentrator() {
        super(MekanismBlocks.ROTARY_CONDENSENTRATOR);
        configComponent = new TileComponentConfig(this, TransmissionType.ITEM, TransmissionType.GAS, TransmissionType.FLUID, TransmissionType.ENERGY);

        ConfigInfo itemConfig = configComponent.getConfig(TransmissionType.ITEM);
        if (itemConfig != null) {
            itemConfig.addSlotInfo(DataType.INPUT, new InventorySlotInfo(true, true, gasInputSlot, fluidInputSlot));
            itemConfig.addSlotInfo(DataType.OUTPUT, new InventorySlotInfo(true, true, gasOutputSlot, fluidOutputSlot));
            itemConfig.addSlotInfo(DataType.ENERGY, new InventorySlotInfo(true, true, energySlot));
            //Set default config directions
            itemConfig.setDataType(DataType.INPUT, RelativeSide.LEFT);
            itemConfig.setDataType(DataType.OUTPUT, RelativeSide.RIGHT);
            itemConfig.setDataType(DataType.ENERGY, RelativeSide.BACK);
        }

        configComponent.setupIOConfig(TransmissionType.GAS, new GasSlotInfo(true, true, gasTank), new GasSlotInfo(true, true, gasTank), RelativeSide.LEFT)
              .setEjecting(true);
        configComponent.setupIOConfig(TransmissionType.FLUID, new FluidSlotInfo(true, true, fluidTank), new FluidSlotInfo(true, true, fluidTank), RelativeSide.RIGHT)
              .setEjecting(true);
        configComponent.setupInputConfig(TransmissionType.ENERGY, new EnergySlotInfo(true, false, energyContainer));

        ejectorComponent = new TileComponentEjector(this);
        ejectorComponent.setOutputData(configComponent, TransmissionType.ITEM);

        gasInputHandler = InputHelper.getInputHandler(gasTank);
        fluidInputHandler = InputHelper.getInputHandler(fluidTank);
        gasOutputHandler = OutputHelper.getOutputHandler(gasTank);
        fluidOutputHandler = OutputHelper.getOutputHandler(fluidTank);
    }

    @Nonnull
    @Override
    protected IChemicalTankHolder<Gas, GasStack, IGasTank> getInitialGasTanks() {
        ChemicalTankHelper<Gas, GasStack, IGasTank> builder = ChemicalTankHelper.forSideGasWithConfig(this::getDirection, this::getConfig);
        //Only allow extraction
        builder.addTank(gasTank = BasicGasTank.create(CAPACITY, (gas, automationType) -> automationType == AutomationType.MANUAL || mode,
              (gas, automationType) -> automationType == AutomationType.INTERNAL || !mode, this::isValidGas, this));
        return builder.build();
    }

    private boolean isValidGas(@Nonnull Gas gas) {
        return containsRecipe(recipe -> recipe.hasGasToFluid() && recipe.getGasInput().testType(gas));
    }

    @Nonnull
    @Override
    protected IFluidTankHolder getInitialFluidTanks() {
        FluidTankHelper builder = FluidTankHelper.forSideWithConfig(this::getDirection, this::getConfig);
        builder.addTank(fluidTank = BasicFluidTank.create(CAPACITY, (fluid, automationType) -> automationType == AutomationType.MANUAL || !mode,
              (fluid, automationType) -> automationType == AutomationType.INTERNAL || mode, this::isValidFluid, this));
        return builder.build();
    }

    private boolean isValidFluid(@Nonnull FluidStack fluidStack) {
        return containsRecipe(recipe -> recipe.hasFluidToGas() && recipe.getFluidInput().testType(fluidStack));
    }

    @Nonnull
    @Override
    protected IEnergyContainerHolder getInitialEnergyContainers() {
        EnergyContainerHelper builder = EnergyContainerHelper.forSideWithConfig(this::getDirection, this::getConfig);
        builder.addContainer(energyContainer = MachineEnergyContainer.input(this));
        return builder.build();
    }

    @Nonnull
    @Override
    protected IInventorySlotHolder getInitialInventory() {
        InventorySlotHelper builder = InventorySlotHelper.forSideWithConfig(this::getDirection, this::getConfig);
        builder.addSlot(gasInputSlot = GasInventorySlot.rotary(gasTank, () -> mode, this, 5, 25));
        builder.addSlot(gasOutputSlot = GasInventorySlot.rotary(gasTank, () -> mode, this, 5, 56));
        builder.addSlot(fluidInputSlot = FluidInventorySlot.rotary(fluidTank, () -> mode, this, 155, 25));
        builder.addSlot(fluidOutputSlot = OutputInventorySlot.at(this, 155, 56));
        builder.addSlot(energySlot = EnergyInventorySlot.fillOrConvert(energyContainer, this::getWorld, this, 155, 5));
        gasInputSlot.setSlotType(ContainerSlotType.INPUT);
        gasInputSlot.setSlotOverlay(SlotOverlay.PLUS);
        gasOutputSlot.setSlotType(ContainerSlotType.OUTPUT);
        gasOutputSlot.setSlotOverlay(SlotOverlay.MINUS);
        fluidInputSlot.setSlotType(ContainerSlotType.INPUT);
        return builder.build();
    }

    @Override
    protected void onUpdateServer() {
        super.onUpdateServer();
        energySlot.fillContainerOrConvert();
        if (mode) {//Fluid to Gas
            fluidInputSlot.fillTank(fluidOutputSlot);
            gasInputSlot.drainTank();
            // emit
            ConfigInfo config = configComponent.getConfig(TransmissionType.GAS);
            if (config != null && config.isEjecting()) {
                GasUtils.emit(config.getSidesForData(DataType.OUTPUT), gasTank, this, gasOutput);
            }
        } else {//Gas to Fluid
            gasOutputSlot.fillTank();
            fluidInputSlot.drainTank(fluidOutputSlot);
            // emit
            ConfigInfo config = configComponent.getConfig(TransmissionType.FLUID);
            if (config != null && config.isEjecting()) {
                PipeUtils.emit(config.getSidesForData(DataType.OUTPUT), fluidTank, this, fluidOutput);
            }
        }
        FloatingLong prev = energyContainer.getEnergy().copyAsConst();
        cachedRecipe = getUpdatedCache(0);
        if (cachedRecipe != null) {
            cachedRecipe.process();
        }
        //Update amount of energy that actually got used, as if we are "near" full we may not have performed our max number of operations
        clientEnergyUsed = prev.subtract(energyContainer.getEnergy());
    }

    @Override
    public void nextMode() {
        mode = !mode;
        markDirty(false);
    }

    @Override
    public void read(CompoundNBT nbtTags) {
        super.read(nbtTags);
        mode = nbtTags.getBoolean(NBTConstants.MODE);
    }

    @Nonnull
    @Override
    public CompoundNBT write(CompoundNBT nbtTags) {
        super.write(nbtTags);
        nbtTags.putBoolean(NBTConstants.MODE, mode);
        return nbtTags;
    }

    @Override
    public int getRedstoneLevel() {
        if (mode) {
            return MekanismUtils.redstoneLevelFromContents(fluidTank.getFluidAmount(), fluidTank.getCapacity());
        }
        return MekanismUtils.redstoneLevelFromContents(gasTank.getStored(), gasTank.getCapacity());
    }

    @Nonnull
    @Override
    public MekanismRecipeType<RotaryRecipe> getRecipeType() {
        return MekanismRecipeType.ROTARY;
    }

    @Nullable
    @Override
    public RotaryRecipe getRecipe(int cacheIndex) {
        if (mode) {//Fluid to Gas
            FluidStack fluid = fluidInputHandler.getInput();
            if (fluid.isEmpty()) {
                return null;
            }
            return findFirstRecipe(recipe -> recipe.test(fluid));
        }
        //Gas to Fluid
        GasStack gas = gasInputHandler.getInput();
        if (gas.isEmpty()) {
            return null;
        }
        return findFirstRecipe(recipe -> recipe.test(gas));
    }

    public MachineEnergyContainer<TileEntityRotaryCondensentrator> getEnergyContainer() {
        return energyContainer;
    }

    @Nullable
    @Override
    public CachedRecipe<RotaryRecipe> createNewCachedRecipe(@Nonnull RotaryRecipe recipe, int cacheIndex) {
        return new RotaryCachedRecipe(recipe, fluidInputHandler, gasInputHandler, gasOutputHandler, fluidOutputHandler, () -> mode)
              .setCanHolderFunction(() -> MekanismUtils.canFunction(this))
              .setActive(this::setActive)
              .setEnergyRequirements(energyContainer::getEnergyPerTick, energyContainer)
              .setOnFinish(() -> markDirty(false))
              .setPostProcessOperations(currentMax -> {
                  if (currentMax <= 0) {
                      //Short circuit that if we already can't perform any outputs, just return
                      return currentMax;
                  }
                  int possibleProcess = (int) Math.pow(2, upgradeComponent.getUpgrades(Upgrade.SPEED));
                  if (mode) {
                      //Fluid to gas
                      return Math.min(Math.min(fluidTank.getFluidAmount(), MathUtils.clampToInt(gasTank.getNeeded())), possibleProcess);
                  }
                  //Gas to fluid
                  return Math.min(Math.min(MathUtils.clampToInt(gasTank.getStored()), fluidTank.getNeeded()), possibleProcess);
              });
    }

    @Override
    public void addContainerTrackers(MekanismContainer container) {
        super.addContainerTrackers(container);
        container.track(SyncableBoolean.create(() -> mode, value -> mode = value));
        container.track(SyncableFloatingLong.create(() -> clientEnergyUsed, value -> clientEnergyUsed = value));
    }
}