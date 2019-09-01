package exerelin.plugins;

import exerelin.campaign.ColonyManager;
import exerelin.campaign.CovertOpsManager;
import exerelin.campaign.ExerelinReputationAdjustmentResult;
import exerelin.campaign.abilities.ai.Nex_SustainedBurnAbilityAI;
import exerelin.campaign.alliances.Alliance;
import exerelin.campaign.alliances.AllianceVoter;
import exerelin.campaign.intel.agents.InstigateRebellion;
import exerelin.campaign.events.ExigencyRespawnFleetEvent;
import exerelin.campaign.events.RebellionEvent;
import exerelin.campaign.events.RebellionEventCreator;
import exerelin.campaign.diplomacy.DiplomacyBrain;
import exerelin.campaign.econ.RaidCondition;
import exerelin.campaign.econ.TributeCondition;
import exerelin.campaign.intel.VengeanceFleetIntel;
import exerelin.campaign.events.covertops.SecurityAlertEvent;
import exerelin.campaign.events.SlavesSoldEvent;
import exerelin.campaign.events.WarmongerEvent;
import exerelin.campaign.fleets.MiningFleetAI;
import exerelin.campaign.fleets.MiningFleetManagerV2;
import exerelin.campaign.fleets.MiningFleetManagerV2.MiningFleetData;
import exerelin.campaign.fleets.SuppressionFleetAI;
import exerelin.campaign.intel.AllianceIntel;
import exerelin.campaign.intel.AllianceVoteIntel;
import exerelin.campaign.intel.ConquestMissionIntel;
import exerelin.campaign.intel.diplomacy.DiplomacyIntel;
import exerelin.campaign.intel.FactionBountyIntel;
import exerelin.campaign.intel.FactionSpawnedOrEliminatedIntel;
import exerelin.campaign.intel.invasion.InvasionIntel;
import exerelin.campaign.intel.MarketTransferIntel;
import exerelin.campaign.intel.NexPirateActivity;
import exerelin.campaign.intel.Nex_PunitiveExpeditionIntel;
import exerelin.campaign.intel.PlayerOutpostIntel;
import exerelin.campaign.intel.RespawnBaseIntel;
import exerelin.campaign.intel.diplomacy.TributeIntel;
import exerelin.campaign.intel.agents.AgentIntel;
import exerelin.campaign.intel.agents.DestabilizeMarket;
import exerelin.campaign.intel.agents.DestroyCommodityStocks;
import exerelin.campaign.intel.agents.InfiltrateCell;
import exerelin.campaign.intel.agents.LowerRelations;
import exerelin.campaign.intel.agents.RaiseRelations;
import exerelin.campaign.intel.agents.SabotageIndustry;
import exerelin.campaign.intel.agents.Travel;
import exerelin.campaign.intel.colony.ColonyExpeditionIntel;
import exerelin.campaign.intel.diplomacy.DiplomacyProfileIntel;
import exerelin.campaign.intel.fleets.NexAssembleStage;
import exerelin.campaign.intel.fleets.NexOrganizeStage;
import exerelin.campaign.intel.fleets.NexReturnStage;
import exerelin.campaign.intel.fleets.NexTravelStage;
import exerelin.campaign.intel.invasion.InvActionStage;
import exerelin.campaign.intel.invasion.InvAssembleStage;
import exerelin.campaign.intel.invasion.InvOrganizeStage;
import exerelin.campaign.intel.invasion.RespawnInvasionIntel;
import exerelin.campaign.intel.raid.BaseStrikeIntel;
import exerelin.campaign.intel.raid.NexRaidActionStage;
import exerelin.campaign.intel.raid.NexRaidAssembleStage;
import exerelin.campaign.intel.raid.NexRaidIntel;
import exerelin.campaign.intel.raid.RemnantRaidIntel;
import exerelin.campaign.submarkets.Nex_BlackMarketPlugin;
import exerelin.campaign.submarkets.Nex_LocalResourcesSubmarketPlugin;
import exerelin.campaign.submarkets.Nex_MilitarySubmarketPlugin;
import exerelin.campaign.submarkets.Nex_OpenMarketPlugin;

public class XStreamConfig {
	
	public static void configureXStream(com.thoughtworks.xstream.XStream x)
	{
		/*
		x.alias("AllianceMngr", AllianceManager.class);
		x.alias("CovertOpsMngr", CovertOpsManager.class);
		x.alias("DiplomacyMngr", DiplomacyManager.class);
		x.alias("ExerelinCoreScript", ExerelinCoreScript.class);
		x.alias("PlayerFactionStore", PlayerFactionStore.class);
		x.alias("RevengeanceMngr", RevengeanceManager.class);
		x.alias("SectorMngr", SectorManager.class);
		
		x.alias("InvasionFltMngr", InvasionFleetManager.class);
		x.alias("ResponseFltMngr", ResponseFleetManager.class);
		x.alias("MiningFltMngr", MiningFleetManager.class);
		x.alias("ExePatrolFltMngr", ExerelinPatrolFleetManager.class);
		*/
		x.alias("ColonyMngr", ColonyManager.class);
		x.alias("ColonyMngrQueuedInd", ColonyManager.QueuedIndustry.class);
		
		x.alias("MiningFltAI", MiningFleetAI.class);
		x.alias("MiningFltData", MiningFleetManagerV2.MiningFleetData.class);
		x.alias("SuppressFltAI", SuppressionFleetAI.class);
		x.alias("VengFltIntl", VengeanceFleetIntel.class);
		
		x.alias("DiploIntl", DiplomacyIntel.class);
		x.alias("DiploPrflIntl", DiplomacyProfileIntel.class);
		x.alias("MktTrnsfrIntl", MarketTransferIntel.class);
		x.alias("FactionChngIntl", FactionSpawnedOrEliminatedIntel.class);
		
		// submarkets
		x.alias("NexOpnMkt", Nex_OpenMarketPlugin.class);
		x.alias("NexMilSubmkt", Nex_MilitarySubmarketPlugin.class);
		x.alias("NexBlackMkt", Nex_BlackMarketPlugin.class);
		x.alias("NexLclRsrcsSubmkt", Nex_LocalResourcesSubmarketPlugin.class);
		
		// events
		// most of these will be deleted eventually
		x.alias("ExiRespawnFltEvnt", ExigencyRespawnFleetEvent.class);
		x.alias("InstgtRbl", InstigateRebellion.class);
		x.alias("RebelEvnt", RebellionEvent.class);
		x.alias("RebelEvntCreator", RebellionEventCreator.class);
		x.alias("SecurityAlertEvnt", SecurityAlertEvent.class);
		x.alias("SlavesSoldEvnt", SlavesSoldEvent.class);
		x.alias("WarmongerEvnt", WarmongerEvent.class);
		
		// intel
		x.alias("NexRaidCond", RaidCondition.class);
		x.alias("NexPunExIntl", Nex_PunitiveExpeditionIntel.class);
		x.alias("NexFctnBntyIntl", FactionBountyIntel.class);
		x.alias("NexTrbtIntl", TributeIntel.class);
		x.alias("NexTrbtCond", TributeCondition.class);
		x.alias("NexConqMssn", ConquestMissionIntel.class);
		x.alias("NexPirActv", NexPirateActivity.class);
		x.alias("NexPlyrOtpst", PlayerOutpostIntel.class);
		
		// raids and such
		x.alias("NexRaidIntl", NexRaidIntel.class);
		x.alias("NexOrgStg", NexOrganizeStage.class);
		x.alias("NexAssmblStg", NexAssembleStage.class);
		x.alias("NexTrvlStg", NexTravelStage.class);
		x.alias("NexRetStg", NexReturnStage.class);
		x.alias("NexRaidAssmblStg", NexRaidAssembleStage.class);
		x.alias("NexRaidActStg", NexRaidActionStage.class);
		x.alias("NexBaseStrkIntl", BaseStrikeIntel.class);
		
		// invasions
		x.alias("NexInvIntl", InvasionIntel.class);
		x.alias("NexInvOrgStg", InvOrganizeStage.class);
		x.alias("NexInvAssmblStg", InvAssembleStage.class);
		x.alias("NexInvActStg", InvActionStage.class);
		x.alias("NexRspwnIntl", RespawnInvasionIntel.class);
		x.alias("NexRspwnBaseIntl", RespawnBaseIntel.class);
		
		// Remnant raids
		x.alias("NexRemRaidIntl", RemnantRaidIntel.class);
		/*
		x.alias("NexRemRaidOrgStg", RemnantRaidOrganizeStage.class);
		x.alias("NexRemRaidAssmblStg", RemnantRaidAssembleStage.class);
		x.alias("NexRemRaidTrvlStg", RemnantRaidTravelStage.class);
		x.alias("NexRemRaidActStg", RemnantRaidActionStage.class);
		x.alias("NexRemRaidRetStg", RemnantRaidReturnStage.class);
		*/
		
		// colony expeditions
		x.alias("ColonyExpdIntl", ColonyExpeditionIntel.class);	
		
		// agents
		x.alias("NexAgntIntl", AgentIntel.class);
		x.alias("NexAgntActTrvl", Travel.class);
		x.alias("NexAgntActDstb", DestabilizeMarket.class);
		x.alias("NexAgntActDstrCmmdty", DestroyCommodityStocks.class);
		x.alias("NexAgntActInfltrtCell", InfiltrateCell.class);
		x.alias("NexAgntActLowerRel", LowerRelations.class);
		x.alias("NexAgntActRaiseRel", RaiseRelations.class);
		x.alias("NexAgntActSbtgInd", SabotageIndustry.class);
		
		// alliances
		x.alias("NexAlliance", Alliance.class);
		x.alias("NexAllyIntl", AllianceIntel.class);
		x.alias("NexAllyVoteIntl", AllianceVoteIntel.class);
		x.alias("AllyVoteRslt", AllianceVoter.VoteResult.class);
		
		// misc
		x.alias("NexRepAdjustmentResult", ExerelinReputationAdjustmentResult.class);
		x.alias("DiploBrain", DiplomacyBrain.class);
		x.alias("DiploDspsEntry", DiplomacyBrain.DispositionEntry.class);
		x.alias("NexSBAI", Nex_SustainedBurnAbilityAI.class);
		
		// enums
		x.alias("CovertActionResult", CovertOpsManager.CovertActionResult.class);
		
		configureXStreamAttributes(x);
	}
	
	// todo: new aliases
	public static void configureXStreamAttributes(com.thoughtworks.xstream.XStream x)
	{
		// these don't seem to actually work, need to apply on a per-class basis
		//x.aliasAttribute("done", "done");
		//x.aliasAttribute("age", "age");
		//x.aliasAttribute("ended", "ended");
		
		// Alliance
		x.aliasAttribute(Alliance.class, "name", "n");
		x.aliasAttribute(Alliance.class, "uuId", "id");
		x.aliasAttribute(Alliance.class, "alignment", "algn");
		
		// RebellionEvent
		x.aliasAttribute(RebellionEvent.class, "stage", "stg");
		x.aliasAttribute(RebellionEvent.class, "suppressionFleetCountdown", "fltCntdwn");
		x.aliasAttribute(RebellionEvent.class, "govtFactionId", "govt");
		x.aliasAttribute(RebellionEvent.class, "rebelFactionId", "rebs");
		x.aliasAttribute(RebellionEvent.class, "govtStrength", "gStr");
		x.aliasAttribute(RebellionEvent.class, "rebelStrength", "rStr");
		x.aliasAttribute(RebellionEvent.class, "govtTradePoints", "gTrd");
		x.aliasAttribute(RebellionEvent.class, "rebelTradePoints", "rTrd");
		x.aliasAttribute(RebellionEvent.class, "suppressionFleet", "flt");
		x.aliasAttribute(RebellionEvent.class, "suppressionFleetSource", "fltSrc");
		x.aliasAttribute(RebellionEvent.class, "suppressionFleetWarning", "fltWarn");
		x.aliasAttribute(RebellionEvent.class, "intensity", "intns");
		x.aliasAttribute(RebellionEvent.class, "delay", "dly");
		x.aliasAttribute(RebellionEvent.class, "stabilityPenalty", "stbLoss");
		x.aliasAttribute(RebellionEvent.class, "result", "rslt");
		x.aliasAttribute(RebellionEvent.class, "conditionToken", "cond");
		
		// VengeanceFleetIntel
		x.aliasAttribute(VengeanceFleetIntel.class, "daysLeft", "days");
		x.aliasAttribute(VengeanceFleetIntel.class, "escalationLevel", "escal");
		x.aliasAttribute(VengeanceFleetIntel.class, "fleet", "flt");
		x.aliasAttribute(VengeanceFleetIntel.class, "foundPlayerYet", "found");
		x.aliasAttribute(VengeanceFleetIntel.class, "timeSpentLooking", "look");
		x.aliasAttribute(VengeanceFleetIntel.class, "trackingMode", "trck");
		
		// SecurityAlertEvent
		x.aliasAttribute(SecurityAlertEvent.class, "elapsedDays", "days");
		x.aliasAttribute(SecurityAlertEvent.class, "alertLevel", "alrt");
		
		// Fleets
		
		// MiningFleetAI
		x.aliasAttribute(MiningFleetAI.class, "daysTotal", "days");
		x.aliasAttribute(MiningFleetAI.class, "miningDailyProgress", "prog");
		x.aliasAttribute(MiningFleetAI.class, "fleet", "flt");
		x.aliasAttribute(MiningFleetAI.class, "orderedReturn", "ret");
		x.aliasAttribute(MiningFleetAI.class, "unloaded", "unld");
		
		// MiningFleetData
		x.aliasAttribute(MiningFleetData.class, "fleet", "flt");
		x.aliasAttribute(MiningFleetData.class, "source", "src");
		x.aliasAttribute(MiningFleetData.class, "target", "tgt");
		x.aliasAttribute(MiningFleetData.class, "sourceMarket", "srcM");
		x.aliasAttribute(MiningFleetData.class, "startingFleetPoints", "strtPt");
		x.aliasAttribute(MiningFleetData.class, "miningStrength", "str");
		
		// ExerelinReputationAdjustmentResult
		x.aliasAttribute(ExerelinReputationAdjustmentResult.class, "wasHostile", "hstl1");
		x.aliasAttribute(ExerelinReputationAdjustmentResult.class, "isHostile", "hstl2");
	}
}
