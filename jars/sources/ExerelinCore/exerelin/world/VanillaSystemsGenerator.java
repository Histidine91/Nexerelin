package exerelin.world;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import data.scripts.world.corvus.Corvus;
import data.scripts.world.systems.Arcadia;
import data.scripts.world.systems.Askonia;
import data.scripts.world.systems.Aztlan;
import data.scripts.world.systems.Duzahk;
import data.scripts.world.systems.Eos;
import data.scripts.world.systems.Hybrasil;
import data.scripts.world.systems.Magec;
import data.scripts.world.systems.Penelope;
import data.scripts.world.systems.Samarra;
import data.scripts.world.systems.Valhalla;
import data.scripts.world.systems.Yma;

public class VanillaSystemsGenerator {
	public static void generate()
	{
		SectorAPI sector = Global.getSector();
		new Askonia().generate(sector);
		new Eos().generate(sector);
		new Valhalla().generate(sector);
		new Arcadia().generate(sector);
		new Magec().generate(sector);
		new Corvus().generate(sector);
		new Aztlan().generate(sector);
		new Samarra().generate(sector);
		new Penelope().generate(sector);
		new Yma().generate(sector);
		new Hybrasil().generate(sector);
		new Duzahk().generate(sector);
		
		LocationAPI hyper = Global.getSector().getHyperspace();
		SectorEntityToken zinLabel = hyper.addCustomEntity("zin_label_id", null, "zin_label", null);
		SectorEntityToken abyssLabel = hyper.addCustomEntity("opabyss_label_id", null, "opabyss_label", null);
		SectorEntityToken telmunLabel = hyper.addCustomEntity("telmun_label_id", null, "telmun_label", null);
		SectorEntityToken cathedralLabel = hyper.addCustomEntity("cathedral_label_id", null, "cathedral_label", null);
		SectorEntityToken coreLabel = hyper.addCustomEntity("core_label_id", null, "core_label", null);
		
		zinLabel.setFixedLocation(-14500, -8000);
		abyssLabel.setFixedLocation(-12000, -19000);
		telmunLabel.setFixedLocation(-16000, 8000);
		cathedralLabel.setFixedLocation(-20000, 2000);
		coreLabel.setFixedLocation(17000, -6000);
	}
}
