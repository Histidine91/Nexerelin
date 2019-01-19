package exerelin.plugins;

import exerelin.campaign.CovertOpsManager;
import exerelin.campaign.ExerelinReputationAdjustmentResult;
import exerelin.campaign.alliances.Alliance;
import exerelin.campaign.alliances.AllianceVoter;
import exerelin.campaign.covertops.InstigateRebellion;
import exerelin.campaign.events.covertops.AgentDestabilizeMarketEvent;
import exerelin.campaign.events.covertops.AgentLowerRelationsEvent;
import exerelin.campaign.events.covertops.CovertOpsEventBase;
import exerelin.campaign.events.ExigencyRespawnFleetEvent;
import exerelin.campaign.events.FactionBountyEvent;
import exerelin.campaign.events.RebellionEvent;
import exerelin.campaign.events.RebellionEventCreator;
import exerelin.campaign.diplomacy.DiplomacyBrain;
import exerelin.campaign.econ.RaidCondition;
import exerelin.campaign.intel.VengeanceFleetIntel;
import exerelin.campaign.events.covertops.SaboteurDestroyFoodEvent;
import exerelin.campaign.events.covertops.SaboteurSabotageReserveEvent;
import exerelin.campaign.events.covertops.SecurityAlertEvent;
import exerelin.campaign.events.SlavesSoldEvent;
import exerelin.campaign.events.SuperweaponEvent;
import exerelin.campaign.events.WarmongerEvent;
import exerelin.campaign.events.covertops.InstigateRebellionEvent;
import exerelin.campaign.fleets.DefenceFleetAI;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.campaign.fleets.InvasionSupportFleetAI;
import exerelin.campaign.fleets.MiningFleetAI;
import exerelin.campaign.fleets.MiningFleetManagerV2;
import exerelin.campaign.fleets.MiningFleetManagerV2.MiningFleetData;
import exerelin.campaign.fleets.RespawnFleetAI;
import exerelin.campaign.fleets.ResponseFleetAI;
import exerelin.campaign.fleets.SuppressionFleetAI;
import exerelin.campaign.intel.DiplomacyIntel;
import exerelin.campaign.intel.FactionSpawnedOrEliminatedIntel;
import exerelin.campaign.intel.InvasionIntel;
import exerelin.campaign.intel.MarketTransferIntel;
import exerelin.campaign.intel.Nex_PunitiveExpeditionIntel;
import exerelin.campaign.intel.fleets.NexAssembleStage;
import exerelin.campaign.intel.fleets.NexOrganizeStage;
import exerelin.campaign.intel.fleets.NexReturnStage;
import exerelin.campaign.intel.fleets.NexTravelStage;
import exerelin.campaign.intel.invasion.InvActionStage;
import exerelin.campaign.intel.invasion.InvAssembleStage;
import exerelin.campaign.intel.invasion.InvOrganizeStage;
import exerelin.campaign.intel.raid.BaseStrikeIntel;
import exerelin.campaign.intel.raid.NexRaidActionStage;
import exerelin.campaign.intel.raid.NexRaidAssembleStage;
import exerelin.campaign.intel.raid.NexRaidIntel;
import exerelin.campaign.missions.ConquestMission;
import exerelin.campaign.missions.ConquestMissionEvent;
import exerelin.campaign.submarkets.Nex_BlackMarketPlugin;
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
		
		x.alias("MiningFltAI", MiningFleetAI.class);
		x.alias("MiningFltData", MiningFleetManagerV2.MiningFleetData.class);
		x.alias("SuppressFltAI", SuppressionFleetAI.class);
		x.alias("InvasionFltData", InvasionFleetManager.InvasionFleetData.class);
		x.alias("VengFltIntl", VengeanceFleetIntel.class);
		
		x.alias("DiploIntl", DiplomacyIntel.class);
		x.alias("MktTrnsfrIntl", MarketTransferIntel.class);
		x.alias("FactionChngIntl", FactionSpawnedOrEliminatedIntel.class);
		
		// submarkets
		x.alias("NexOpnMkt", Nex_OpenMarketPlugin.class);
		x.alias("NexMilSubmkt", Nex_MilitarySubmarketPlugin.class);
		x.alias("NexBlackMkt", Nex_BlackMarketPlugin.class);
		
		// events
		// most of these will be deleted eventually
		x.alias("AgntDestabilizeMrktEvnt", AgentDestabilizeMarketEvent.class);
		x.alias("AgntLowerRelationsEvnt", AgentLowerRelationsEvent.class);
		x.alias("AllyVoteRslt", AllianceVoter.VoteResult.class);
		x.alias("CovertOpsEvnt", CovertOpsEventBase.class);
		x.alias("ExiRespawnFltEvnt", ExigencyRespawnFleetEvent.class);
		x.alias("FactionBntyEvnt", FactionBountyEvent.class);
		x.alias("FactionBntyEvntKey", FactionBountyEvent.FactionBountyPairKey.class);
		x.alias("InstgtRbl", InstigateRebellion.class);
		x.alias("InstgtRblEvnt", InstigateRebellionEvent.class);
		x.alias("RebelEvnt", RebellionEvent.class);
		x.alias("RebelEvntCreator", RebellionEventCreator.class);
		x.alias("SbtrDestroyFoodEvnt", SaboteurDestroyFoodEvent.class);
		x.alias("SbtrSabotageReserveEvnt", SaboteurSabotageReserveEvent.class);
		x.alias("SecurityAlertEvnt", SecurityAlertEvent.class);
		x.alias("SlavesSoldEvnt", SlavesSoldEvent.class);
		x.alias("SuperweaponEvnt", SuperweaponEvent.class);
		x.alias("WarmongerEvnt", WarmongerEvent.class);
		
		x.alias("ConquestMission", ConquestMission.class);
		x.alias("ConquestMissionEvnt", ConquestMissionEvent.class);
		
		// intel
		x.alias("NexRaidCond", RaidCondition.class);
		x.alias("NexPunExIntl", Nex_PunitiveExpeditionIntel.class);
		
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
		
		// Remnant raids
		// not worthwhile tbh; there'll be at most one of each at a time pretty much
		/*
		x.alias("NexRemRaidIntl", RemnantRaidIntel.class);
		x.alias("NexRemRaidOrgStg", RemnantRaidOrganizeStage.class);
		x.alias("NexRemRaidAssmblStg", RemnantRaidAssembleStage.class);
		x.alias("NexRemRaidTrvlStg", RemnantRaidTravelStage.class);
		x.alias("NexRemRaidActStg", RemnantRaidActionStage.class);
		x.alias("NexRemRaidRetStg", RemnantRaidReturnStage.class);
		*/
		
		// misc
		x.alias("NexRepAdjustmentResult", ExerelinReputationAdjustmentResult.class);
		x.alias("NexAlliance", Alliance.class);
		x.alias("DiploBrain", DiplomacyBrain.class);
		x.alias("DiploDspsEntry", DiplomacyBrain.DispositionEntry.class);
		
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
		
		// FactionBountyEvent
		x.aliasAttribute(FactionBountyEvent.class, "elapsedDays", "days");
		x.aliasAttribute(FactionBountyEvent.class, "duration", "max");
		x.aliasAttribute(FactionBountyEvent.class, "baseBounty", "pays");
		x.aliasAttribute(FactionBountyEvent.class, "lastBounty", "last");
		x.aliasAttribute(FactionBountyEvent.class, "enemyFaction", "ef");
		
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
		
		// SuperweaponEvent
		x.aliasAttribute(SuperweaponEvent.class, "elapsedDays", "days");
		x.aliasAttribute(SuperweaponEvent.class, "stabilityPenalty", "stbLoss");
		x.aliasAttribute(SuperweaponEvent.class, "lastAttackerFaction", "attkr");
		x.aliasAttribute(SuperweaponEvent.class, "repPenalty", "rep");
		x.aliasAttribute(SuperweaponEvent.class, "lastRepEffect", "lastRep");
		x.aliasAttribute(SuperweaponEvent.class, "avgRepLoss", "avgRep");
		x.aliasAttribute(SuperweaponEvent.class, "wasPlayer", "plyr");
		
		// Agent events
		// AgentDestabilizeMarketEvent
		x.aliasAttribute(AgentDestabilizeMarketEvent.class, "stabilityPenalty", "stbLoss");
				
		// CovertOpsEventBase
		x.aliasAttribute(CovertOpsEventBase.class, "agentFaction", "af");
		x.aliasAttribute(CovertOpsEventBase.class, "result", "rslt");
		x.aliasAttribute(CovertOpsEventBase.class, "playerInvolved", "plyr");
		x.aliasAttribute(CovertOpsEventBase.class, "repResult", "rep");
		
		// InstigateRebellionEvent
		x.aliasAttribute(InstigateRebellionEvent.class, "timeframe", "time");
		
		// SaboteurDestroyFoodEvent
		x.aliasAttribute(SaboteurDestroyFoodEvent.class, "foodDestroyed", "food");
		
		// SaboteurSabotageReserveEvent
		x.aliasAttribute(SaboteurSabotageReserveEvent.class, "reserveDamage", "dmg");
		
		// SecurityAlertEvent
		x.aliasAttribute(SecurityAlertEvent.class, "elapsedDays", "days");
		x.aliasAttribute(SecurityAlertEvent.class, "alertLevel", "alrt");
		
		// ConquestMission
		x.aliasAttribute(ConquestMission.class, "target", "tgt");
		x.aliasAttribute(ConquestMission.class, "baseDuration", "dur1");
		x.aliasAttribute(ConquestMission.class, "bonusDuration", "dur2");
		x.aliasAttribute(ConquestMission.class, "baseReward", "pay1");
		x.aliasAttribute(ConquestMission.class, "bonusReward", "pay2");
				
		// ConquestMissionEvent
		x.aliasAttribute(ConquestMissionEvent.class, "mission", "msn");
		x.aliasAttribute(ConquestMissionEvent.class, "elapsedDays", "elpsd");
		x.aliasAttribute(ConquestMissionEvent.class, "daysLeftStr", "daysStr");
		x.aliasAttribute(ConquestMissionEvent.class, "bonusDaysLeftStr", "daysStr2");
		
		// Fleets
		// DefenceFleetAI
		x.aliasAttribute(DefenceFleetAI.class, "daysTotal", "days");
		x.aliasAttribute(DefenceFleetAI.class, "fleet", "flt");
		x.aliasAttribute(DefenceFleetAI.class, "orderedReturn", "ret");
				
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
		
		// RespawnFleetAI
		x.aliasAttribute(RespawnFleetAI.class, "captureSuccessful", "cap");
		x.aliasAttribute(RespawnFleetAI.class, "forceHostile", "hstl");
				
		// ExerelinReputationAdjustmentResult
		x.aliasAttribute(ExerelinReputationAdjustmentResult.class, "wasHostile", "hstl1");
		x.aliasAttribute(ExerelinReputationAdjustmentResult.class, "isHostile", "hstl2");
	}
}
