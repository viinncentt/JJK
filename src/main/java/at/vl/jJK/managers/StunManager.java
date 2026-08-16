package at.vl.jJK.managers;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;

import at.vl.jJK.JJK;

/**
 * Generic per-entity stun state — any ability can call {@link #stun(LivingEntity, long)} to lock a
 * target's movement for a duration. Works on both players (movement is cancelled by
 * {@code StunListener}) and mobs (AI is paused directly via {@link Mob#setAware(boolean)}, since
 * there's no move event to cancel for entities driven by AI instead of client input).
 */
public class StunManager implements JJKManager {
	private final JJK jjk;

	private final Map<UUID, Long> stunExpiry = new HashMap<>();

	public StunManager(JJK jjk) {
		this.jjk = jjk;
	}

	@Override
	public void start() {
	}

	public void stun(LivingEntity entity, long durationMillis) {
		boolean wasStunned = isStunned(entity);
		stunExpiry.put(entity.getUniqueId(), System.currentTimeMillis() + durationMillis);

		if (!wasStunned && entity instanceof Mob mob) {
			mob.setAware(false);
		}

		long delayTicks = Math.max(1L, durationMillis / 50L + 1L);
		Bukkit.getScheduler().runTaskLater(jjk, () -> {
			if (entity.isValid() && !isStunned(entity)) {
				restoreAwareness(entity);
			}
		}, delayTicks);
	}

	public boolean isStunned(LivingEntity entity) {
		Long expiry = stunExpiry.get(entity.getUniqueId());
		return expiry != null && expiry > System.currentTimeMillis();
	}

	public void clearStun(LivingEntity entity) {
		stunExpiry.remove(entity.getUniqueId());
		restoreAwareness(entity);
	}

	private void restoreAwareness(LivingEntity entity) {
		if (entity instanceof Mob mob) {
			mob.setAware(true);
		}
	}
}
