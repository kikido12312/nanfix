package com.example.nanfix;

import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.ArrayList;

@Mod(NanFix.MODID)
public class NanFix {
    public static final String MODID = "nanfix";
    private static final Logger LOGGER = LogUtils.getLogger();

    public NanFix() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    private static boolean bad(float v) {
        return Float.isNaN(v) || Float.isInfinite(v);
    }

    private static boolean bad(double v) {
        return Double.isNaN(v) || Double.isInfinite(v);
    }

    /** Каждый тик проверяем сущность и чиним всё, что сломано. */
    @SubscribeEvent
    public void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity e = event.getEntity();
        if (e.level().isClientSide) return;
        fixEntity(e);
    }

    private void fixEntity(LivingEntity e) {
        // 1. Максимальное здоровье (из-за него getMaxHealth() может быть NaN)
        AttributeInstance maxAttr = e.getAttribute(Attributes.MAX_HEALTH);
        double defaultMax = 20.0D;
        if (maxAttr != null) {
            defaultMax = maxAttr.getAttribute().getDefaultValue();

            if (bad(maxAttr.getBaseValue())) {
                LOGGER.warn("[NaN Fix] MAX_HEALTH base у {} был {}, ставлю {}",
                        e.getName().getString(), maxAttr.getBaseValue(), defaultMax);
                maxAttr.setBaseValue(defaultMax);
            }

            // Модификаторы с NaN/Infinity тоже ломают значение
            if (bad(maxAttr.getValue())) {
                for (AttributeModifier mod : new ArrayList<>(maxAttr.getModifiers())) {
                    if (bad(mod.getAmount())) {
                        LOGGER.warn("[NaN Fix] Удаляю сломанный модификатор MAX_HEALTH '{}' у {}",
                                mod.getName(), e.getName().getString());
                        maxAttr.removeModifier(mod);
                    }
                }
                if (bad(maxAttr.getValue())) {
                    maxAttr.setBaseValue(defaultMax);
                }
            }
        }

        // 2. Текущее здоровье
        float health = e.getHealth();
        if (bad(health)) {
            float max = e.getMaxHealth();
            if (bad(max) || max <= 0) max = (float) defaultMax;
            LOGGER.warn("[NaN Fix] Здоровье у {} было {}, восстанавливаю до {}",
                    e.getName().getString(), health, max);
            e.setHealth(max);
        }

        // 3. Поглощение (золотые сердца)
        if (bad(e.getAbsorptionAmount())) {
            e.setAbsorptionAmount(0.0F);
        }

        // 4. Сытость у игрока
        if (e instanceof Player p) {
            if (bad(p.getFoodData().getSaturationLevel())) {
                p.getFoodData().setSaturation(5.0F);
            }
        }
    }

    /** Урон с NaN обнуляем, иначе он снова испортит здоровье. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onHurt(LivingHurtEvent event) {
        if (bad(event.getAmount())) {
            LOGGER.warn("[NaN Fix] Урон {} по {} обнулён", event.getAmount(), event.getEntity().getName().getString());
            event.setAmount(0.0F);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onDamage(LivingDamageEvent event) {
        if (bad(event.getAmount())) {
            event.setAmount(0.0F);
            event.setCanceled(true);
        }
    }

    /** Лечение с NaN отменяем. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onHeal(LivingHealEvent event) {
        if (bad(event.getAmount())) {
            LOGGER.warn("[NaN Fix] Лечение {} у {} отменено", event.getAmount(), event.getEntity().getName().getString());
            event.setCanceled(true);
        }
    }
}
