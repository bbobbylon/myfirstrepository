package Tlutter;

//Used from Employeepalooza in class, as well as the notes on the String[]parts example
//It takes a String tleet description and gets a 
 //tleet from it.
import java.util.ArrayList;

public class OliverParser {
	public static OliverTleet parseDescription(String desc) {
		desc = desc.trim();
		String text,time,name;
		int day;
		ArrayList<String> hashtag = new ArrayList<String>();
		String[] parts = desc.split("\t");
		name = parts[0];
		day = Integer.parseInt(parts[1]);
		time = parts[2];
		text = parts[3];
		for (int i =4; i < parts.length; i++)
		{
			hashtag.add(i-4, parts[i]);
		}
		return new OliverTleet(name, day, time, text, hashtag);
	}
	
	
}