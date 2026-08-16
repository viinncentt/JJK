package at.vl.jJK.managers;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import at.vl.jJK.JJK;
import at.vl.jJK.cursedtechniques.technique.AbilityType;
import at.vl.jJK.cursedtechniques.Bindable;
import at.vl.jJK.cursedtechniques.BindableCategory;
import at.vl.jJK.cursedtechniques.melee.MeleeType;
import at.vl.jJK.cursedtechniques.specialmove.SpecialMoveType;
import at.vl.jJK.cursedtechniques.weaponeering.WeaponeeringType;

public class BindManager implements JJKManager {
	private final JJK jjk;

	public BindManager(JJK jjk) {
		this.jjk = jjk;
	}

	private NamespacedKey slotKey(int slot) {
		return new NamespacedKey(jjk, "bind_slot_" + slot);
	}

	public void bind(Player player, int slot, Bindable bindable) {
		String value = bindable.getCategory().name() + ":" + bindable.getId();
		player.getPersistentDataContainer().set(slotKey(slot), PersistentDataType.STRING, value);
	}

	public void bindNothing(Player player, int slot) {
		player.getPersistentDataContainer().remove(slotKey(slot));
	}

	public Bindable getBoundAbility(Player player, int slot) {
		String value = player.getPersistentDataContainer().get(slotKey(slot), PersistentDataType.STRING);
		if (value == null) {
			return null;
		}

		String[] parts = value.split(":", 2);
		if (parts.length != 2) {
			return null;
		}

		BindableCategory category;
		try {
			category = BindableCategory.valueOf(parts[0]);
		} catch (IllegalArgumentException e) {
			return null;
		}

		try {
			return switch (category) {
				case CURSED_TECHNIQUE -> AbilityType.valueOf(parts[1]);
				case MELEE -> MeleeType.valueOf(parts[1]);
				case WEAPONEERING -> WeaponeeringType.valueOf(parts[1]);
				case SPECIAL_MOVE -> SpecialMoveType.valueOf(parts[1]);
			};
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	@Override
	public void start() {
	}
}
