package exerelin.utilities;

import com.fs.starfarer.api.Global;

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
