package exerelin.world;

import java.util.ArrayList;
import java.util.List;
import org.lwjgl.util.vector.Vector2f;

// TODO externalise
public class StarLocations {
	public static final List<Vector2f> SPOT = new ArrayList<>();
	static
    {
		List<Vector2f> temp = new ArrayList<>();
		temp.add(new Vector2f(1000,4000));		// askonia
		temp.add(new Vector2f(8000,-1000));		// corvus
		temp.add(new Vector2f(12750,250));		// aztlan
		temp.add(new Vector2f(9750,-7500));		// samarra
		temp.add(new Vector2f(7000,5500));		// valhalla
		temp.add(new Vector2f(3000,-7000));		// arcadia
		temp.add(new Vector2f(-3000,-4000));	// magec
		temp.add(new Vector2f(-10000,-2000));	// eos
		temp.add(new Vector2f(3000,250));		// duzahk
		temp.add(new Vector2f(-6500,-7500));	// penelope
		temp.add(new Vector2f(8250,9500));		// hybrasil
		temp.add(new Vector2f(14100,4400));		// yma
		
        temp.add(new Vector2f(25000,9000));     
        temp.add(new Vector2f(23000,17000));  
        temp.add(new Vector2f(23000,11000));  
        temp.add(new Vector2f(19000,-8000)); 
        temp.add(new Vector2f(18000,12000));   
        temp.add(new Vector2f(18000,-13000)); 
        temp.add(new Vector2f(17000,-3500));   
        temp.add(new Vector2f(18000,1000));  
        temp.add(new Vector2f(13000,23000));     
        temp.add(new Vector2f(13000,-4000)); 
        temp.add(new Vector2f(13000,-15000));
        temp.add(new Vector2f(13000,13000));  
        temp.add(new Vector2f(11000,16000));       
        temp.add(new Vector2f(11000,11000));
        temp.add(new Vector2f(9000,19000));     
        temp.add(new Vector2f(10000,-3000));   
        temp.add(new Vector2f(9000,-11000));  
        temp.add(new Vector2f(9000,-15000)); 
        temp.add(new Vector2f(7000,15500));           
        temp.add(new Vector2f(7000,1000));  
        temp.add(new Vector2f(6000,-5000));  
        temp.add(new Vector2f(5000,-13000));
        temp.add(new Vector2f(4000,20000));       
        temp.add(new Vector2f(3500,6000)); 
        temp.add(new Vector2f(3000,-17000)); 
        temp.add(new Vector2f(2500,10500));      
        temp.add(new Vector2f(1000,-13000));  
        temp.add(new Vector2f(-1000,19000));      
        temp.add(new Vector2f(-1000,8000));   
        temp.add(new Vector2f(-1000,-15500)); 
        temp.add(new Vector2f(-1500,-500));   
        temp.add(new Vector2f(-2500,3000)); 
        temp.add(new Vector2f(-5000,18000));     
        temp.add(new Vector2f(-5000,-11500)); 
        temp.add(new Vector2f(-6000,12000));       
        temp.add(new Vector2f(-8000,-17000));
        temp.add(new Vector2f(-9000,5000));     
        temp.add(new Vector2f(-10000,14000)); 
        temp.add(new Vector2f(-10000,10000));          
        temp.add(new Vector2f(-11000,-17000));
        temp.add(new Vector2f(-10000,-9000));     
        temp.add(new Vector2f(-12000,1000));      
        temp.add(new Vector2f(-13000,11000));     
        temp.add(new Vector2f(-13000,5000));      
        temp.add(new Vector2f(-13000,-11000));
        temp.add(new Vector2f(-16000,-8000));     
        temp.add(new Vector2f(-17000,13000));
        temp.add(new Vector2f(-19000,9000));
        temp.add(new Vector2f(-19000,0));     
        temp.add(new Vector2f(-19500,4000)); 
        temp.add(new Vector2f(-20000,-3000));      
        temp.add(new Vector2f(-23000,4000)); 
        temp.add(new Vector2f(-23000,-5000));         
        temp.add(new Vector2f(-14000,-4000));        
        temp.add(new Vector2f(20500,8500));
		
		//double max = Math.pow(10000, 2);
		int maxX = 15000;
		int maxY = 12000;
		for (Vector2f pos : temp)
		{
			if (Math.abs(pos.x) <= maxX && Math.abs(pos.y) <= maxY)
				SPOT.add(pos);
		}
    }
}
