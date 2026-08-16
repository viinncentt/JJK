package at.vl.jJK.cursedtechniques.shikigami.behavior;

import java.util.UUID;

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
	 * Whether this archetype supports being ridden via {@code ShikigamiManager#toggleCarry} at all.
	 */
	default boolean supportsCarry() {
		return false;
	}

	/**
	 * Called from {@code ShikigamiManager#toggleCarry} after the generic checks (owner match, archetype
	 * support, the shared carrying PDC flag) already passed — entirely responsible for the actual
	 * riding mechanics: what the owner ends up mounted on and how it's driven. {@code start} is
	 * {@code true} when a ride is beginning, {@code false} when one is ending.
	 */
	default void onCarryToggle(Mob mob, ShikigamiType type, Player owner, boolean start) {
	}

	/**
	 * Called from {@code ShikigamiManager#despawn} — clean up any per-entity state (tasks, cooldown
	 * maps) this behavior is tracking for {@code mob}.
	 */
	default void onDespawn(Mob mob, ShikigamiType type) {
	}

	/**
	 * Whether this archetype supports the shield ability via
	 * {@code ShikigamiManager#startShieldApproach}/{@code #endShield} at all.
	 */
	default boolean supportsShield() {
		return false;
	}

	/**
	 * Called from {@code ShikigamiManager#startShieldApproach}/{@code #endShield} after the generic
	 * checks (owner match, archetype support for a start) already passed — entirely responsible for
	 * the actual shield mechanics (flying to the owner, immobilizing both, absorbing damage).
	 * {@code start} is {@code true} for a request to begin (which may mean "come shield me" or, if
	 * already mid-approach/engaged, is up to the archetype to interpret), {@code false} for a request
	 * to end — expected to be a safe no-op if nothing was pending or active.
	 */
	default void onShieldToggle(Mob mob, ShikigamiType type, Player owner, boolean start) {
	}

	/**
	 * Whether this specific shikigami's shield is currently on cooldown — used to strike through its
	 * lore line the same way {@code ShikigamiManager#isTrackOnCooldown} already does for tracking.
	 */
	default boolean isShieldOnCooldown(UUID shikigamiId) {
		return false;
	}

	/**
	 * Reduces {@code incomingDamage} against this shikigami's owner by however much of an active
	 * shield's remaining absorption pool applies, returning the amount actually absorbed (0 if no
	 * shield is active). May end the shield as a side effect if this exhausts its pool.
	 */
	default double absorbShieldDamage(Mob mob, double incomingDamage) {
		return 0;
	}

	/**
	 * Whether this archetype supports the "clap" ability (a one-shot effect centered on the
	 * shikigami's own current location, e.g. Nue's Electric Wing Clap) via
	 * {@code ShikigamiManager#activateClap} at all.
	 */
	default boolean supportsClap() {
		return false;
	}

	/**
	 * Called from {@code ShikigamiManager#activateClap} after the generic checks (owner match,
	 * archetype support) already passed — entirely responsible for whatever the clap actually does
	 * (damage, visuals, cooldown).
	 */
	default void onClap(Mob mob, ShikigamiType type, Player owner) {
	}

	/**
	 * Whether this specific shikigami's clap is currently on cooldown — used to strike through its
	 * lore line the same way {@code ShikigamiManager#isTrackOnCooldown} already does for tracking.
	 */
	default boolean isClapOnCooldown(UUID shikigamiId) {
		return false;
	}

	/**
	 * Whether this specific shikigami's secondary ability (see {@link #onSecondaryCommand}, e.g.
	 * Divine Dogs' charge) is currently on cooldown — used to strike through its lore line the same
	 * way {@code ShikigamiManager#isTrackOnCooldown} already does for tracking.
	 */
	default boolean isSecondaryCommandOnCooldown(UUID shikigamiId) {
		return false;
	}
}
