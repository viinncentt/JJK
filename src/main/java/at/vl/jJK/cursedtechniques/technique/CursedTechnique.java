package at.vl.jJK.cursedtechniques.technique;

import org.bukkit.entity.Player;

public interface CursedTechnique {
	void activateTechnique(Player player);

	void activateAbility(Player player, AbilityType type);

	void load();

	void reload();

}
