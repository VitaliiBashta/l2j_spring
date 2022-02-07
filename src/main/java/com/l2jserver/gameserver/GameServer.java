package com.l2jserver.gameserver;

import com.l2jserver.commons.UPnPService;
import com.l2jserver.commons.dao.ServerNameDAO;
import com.l2jserver.commons.database.ConnectionFactory;
import com.l2jserver.commons.util.IPv4Filter;
import com.l2jserver.commons.util.Util;
import com.l2jserver.gameserver.bbs.service.ForumsBBSManager;
import com.l2jserver.gameserver.cache.HtmCache;
import com.l2jserver.gameserver.dao.factory.impl.DAOFactory;
import com.l2jserver.gameserver.data.json.ExperienceData;
import com.l2jserver.gameserver.data.sql.impl.*;
import com.l2jserver.gameserver.data.xml.impl.*;
import com.l2jserver.gameserver.datatables.*;
import com.l2jserver.gameserver.handler.EffectHandler;
import com.l2jserver.gameserver.idfactory.IdFactory;
import com.l2jserver.gameserver.instancemanager.*;
import com.l2jserver.gameserver.model.AutoSpawnHandler;
import com.l2jserver.gameserver.model.L2World;
import com.l2jserver.gameserver.model.PartyMatchRoomList;
import com.l2jserver.gameserver.model.PartyMatchWaitingList;
import com.l2jserver.gameserver.model.entity.Hero;
import com.l2jserver.gameserver.model.entity.TvTManager;
import com.l2jserver.gameserver.model.events.EventDispatcher;
import com.l2jserver.gameserver.model.olympiad.Olympiad;
import com.l2jserver.gameserver.network.L2GameClient;
import com.l2jserver.gameserver.network.L2GamePacketHandler;
import com.l2jserver.gameserver.pathfinding.PathFinding;
import com.l2jserver.gameserver.script.faenor.FaenorScriptEngine;
import com.l2jserver.gameserver.scripting.ScriptEngineManager;
import com.l2jserver.gameserver.status.Status;
import com.l2jserver.gameserver.taskmanager.KnownListUpdateTaskManager;
import com.l2jserver.gameserver.taskmanager.TaskManager;
import com.l2jserver.gameserver.util.DeadLockDetector;
import com.l2jserver.mmocore.SelectorConfig;
import com.l2jserver.mmocore.SelectorThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Calendar;
import java.util.logging.LogManager;

import static com.l2jserver.gameserver.config.Configuration.*;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public final class GameServer {
	
	private static final Logger LOG = LoggerFactory.getLogger(GameServer.class);
	
	private static final String DATAPACK = "-dp";
	
	private static final String SCRIPT = "-s";
	
	private static final String GEODATA = "-gd";

  private final SelectorThread<L2GameClient> selectorThread;

  private final L2GamePacketHandler gamePacketHandler;

  private final DeadLockDetector deadDetectThread;

	public static GameServer gameServer;
	
	public static final Calendar dateTimeServerStarted = Calendar.getInstance();
	
	public GameServer() throws Exception {
		// TODO(Zoey76): Remove when loggers rework is completed.
		LogManager.getLogManager().reset();
		SLF4JBridgeHandler.install();
		
		final var serverLoadStart = System.currentTimeMillis();
		printSection("Database");
		ConnectionFactory.builder() //
			.withUrl(database().getURL()) //
			.withUser(database().getUser()) //
			.withPassword(database().getPassword()) //
			.withMaxIdleTime(database().getMaxIdleTime()) //
			.withMaxPoolSize(database().getMaxConnections()) //
			.build();
		
		DAOFactory.getInstance();
		
		if (!IdFactory.getInstance().isInitialized()) {
			LOG.error("Could not read object IDs from database. Please check your configuration.");
			throw new Exception("Could not initialize the Id factory!");
		}
		
		ThreadPoolManager.getInstance();
		EventDispatcher.getInstance();
		ScriptEngineManager.getInstance();
		
		printSection("World");
		GameTimeController.init();
		InstanceManager.getInstance();
		L2World.getInstance();
		MapRegionManager.getInstance();
		AnnouncementsTable.getInstance();
		GlobalVariablesManager.getInstance();
		
		printSection("Data");
		CategoryData.getInstance();
		SecondaryAuthData.getInstance();
		
		printSection("Effects");
		EffectHandler.getInstance().executeScript();
		
		printSection("Enchant Skill Groups");
		EnchantSkillGroupsData.getInstance();
		
		printSection("Skill Trees");
		SkillTreesData.getInstance();
		
		printSection("Skills");
		SkillData.getInstance();
		SummonSkillsTable.getInstance();
		
		printSection("Items");
		ItemTable.getInstance();
		EnchantItemGroupsData.getInstance();
		EnchantItemData.getInstance();
		EnchantItemOptionsData.getInstance();
		OptionData.getInstance();
		EnchantItemHPBonusData.getInstance();
		MerchantPriceConfigTable.getInstance().loadInstances();
		BuyListData.getInstance();
		MultisellData.getInstance();
		RecipeData.getInstance();
		ArmorSetsData.getInstance();
		FishData.getInstance();
		FishingMonstersData.getInstance();
		FishingRodsData.getInstance();
		HennaData.getInstance();
		
		printSection("Characters");
		ClassListData.getInstance();
		InitialEquipmentData.getInstance();
		InitialShortcutData.getInstance();
		ExperienceData.getInstance();
		PlayerXpPercentLostData.getInstance();
		KarmaData.getInstance();
		HitConditionBonusData.getInstance();
		PlayerTemplateData.getInstance();
		PlayerCreationPointData.getInstance();
		CharNameTable.getInstance();
		AdminData.getInstance();
		RaidBossPointsManager.getInstance();
		PetDataTable.getInstance();
		CharSummonTable.getInstance().init();
		
		printSection("BBS");
		if (general().enableCommunityBoard()) {
			ForumsBBSManager.getInstance().load();
		}
		
		printSection("Clans");
		ClanTable.getInstance();
		ClanHallSiegeManager.getInstance();
		ClanHallManager.getInstance();
		AuctionManager.getInstance();
		
		printSection("Geodata");
		GeoData.getInstance();
		if (geodata().getPathFinding() > 0) {
			PathFinding.getInstance();
		}
		
		printSection("NPCs");
		SkillLearnData.getInstance();
		NpcData.getInstance();
		WalkingManager.getInstance();
		StaticObjectData.getInstance();
		ZoneManager.getInstance();
		DoorData.getInstance();
		CastleManager.getInstance().loadInstances();
		NpcBufferTable.getInstance();
		GrandBossManager.getInstance().initZones();
		EventDroplist.getInstance();
		
		printSection("Auction Manager");
		ItemAuctionManager.getInstance();
		
		printSection("Olympiad");
		Olympiad.getInstance();
		Hero.getInstance();
		
		printSection("Seven Signs");
		SevenSigns.getInstance();
		
		// Call to load caches
		printSection("Cache");
		HtmCache.getInstance();
		CrestTable.getInstance();
		TeleportLocationTable.getInstance();
		UIData.getInstance();
		PartyMatchWaitingList.getInstance();
		PartyMatchRoomList.getInstance();
		PetitionManager.getInstance();
		AugmentationData.getInstance();
		CursedWeaponsManager.getInstance();
		TransformData.getInstance();
		BotReportTable.getInstance();
		QuestManager.getInstance();
		BoatManager.getInstance();
		AirShipManager.getInstance();
		GraciaSeedsManager.getInstance();
		
		printSection("Handlers");
		ScriptEngineManager.getInstance().executeScript("com/l2jserver/datapack/handlers/MasterHandler.java");
		
		printSection("AI");
		ScriptEngineManager.getInstance().executeScript("com/l2jserver/datapack/ai/AILoader.java");
		
		printSection("Instances");
		ScriptEngineManager.getInstance().executeScript("com/l2jserver/datapack/instances/InstanceLoader.java");
		
		printSection("Gracia");
		ScriptEngineManager.getInstance().executeScript("com/l2jserver/datapack/gracia/GraciaLoader.java");
		
		printSection("Hellbound");
		ScriptEngineManager.getInstance().executeScript("com/l2jserver/datapack/hellbound/HellboundLoader.java");
		
		printSection("Quests");
		ScriptEngineManager.getInstance().executeScript("com/l2jserver/datapack/quests/QuestLoader.java");
		ScriptEngineManager.getInstance().executeScript("com/l2jserver/datapack/quests/TerritoryWarScripts/TerritoryWarSuperClass.java");
		
		printSection("Scripts");
		ScriptEngineManager.getInstance().executeScriptList(new File(server().getDatapackRoot(), "data/scripts.cfg"));
		
		SpawnTable.getInstance().load();
		DayNightSpawnManager.getInstance().trim().notifyChangeMode();
		FourSepulchersManager.getInstance().init();
		DimensionalRiftManager.getInstance();
		RaidBossSpawnManager.getInstance();
		
		printSection("Siege");
		SiegeManager.getInstance().getSieges();
		CastleManager.getInstance().activateInstances();
		FortManager.getInstance().loadInstances();
		FortManager.getInstance().activateInstances();
		FortSiegeManager.getInstance();
		SiegeScheduleData.getInstance();
		MerchantPriceConfigTable.getInstance().updateReferences();
		TerritoryWarManager.getInstance();
		CastleManorManager.getInstance();
		MercTicketManager.getInstance();
		
		if (general().saveDroppedItem()) {
			ItemsOnGroundManager.getInstance();
		}
		
		if ((general().getAutoDestroyDroppedItemAfter() > 0) || (general().getAutoDestroyHerbTime() > 0)) {
			ItemsAutoDestroy.getInstance();
		}
		
		MonsterRace.getInstance();
		SevenSigns.getInstance().spawnSevenSignsNPC();
		SevenSignsFestival.getInstance();
		AutoSpawnHandler.getInstance();
		FaenorScriptEngine.getInstance();
		
		if (customs().allowWedding()) {
			CoupleManager.getInstance();
		}
		
		TaskManager.getInstance();
		
		AntiFeedManager.getInstance().registerEvent(AntiFeedManager.GAME_ID);
		
		if (general().allowMail()) {
			MailManager.getInstance();
		}
		
		PunishmentManager.getInstance();
		
		Runtime.getRuntime().addShutdownHook(Shutdown.getInstance());
		
		LOG.info("Free Object Ids remaining {}.", IdFactory.getInstance().size());
		
		TvTManager.getInstance();
		KnownListUpdateTaskManager.getInstance();
		
		if ((customs().offlineTradeEnable() || customs().offlineCraftEnable()) && customs().restoreOffliners()) {
			OfflineTradersTable.getInstance().restoreOfflineTraders();
		}
		
		if (general().deadLockDetector()) {
      deadDetectThread = new DeadLockDetector();
      deadDetectThread.setDaemon(true);
      deadDetectThread.start();
		} else {
      deadDetectThread = null;
		}
		// maxMemory is the upper limit the jvm can use, totalMemory the size of
		// the current allocation pool, freeMemory the unused memory in the allocation pool
		long freeMem = ((Runtime.getRuntime().maxMemory() - Runtime.getRuntime().totalMemory()) + Runtime.getRuntime().freeMemory()) / 1048576;
		long totalMem = Runtime.getRuntime().maxMemory() / 1048576;
		LOG.info("Started, free memory {} Mb of {} Mb", freeMem, totalMem);
		Toolkit.getDefaultToolkit().beep();
		LoginServerThread.getInstance().start();
		
		final SelectorConfig sc = new SelectorConfig();
		sc.MAX_READ_PER_PASS = mmo().getMaxReadPerPass();
		sc.MAX_SEND_PER_PASS = mmo().getMaxSendPerPass();
		sc.SLEEP_TIME = mmo().getSleepTime();
		sc.HELPER_BUFFER_COUNT = mmo().getHelperBufferCount();
		sc.TCP_NODELAY = mmo().isTcpNoDelay();

    gamePacketHandler = new L2GamePacketHandler();
    selectorThread =
        new SelectorThread<>(
            sc, gamePacketHandler, gamePacketHandler, gamePacketHandler, new IPv4Filter());

		InetAddress bindAddress = null;
		if (!server().getHost().equals("*")) {
			try {
				bindAddress = InetAddress.getByName(server().getHost());
			} catch (UnknownHostException ex) {
				LOG.warn("Bind address is invalid, using all available IPs!", ex);
			}
		}
		
		try {
      selectorThread.openServerSocket(bindAddress, server().getPort());
      selectorThread.start();
			LOG.info("Now listening on {}:{}", server().getHost(), server().getPort());
		} catch (IOException ex) {
			LOG.error("Failed to open server socket!", ex);
			System.exit(1);
		}
		
		if (server().enableUPnP()) {
			printSection("UPnP");
			UPnPService.getInstance().load(server().getPort(), "L2J Game Server");
		}
		
		if (telnet().isEnabled()) {
			new Status(telnet().getPort(), telnet().getPassword()).start();
		} else {
			LOG.info("Telnet server is currently disabled.");
		}
		
		LOG.info("Maximum numbers of connected players {}.", server().getMaxOnlineUsers());
		LOG.info("Server {} loaded in {} seconds.", ServerNameDAO.getServer(hexId().getServerID()), MILLISECONDS.toSeconds(System.currentTimeMillis() - serverLoadStart));
	}
	
	public static void main(String[] args) throws Exception {
		final String datapackRoot = Util.parseArg(args, DATAPACK, true);
		if (datapackRoot != null) {
			server().setProperty("DatapackRoot", datapackRoot);
		}
		
		final String scriptRoot = Util.parseArg(args, SCRIPT, true);
		if (scriptRoot != null) {
			server().setProperty("ScriptRoot", scriptRoot);
		}
		
		final String geodata = Util.parseArg(args, GEODATA, true);
		if (geodata != null) {
			geodata().setProperty("GeoDataPath", geodata);
		}
		
		gameServer = new GameServer();
	}
	
	public long getUsedMemoryMB() {
		return (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1048576;
	}
	
	public SelectorThread<L2GameClient> getSelectorThread() {
    return selectorThread;
	}
	
	public L2GamePacketHandler getL2GamePacketHandler() {
    return gamePacketHandler;
	}
	
	public DeadLockDetector getDeadLockDetectorThread() {
    return deadDetectThread;
	}
	
	public static void printSection(String s) {
		StringBuilder sBuilder = new StringBuilder("=[ " + s + " ]");
		while (sBuilder.length() < 61) {
			sBuilder.insert(0, "-");
		}
		s = sBuilder.toString();
		LOG.info(s);
	}
}
