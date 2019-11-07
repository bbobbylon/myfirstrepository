package Tlutter;

//Based on the File Reader from both Weather App and Employeepalooza
import java.io.File;
import java.util.ArrayList;
import java.util.Scanner;

public class OliverFileReader {
	public static ArrayList<OliverTleet> readFile(String fname)
	{
		try
		{
			ArrayList<OliverTleet> tleets = new ArrayList<OliverTleet>();
			Scanner fsc = new Scanner(new File(fname));
			String line;
			while (fsc.hasNextLine())
			{
			line = fsc.nextLine().trim();
			tleets.add(OliverParser.parseDescription(line));
			}
			fsc.close();
			return tleets;
			
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
			return null;
		}
	}

}
