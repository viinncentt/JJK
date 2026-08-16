package at.vl.jJK.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import at.vl.jJK.JJK;

public class PlayerJoinListener implements Listener {
	private JJK jjk;

	public PlayerJoinListener(JJK jjk) {
		this.jjk = jjk;
	}

	@EventHandler
	public void onJoin(PlayerJoinEvent event) {
		jjk.getScoreboardManager().createScoreboard(event.getPlayer());

		// Set current cursed energy to max on join
		if (jjk.getCursedEnergyManager().getMaxCursedEnergy(event.getPlayer()) == null) {
			jjk.getCursedEnergyManager().setMaxCursedEnergy(event.getPlayer(), jjk.getCursedEnergyManager().getDefaultMaxEnergy());
		}
		jjk.getCursedEnergyManager().setCursedEnergy(event.getPlayer(), jjk.getCursedEnergyManager().getMaxCursedEnergy(event.getPlayer()));

		// Activate Cursed Technique
		jjk.getCursedTechniqueManager().activateTechnique(event.getPlayer());
	}
}
