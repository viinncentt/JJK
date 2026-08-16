package at.vl.jJK.cursedtechniques.technique;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import at.vl.jJK.enums.Families;

public enum CursedTechniqueType {
	// Special Cursed Tecniques
	// Zenin
	TEN_SHADOWS,
	PROJECTION_SORCERY,

	// Nobara's cursed technique
	STRAW_DOLL;

	private Families family;
	private String displayName;

	public void load(Families family, String displayName) {
		this.family = family;
		this.displayName = displayName;
	}

	public Families getFamily() {
		return family;
	}

	public String getDisplayName() {
		return displayName;
	}

	public Component getDisplayNameComponent() {
		return MiniMessage.miniMessage().deserialize(displayName);
	}
}
