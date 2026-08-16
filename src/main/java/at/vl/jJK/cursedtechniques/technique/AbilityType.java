package at.vl.jJK.cursedtechniques.technique;

import org.bukkit.Material;

import at.vl.jJK.cursedtechniques.Bindable;
import at.vl.jJK.cursedtechniques.BindableCategory;

public enum AbilityType implements Bindable {
	// Ten Shadows
	DIVINE_DOGS,
	DIVINE_DOG_TOTALITY,
	NUE,
	TOAD,
	TOAD_MOBILITY,
	GREAT_SERPENT,
	MAX_ELEPHANT,
	RABBIT_ESCAPE,
	ROUND_DEER,
	PIERCING_OX,
	MERGED_BEAST_AGITO,
	MAHORAGA,
	// Domain
	CHIMERA_SHADOW_GARDEN;

	private CursedTechniqueType cursedTechnique;
	private String displayName;
	private Material material;

	public void load(CursedTechniqueType cursedTechnique, String displayName, Material material) {
		this.cursedTechnique = cursedTechnique;
		this.displayName = displayName;
		this.material = material;
	}

	public CursedTechniqueType getCursedTechniques() {
		return cursedTechnique;
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
		return BindableCategory.CURSED_TECHNIQUE;
	}
}
