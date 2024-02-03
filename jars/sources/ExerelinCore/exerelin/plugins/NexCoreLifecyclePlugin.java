package exerelin.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.GenericPluginManagerAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.listeners.CoreDiscoverEntityPlugin;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.campaign.listeners.TestIndustryOptionProvider;
import com.fs.starfarer.api.characters.SkillsChangeOfficerEffect;
import com.fs.starfarer.api.characters.SkillsChangeRemoveExcessOPEffect;
import com.fs.starfarer.api.characters.SkillsChangeRemoveSmodsEffect;
import com.fs.starfarer.api.characters.SkillsChangeRemoveVentsCapsEffect;
import com.fs.starfarer.api.impl.PlayerFleetPersonnelTracker;
import com.fs.starfarer.api.impl.campaign.CoreLifecyclePluginImpl;
import com.fs.starfarer.api.impl.campaign.HasslePlayerScript;
import com.fs.starfarer.api.impl.campaign.SmugglingScanScript;
import com.fs.starfarer.api.impl.campaign.command.WarSimScript;
import com.fs.starfarer.api.impl.campaign.econ.impl.Cryorevival;
import com.fs.starfarer.api.impl.campaign.econ.impl.PopulationAndInfrastructure;
import com.fs.starfarer.api.impl.campaign.econ.impl.ShipQuality;
import com.fs.starfarer.api.impl.campaign.enc.EncounterManager;
import com.fs.starfarer.api.impl.campaign.enc.StrandedGiveTJScript;
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent;
import com.fs.starfarer.api.impl.campaign.fleets.PersonalFleetHoracioCaden;
import com.fs.starfarer.api.impl.campaign.fleets.PersonalFleetOxanaHyder;
import com.fs.starfarer.api.impl.campaign.fleets.misc.MiscFleetRouteManager;
import com.fs.starfarer.api.impl.campaign.ghosts.SensorGhostManager;
import com.fs.starfarer.api.impl.campaign.graid.StandardGroundRaidObjectivesCreator;
import com.fs.starfarer.api.impl.campaign.intel.*;
import com.fs.starfarer.api.impl.campaign.intel.deciv.DecivTracker;
import com.fs.starfarer.api.impl.campaign.intel.events.CommerceBountyManager;
import com.fs.starfarer.api.impl.campaign.intel.events.DisposableHostileActivityFleetManager;
import com.fs.starfarer.api.impl.campaign.intel.events.ht.HTFactorTracker;
import com.fs.starfarer.api.impl.campaign.plog.PlaythroughLog;
import com.fs.starfarer.api.impl.campaign.procgen.themes.OmegaOfficerGeneratorPlugin;
import com.fs.starfarer.api.impl.campaign.procgen.themes.PKDefenderPluginImpl;
import com.fs.starfarer.api.impl.campaign.procgen.themes.RemnantOfficerGeneratorPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.SalvageGenFromSeed;
import com.fs.starfarer.api.impl.campaign.skills.FieldRepairsScript;
import com.fs.starfarer.api.impl.campaign.velfield.SlipstreamManager;
import com.fs.starfarer.api.impl.campaign.velfield.SlipstreamVisibilityManager;
import com.fs.starfarer.api.plugins.impl.CoreBuildObjectiveTypePicker;
import exerelin.campaign.ExerelinSetupData;
import exerelin.campaign.SectorManager;
import exerelin.campaign.colony.NexAbandonMarketPlugin;
import exerelin.campaign.colony.NexStabilizeMarketPlugin;
import exerelin.campaign.intel.Nex_PunitiveExpeditionManager;
import exerelin.campaign.intel.bases.Nex_LuddicPathBaseManager;
import exerelin.campaign.intel.bases.Nex_PirateBaseManager;
import exerelin.campaign.intel.bases.Nex_PlayerRelatedPirateBaseManager;
import exerelin.campaign.intel.hostileactivity.NexHostileActivityManager;

public class NexCoreLifecyclePlugin extends CoreLifecyclePluginImpl {
	
	// don't add hostility event manager
	// add own versions of punitive expedition manager and Hegemony inspection manager
	// also own versions of base managers and stabilize market plugin
	@Override
	protected void addScriptsIfNeeded() {
		ShipQuality.getInstance();
		//ConditionManager.getInstance();
		
		SectorAPI sector = Global.getSector();
		
		ListenerManagerAPI listeners = sector.getListenerManager();

		if (Global.getSettings().isDevMode()) {
			listeners.addListener(new TestIndustryOptionProvider(), true);
		}

		if (!listeners.hasListenerOfClass(StandardGroundRaidObjectivesCreator.class)) {
			listeners.addListener(new StandardGroundRaidObjectivesCreator(), true);
		}
		
		if (!listeners.hasListenerOfClass(Cryorevival.CryosleeperFactor.class)) {
			listeners.addListener(new Cryorevival.CryosleeperFactor(), true);
		}
		if (!listeners.hasListenerOfClass(PopulationAndInfrastructure.CoronalTapFactor.class)) {
			listeners.addListener(new PopulationAndInfrastructure.CoronalTapFactor(), true);
		}
		if (!listeners.hasListenerOfClass(SlipstreamVisibilityManager.class)) {
			listeners.addListener(new SlipstreamVisibilityManager(), true);
		}

		GenericPluginManagerAPI plugins = sector.getGenericPlugins();
		if (!plugins.hasPlugin(PKDefenderPluginImpl.class)) {
			plugins.addPlugin(new PKDefenderPluginImpl(), true);
		}
		if (!plugins.hasPlugin(SalvageGenFromSeed.SalvageDefenderModificationPluginImpl.class)) {
			plugins.addPlugin(new SalvageGenFromSeed.SalvageDefenderModificationPluginImpl(), true);
		}
		if (!plugins.hasPlugin(CoreDiscoverEntityPlugin.class)) {
			plugins.addPlugin(new CoreDiscoverEntityPlugin(), true);
		}
		if (!plugins.hasPlugin(CoreBuildObjectiveTypePicker.class)) {
			plugins.addPlugin(new CoreBuildObjectiveTypePicker(), true);
		}
		if (!plugins.hasPlugin(NexAbandonMarketPlugin.class)) {
			plugins.addPlugin(new NexAbandonMarketPlugin(), true);
		}
		if (!plugins.hasPlugin(NexStabilizeMarketPlugin.class)) {
			plugins.addPlugin(new NexStabilizeMarketPlugin(), true);
		}
		if (!plugins.hasPlugin(RemnantOfficerGeneratorPlugin.class)) {
			plugins.addPlugin(new RemnantOfficerGeneratorPlugin(), true);
		}
		if (!plugins.hasPlugin(OmegaOfficerGeneratorPlugin.class)) {
			plugins.addPlugin(new OmegaOfficerGeneratorPlugin(), true);
		}
//		if (!plugins.hasPlugin(PlayerFleetPersonnelTracker.class)) {
//			plugins.addPlugin(new PlayerFleetPersonnelTracker(), false);
//		}
		
		PlayerFleetPersonnelTracker.getInstance();

		if (!sector.hasScript(StrandedGiveTJScript.class)) {
			sector.addScript(new StrandedGiveTJScript());
		}
		if (!sector.hasScript(PersonalFleetHoracioCaden.class)) {
			sector.addScript(new PersonalFleetHoracioCaden());
		}
		if (!sector.hasScript(PersonalFleetOxanaHyder.class)) {
			sector.addScript(new PersonalFleetOxanaHyder());
		}
//		if (!sector.hasScript(PilgrimageFleetRouteManager.class)) {
//			sector.addScript(new PilgrimageFleetRouteManager());
//		}
		if (!sector.hasScript(MiscFleetRouteManager.class)) {
			sector.addScript(new MiscFleetRouteManager());
		}
		if (!sector.hasScript(EncounterManager.class)) {
			sector.addScript(new EncounterManager());
		}
		if (!sector.hasScript(SlipstreamManager.class)) {
			sector.addScript(new SlipstreamManager());
		}
		if (!sector.hasScript(SensorGhostManager.class)) {
			sector.addScript(new SensorGhostManager());
		}
		if (!sector.hasScript(OfficerManagerEvent.class)) {
			sector.addScript(new OfficerManagerEvent());
		}
		if (!sector.hasScript(FieldRepairsScript.class)) {
			sector.addScript(new FieldRepairsScript());
		}
		if (!sector.hasScript(WarSimScript.class)) {
			sector.addScript(new WarSimScript());
		}
		if (!sector.hasScript(PersonBountyManager.class)) {
			sector.addScript(new PersonBountyManager());
		}
		if (!sector.hasScript(SystemBountyManager.class)) {
			sector.addScript(new SystemBountyManager());
		}
		
		// MODIFIED
		if (!sector.hasScript(Nex_PirateBaseManager.class)) {
			sector.addScript(new Nex_PirateBaseManager());
		}
		if (!sector.hasScript(Nex_PlayerRelatedPirateBaseManager.class)) {
			sector.addScript(new Nex_PlayerRelatedPirateBaseManager());
		}
		if (!sector.hasScript(Nex_LuddicPathBaseManager.class)) {
			sector.addScript(new Nex_LuddicPathBaseManager());
		}
		//if (!sector.hasScript(Nex_HegemonyInspectionManager.class)) {
		//	sector.addScript(new Nex_HegemonyInspectionManager());
		//}
		if (!sector.hasScript(Nex_PunitiveExpeditionManager.class)) {
			sector.addScript(new Nex_PunitiveExpeditionManager());
		}
		
		if (!sector.hasScript(DecivTracker.class)) {
			sector.addScript(new DecivTracker());
		}

		if (!sector.hasScript(DisposableHostileActivityFleetManager.class)) {
			sector.addScript(new DisposableHostileActivityFleetManager());
		}
		// MODIFIED
		if (!sector.hasScript(NexHostileActivityManager.class)) {
			sector.addScript(new NexHostileActivityManager());
		}
		if (!sector.hasScript(HTFactorTracker.class)) {
			sector.addScript(new HTFactorTracker());
		}
		if (!sector.hasScript(CommerceBountyManager.class)) {
			sector.addScript(new CommerceBountyManager());
		}
		
		// MODIFIED
		/*
		if (!sector.hasScript(FactionHostilityManager.class)) {
			sector.addScript(new FactionHostilityManager());
			
			FactionHostilityManager.getInstance().startHostilities(Factions.HEGEMONY, Factions.TRITACHYON);
			FactionHostilityManager.getInstance().startHostilities(Factions.HEGEMONY, Factions.PERSEAN);
			FactionHostilityManager.getInstance().startHostilities(Factions.TRITACHYON, Factions.LUDDIC_CHURCH);
		}
		*/
		
		if (!sector.hasScript(GenericMissionManager.class)) {
			sector.addScript(new GenericMissionManager());
		}
		GenericMissionManager manager = GenericMissionManager.getInstance();
// 		Replaced with bar/contact com.fs.starfarer.api.impl.campaign.missions.ProcurementMission		
//		if (!manager.hasMissionCreator(ProcurementMissionCreator.class)) {
//			manager.addMissionCreator(new ProcurementMissionCreator());
//		}
		if (!manager.hasMissionCreator(AnalyzeEntityIntelCreator.class)) {
			manager.addMissionCreator(new AnalyzeEntityIntelCreator());
		}
		if (!manager.hasMissionCreator(SurveyPlanetIntelCreator.class)) {
			manager.addMissionCreator(new SurveyPlanetIntelCreator());
		}
		
		addBarEvents();
		
		if (!sector.hasScript(SmugglingScanScript.class)) {
			sector.addScript(new SmugglingScanScript());
		}
		if (!sector.hasScript(HasslePlayerScript.class)) {
			sector.addScript(new HasslePlayerScript());
		}
		
		PlaythroughLog.getInstance();
		
		sector.getListenerManager().addListener(new SkillsChangeRemoveExcessOPEffect(), true);
		sector.getListenerManager().addListener(new SkillsChangeRemoveVentsCapsEffect(), true);
		sector.getListenerManager().addListener(new SkillsChangeRemoveSmodsEffect(), true);
		sector.getListenerManager().addListener(new SkillsChangeOfficerEffect(), true);
	}
	
	@Override
	public void markStoryCriticalMarketsEtc() {
		if (!SectorManager.getManager().isCorvusMode())
			return;
		if (Global.getSettings().getBoolean("nex_noStoryCriticalMarkets") || ExerelinSetupData.getInstance().skipStory) 
		{
			Global.getSector().getMemoryWithoutUpdate().set("$interactedWithGABarEvent", true);
			return;
		}
		
		super.markStoryCriticalMarketsEtc();
	}

	@Override
	public void tagLuddicShrines() {
		if (!SectorManager.getManager().isCorvusMode())
			return;

		super.tagLuddicShrines();
	}
}
