package at.vl.jJK.cursedtechniques.technique;

public class Ability {
	private AbilityType abilityType;
	private String displayName;

	private int damage;
	private long cooldown;
	private double cursedEnergyCost;

	// If ability affects user
	private boolean self;

	public Ability(AbilityType abilityType, String displayName, int damage, long cooldown, double cursedEnergyCost, boolean self) {
		this.abilityType = abilityType;
		this.displayName = displayName;

		this.damage = damage;
		this.cooldown = cooldown;
		this.cursedEnergyCost = cursedEnergyCost;

		this.self = self;
	}

	// Getters
	public AbilityType getAbilityType() {
		return abilityType;
	}

	public String getDisplayName() {
		return displayName;
	}

	public int getDamage() {
		return damage;
	}

	public long getCooldown() {
		return cooldown;
	}

	public double getCursedEnergyCost() {
		return cursedEnergyCost;
	}

	public boolean isSelf() {
		return self;
	}
}
