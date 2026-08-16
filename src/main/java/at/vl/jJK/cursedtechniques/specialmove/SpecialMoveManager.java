package at.vl.jJK.cursedtechniques.specialmove;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import at.vl.jJK.managers.JJKManager;
import at.vl.jJK.JJK;
import at.vl.jJK.cursedtechniques.specialmove.SpecialMoveType;

public class SpecialMoveManager implements JJKManager {
	private final JJK jjk;

	private File file;
	private FileConfiguration config;

	private final Map<UUID, Map<SpecialMoveType, Long>> cooldowns = new HashMap<>();

	public SpecialMoveManager(JJK jjk) {
		this.jjk = jjk;
	}

	@Override
	public void start() {
		file = new File(jjk.getDataFolder(), "abilities/specialmoves.yml");

		if (!file.exists()) {
			jjk.saveResource("abilities/specialmoves.yml", false);
		}

		config = YamlConfiguration.loadConfiguration(file);

		loadMoves();
	}

	private void loadMoves() {
		ConfigurationSection section = config.getConfigurationSection("moves");

		if (section == null) {
			jjk.getLogger().info("No 'moves' section found in specialmoves.yml!");
			return;
		}

		for (SpecialMoveType type : SpecialMoveType.values()) {
			String path = "moves." + type.name();

			String displayName = config.getString(path + ".display-name", type.name());
			String materialName = config.getString(path + ".material", "NETHER_STAR");

			Material material;
			try {
				material = Material.valueOf(materialName.toUpperCase());
			} catch (IllegalArgumentException e) {
				jjk.getLogger().warning("Invalid material '" + materialName + "' for special move " + type.name()
						+ ", defaulting to NETHER_STAR");
				material = Material.NETHER_STAR;
			}

			long cooldownMillis = config.getLong(path + ".cooldown", 0);
			double cursedEnergyCost = config.getDouble(path + ".cursed-energy-cost", 0);

			type.load(displayName, material, cooldownMillis, cursedEnergyCost);
		}
	}

	public void reload() {
		start();
	}

	private NamespacedKey moveKey(SpecialMoveType type) {
		return new NamespacedKey(jjk, "specialmove_" + type.name());
	}

	public boolean hasMove(Player player, SpecialMoveType type) {
		return player.getPersistentDataContainer()
				.getOrDefault(moveKey(type), PersistentDataType.BOOLEAN, false);
	}

	public void unlockMove(Player player, SpecialMoveType type) {
		player.getPersistentDataContainer().set(moveKey(type), PersistentDataType.BOOLEAN, true);
	}

	public void unlockAllMoves(Player player) {
		for (SpecialMoveType type : SpecialMoveType.values()) {
			unlockMove(player, type);
		}
	}

	public boolean hasAnyUnlocked(Player player) {
		for (SpecialMoveType type : SpecialMoveType.values()) {
			if (hasMove(player, type)) {
				return true;
			}
		}
		return false;
	}

	public boolean isOnCooldown(Player player, SpecialMoveType type) {
		return getRemainingCooldown(player, type) > 0;
	}

	public long getRemainingCooldown(Player player, SpecialMoveType type) {
		Long expiry = cooldowns.getOrDefault(player.getUniqueId(), Map.of()).get(type);
		if (expiry == null) {
			return 0L;
		}
		return Math.max(0L, expiry - System.currentTimeMillis());
	}

	public void startCooldown(Player player, SpecialMoveType type, long cooldownMillis) {
		cooldowns
				.computeIfAbsent(player.getUniqueId(), uuid -> new HashMap<>())
				.put(type, System.currentTimeMillis() + cooldownMillis);
	}

	public void clearCooldowns(Player player) {
		cooldowns.remove(player.getUniqueId());
	}

	/**
	 * Executes {@code type}'s effect. Placeholder stub — flesh out per move as they're added.
	 */
	public void activate(Player player, SpecialMoveType type) {
		// Check if player has enough cursed energy
		if (jjk.getCursedEnergyManager().getCursedEnergy(player) < type.getCursedEnergyCost()) {
			return;
		}

		if (isOnCooldown(player, type)) {
			return;
		}

		startCooldown(player, type, type.getCooldownMillis());

		jjk.getCursedEnergyManager().reduceCursedEnergy(player, type.getCursedEnergyCost());

		switch (type) {
			case BLACK_FLASH -> activateBlackFlash(player);
		}
	}

	private void activateBlackFlash(Player player) {
		// TODO: real effect.
	}

	public FileConfiguration getConfig() {
		return config;
	}
}
