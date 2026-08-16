package at.vl.jJK.cursedtechniques.melee;

import org.bukkit.Material;

import at.vl.jJK.cursedtechniques.Bindable;
import at.vl.jJK.cursedtechniques.BindableCategory;

public enum MeleeType implements Bindable {
	BARRAGE,
	UPPERCUT;

	private String displayName;
	private Material material;
	private long cooldownMillis;
	private double cursedEnergyCost;

	public void load(String displayName, Material material, long cooldownMillis, double cursedEnergyCost) {
		this.displayName = displayName;
		this.material = material;
		this.cooldownMillis = cooldownMillis;
		this.cursedEnergyCost = cursedEnergyCost;
	}

	public long getCooldownMillis() {
		return cooldownMillis;
	}

	public double getCursedEnergyCost() {
		return cursedEnergyCost;
	}

	@Override
	public String getId() {
		return name();
	}

	@Override
	public String getDisplayName() {
		return displayName;
	}

	@Override
	public Material getMaterial() {
		return material;
	}

	@Override
	public BindableCategory getCategory() {
		return BindableCategory.MELEE;
	}
}
