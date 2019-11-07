package Tlutter;

/* I used the Employee Analytic class from Employeepalooza in calss. It analyzes data 
 * from a collection of names, hashtags, and days. 
 * used for options 2-4 in the main class.
 */
import java.util.ArrayList;

public class OliverAnalytic {
	public static ArrayList<OliverTleet> getAName(ArrayList<OliverTleet> tleets, String name)
	{
		ArrayList<OliverTleet> result = new ArrayList<OliverTleet>();
		for(OliverTleet tleet : tleets)
		{
			if (name.equals(tleet.getName()))
			{
				result.add(tleet);
			}
		} return result;

	}
	public static ArrayList<OliverTleet> getAHashtag(ArrayList<OliverTleet> tleets, String hashtag)
	{
		ArrayList<OliverTleet> result = new ArrayList<OliverTleet>();
		for(OliverTleet tleet: tleets)
		{
			for (String hash: tleet.getHashtag())
			{
				if(hash.contentEquals(hashtag))
				{
					result.add(tleet);
				}
			}
		}return result;
	}
	public static ArrayList<OliverTleet> getADay(ArrayList<OliverTleet> tleets, int []day)
	{
		int startDay = day[0];
		int endDay = day[1];
		ArrayList<OliverTleet> result = new ArrayList<OliverTleet>();
		for(OliverTleet tleet: tleets)
		{
			if(startDay <= tleet.getDay() && endDay >= tleet.getDay())
			{
				result.add(tleet);
			}
		}
		return result;
	}
}