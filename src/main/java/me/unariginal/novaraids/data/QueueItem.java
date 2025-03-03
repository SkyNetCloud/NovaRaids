package me.unariginal.novaraids.data;

import me.unariginal.novaraids.NovaRaids;
import me.unariginal.novaraids.managers.Raid;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

public record QueueItem(Boss boss_info, Location raidBoss_location, ServerPlayerEntity started_by, ItemStack starting_item) {
    private static final NovaRaids nr = NovaRaids.INSTANCE;

    public void start_raid() {
        nr.add_raid(new Raid(boss_info, raidBoss_location, started_by, starting_item));
    }

    public void cancel_item() {
        if (nr.config().getCategory(boss_info.category()).require_pass()) {
            if (starting_item != null) {
                started_by.giveItemStack(starting_item);
            }
        }
    }
}
