package at.vl.jJK.managers;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import net.kyori.adventure.text.minimessage.MiniMessage;

import at.vl.jJK.JJK;

public class CursedEnergyManager implements JJKManager {
	private final JJK jjk;
	private final Map<UUID, Double> cursedEnergy = new HashMap<>();

	private double defaultMaxCursedEnergy;
	private double regenAmount;
	private long regenPeriodTicks;

	private BukkitTask task;

	private final NamespacedKey maxCursedEnergyKey;

	public CursedEnergyManager(JJK jjk) {
		this.jjk = jjk;

		defaultMaxCursedEnergy = jjk.getConfig().getDouble("CursedEnergy.DefaultMaxCursedEnergy");
		regenAmount = jjk.getConfig().getDouble("CursedEnergy.RegenAmount");
		regenPeriodTicks = jjk.getConfig().getLong("CursedEnergy.RegenPeriodTicks");

		maxCursedEnergyKey = new NamespacedKey(jjk, "max_cursed_energy");
	}

	@Override
	public void start() {
		task = new BukkitRunnable() {
			@Override
			public void run() {
				for (Player player : Bukkit.getOnlinePlayers()) {
					// If max energy is null set to default max energy
					if (getMaxCursedEnergy(player) == null) {
						setMaxCursedEnergy(player, defaultMaxCursedEnergy);
					}

					// No regen while a shikigami is out — it's already draining cursed energy on
					// its own schedule, which keeps the action bar refreshed in the meantime.
					if (jjk.getShikigamiManager().hasActiveShikigami(player.getUniqueId())) {
						continue;
					}

					// Only regen and show if player isnt at max
					if (!(getCursedEnergy(player) == getMaxCursedEnergy(player))) {
						regen(player);
						displayActionBar(player);
					}
				}
			}
		}.runTaskTimer(jjk, regenPeriodTicks, regenPeriodTicks);
	}

	public void regen(Player player) {
		setCursedEnergy(player, getCursedEnergy(player) + getRegenAmount(player));
	}

	public void stop() {
		if (task != null) {
			task.cancel();
		}
	}

	public double getCursedEnergy(Player player) {
		return cursedEnergy.getOrDefault(player.getUniqueId(), 0d);
	}

	public void setCursedEnergy(Player player, double value) {
		double clamped = Math.max(0, Math.min(getMaxCursedEnergy(player), value));
		cursedEnergy.put(player.getUniqueId(), clamped);
	}

	public void reduceCursedEnergy(Player player, double amount) {
		setCursedEnergy(player, getCursedEnergy(player) - amount);
		displayActionBar(player); // instant feedback, don't wait for next tick

		if (getCursedEnergy(player) <= 0) {
			jjk.getShikigamiManager().despawnAll(player.getUniqueId());
		}
	}


	public void displayActionBar(Player player) {
		String cursedEnergy = "<gradient:#BF606C:#77C255>Cursed Energy:</gradient> <white>"
				+  (int) Math.round(getCursedEnergy(player)) + " / " + (int) Math.round(getMaxCursedEnergy(player)) + "</white>";

		String cursedTechnique = jjk.getCursedTechniqueManager().getPlayerCursedTechnique(player).getDisplayName();

		player.sendActionBar(MiniMessage.miniMessage().deserialize(cursedEnergy +
				"                 " + "Cursed Technique: " + cursedTechnique));
	}

	public void remove(Player player) {
		cursedEnergy.remove(player.getUniqueId());
	}

	public void reload() {
		regenAmount = jjk.getConfig().getDouble("CursedEnergy.RegenAmount");
		regenPeriodTicks = jjk.getConfig().getLong("CursedEnergy.RegenPeriodTicks");
	}

	public void setMaxCursedEnergy(Player player, double maxCursedEnergy) {
		player.getPersistentDataContainer().set(
				maxCursedEnergyKey, PersistentDataType.DOUBLE, maxCursedEnergy);
	}

	public Double getMaxCursedEnergy(Player player) {
		Double value = player.getPersistentDataContainer().get(
				maxCursedEnergyKey, PersistentDataType.DOUBLE);

		if (value == null) {
			return null;
		}

		return value;
	}

	public Double getRegenAmount(Player player) {
		return getMaxCursedEnergy(player) * regenAmount;
	}

	public Double getDefaultMaxEnergy() {
		return defaultMaxCursedEnergy;
	}
}
