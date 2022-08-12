package me.mykindos.betterpvp.clans.champions.skills.skills.warlock.sword;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.Data;
import me.mykindos.betterpvp.clans.Clans;
import me.mykindos.betterpvp.clans.champions.ChampionsManager;
import me.mykindos.betterpvp.clans.champions.roles.Role;
import me.mykindos.betterpvp.clans.champions.skills.data.SkillActions;
import me.mykindos.betterpvp.clans.champions.skills.data.SkillType;
import me.mykindos.betterpvp.clans.champions.skills.types.CooldownSkill;
import me.mykindos.betterpvp.clans.champions.skills.types.PrepareSkill;
import me.mykindos.betterpvp.core.combat.events.CustomDamageEvent;
import me.mykindos.betterpvp.core.framework.updater.UpdateEvent;
import me.mykindos.betterpvp.core.listener.BPvPListener;
import me.mykindos.betterpvp.core.utilities.UtilBlock;
import me.mykindos.betterpvp.core.utilities.UtilDamage;
import me.mykindos.betterpvp.core.utilities.UtilEntity;
import me.mykindos.betterpvp.core.utilities.UtilPlayer;
import me.mykindos.betterpvp.core.utilities.events.EntityProperty;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

@Singleton
@BPvPListener
public class Leech extends PrepareSkill implements CooldownSkill {

    private final List<LeechData> leechData = new ArrayList<>();
    private final List<LeechData> removeList = new ArrayList<>();

    @Inject
    public Leech(Clans clans, ChampionsManager championsManager) {
        super(clans, championsManager);
    }


    @Override
    public String getName() {
        return "Leech";
    }

    @Override
    public String[] getDescription(int level) {
        return new String[]{"Right click with a sword to activate.",
                "",
                "Create a soul link between all enemies within 7 blocks",
                "of your target, and all enemies within 7 blocks of them",
                "",
                "Linked targets have 1 health leeched per second.",
                "All leeched health is given to the caster.",
                "",
                "Recharge: " + ChatColor.GREEN + getCooldown(level)
        };
    }

    @Override
    public Role getClassType() {
        return Role.WARLOCK;
    }


    @EventHandler
    public void onDamage(CustomDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        if (!(event.getDamager() instanceof Player damager)) return;
        if (!active.contains(damager.getUniqueId())) return;

        int level = getLevel(damager);
        if(level > 0) {
            leechData.add(new LeechData(damager, damager, event.getDamagee()));
            chainEnemies(damager, event.getDamagee());
            active.remove(damager.getUniqueId());

            championsManager.getCooldowns().removeCooldown(damager, getName(), true);
            championsManager.getCooldowns().add(damager, getName(), getCooldown(level), showCooldownFinished());
        }

    }

    private void chainEnemies(Player player, LivingEntity link) {
        List<LivingEntity> temp = new ArrayList<>();
        for (var entAData : UtilEntity.getNearbyEntities(player, link.getLocation(), 7, EntityProperty.ENEMY)) {
            LivingEntity entA = entAData.get();
            if (isNotLinked(player, entA)) {
                leechData.add(new LeechData(player, link, entA));
                temp.add(entA);
            }


        }

        for (LivingEntity entA : temp) {
            for (var entBData : UtilEntity.getNearbyEntities(player, entA.getLocation(), 7, EntityProperty.ENEMY)) {
                LivingEntity entB = entBData.get();
                if (isNotLinked(player, entB)) {
                    leechData.add(new LeechData(player, entA, entB));
                }
            }
        }
    }

    private void removeLinks(LivingEntity link) {
        List<LivingEntity> children = new ArrayList<>();
        leechData.forEach(leech -> {
            if (leech.getLinkedTo().getUniqueId().equals(link.getUniqueId()) || leech.getTarget().getUniqueId().equals(link.getUniqueId())) {
                children.add(leech.getTarget());
                children.add(leech.getLinkedTo());
                removeList.add(leech);
            }
        });

        children.forEach(ent -> {
            leechData.forEach(leech -> {
                if (leech.getLinkedTo().getUniqueId().equals(ent.getUniqueId()) || leech.getTarget().getUniqueId().equals(ent.getUniqueId())) {
                    removeList.add(leech);
                }
            });
        });


    }

    private void breakChain(LeechData leech) {
        leechData.forEach(l -> {
            if (l.getOwner().getUniqueId().equals(leech.getOwner().getUniqueId())) {
                removeList.add(l);
            }
        });
    }

    private boolean isNotLinked(Player player, LivingEntity ent) {
        if (player.equals(ent)) return false;
        for (LeechData leech : leechData) {
            if (leech.owner.equals(player)) {
                if (leech.linkedTo.equals(ent) || leech.target.equals(ent)) {
                    return false;
                }
            }
        }

        return true;
    }


    @Override
    public SkillType getType() {
        return SkillType.SWORD;
    }

    @Override
    public double getCooldown(int level) {
        return cooldown - (level * 2);
    }

    @Override
    public void activate(Player player, int level) {
        active.add(player.getUniqueId());
    }

    @Override
    public Action[] getActions() {
        return SkillActions.RIGHT_CLICK;
    }

    @UpdateEvent
    public void onLeech() {
        if (!removeList.isEmpty()) {
            leechData.removeIf(removeList::contains);
            removeList.clear();
        }
    }

    @UpdateEvent(delay = 250)
    public void chain() {
        for (LeechData leech : leechData) {
            if (leech.getLinkedTo() == null || leech.getTarget() == null || leech.getOwner() == null) {
                removeList.add(leech);
                continue;
            }

            if (leech.getLinkedTo().isDead() || leech.getOwner().isDead() || leech.getLinkedTo().isDead()) {
                if (leech.getOwner().isDead()) {
                    breakChain(leech);
                }
                removeList.add(leech);
                continue;
            }

            if (leech.getTarget().getLocation().distance(leech.getLinkedTo().getLocation()) > 7
                    || leech.getTarget().getLocation().distance(leech.getOwner().getLocation()) > 21) {
                if (leech.getLinkedTo().getUniqueId().equals(leech.getOwner().getUniqueId())) {
                    breakChain(leech);
                }
                removeList.add(leech);
            }

        }
    }

    @UpdateEvent(delay = 125)
    public void display() {
        for (LeechData leech : leechData) {
            if (leech.getLinkedTo() == null || leech.getTarget() == null || leech.getOwner() == null) {
                continue;
            }

            Location loc = leech.getLinkedTo().getLocation();
            Vector v = leech.getTarget().getLocation().toVector().subtract(loc.toVector());
            double distance = leech.getLinkedTo().getLocation().distance(leech.getTarget().getLocation());
            boolean remove = false;
            if (distance > 7) continue;
            for (double i = 0.5; i < distance; i += 0.5) {

                v.multiply(i);
                loc.add(v);
                if (UtilBlock.solid(loc.getBlock()) && UtilBlock.solid(loc.clone().add(0, 1, 0).getBlock())) {
                    remove = true;
                }
                Particle.REDSTONE.builder().location(loc.clone().add(0, 0.7, 0)).receivers(30).color(230, 0, 0).extra(0).spawn();
                loc.subtract(v);
                v.normalize();

            }

            if (remove) {
                removeList.add(leech);
            }

        }
    }

    @UpdateEvent(delay = 1000)
    public void dealDamage() {
        for (LeechData leech : leechData) {
            CustomDamageEvent leechDmg = new CustomDamageEvent(leech.getTarget(), leech.getOwner(), null, EntityDamageEvent.DamageCause.MAGIC, 1, false, getName());
            leechDmg.setIgnoreArmour(true);
            UtilDamage.doCustomDamage(leechDmg);
            UtilPlayer.health(leech.getOwner(), 1);
        }
    }

    @EventHandler
    public void removeOnDeath(EntityDeathEvent e) {
        removeLinks(e.getEntity());
    }


    @Data
    private static class LeechData {
        private final Player owner;

        private final LivingEntity linkedTo;
        private final LivingEntity target;

    }
}
