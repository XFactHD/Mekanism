package mekanism.client.gui;

import java.util.Arrays;
import mekanism.client.gui.element.GuiEnergyInfo;
import mekanism.client.gui.element.GuiRedstoneControl;
import mekanism.client.gui.element.bar.GuiHorizontalPowerBar;
import mekanism.client.gui.element.gauge.GaugeType;
import mekanism.client.gui.element.gauge.GuiGasGauge;
import mekanism.client.gui.element.progress.GuiProgress;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.gui.element.tab.GuiSecurityTab;
import mekanism.client.gui.element.tab.GuiSideConfigurationTab;
import mekanism.client.gui.element.tab.GuiTransporterConfigTab;
import mekanism.client.gui.element.tab.GuiUpgradeTab;
import mekanism.common.MekanismLang;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import mekanism.common.tile.TileEntityIsotopicCentrifuge;
import mekanism.common.util.text.EnergyDisplay;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.util.text.ITextComponent;

public class GuiIsotopicCentrifuge extends GuiMekanismTile<TileEntityIsotopicCentrifuge, MekanismTileContainer<TileEntityIsotopicCentrifuge>> {

    public GuiIsotopicCentrifuge(MekanismTileContainer<TileEntityIsotopicCentrifuge> container, PlayerInventory inv, ITextComponent title) {
        super(container, inv, title);
        dynamicSlots = true;
    }

    @Override
    public void init() {
        super.init();
        addButton(new GuiSecurityTab<>(this, tile));
        addButton(new GuiRedstoneControl(this, tile));
        addButton(new GuiUpgradeTab(this, tile));
        addButton(new GuiSideConfigurationTab(this, tile));
        addButton(new GuiTransporterConfigTab(this, tile));
        addButton(new GuiHorizontalPowerBar(this, tile.getEnergyContainer(), 115, 75));
        addButton(new GuiEnergyInfo(() -> Arrays.asList(MekanismLang.USING.translate(EnergyDisplay.of(tile.clientEnergyUsed)),
              MekanismLang.NEEDED.translate(EnergyDisplay.of(tile.getEnergyContainer().getNeeded()))), this));
        addButton(new GuiGasGauge(() -> tile.inputTank, () -> tile.getGasTanks(null), GaugeType.STANDARD, this, 25, 13));
        addButton(new GuiGasGauge(() -> tile.outputTank, () -> tile.getGasTanks(null), GaugeType.STANDARD, this, 133, 13));
        addButton(new GuiProgress(() -> tile.getActive() ? 1 : 0, ProgressType.LARGE_RIGHT, this, 64, 39));
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        drawString(tile.getName(), (getXSize() / 2) - (getStringWidth(tile.getName()) / 2), 4, 0x404040);
        drawString(MekanismLang.INVENTORY.translate(), 8, (getYSize() - 96) + 4, 0x404040);
        super.drawGuiContainerForegroundLayer(mouseX, mouseY);
    }
}
