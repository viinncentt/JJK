package at.vl.jJK.cursedtechniques.shikigami;

import java.util.List;

import org.bukkit.entity.EntityType;

import net.kyori.adventure.text.format.TextColor;

/**
 * Only the scalars every shikigami shares (stats, tame defaults, lore, track colors/cost). Every
 * AI-archetype-specific config (charge, flight, ...) lives with its
 * {@code at.vl.jJK.cursedtechniques.shikigami.behavior.ShikigamiBehavior} implementation instead —
 * see {@code ShikigamiManager#loadBehavior} — so this enum doesn't keep accumulating optional,
 * mostly-unused fields as more shikigami with different AI get added.
 */
public enum ShikigamiType {
	WHITE_DIVINE_DOG,
	BLACK_DIVINE_DOG,
	NUE;

	private String displayName;
	private EntityType entityType;
	private double cursedEnergyDrainAmount;
	private long trackCooldownMillis;
	private double trackCursedEnergyCost;
	private TextColor trackLocatorColor;
	private TextColor trackEntityColor;
	private TextColor highlightColor;
	private double scale;
	private double speed;
	private double damage;
	private double health;
	private double regenAmount;
	private double despawnedRegenPerSecond;
	private double followRange;
	private boolean defaultTamed;
	private boolean defaultSummonable;
	private ControlItemLore controlItemLore;

	/**
	 * The control item's lore, as a flat, ordered list of lines (configured as {@code Lore.Line1},
	 * {@code Lore.Line2}, ...) — configurable per type since flavor text (and the actual wording of
	 * controls) may differ between shikigami. The track-ability line specifically gets a
	 * strikethrough prefix applied at render time whenever the track ability is on cooldown (see
	 * {@code ShikigamiManager#buildControlItemLore}); the rest are shown as-is.
	 */
	public record ControlItemLore(List<String> lines) {
	}

	public void load(String displayName, EntityType entityType, double cursedEnergyDrainAmount,
			long trackCooldownMillis, double trackCursedEnergyCost, TextColor trackLocatorColor,
			TextColor trackEntityColor, TextColor highlightColor, double scale, double speed, double damage,
			double health, double regenAmount, double despawnedRegenPerSecond, double followRange,
			boolean defaultTamed, boolean defaultSummonable, ControlItemLore controlItemLore) {
		this.displayName = displayName;
		this.entityType = entityType;
		this.cursedEnergyDrainAmount = cursedEnergyDrainAmount;
		this.trackCooldownMillis = trackCooldownMillis;
		this.trackCursedEnergyCost = trackCursedEnergyCost;
		this.trackLocatorColor = trackLocatorColor;
		this.trackEntityColor = trackEntityColor;
		this.highlightColor = highlightColor;
		this.scale = scale;
		this.speed = speed;
		this.damage = damage;
		this.health = health;
		this.regenAmount = regenAmount;
		this.despawnedRegenPerSecond = despawnedRegenPerSecond;
		this.followRange = followRange;
		this.defaultTamed = defaultTamed;
		this.defaultSummonable = defaultSummonable;
		this.controlItemLore = controlItemLore;
	}

	/**
	 * The control item's display name (MiniMessage), configured as {@code DisplayName} — what shows
	 * up in the item's own tooltip and any admin-menu listing of this type.
	 */
	public String getDisplayName() {
		return displayName;
	}

	public EntityType getEntityType() {
		return entityType;
	}

	public double getCursedEnergyDrainAmount() {
		return cursedEnergyDrainAmount;
	}

	public double getScale() {
		return scale;
	}

	/**
	 * Base value for the shikigami's {@code Attribute.MOVEMENT_SPEED}.
	 */
	public double getSpeed() {
		return speed;
	}

	/**
	 * Base value for the shikigami's {@code Attribute.ATTACK_DAMAGE}.
	 */
	public double getDamage() {
		return damage;
	}

	/**
	 * Base value for the shikigami's {@code Attribute.MAX_HEALTH}. Actual spawn health may be
	 * lower than this if it's resuming from a previous despawn with unregenerated damage.
	 */
	public double getHealth() {
		return health;
	}

	/**
	 * Health regenerated per active-regen tick while the shikigami is summoned and alive.
	 */
	public double getRegenAmount() {
		return regenAmount;
	}

	/**
	 * Health regenerated per real-world second while the shikigami is despawned, applied lazily
	 * (based on elapsed time) the next time it's summoned.
	 */
	public double getDespawnedRegenPerSecond() {
		return despawnedRegenPerSecond;
	}

	/**
	 * Base value for the shikigami's {@code Attribute.FOLLOW_RANGE} — how far it can sense its
	 * owner and other entities.
	 */
	public double getFollowRange() {
		return followRange;
	}

	/**
	 * Whether a player who has never tamed this type before starts out having it already tamed.
	 * Actual tame state is per-player (see {@code ShikigamiManager#hasTamed}) — this is only the
	 * fallback used the first time a given player ever summons this type.
	 */
	public boolean isDefaultTamed() {
		return defaultTamed;
	}

	/**
	 * Whether a player who has never had this type's summonable state set before starts out able
	 * to summon it at all. Actual summonable state is per-player (see
	 * {@code ShikigamiManager#hasSummonable}) — this is only the fallback used the first time a
	 * given player's state is looked up.
	 */
	public boolean isDefaultSummonable() {
		return defaultSummonable;
	}

	public ControlItemLore getControlItemLore() {
		return controlItemLore;
	}

	public long getTrackCooldownMillis() {
		return trackCooldownMillis;
	}

	public double getTrackCursedEnergyCost() {
		return trackCursedEnergyCost;
	}

	/**
	 * Color shown on the player locator bar waypoint. Full hex/RGB is supported.
	 */
	public TextColor getTrackLocatorColor() {
		return trackLocatorColor;
	}

	/**
	 * Color of the glow outline on the tracked entity itself. Vanilla team colors only support
	 * the 16 legacy named colors — a configured hex value gets approximated to the nearest one
	 * at the point it's applied.
	 */
	public TextColor getTrackEntityColor() {
		return trackEntityColor;
	}

	/**
	 * Standing glow color shown on this shikigami itself, visible only to its owner, for as long
	 * as it's summoned. Same 16-legacy-color caveat as {@link #getTrackEntityColor()}.
	 */
	public TextColor getHighlightColor() {
		return highlightColor;
	}
}
