package exerelin.plugins;

import exerelin.campaign.CovertOpsManager;
import exerelin.campaign.ExerelinReputationAdjustmentResult;
import exerelin.campaign.alliances.Alliance;
import exerelin.campaign.alliances.AllianceVoter;
import exerelin.campaign.covertops.InstigateRebellion;
import exerelin.campaign.events.covertops.AgentDestabilizeMarketEvent;
import exerelin.campaign.events.covertops.AgentLowerRelationsEvent;
import exerelin.campaign.events.AllianceChangedEvent;
import exerelin.campaign.events.AllianceVoteEvent;
import exerelin.campaign.events.covertops.CovertOpsEventBase;
import exerelin.campaign.events.DiplomacyEvent;
import exerelin.campaign.events.ExerelinRepTrackerEvent;
import exerelin.campaign.events.ExigencyRespawnFleetEvent;
import exerelin.campaign.events.FactionBountyEvent;
import exerelin.campaign.events.FactionChangedEvent;
import exerelin.campaign.events.FactionEliminatedEvent;
import exerelin.campaign.events.FactionInsuranceEvent;
import exerelin.campaign.events.FactionRespawnedEvent;
import exerelin.campaign.events.FactionSalaryEvent;
import exerelin.campaign.events.InvasionFleetEvent;
import exerelin.campaign.events.MarketCapturedEvent;
import exerelin.campaign.events.MarketTransferedEvent;
import exerelin.campaign.events.RebellionEvent;
import exerelin.campaign.events.RebellionEventCreator;
import exerelin.campaign.events.RevengeanceManagerEvent;
import exerelin.campaign.events.SSP_FactionVengeanceEvent;
import exerelin.campaign.events.covertops.SaboteurDestroyFoodEvent;
import exerelin.campaign.events.covertops.SaboteurSabotageReserveEvent;
import exerelin.campaign.events.covertops.SecurityAlertEvent;
import exerelin.campaign.events.SlavesSoldEvent;
import exerelin.campaign.events.SuperweaponEvent;
import exerelin.campaign.events.WarmongerEvent;
import exerelin.campaign.events.covertops.InstigateRebellionEvent;
import exerelin.campaign.fleets.DefenceFleetAI;
import exerelin.campaign.fleets.ExerelinPatrolFleetManager;
import exerelin.campaign.fleets.InvasionFleetAI;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.campaign.fleets.InvasionFleetManager.InvasionFleetData;
import exerelin.campaign.fleets.InvasionSupportFleetAI;
import exerelin.campaign.fleets.MiningFleetAI;
import exerelin.campaign.fleets.MiningFleetManager;
import exerelin.campaign.fleets.MiningFleetManager.MiningFleetData;
import exerelin.campaign.fleets.RespawnFleetAI;
import exerelin.campaign.fleets.ResponseFleetAI;
import exerelin.campaign.fleets.ResponseFleetManager;
import exerelin.campaign.fleets.ResponseFleetManager.ResponseFleetData;
import exerelin.campaign.fleets.SuppressionFleetAI;
import exerelin.campaign.missions.ConquestMission;
import exerelin.campaign.missions.ConquestMissionEvent;
import exerelin.campaign.terrain.ExpiringDebrisFieldTerrainPlugin;

public class XStreamConfig {
	
	// TODO: shorten aliases when willing to break saves
	public static void configureXStream(com.thoughtworks.xstream.XStream x)
	{
		/*
		x.alias("AllianceMngr", AllianceManager.class);
		x.alias("CovertOpsMngr", CovertOpsManager.class);
		x.alias("DiplomacyMngr", DiplomacyManager.class);
		x.alias("ExerelinCoreScript", ExerelinCoreScript.class);
		x.alias("PlayerFactionStore", PlayerFactionStore.class);
		x.alias("SectorMngr", SectorManager.class);
		
		x.alias("InvasionFltMngr", InvasionFleetManager.class);
		x.alias("ResponseFltMngr", ResponseFleetManager.class);
		x.alias("MiningFltMngr", MiningFleetManager.class);
		x.alias("ExePatrolFltMngr", ExerelinPatrolFleetManager.class);
		*/
		
		x.alias("DefenceFltAI", DefenceFleetAI.class);
		x.alias("InvasionFltAI", InvasionFleetAI.class);
		x.alias("InvasionSupportFltAI", InvasionSupportFleetAI.class);
		x.alias("MiningFltAI", MiningFleetAI.class);
		x.alias("MiningFltData", MiningFleetManager.MiningFleetData.class);
		x.alias("RespawnFltAI", RespawnFleetAI.class);
		x.alias("ResponseFltAI", ResponseFleetAI.class);
		x.alias("SuppressFltAI", SuppressionFleetAI.class);
		x.alias("ExePatrolFltMngr", ExerelinPatrolFleetManager.class);
		
		x.alias("AgntDestabilizeMrktEvnt", AgentDestabilizeMarketEvent.class);
		x.alias("AgntLowerRelationsEvnt", AgentLowerRelationsEvent.class);
		x.alias("AllyChangedEvnt", AllianceChangedEvent.class);
		x.alias("AllyVoteEvnt", AllianceVoteEvent.class);
		x.alias("AllyVoteRslt", AllianceVoter.VoteResult.class);
		x.alias("CovertOpsEvnt", CovertOpsEventBase.class);
		x.alias("DiploEvnt", DiplomacyEvent.class);
		x.alias("ExeRepTrckrEvnt", ExerelinRepTrackerEvent.class);
		x.alias("ExiRespawnFltEvnt", ExigencyRespawnFleetEvent.class);
		x.alias("FactionBntyEvnt", FactionBountyEvent.class);
		x.alias("FactionBntyEvntKey", FactionBountyEvent.FactionBountyPairKey.class);
		x.alias("FactionChangeEvnt", FactionChangedEvent.class);
		x.alias("FactionElimEvnt", FactionEliminatedEvent.class);
		x.alias("FactionInsurEvnt", FactionInsuranceEvent.class);
		x.alias("FactionRespawnEvnt", FactionRespawnedEvent.class);
		x.alias("FactionSalaryEvnt", FactionSalaryEvent.class);
		x.alias("FactionVengeanceEvnt", SSP_FactionVengeanceEvent.class);
		x.alias("InvasionFltEvnt", InvasionFleetEvent.class);
		x.alias("InstigateRebellionEvnt", InstigateRebellion.class);	// FIXME wrong alias
		x.alias("InstgtRblEvnt", InstigateRebellionEvent.class);
		x.alias("MrktCapturedEvnt", MarketCapturedEvent.class);
		x.alias("MrktTrnsfrEvnt", MarketTransferedEvent.class);
		x.alias("RebelEvnt", RebellionEvent.class);
		x.alias("RebelEvntCreator", RebellionEventCreator.class);
		x.alias("RevengeanceMngrEvnt", RevengeanceManagerEvent.class);
		x.alias("SbtrDestroyFoodEvnt", SaboteurDestroyFoodEvent.class);
		x.alias("SbtrSabotageReserveEvnt", SaboteurSabotageReserveEvent.class);
		x.alias("SecurityAlertEvnt", SecurityAlertEvent.class);
		x.alias("SlavesSoldEvnt", SlavesSoldEvent.class);
		x.alias("SuperweaponEvnt", SuperweaponEvent.class);
		//x.alias("VictoryEvnt", VictoryEvent.class);
		x.alias("WarmongerEvnt", WarmongerEvent.class);
		
		x.alias("ConquestMission", ConquestMission.class);
		x.alias("ConquestMissionEvnt", ConquestMissionEvent.class);
		
		x.alias("InvasionFltData", InvasionFleetManager.InvasionFleetData.class);
		x.alias("ResponseFltData", ResponseFleetManager.ResponseFleetData.class);
		
		x.alias("ExeRepAdjustmentResult", ExerelinReputationAdjustmentResult.class);
		x.alias("ExeAlliance", Alliance.class);
		
		x.alias("NexExpDbrsFld", ExpiringDebrisFieldTerrainPlugin.class);
		
		// enums
		x.alias("CovertActionResult", CovertOpsManager.CovertActionResult.class);
		x.alias("InvasionFltReturnReason", InvasionFleetEvent.FleetReturnReason.class);
		
		configureXStreamAttributes(x);
	}
	
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
		x.aliasAttribute(Alliance.class, "event", "evnt");
		x.aliasAttribute(Alliance.class, "voteEvent", "vote");
		
		// AllianceChangedEvent
		x.aliasAttribute(AllianceChangedEvent.class, "faction1Id", "f1");
		x.aliasAttribute(AllianceChangedEvent.class, "faction2Id", "f2");
		x.aliasAttribute(AllianceChangedEvent.class, "allianceId", "aID");
		x.aliasAttribute(AllianceChangedEvent.class, "stage", "stg");
		
		// AllianceVoteEvent
		x.aliasAttribute(AllianceVoteEvent.class, "allianceId", "aID");
		x.aliasAttribute(AllianceVoteEvent.class, "otherParty", "other");
		x.aliasAttribute(AllianceVoteEvent.class, "stage", "stg");
		
		// DiplomacyEvent
		x.aliasAttribute(DiplomacyEvent.class, "eventStage", "stage");
		x.aliasAttribute(DiplomacyEvent.class, "result", "rslt");
		x.aliasAttribute(DiplomacyEvent.class, "otherFaction", "other");
		
		// FactionBountyEvent
		x.aliasAttribute(FactionBountyEvent.class, "elapsedDays", "days");
		x.aliasAttribute(FactionBountyEvent.class, "duration", "max");
		x.aliasAttribute(FactionBountyEvent.class, "baseBounty", "pays");
		x.aliasAttribute(FactionBountyEvent.class, "lastBounty", "last");
		x.aliasAttribute(FactionBountyEvent.class, "enemyFaction", "ef");
		
		// FactionChangedEvent
		x.aliasAttribute(FactionChangedEvent.class, "oldFaction", "old");
		x.aliasAttribute(FactionChangedEvent.class, "newFaction", "new");
		
		// FactionEliminatedEvent
		x.aliasAttribute(FactionEliminatedEvent.class, "defeatedFaction", "lose");
		x.aliasAttribute(FactionEliminatedEvent.class, "victorFaction", "win");
		x.aliasAttribute(FactionEliminatedEvent.class, "playerDefeated", "pLose");
		x.aliasAttribute(FactionEliminatedEvent.class, "playerVictory", "pWin");
		
		// FactionRespawnedEvent
		x.aliasAttribute(FactionRespawnedEvent.class, "existedBefore", "notNew");
		
		// InvasionFleetEvent
		x.aliasAttribute(InvasionFleetEvent.class, "target", "tgt");
		x.aliasAttribute(InvasionFleetEvent.class, "factionPermanent", "facP");
				
		// MarketCapturedEvent
		x.aliasAttribute(MarketCapturedEvent.class, "newOwner", "new");
		x.aliasAttribute(MarketCapturedEvent.class, "oldOwner", "old");
		x.aliasAttribute(MarketCapturedEvent.class, "repChangeStrength", "rep");
		x.aliasAttribute(MarketCapturedEvent.class, "factionsToNotify", "ntfy");
		x.aliasAttribute(MarketCapturedEvent.class, "playerInvolved", "plyr");
		
		// MarketTransferedEvent
		x.aliasAttribute(MarketTransferedEvent.class, "newOwner", "new");
		x.aliasAttribute(MarketTransferedEvent.class, "oldOwner", "old");
		x.aliasAttribute(MarketTransferedEvent.class, "repEffect", "rep");
		
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
		
		// SSP_FactionVengeanceEvent
		x.aliasAttribute(SSP_FactionVengeanceEvent.class, "daysLeft", "days");
		x.aliasAttribute(SSP_FactionVengeanceEvent.class, "escalationLevel", "escal");
		x.aliasAttribute(SSP_FactionVengeanceEvent.class, "fleet", "flt");
		x.aliasAttribute(SSP_FactionVengeanceEvent.class, "foundPlayerYet", "found");
		x.aliasAttribute(SSP_FactionVengeanceEvent.class, "timeSpentLooking", "look");
		x.aliasAttribute(SSP_FactionVengeanceEvent.class, "trackingMode", "trck");
		
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
		
		// buggy; breaks with variables that are public in parent
		// ExerelinPatrolFleetManager
		//x.aliasAttribute(ExerelinPatrolFleetManager.class, "market", "mkt");
		//x.aliasAttribute(ExerelinPatrolFleetManager.class, "activePatrols", "flts");
		//x.aliasAttribute(ExerelinPatrolFleetManager.class, "maxPatrols", "max");
		x.aliasAttribute(ExerelinPatrolFleetManager.class, "patrolPoints", "pts");
		//x.aliasAttribute(ExerelinPatrolFleetManager.class, "patrolBattlesLost", "lost");
		
		// InvasionFleetAI
		x.aliasAttribute(InvasionFleetAI.class, "daysTotal", "days");
		x.aliasAttribute(InvasionFleetAI.class, "fleet", "flt");
		x.aliasAttribute(InvasionFleetAI.class, "orderedReturn", "ret");
		x.aliasAttribute(InvasionFleetAI.class, "responseFleetRequested", "rspn");
		
		// InvasionFleetData
		x.aliasAttribute(InvasionFleetData.class, "fleet", "flt");
		x.aliasAttribute(InvasionFleetData.class, "source", "src");
		x.aliasAttribute(InvasionFleetData.class, "target", "tgt");
		x.aliasAttribute(InvasionFleetData.class, "sourceMarket", "srcM");
		x.aliasAttribute(InvasionFleetData.class, "targetMarket", "tgtM");
		x.aliasAttribute(InvasionFleetData.class, "startingFleetPoints", "strtPt");
		x.aliasAttribute(InvasionFleetData.class, "marineCount", "mrn");
		
		// InvasionSupportFleetAI
		x.aliasAttribute(InvasionSupportFleetAI.class, "daysTotal", "days");
		x.aliasAttribute(InvasionSupportFleetAI.class, "fleet", "flt");
		x.aliasAttribute(InvasionSupportFleetAI.class, "orderedReturn", "ret");
		x.aliasAttribute(InvasionSupportFleetAI.class, "criticalDamage", "crit");
		
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
		
		// ResponseFleetAI
		x.aliasAttribute(ResponseFleetAI.class, "daysTotal", "days");
		x.aliasAttribute(ResponseFleetAI.class, "fleet", "flt");
		x.aliasAttribute(ResponseFleetAI.class, "orderedReturn", "ret");
		
		// ResponseFleetData
		x.aliasAttribute(ResponseFleetData.class, "fleet", "flt");
		x.aliasAttribute(ResponseFleetData.class, "source", "src");
		x.aliasAttribute(ResponseFleetData.class, "target", "tgt");
		x.aliasAttribute(ResponseFleetData.class, "sourceMarket", "srcM");
		x.aliasAttribute(ResponseFleetData.class, "startingFleetPoints", "strtPt");
		
		// ExerelinReputationAdjustmentResult
		x.aliasAttribute(ExerelinReputationAdjustmentResult.class, "wasHostile", "hstl1");
		x.aliasAttribute(ExerelinReputationAdjustmentResult.class, "isHostile", "hstl2");
	}
}
