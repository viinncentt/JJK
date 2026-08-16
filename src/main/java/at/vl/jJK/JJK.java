package at.vl.jJK;

import java.util.List;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import com.github.retrooper.packetevents.PacketEvents;

import net.megavex.scoreboardlibrary.api.ScoreboardLibrary;
import net.megavex.scoreboardlibrary.api.exception.NoPacketAdapterAvailableException;
import net.megavex.scoreboardlibrary.api.noop.NoopScoreboardLibrary;

import at.vl.jJK.commands.AdminMenuCommand;
import at.vl.jJK.commands.JJKBaseCommand;
import at.vl.jJK.commands.JJKCommand;
import at.vl.jJK.listeners.AbilityListener;
import at.vl.jJK.cursedtechniques.melee.BarrageListener;
import at.vl.jJK.listeners.FirstJoinListener;
import at.vl.jJK.listeners.PlayerJoinListener;
import at.vl.jJK.listeners.StunListener;
import at.vl.jJK.cursedtechniques.technique.AbilityManager;
import at.vl.jJK.managers.BindManager;
import at.vl.jJK.managers.CursedEnergyManager;
import at.vl.jJK.cursedtechniques.technique.CursedTechniqueManager;
import at.vl.jJK.managers.LocatorBarManager;
import at.vl.jJK.cursedtechniques.melee.MeleeManager;
import at.vl.jJK.managers.ScoreboardManager;
import at.vl.jJK.cursedtechniques.specialmove.SpecialMoveManager;
import at.vl.jJK.managers.StunManager;
import at.vl.jJK.cursedtechniques.weaponeering.WeaponeeringManager;
import at.vl.jJK.cursedtechniques.shikigami.ShikigamiListener;
import at.vl.jJK.cursedtechniques.shikigami.ShikigamiManager;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;

public final class JJK extends JavaPlugin {

	private final String logo = "<gradient:#4BAEB7:#700F52>[JJK]</gradient>";
	private ScoreboardLibrary scoreboardLibrary;

	// Managers
	private CursedEnergyManager cursedEnergyManager;
	private CursedTechniqueManager cursedTechniqueManager;
	private ScoreboardManager scoreboardManager;
	private AbilityManager abilityManager;
	private BindManager bindManager;
	private LocatorBarManager locatorBarManager;
	private ShikigamiManager shikigamiManager;
	private ShikigamiListener shikigamiListener;
	private MeleeManager meleeManager;
	private WeaponeeringManager weaponeeringManager;
	private SpecialMoveManager specialMoveManager;
	private StunManager stunManager;

	@Override
	public void onEnable() {
		getLogger().info("JJK has started");

		// Save configs
		saveDefaultConfig();
		reloadConfig();

		// Scoreboard Library
		try {
			scoreboardLibrary = ScoreboardLibrary.loadScoreboardLibrary(this);
		} catch (NoPacketAdapterAvailableException e) {
			scoreboardLibrary = new NoopScoreboardLibrary();
			getLogger().warning("Server version unsupported, scoreboard functionality will not be visible!");
		}

		// Packetevents
		PacketEvents.setAPI(
				SpigotPacketEventsBuilder.build(this)
		);

		PacketEvents.getAPI().load();
		PacketEvents.getAPI().init();

		// Commands
		List<JJKCommand> commandList = List.of(
				new AdminMenuCommand(this),
				new JJKBaseCommand(this)
		);

		LifecycleEventManager<Plugin> manager = this.getLifecycleManager();
		manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
			Commands commands = event.registrar();
			for (JJKCommand cmd : commandList) {
				commands.register(cmd.build(), cmd.description(), cmd.aliases());
			}
		});

		// Managers
		cursedEnergyManager = new CursedEnergyManager(this);
		cursedEnergyManager.start();

		// Constructed before CursedTechniqueManager (which builds TenShadows internally) since
		// TenShadows registers a shikigami despawn listener at construction time.
		shikigamiManager = new ShikigamiManager(this);
		shikigamiManager.start();

		cursedTechniqueManager = new CursedTechniqueManager(this);
		cursedTechniqueManager.start();

		scoreboardManager = new ScoreboardManager(this);

		abilityManager = new AbilityManager(this);
		bindManager = new BindManager(this);

		meleeManager = new MeleeManager(this);
		meleeManager.start();
		weaponeeringManager = new WeaponeeringManager(this);
		weaponeeringManager.start();
		specialMoveManager = new SpecialMoveManager(this);
		specialMoveManager.start();
		stunManager = new StunManager(this);

		locatorBarManager = new LocatorBarManager(this);
		locatorBarManager.start();

		// Listeners
		getServer().getPluginManager().registerEvents(new FirstJoinListener(this), this);
		getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
		getServer().getPluginManager().registerEvents(new AbilityListener(this), this);
		getServer().getPluginManager().registerEvents(new BarrageListener(this), this);
		getServer().getPluginManager().registerEvents(new StunListener(this), this);
		shikigamiListener = new ShikigamiListener(this);
	}

	@Override
	public void onDisable() {
		getLogger().info("JJK has shutdown");

		cursedEnergyManager.stop();
		locatorBarManager.stop();
		shikigamiManager.despawnAll();
		shikigamiManager.stop();

		scoreboardLibrary.close();
		PacketEvents.getAPI().terminate();
	}

	public CursedEnergyManager getCursedEnergyManager() {
		return cursedEnergyManager;
	}

	public CursedTechniqueManager getCursedTechniqueManager() {
		return cursedTechniqueManager;
	}

	public ScoreboardManager getScoreboardManager() {
		return scoreboardManager;
	}

	public AbilityManager getAbilityManager() {
		return abilityManager;
	}

	public ScoreboardLibrary getScoreboardLibrary() {
		return scoreboardLibrary;
	}

	public BindManager getBindManager() {
		return bindManager;
	}

	public LocatorBarManager getLocatorBarManager() {
		return locatorBarManager;
	}

	public ShikigamiManager getShikigamiManager() {
		return shikigamiManager;
	}

	public ShikigamiListener getShikigamiListener() {
		return shikigamiListener;
	}

	public MeleeManager getMeleeManager() {
		return meleeManager;
	}

	public WeaponeeringManager getWeaponeeringManager() {
		return weaponeeringManager;
	}

	public SpecialMoveManager getSpecialMoveManager() {
		return specialMoveManager;
	}

	public StunManager getStunManager() {
		return stunManager;
	}

	public String getLogo() {
		return logo;
	}

}
