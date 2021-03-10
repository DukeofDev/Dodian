package net.dodian.plugins.impl.skill;

import net.dodian.game.events.EventHandler;
import net.dodian.game.events.EventListener;
import net.dodian.game.events.impl.player.interact.item.PlayerItemOnItemUsage;
import net.dodian.old.engine.task.Task;
import net.dodian.old.engine.task.TaskManager;
import net.dodian.old.util.Misc;
import net.dodian.old.world.entity.impl.player.Player;
import net.dodian.old.world.model.Animation;
import net.dodian.old.world.model.Item;
import net.dodian.old.world.model.Skill;
import net.dodian.old.world.model.movement.MovementStatus;
import org.springframework.stereotype.Component;

@Component
public class Firemaking implements EventListener{
    //Defining my items
    private static final Item TINDERBOX = new Item(590);
    //Defining my Animation
    private static final Animation LIGHTING_LOGS_ANIMATION = new Animation(733);

    //Declare Logs Data
    private enum Burn {
        LOGS(1511, 1, 120),
        OAK_LOGS(1521, 15, 180),
        WILLOW_LOGS(1519, 30,270),
        MAPLE_LOGS(1517, 45, 405),
        YEW_LOGS(1515, 60, 606),
        MAGIC_LOGS(1513, 75, 909);
        final int itemID;
        final int level;
        final int xp;
        Burn(int itemID, int level, int xp){
            this.itemID = itemID;
            this.level = level;
            this.xp = xp;
        }
    }

    //Data Access Method that compare the item with the logs data declared then return it.
    public Burn getBurnData(Item item){
        for (Burn data : Burn.values()) {
            if (data.itemID == item.getId()){
                return data;
            }
        }
        return null;
    }

    @EventHandler
    public void onItemUsage(PlayerItemOnItemUsage event) {
        boolean usingTinderbox = event.getUsed().getId() == TINDERBOX.getId() || event.getUsedOn().getId() == TINDERBOX.getId();
        if(usingTinderbox) {
            Item itemUsedWithTinderBox = event.getUsed().getId() == TINDERBOX.getId() ? event.getUsedOn() : event.getUsed();
            Firemaking.Burn burnData = getBurnData(itemUsedWithTinderBox);
            if(burnData != null) {
                event.getPlayer().setWalkToTask(null);
                TaskManager.cancelTasks(event.getPlayer());
                logBurn(event, burnData);
            }
        }
    }

    private <T extends PlayerItemOnItemUsage> void logBurn(T event, Burn burnData) {
        //check if player can do the action based on his level
        if(event.getPlayer().getSkillManager().getCurrentLevel(Skill.FIREMAKING) < burnData.level) {
            event.getPlayer().getPacketSender().sendMessage("You need level " + burnData.level + " Firemaking to burn " + new Item(burnData.itemID).getDefinition().getName().toLowerCase() + "!");
            return;
        }

        //Cycle until inventory is burned.
        Task task = new Task(1, event.getPlayer(), true) {
            final Player player = event.getPlayer();
            double cycle = getFiremakingDelay(player);
            @Override
            protected void execute() {
                /* Stops action! */
                if (player.getMovementQueue().getMovementStatus().equals(MovementStatus.DISABLED)) {
                    stop();
                } else if (!player.getInventory().contains(TINDERBOX)) {
                    player.getPacketSender().sendMessage("You are missing a Tinderbox!");
                    stop();
                } else if (!player.getInventory().contains(burnData.itemID)) {
                    player.getPacketSender().sendMessage("You are out of " + new Item(burnData.itemID).getDefinition().getName().toLowerCase());
                    stop();
                }

                /* Cycle action! */
                if((int)cycle % 3 == 0)
                    event.getPlayer().performAnimation(LIGHTING_LOGS_ANIMATION);
                if (cycle > 0) {
                    cycle--;
                } else {
                    player.getInventory().delete(new Item(burnData.itemID));
                    player.getSkillManager().addExperience(Skill.FIREMAKING, burnData.xp);
                    player.getPacketSender().sendMessage("You burn a " + new Item(burnData.itemID).getDefinition().getName().toLowerCase() + ".");
                    cycle = getFiremakingDelay(player);

                    //randomly take a rest
                    if(Misc.getRandom(49) == 0) {
                        event.getPlayer().getPacketSender().sendMessage("You take a rest");
                        stop();
                    }
                }
            }
            @Override
            public void stop() {
                cycle = 0;
                event.getPlayer().performAnimation(new Animation(65535));
                this.setEventRunning(false);
            }
        };
        TaskManager.submit(task);
    }

    private long getFiremakingDelay(Player player) {
        double baseTime = 1;
        double level = player.getSkillManager().getCurrentLevel(Skill.FIREMAKING) / 200D;
        double random = Misc.getRandom(150) / 100D;
        double time = baseTime + random / level;
        System.out.println("Time = "+time/900L);
        return (long) time;
    }
}