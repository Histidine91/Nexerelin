package exerelin.utilities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import exerelin.SectorManager;

import java.awt.*;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;

public class ExerelinCheck
{
    public static Boolean isToreUpPlentyInstalled()
    {
        try
        {
            Global.getSettings().getScriptClassLoader().loadClass("data.scripts.TUPModPlugin");
            System.out.println("EXERELIN: tore up plenty installed");
            return true;
        }
        catch (ClassNotFoundException ex)
        {
            System.out.println("EXERELIN: tore up plenty not installed");
            return false;
        }
    }
}
