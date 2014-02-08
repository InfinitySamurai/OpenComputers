package li.cil.occ.mods.thermalexpansion;

import cofh.api.energy.IEnergyContainerItem;
import li.cil.oc.api.Driver;
import li.cil.occ.mods.IMod;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.Map;

public final class ModThermalExpansion implements IMod {
    @Override
    public String getModId() {
        return "ThermalExpansion";
    }

    @Override
    public void initialize() {
        Driver.add(new DriverEnderAttuned());
        Driver.add(new DriverEnergyHandler());
        Driver.add(new DriverLamp());
        Driver.add(new DriverEnergyInfo());
        Driver.add(new DriverRedstoneControl());
        Driver.add(new DriverSecureTile());
    }

    @Override
    public void populate(final Map<String, Object> map, final ItemStack stack) {
        final Item item = stack.getItem();
        if (item instanceof IEnergyContainerItem) {
            final IEnergyContainerItem energyItem = (IEnergyContainerItem) item;
            map.put("energy", energyItem.getEnergyStored(stack));
            map.put("maxEnergy", energyItem.getMaxEnergyStored(stack));
        }
    }
}
