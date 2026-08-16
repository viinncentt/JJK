package at.vl.jJK.cursedtechniques.weaponeering;

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
import at.vl.jJK.cursedtechniques.weaponeering.WeaponeeringType;

public class WeaponeeringManager implements JJKManager {
	private final JJK jjk;

	private File file;
	private FileConfiguration config;

	private final Map<UUID, Map<WeaponeeringType, Long>> cooldowns = new HashMap<>();

	public WeaponeeringManager(JJK jjk) {
		this.jjk = jjk;
	}

	@Override
	public void start() {
		file = new File(jjk.getDataFolder(), "abilities/weaponeering.yml");

		if (!file.exists()) {
			jjk.saveResource("abilities/weaponeering.yml", false);
		}

		config = YamlConfiguration.loadConfiguration(file);

		loadMoves();
	}

	private void loadMoves() {
		ConfigurationSection section = config.getConfigurationSection("moves");

		if (section == null) {
			jjk.getLogger().info("No 'moves' section found in weaponeering.yml!");
			return;
		}

		for (WeaponeeringType type : WeaponeeringType.values()) {
			String path = "moves." + type.name();

			String displayName = config.getString(path + ".display-name", type.name());
			String materialName = config.getString(path + ".material", "IRON_SWORD");

			Material material;
			try {
				material = Material.valueOf(materialName.toUpperCase());
			} catch (IllegalArgumentException e) {
				jjk.getLogger().warning("Invalid material '" + materialName + "' for weaponeering move " + type.name()
						+ ", defaulting to IRON_SWORD");
				material = Material.IRON_SWORD;
			}

			long cooldownMillis = config.getLong(path + ".cooldown", 0);
			double cursedEnergyCost = config.getDouble(path + ".cursed-energy-cost", 0);

			type.load(displayName, material, cooldownMillis, cursedEnergyCost);
		}
	}

	public void reload() {
		start();
	}

	private NamespacedKey moveKey(WeaponeeringType type) {
		return new NamespacedKey(jjk, "weaponeering_" + type.name());
	}

	public boolean hasMove(Player player, WeaponeeringType type) {
		return player.getPersistentDataContainer()
				.getOrDefault(moveKey(type), PersistentDataType.BOOLEAN, false);
	}

	public void unlockMove(Player player, WeaponeeringType type) {
		player.getPersistentDataContainer().set(moveKey(type), PersistentDataType.BOOLEAN, true);
	}

	public void unlockAllMoves(Player player) {
		for (WeaponeeringType type : WeaponeeringType.values()) {
			unlockMove(player, type);
		}
	}

	public boolean hasAnyUnlocked(Player player) {
		for (WeaponeeringType type : WeaponeeringType.values()) {
			if (hasMove(player, type)) {
				return true;
			}
		}
		return false;
	}

	public boolean isOnCooldown(Player player, WeaponeeringType type) {
		return getRemainingCooldown(player, type) > 0;
	}

	public long getRemainingCooldown(Player player, WeaponeeringType type) {
		Long expiry = cooldowns.getOrDefault(player.getUniqueId(), Map.of()).get(type);
		if (expiry == null) {
			return 0L;
		}
		return Math.max(0L, expiry - System.currentTimeMillis());
	}

	public void startCooldown(Player player, WeaponeeringType type, long cooldownMillis) {
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
	public void activate(Player player, WeaponeeringType type) {
		if (isOnCooldown(player, type)) {
			return;
		}

		startCooldown(player, type, type.getCooldownMillis());

		switch (type) {
			case SWORD_SLASH -> activateSwordSlash(player);
		}
	}

	private void activateSwordSlash(Player player) {
		// TODO: real effect.
	}

	public FileConfiguration getConfig() {
		return config;
	}
}
