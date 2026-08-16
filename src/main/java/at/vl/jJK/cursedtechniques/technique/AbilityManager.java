package at.vl.jJK.cursedtechniques.technique;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import at.vl.jJK.managers.JJKManager;
import at.vl.jJK.JJK;
import at.vl.jJK.cursedtechniques.technique.AbilityType;
import at.vl.jJK.cursedtechniques.technique.CursedTechniqueType;

public class AbilityManager implements JJKManager {
	private JJK jjk;

	private final Map<UUID, Map<AbilityType, Long>> cooldowns = new HashMap<>();

	public AbilityManager(JJK jjk) {
		this.jjk = jjk;
	}

	private NamespacedKey abilityKey(AbilityType type) {
		return new NamespacedKey(jjk, "ability_" + type.name());
	}

	public boolean hasAbility(Player player, AbilityType type) {
		return player.getPersistentDataContainer()
				.getOrDefault(abilityKey(type), PersistentDataType.BOOLEAN, false);
	}

	public void unlockAbility(Player player, AbilityType type) {
		player.getPersistentDataContainer().set(abilityKey(type), PersistentDataType.BOOLEAN, true);
	}

	public boolean isOnCooldown(Player player, AbilityType type) {
		return getRemainingCooldown(player, type) > 0;
	}

	public long getRemainingCooldown(Player player, AbilityType type) {
		Long expiry = cooldowns.getOrDefault(player.getUniqueId(), Map.of()).get(type);
		if (expiry == null) {
			return 0L;
		}
		return Math.max(0L, expiry - System.currentTimeMillis());
	}

	public void startCooldown(Player player, AbilityType type, long cooldownMillis) {
		cooldowns
				.computeIfAbsent(player.getUniqueId(), uuid -> new HashMap<>())
				.put(type, System.currentTimeMillis() + cooldownMillis);
	}

	public void clearCooldowns(Player player) {
		cooldowns.remove(player.getUniqueId());
	}

	public void unlockCursedTechniqueAbilities(Player player, CursedTechniqueType cursedTechnique) {
		for (AbilityType type : AbilityType.values()) {
			if (type.getCursedTechniques() == cursedTechnique) {
				unlockAbility(player, type);
			}
		}
	}
	@Override
	public void start() {

	}
}