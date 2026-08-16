package at.vl.jJK.cursedtechniques.melee;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.Listener;

import at.vl.jJK.JJK;
import at.vl.jJK.cursedtechniques.Bindable;
import at.vl.jJK.cursedtechniques.melee.MeleeType;

/**
 * Barrage isn't triggered by left-click like every other ability — it's active for as long as the
 * player holds sneak while on the hotbar slot it's bound to. This translates those two independent
 * conditions (sneak state, held slot) into start/stop calls on {@code MeleeManager}.
 */
public class BarrageListener implements Listener {
	private final JJK jjk;

	public BarrageListener(JJK jjk) {
		this.jjk = jjk;
	}

	@EventHandler
	public void onToggleSneak(PlayerToggleSneakEvent event) {
		Player player = event.getPlayer();

		if (!event.isSneaking()) {
			jjk.getMeleeManager().stopBarrageChannel(player);
			return;
		}

		if (isBoundToBarrage(player, player.getInventory().getHeldItemSlot())) {
			jjk.getMeleeManager().startBarrageChannel(player);
		}
	}

	@EventHandler
	public void onItemHeld(PlayerItemHeldEvent event) {
		Player player = event.getPlayer();

		// Always stop first — covers leaving the Barrage slot mid-channel.
		jjk.getMeleeManager().stopBarrageChannel(player);

		// Switching onto the Barrage slot while already sneaking should start a channel too.
		if (player.isSneaking() && isBoundToBarrage(player, event.getNewSlot())) {
			jjk.getMeleeManager().startBarrageChannel(player);
		}
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		jjk.getMeleeManager().stopBarrageChannel(event.getPlayer());
	}

	@EventHandler
	public void onDeath(PlayerDeathEvent event) {
		jjk.getMeleeManager().stopBarrageChannel(event.getPlayer());
	}

	private boolean isBoundToBarrage(Player player, int slot) {
		Bindable bound = jjk.getBindManager().getBoundAbility(player, slot);
		return bound == MeleeType.BARRAGE;
	}
}
