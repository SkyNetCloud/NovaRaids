package me.unariginal.novaraids.mixin;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import me.unariginal.novaraids.NovaRaids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PokemonEntity.class)
public class PreventHeldItems {
    @Inject(at = {@At("HEAD")}, method = {"updatePostDeath"})
    public void updatePostDeath(CallbackInfo ci) {
        PokemonEntity entity = (PokemonEntity) (Object) this;
        Pokemon pokemon = entity.getPokemon();
        if (pokemon.getPersistentData().contains("raid_entity")) {
            if (pokemon.getPersistentData().getBoolean("raid_entity")) {
                NovaRaids.INSTANCE.logInfo("[RAIDS] Raid entity detected! (Pokemon Entity Mixin");
                pokemon.removeHeldItem();
            }
        }
    }
}
