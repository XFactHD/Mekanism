package mekanism.tools.common.item;

import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import mekanism.common.registration.impl.ItemDeferredRegister;
import mekanism.tools.common.IHasRepairType;
import mekanism.tools.common.ToolsLang;
import mekanism.tools.common.material.BaseMekanismMaterial;
import net.minecraft.block.BlockState;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.ai.attributes.Attribute;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ShovelItem;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.ToolType;

public class ItemMekanismShovel extends ShovelItem implements IHasRepairType {

    private final BaseMekanismMaterial material;

    public ItemMekanismShovel(BaseMekanismMaterial material) {
        super(material, material.getShovelDamage(), material.getShovelAtkSpeed(), ItemDeferredRegister.getMekBaseProperties());
        this.material = material;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void addInformation(@Nonnull ItemStack stack, @Nullable World world, @Nonnull List<ITextComponent> tooltip, @Nonnull ITooltipFlag flag) {
        tooltip.add(ToolsLang.HP.translate(stack.getMaxDamage() - stack.getDamage()));
    }

    public float getAttackDamage() {
        return material.getShovelDamage() + getTier().getAttackDamage();
    }

    public int getHarvestLevel() {
        return getTier().getHarvestLevel();
    }

    /**
     * {@inheritDoc}
     *
     * @implNote Wrap {@link net.minecraft.item.ToolItem#getDestroySpeed(ItemStack, BlockState)} to return our efficiency level
     */
    @Override
    public float getDestroySpeed(@Nonnull ItemStack stack, BlockState state) {
        return getToolTypes(stack).stream().anyMatch(state::isToolEffective) || effectiveBlocks.contains(state.getBlock()) ? getTier().getEfficiency() : 1;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote Patches {@link ShovelItem} to return true for more than just snow
     */
    @Override
    public boolean canHarvestBlock(BlockState state) {
        return state.getHarvestTool() == ToolType.SHOVEL ? getHarvestLevel() >= state.getHarvestLevel() : super.canHarvestBlock(state);
    }

    @Nonnull
    @Override
    public Ingredient getRepairMaterial() {
        return getTier().getRepairMaterial();
    }

    @Override
    public int getMaxDamage(ItemStack stack) {
        return getTier().getMaxUses();
    }

    @Override
    public boolean isDamageable() {
        return getTier().getMaxUses() > 0;
    }

    @Override
    public int getHarvestLevel(@Nonnull ItemStack stack, @Nonnull ToolType tool, @Nullable PlayerEntity player, @Nullable BlockState blockState) {
        return tool == ToolType.SHOVEL ? getHarvestLevel() : super.getHarvestLevel(stack, tool, player, blockState);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote We bypass calling super to ensure we get added instead of not being able to add the proper values that {@link net.minecraft.item.ToolItem} tries to set
     */
    @Nonnull
    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(@Nonnull EquipmentSlotType slot, @Nonnull ItemStack stack) {
        //TODO - 1.16: Cache this, and update it when one of the values change
        Multimap<Attribute, AttributeModifier> attributes = HashMultimap.create();
        if (slot == EquipmentSlotType.MAINHAND) {
            attributes.put(Attributes.field_233823_f_, new AttributeModifier(ATTACK_DAMAGE_MODIFIER, "Tool modifier", getAttackDamage(), Operation.ADDITION));
            attributes.put(Attributes.field_233825_h_, new AttributeModifier(ATTACK_SPEED_MODIFIER, "Tool modifier", material.getShovelAtkSpeed(), Operation.ADDITION));
        }
        return attributes;
    }
}