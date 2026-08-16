package at.vl.jJK.cursedtechniques.technique;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import at.vl.jJK.managers.JJKManager;
import at.vl.jJK.JJK;
import at.vl.jJK.cursedtechniques.technique.CursedTechnique;
import at.vl.jJK.cursedtechniques.technique.CursedTechniqueType;
import at.vl.jJK.cursedtechniques.technique.TenShadows;
import at.vl.jJK.enums.Families;

public class CursedTechniqueManager implements JJKManager {
	private final JJK jjk;

	private File file;
	private FileConfiguration config;

	private final NamespacedKey cursedTechniqueKey;

	private final Map<CursedTechniqueType, CursedTechnique> techniques = new HashMap<>();

	public CursedTechniqueManager(JJK jjk) {
		this.jjk = jjk;

		this.cursedTechniqueKey = new NamespacedKey(jjk, "cursed_technique");

		techniques.put(CursedTechniqueType.TEN_SHADOWS, new TenShadows(jjk));
	}

	public Map<CursedTechniqueType, CursedTechnique> getTechniques() {
		return techniques;
	}

	public CursedTechnique getTechnique(CursedTechniqueType type) {
		return techniques.get(type);
	}

	public void activateTechnique(Player player) {
		CursedTechniqueType type = getPlayerCursedTechnique(player);

		CursedTechnique technique = getTechnique(type);

		if (technique != null) {
			technique.activateTechnique(player);
		}
	}

	public void addCursedTechnique(Player player, CursedTechniqueType technique) {
		player.getPersistentDataContainer().set(
				cursedTechniqueKey, PersistentDataType.STRING, technique.name());
	}

	public CursedTechniqueType getPlayerCursedTechnique(Player player) {
		String value = player.getPersistentDataContainer().get(
				cursedTechniqueKey, PersistentDataType.STRING);

		if (value == null) {
			return null;
		}

		return CursedTechniqueType.valueOf(value);
	}

	public void clearCursedTechnique(Player player) {
		player.getPersistentDataContainer().remove(cursedTechniqueKey);
	}

	@Override
	public void start() {
		file = new File(jjk.getDataFolder(), "abilities/cursedtechniques.yml");

		if (!file.exists()) {
			jjk.saveResource("abilities/cursedtechniques.yml", false);
		}

		config = YamlConfiguration.loadConfiguration(file);

		loadTechniques();
	}

	private void loadTechniques() {
		ConfigurationSection section = config.getConfigurationSection("techniques");

		if (section == null) {
			jjk.getLogger().info("No 'techniques' section found in cursedtechniques.yml!");
			return;
		}

		for (CursedTechniqueType technique : CursedTechniqueType.values()) {

			String path = "techniques." + technique.name();

			String familyName = config.getString(path + ".family", "NONE");
			String displayName = config.getString(path + ".display-name", technique.name());

			Families family;

			try {
				family = Families.valueOf(familyName.toUpperCase());
			} catch (IllegalArgumentException e) {
				jjk.getLogger().info("Error loading technique: " + familyName);
				family = Families.NONE;
			}

			technique.load(family, displayName);
		}
	}

	public void reload() {
		start();
		techniques.forEach((key, value) -> {
			value.reload();
		});
	}

	public FileConfiguration getConfig() {
		return config;
	}
}
