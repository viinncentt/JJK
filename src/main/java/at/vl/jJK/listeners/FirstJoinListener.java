package at.vl.jJK.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import net.kyori.adventure.text.minimessage.MiniMessage;

import at.vl.jJK.JJK;

import static org.bukkit.Bukkit.getServer;

public class FirstJoinListener implements Listener {
	private JJK jjk;

	public FirstJoinListener(JJK jjk) {
		this.jjk = jjk;
	}

	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent event) {
		Player player = event.getPlayer();

		// Check if this is the player's first time joining
		if (!player.hasPlayedBefore()) {
			player.sendMessage(MiniMessage.miniMessage().deserialize("<yellow> Welcome! </yellow>"));

			event.joinMessage(null);
			for (Player p : getServer().getOnlinePlayers()) {
			    	p.sendMessage(MiniMessage.miniMessage().deserialize(
							"<rainbow>" + player.getName() + "</rainbow><cyan>has joined for the first time!</cyan>"));
			}

			jjk.getCursedEnergyManager().setMaxCursedEnergy(player, jjk.getCursedEnergyManager().getDefaultMaxEnergy());
		}
	}
}
