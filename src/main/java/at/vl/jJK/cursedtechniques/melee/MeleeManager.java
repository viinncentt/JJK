package at.vl.jJK.cursedtechniques.melee;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import at.vl.jJK.managers.JJKManager;
import at.vl.jJK.JJK;
import at.vl.jJK.cursedtechniques.melee.Melee;
import at.vl.jJK.cursedtechniques.melee.MeleeType;

public class MeleeManager implements JJKManager {
	private final JJK jjk;
	private final Melee melee;

	private final Map<UUID, Map<MeleeType, Long>> cooldowns = new HashMap<>();

	public MeleeManager(JJK jjk) {
		this.jjk = jjk;
		this.melee = new Melee(jjk);
	}

	@Override
	public void start() {
	}

	public void reload() {
		melee.reload();
	}

	private NamespacedKey moveKey(MeleeType type) {
		return new NamespacedKey(jjk, "melee_" + type.name());
	}

	public boolean hasMove(Player player, MeleeType type) {
		return player.getPersistentDataContainer()
				.getOrDefault(moveKey(type), PersistentDataType.BOOLEAN, false);
	}

	public void unlockMove(Player player, MeleeType type) {
		player.getPersistentDataContainer().set(moveKey(type), PersistentDataType.BOOLEAN, true);
	}

	public void unlockAllMoves(Player player) {
		for (MeleeType type : MeleeType.values()) {
			unlockMove(player, type);
		}
	}

	public boolean hasAnyUnlocked(Player player) {
		for (MeleeType type : MeleeType.values()) {
			if (hasMove(player, type)) {
				return true;
			}
		}
		return false;
	}

	public boolean isOnCooldown(Player player, MeleeType type) {
		return getRemainingCooldown(player, type) > 0;
	}

	public long getRemainingCooldown(Player player, MeleeType type) {
		Long expiry = cooldowns.getOrDefault(player.getUniqueId(), Map.of()).get(type);
		if (expiry == null) {
			return 0L;
		}
		return Math.max(0L, expiry - System.currentTimeMillis());
	}

	public void startCooldown(Player player, MeleeType type, long cooldownMillis) {
		cooldowns
				.computeIfAbsent(player.getUniqueId(), uuid -> new HashMap<>())
				.put(type, System.currentTimeMillis() + cooldownMillis);
	}

	public void clearCooldowns(Player player) {
		cooldowns.remove(player.getUniqueId());
	}

	public void activate(Player player, MeleeType type) {
		melee.activateMove(player, type);
	}

	public void startBarrageChannel(Player player) {
		melee.startBarrageChannel(player);
	}

	public void stopBarrageChannel(Player player) {
		melee.stopBarrageChannel(player);
	}
}
