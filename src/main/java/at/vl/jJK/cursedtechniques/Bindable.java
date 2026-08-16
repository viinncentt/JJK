package at.vl.jJK.cursedtechniques;

import org.bukkit.Material;

/**
 * Anything that can be bound to a hotbar slot via {@code BindManager} and triggered on left-click
 * by {@code AbilityListener}. Implemented by {@link AbilityType} and its Melee/Weaponeering/Special
 * Move siblings.
 */
public interface Bindable {
	String getId();

	String getDisplayName();

	Material getMaterial();

	BindableCategory getCategory();
}
