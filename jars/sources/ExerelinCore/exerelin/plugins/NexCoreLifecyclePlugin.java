package exerelin.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.GenericPluginManagerAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.listeners.CoreDiscoverEntityPlugin;
import com.fs.starfarer.api.impl.campaign.AbandonMarketPluginImpl;
import com.fs.starfarer.api.impl.campaign.CoreLifecyclePluginImpl;
import com.fs.starfarer.api.impl.campaign.SmugglingScanScript;
import com.fs.starfarer.api.impl.campaign.StabilizeMarketPluginImpl;
import com.fs.starfarer.api.impl.campaign.command.WarSimScript;
import com.fs.starfarer.api.impl.campaign.econ.impl.ShipQuality;
import com.fs.starfarer.api.impl.campaign.intel.AnalyzeEntityIntelCreator;
import com.fs.starfarer.api.impl.campaign.intel.GenericMissionManager;
import com.fs.starfarer.api.impl.campaign.intel.PersonBountyManager;
import com.fs.starfarer.api.impl.campaign.intel.ProcurementMissionCreator;
import com.fs.starfarer.api.impl.campaign.intel.SurveyPlanetIntelCreator;
import com.fs.starfarer.api.impl.campaign.intel.SystemBountyManager;
import com.fs.starfarer.api.impl.campaign.intel.bases.LuddicPathBaseManager;
import com.fs.starfarer.api.impl.campaign.intel.bases.PirateBaseManager;
import com.fs.starfarer.api.impl.campaign.intel.bases.PlayerRelatedPirateBaseManager;
import com.fs.starfarer.api.impl.campaign.intel.deciv.DecivTracker;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.SalvageGenFromSeed;
import com.fs.starfarer.api.plugins.impl.CoreBuildObjectiveTypePicker;
import exerelin.campaign.intel.Nex_HegemonyInspectionManager;
import exerelin.campaign.intel.Nex_PunitiveExpeditionManager;

public class NexCoreLifecyclePlugin extends CoreLifecyclePluginImpl {
	
	// don't add hostility event manager; add own versions of punitive expedition manager and Hegemony inspection manager
	@Override
	protected void addScriptsIfNeeded() {
		ShipQuality.getInstance();
		
		SectorAPI sector = Global.getSector();
		GenericPluginManagerAPI plugins = sector.getGenericPlugins();
		if (!plugins.hasPlugin(SalvageGenFromSeed.SalvageDefenderModificationPluginImpl.class)) {
			plugins.addPlugin(new SalvageGenFromSeed.SalvageDefenderModificationPluginImpl(), true);
		}
		if (!plugins.hasPlugin(CoreDiscoverEntityPlugin.class)) {
			plugins.addPlugin(new CoreDiscoverEntityPlugin(), true);
		}
		if (!plugins.hasPlugin(CoreBuildObjectiveTypePicker.class)) {
			plugins.addPlugin(new CoreBuildObjectiveTypePicker(), true);
		}
		if (!plugins.hasPlugin(AbandonMarketPluginImpl.class)) {
			plugins.addPlugin(new AbandonMarketPluginImpl(), true);
		}
		if (!plugins.hasPlugin(StabilizeMarketPluginImpl.class)) {
			plugins.addPlugin(new StabilizeMarketPluginImpl(), true);
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
		
		if (!sector.hasScript(PirateBaseManager.class)) {
			sector.addScript(new PirateBaseManager());
		}
		if (!sector.hasScript(PlayerRelatedPirateBaseManager.class)) {
			sector.addScript(new PlayerRelatedPirateBaseManager());
		}
		
		if (!sector.hasScript(LuddicPathBaseManager.class)) {
			sector.addScript(new LuddicPathBaseManager());
		}
		if (!sector.hasScript(Nex_HegemonyInspectionManager.class)) {
			sector.addScript(new Nex_HegemonyInspectionManager());
		}
		if (!sector.hasScript(Nex_PunitiveExpeditionManager.class)) {
			sector.addScript(new Nex_PunitiveExpeditionManager());
		}
		if (!sector.hasScript(DecivTracker.class)) {
			sector.addScript(new DecivTracker());
		}
		
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
		if (!manager.hasMissionCreator(ProcurementMissionCreator.class)) {
			manager.addMissionCreator(new ProcurementMissionCreator());
		}
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
		
	}
}
