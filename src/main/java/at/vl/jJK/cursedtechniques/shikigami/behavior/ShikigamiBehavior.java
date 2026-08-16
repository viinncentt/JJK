package at.vl.jJK.cursedtechniques.shikigami.behavior;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import at.vl.jJK.cursedtechniques.shikigami.ShikigamiType;

/**
 * One implementation per AI archetype, not per {@link ShikigamiType} — e.g. a single
 * {@code GroundMeleeBehavior} instance backs both Divine Dog types, each constructed with its own
 * config. {@code ShikigamiManager} holds a {@code ShikigamiType -> ShikigamiBehavior} registry
 * (built from each type's configured {@code Behavior} archetype) and delegates every
 * type-specific decision through these hooks instead of switching on type inline — adding a new
 * shikigami with genuinely different AI means writing one new class (or reusing an existing
 * archetype with different config), not touching shared summon/recall/tick code.
 */
public interface ShikigamiBehavior {

	/**
	 * Called once, inside the spawn callback, right after universal attributes/PDC/taming are set
	 * up — the hook for stripping/adding vanilla goals or anything else that has to happen before
	 * the mob is added to the world.
	 */
	default void onSummon(Mob mob, ShikigamiType type, boolean tamed) {
	}

	/**
	 * Called every AI tick for every active shikigami wired to this behavior (see
	 * {@code ShikigamiManager}'s shared tick loop). The default no-op suits archetypes that rely
	 * entirely on vanilla goals instead of custom per-tick movement.
	 */
	default void tick(Mob mob, ShikigamiType type) {
	}

	/**
	 * Called from {@code ShikigamiManager#recall} after the commanded target is already cleared and
	 * the owner is known — anything beyond that (e.g. a ground type issuing a one-shot Pathfinder
	 * command) is archetype-specific.
	 */
	default void onRecall(Mob mob, ShikigamiType type, Player owner) {
	}

	/**
	 * The "secondary" control-item trigger (drop, today) — e.g. Divine Dogs' charge rush. Returns
	 * whether this behavior actually did something with it; the default false means the archetype
	 * simply has no secondary ability.
	 */
	default boolean onSecondaryCommand(Mob mob, ShikigamiType type, LivingEntity target) {
		return false;
	}

	/**
	 * Whether this archetype supports the track ability at all.
	 */
	default boolean supportsTracking() {
		return true;
	}

	/**
	 * Called from {@code ShikigamiManager#despawn} — clean up any per-entity state (tasks, cooldown
	 * maps) this behavior is tracking for {@code mob}.
	 */
	default void onDespawn(Mob mob, ShikigamiType type) {
	}
}
