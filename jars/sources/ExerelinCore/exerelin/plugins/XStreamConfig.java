package exerelin.plugins;

import exerelin.campaign.CovertOpsManager;
import exerelin.campaign.ExerelinReputationAdjustmentResult;
import exerelin.campaign.alliances.Alliance;
import exerelin.campaign.events.AgentDestabilizeMarketEvent;
import exerelin.campaign.events.AgentDestabilizeMarketEventForCondition;
import exerelin.campaign.events.AgentLowerRelationsEvent;
import exerelin.campaign.events.AllianceChangedEvent;
import exerelin.campaign.events.AllianceVoteEvent;
import exerelin.campaign.events.CovertOpsEventBase;
import exerelin.campaign.events.DiplomacyEvent;
import exerelin.campaign.events.ExerelinFactionCommissionMissionEvent;
import exerelin.campaign.events.ExerelinRepTrackerEvent;
import exerelin.campaign.events.ExigencyRespawnFleetEvent;
import exerelin.campaign.events.FactionChangedEvent;
import exerelin.campaign.events.FactionEliminatedEvent;
import exerelin.campaign.events.FactionInsuranceEvent;
import exerelin.campaign.events.FactionRespawnedEvent;
import exerelin.campaign.events.FactionSalaryEvent;
import exerelin.campaign.events.InvasionFleetEvent;
import exerelin.campaign.events.MarketAttackedEvent;
import exerelin.campaign.events.MarketCapturedEvent;
import exerelin.campaign.events.RevengeanceFleetEvent;
import exerelin.campaign.events.SaboteurDestroyFoodEvent;
import exerelin.campaign.events.SaboteurSabotageReserveEvent;
import exerelin.campaign.events.SecurityAlertEvent;
import exerelin.campaign.events.SlavesSoldEvent;
import exerelin.campaign.events.SuperweaponEvent;
import exerelin.campaign.events.WarmongerEvent;
import exerelin.campaign.fleets.DefenceFleetAI;
import exerelin.campaign.fleets.ExerelinPatrolFleetManager;
import exerelin.campaign.fleets.InvasionFleetAI;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.campaign.fleets.InvasionSupportFleetAI;
import exerelin.campaign.fleets.MiningFleetAI;
import exerelin.campaign.fleets.RespawnFleetAI;
import exerelin.campaign.fleets.ResponseFleetAI;
import exerelin.campaign.fleets.ResponseFleetManager;
import exerelin.campaign.missions.ConquestMission;
import exerelin.campaign.missions.ConquestMissionEvent;
import exerelin.campaign.missions.ExerelinMarketProcurementMissionEvent;

public class XStreamConfig {
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
        x.alias("RespawnFltAI", RespawnFleetAI.class);
        x.alias("ResponseFltAI", ResponseFleetAI.class);
        x.alias("ExePatrolFltMngr", ExerelinPatrolFleetManager.class);
        
        x.alias("AgntDestabilizeMrktEvnt", AgentDestabilizeMarketEvent.class);
        x.alias("AgntDestabilizeMrktEvntForCondition", AgentDestabilizeMarketEventForCondition.class);
        x.alias("AgntLowerRelationsEvnt", AgentLowerRelationsEvent.class);
        x.alias("AllyChangedEvnt", AllianceChangedEvent.class);
		x.alias("AllyVoteEvnt", AllianceVoteEvent.class);
        x.alias("CovertOpsEvnt", CovertOpsEventBase.class);
        x.alias("DiploEvnt", DiplomacyEvent.class);
        x.alias("ExeCommissionMissionEvnt", ExerelinFactionCommissionMissionEvent.class);
        x.alias("ExeRepTrckrEvnt", ExerelinRepTrackerEvent.class);
        x.alias("ExiRespawnFltEvnt", ExigencyRespawnFleetEvent.class);
        x.alias("FactionChangeEvnt", FactionChangedEvent.class);
        x.alias("FactionElimEvnt", FactionEliminatedEvent.class);
        x.alias("FactionInsurEvnt", FactionInsuranceEvent.class);
        x.alias("FactionRespawnEvnt", FactionRespawnedEvent.class);
        x.alias("FactionSalaryEvnt", FactionSalaryEvent.class);
        x.alias("InvasionFltEvnt", InvasionFleetEvent.class);
        x.alias("MrktAttackedEvnt", MarketAttackedEvent.class);
        x.alias("MrktCapturedEvnt", MarketCapturedEvent.class);
        x.alias("RevengeanceFltEvnt", RevengeanceFleetEvent.class);
        x.alias("SbtrDestroyFoodEvnt", SaboteurDestroyFoodEvent.class);
        x.alias("SbtrSabotageReserveEvnt", SaboteurSabotageReserveEvent.class);
		x.alias("SecurityAlertEvnt", SecurityAlertEvent.class);
        x.alias("SlavesSoldEvnt", SlavesSoldEvent.class);
        x.alias("SuperweaponEvnt", SuperweaponEvent.class);
        //x.alias("VictoryEvnt", VictoryEvent.class);
        x.alias("WarmongerEvnt", WarmongerEvent.class);
		
		x.alias("ConquestMission", ConquestMission.class);
		x.alias("ConquestMissionEvnt", ConquestMissionEvent.class);
		x.alias("ExeMrktProcurementMissionEvnt", ExerelinMarketProcurementMissionEvent.class);
		
		x.alias("InvasionFltData", InvasionFleetManager.InvasionFleetData.class);
		x.alias("ResponseFltData", ResponseFleetManager.ResponseFleetData.class);
		
		x.alias("ExeRepAdjustmentResult", ExerelinReputationAdjustmentResult.class);
		x.alias("ExeAlliance", Alliance.class);
		
		// enums
		x.alias("CovertActionResult", CovertOpsManager.CovertActionResult.class);
		x.alias("InvasionFltReturnReason", InvasionFleetEvent.FleetReturnReason.class);
	}
}
