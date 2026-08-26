package dev.lopyluna.create_lnl.content.utils;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import static net.minecraft.core.registries.Registries.*;


@SuppressWarnings({"unused", "removal"})
public class LiftsRegistry {
    public final String name;
    public final String modID;

    public LiftsRegistry(String name, String modID) {
        this.name = name;
        this.modID = modID;
        DATA_COMPONENTS = DeferredRegister.createDataComponents(modID);
        DAMAGES = DeferredRegister.create(DAMAGE_TYPE, modID);
        MOB_EFFECTS = DeferredRegister.create(MOB_EFFECT, modID);
        PARTICLES = DeferredRegister.create(PARTICLE_TYPE, modID);
        RECIPE_SERIALIZERS = DeferredRegister.create(RECIPE_SERIALIZER, modID);
        RECIPES = DeferredRegister.create(RECIPE_TYPE, modID);
        MENUS = DeferredRegister.create(MENU, modID);
        SOUNDS = DeferredRegister.create(SOUND_EVENT, modID);
        CREATIVE_MODE_TABS = DeferredRegister.create(CREATIVE_MODE_TAB, modID);
        DIMENSIONS = DeferredRegister.create(DIMENSION, modID);
        ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, modID);
    }

    public final DeferredRegister.DataComponents DATA_COMPONENTS;
    public final DeferredRegister<DamageType> DAMAGES;
    public final DeferredRegister<MobEffect> MOB_EFFECTS;
    public final DeferredRegister<ParticleType<?>> PARTICLES;
    public final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS;
    public final DeferredRegister<RecipeType<?>> RECIPES;
    public final DeferredRegister<MenuType<?>> MENUS;
    public final DeferredRegister<SoundEvent> SOUNDS;
    public final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS;
    public final DeferredRegister<Level> DIMENSIONS;
    public final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES;

    public DeferredRegister.DataComponents components() {return DATA_COMPONENTS;}
    public DeferredRegister<CreativeModeTab> creativeTab() {return CREATIVE_MODE_TABS;}
    public DeferredRegister<DamageType> damages() {return DAMAGES;}
    public DeferredRegister<MobEffect> mobEffects() {return MOB_EFFECTS;}
    public DeferredRegister<ParticleType<?>> particles() {return PARTICLES;}
    public DeferredRegister<RecipeSerializer<?>> recipe_ser() {return RECIPE_SERIALIZERS;}
    public DeferredRegister<RecipeType<?>> recipes() {return RECIPES;}
    public DeferredRegister<MenuType<?>> menus() {return MENUS;}
    public DeferredRegister<SoundEvent> sounds() {return SOUNDS;}
    public DeferredRegister<Level> dimensions() {return DIMENSIONS;}
    public DeferredRegister<AttachmentType<?>> attachments() {return ATTACHMENT_TYPES;}

    public void register(IEventBus bus) {
        System.out.println("Registering " + name + " Data Components...");
        DATA_COMPONENTS.register(bus);
        System.out.println("Registering " + name + " Creative Tabs...");
        CREATIVE_MODE_TABS.register(bus);
        System.out.println("Registering " + name + " Damage Types...");
        DAMAGES.register(bus);
        System.out.println("Registering " + name + " Mob Effects...");
        MOB_EFFECTS.register(bus);
        System.out.println("Registering " + name + " Particles...");
        PARTICLES.register(bus);
        System.out.println("Registering " + name + " Recipes Serializers...");
        RECIPE_SERIALIZERS.register(bus);
        System.out.println("Registering " + name + " Recipes...");
        RECIPES.register(bus);
        System.out.println("Registering " + name + " Menus...");
        MENUS.register(bus);
        System.out.println("Registering " + name + " Sounds...");
        SOUNDS.register(bus);
        System.out.println("Registering " + name + " Dimensions...");
        DIMENSIONS.register(bus);
        System.out.println("Registering " + name + " Attachments...");
        ATTACHMENT_TYPES.register(bus);
        System.out.println("Registering " + name + " Done");
    }
}