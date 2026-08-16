package at.vl.jJK.listeners;

import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import at.vl.jJK.JJK;

public class StunListener implements Listener {
	private final JJK jjk;

	public StunListener(JJK jjk) {
		this.jjk = jjk;
	}

	@EventHandler
	public void onMove(PlayerMoveEvent event) {
		Location from = event.getFrom();
		Location to = event.getTo();

		if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) {
			// Pure look-direction change — always allowed, even while stunned.
			return;
		}

		if (jjk.getStunManager().isStunned(event.getPlayer())) {
			// Keep the new look direction (yaw/pitch) but reset position — lets a stunned player
			// still look around freely without being able to actually move.
			Location corrected = to.clone();
			corrected.setX(from.getX());
			corrected.setY(from.getY());
			corrected.setZ(from.getZ());
			event.setTo(corrected);
		}
	}
}
