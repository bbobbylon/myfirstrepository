package Tlutter;

/*Robert Oliver Jr
 09/13/2019
 Computer Organization
This app / program is designed to import a file in a certain format;
the format is from (0-165), each number representing a character/type of weather:
(0-17) is for the station
(18-68) is for the station name
(69-77) is for the date
(78-84) is for the precipitation
(85-93) is for the snow
(94-103) is for the maximum temperature
(104-110) is for the minimum temperature
(111-120) is for wt01
(121-129) is for wt06
(130-138) is for wt05
(139-148) is for wt11
(149-156) is for wt04
(156-165) is for wt03
below is a key for identifying the data types
tows = the weather file as an array list
tow = the weather file on a certain line in the array list

DATA TYPES / KEY ---
snow = Snow fall
prcp = precipitation
wt11 = high or damaging winds
wt01 = fog, ice fog, or freezing fog, may include heavy fog\
wt03 = thunder
wt04 = ice pellets, sleet, snow pellets, or small hail
wt05 = hail
wt06 = glaze or rime
tmax = max temperature
tmin = min temperature
*/

import java.util.ArrayList;
import java.util.Scanner;
import java.io.File;

public class OliverJrWeatherApp
{
	public static void starPrinter(int howMany)
	{
		String star = "";
		for (int i =0; i < howMany; i++) 
		{
			star = star + "*";
		}
		System.out.println(star);
	}
	
	public static ArrayList<String> readFile(String fname)
	{
		try
		{
			ArrayList<String> tows = new ArrayList<String>();
			Scanner fsc = new Scanner(new File(fname));
			String line;
			fsc.nextLine();
			fsc.nextLine(); //ignoring the first two lines of the file
			while (fsc.hasNextLine())
			{
			line = fsc.nextLine().trim();
			tows.add(line);
			}
			fsc.close();
			return tows;
			// tow meaning type of weather
		} catch (Exception ex)
		{
			return null;
		}
	}
	
	public static void showMenu() //displays the menu
	{
		System.out.println("Here are your options: ");
		System.out.println("1. Average High Temperature: ");
		System.out.println("2. Average Low Temperature: ");
		System.out.println("3. Highest Temperature: ");
		System.out.println("4. Lowest Temperature: ");
		System.out.println("5. Total Precipitation: ");
		System.out.println("6. Number of days of thunder: ");
		System.out.println("7. High and low temperatures of any given date: ");
		System.out.println("8. Total days of Hail! ");
		System.out.println("9. Total days of Pellets! ");
		System.out.println("10. Exit.");
		System.out.println("Please enter the number of your choice: ");
	}
	
	public static void welcomeMenu() //welcomes the user
	{
		starPrinter(75);
		System.out.println("JAVA WEATHER APP");
		System.out.println("This program reads the file of weather data downloaded");
		System.out.println("from NOAA. The user  can learn a lot about this year's");
		System.out.println("weather by using this application, if he/she pleases. Enjoy!");
		System.out.println("P.S. all temperatures shown are in degrees Fahrenheit!!!");
		starPrinter(75);
		
	}
	
	public static double getP(String tows) //function to get precipitation from each line
	{
		String none = tows.substring(77,83).trim();
		if (none.equals("-9999"))
		{
			return 0;
			
		}
		else 
		{
			return Double.parseDouble(none);
		}
		
	}
	
	public static double getS(String tows) //function to get snow from each line and stores it
	{
		String none = tows.substring(86,93).trim();
		if (none.equals("-9999"))
		{
			return 0;
		}
		else 
		{
			return Double.parseDouble(none);
		}
	}
	
	public static double getT(String tows) //function that gets thunder from each line and stores it
	{
		//for below, I used tows.length to go to the end of the file. For some reason if I entered
		// (156,160) I would get an exception in main "For input string: '-' "
		String none = tows.substring(156,tows.length()).trim();
		if (none.equals("-9999"))
		{
			return 0;
		} 
		else 
		{
			return Double.parseDouble(none);
		}
	}
	
	public static double showTotalT(ArrayList<String> tows) //function that shows the total days of Thunder for the entire file

	{
		double total = 0;
		for (String tow: tows)
		{
			total = total+getT(tow);
		} return total;
	}
	
	public static double getH(String tows) // function that gets hail! from each line and stores it
	{
		String none = tows.substring(130,138).trim();
		if (none.equals("-9999"))
		{
			return 0;
		} 
		else 
		{
			return Double.parseDouble(none);
		}
	}
	
	public static double showH(ArrayList<String> tows) // function that displays the total days of hail
	{
		double total = 0;
		for (String tow:tows)
		{
			total = total +getH(tow);
		} return total;
	}
	
	public static double getHTemp(String tows) //gets the highest temperature in any given line of the text file
	{
		return Double.parseDouble(tows.substring(95,102).trim());
	}
	
	public static double getPellets(String tows) //gets pellets! in any given line of the text file
	{
		String none = tows.substring(148,156).trim();
		if (none.equals("-9999"))	
		{
			return 0;
		}
		else 
		{
			return Double.parseDouble(none);
		}
	}
	
	public static double showTotalPellets(ArrayList<String> tows) // function that shows the total days of pellets! in the entire file
	{
		double total = 0;
		for (String tow: tows)
		{
			total = total + getPellets(tow);
		} return total;
	}
	
	public static double showHighestTemp(ArrayList<String> tows)// shows the highest temperature in the whole file
	{
		double highest = getHTemp(tows.get(0));
		int index = 0; //where the highest temperature is found / located
		int counter = 0; // number of temperatures currently considered
		double temp;
		for (String tow: tows)
		{
		temp = getHTemp(tow);
		if (temp > highest)
		{
			highest = temp;
			index = counter;
		}
		counter = counter +1;
		}
		return getHTemp(tows.get(index));
	}
	
	public static double getLTemp(String tow)//gets the lowest temperature in any given line of the text file
	{
		return Double.parseDouble(tow.substring(104,110).trim());
	}
	
	public static double showLowestTemp(ArrayList<String> tows) //shows the lowest temperature in the whole file
	{
		double lowest = getLTemp(tows.get(0));
		int index = 0;
		int counter = 0;
		double temp;
		for (String tow : tows)
		{
			temp = getLTemp(tow);
			if (temp < lowest)
			{
				lowest = temp;
				index = counter;
				
			}
			counter = counter +1;
		}
		return getLTemp(tows.get(index));
	}
	
	public static double getTotalLowTemp(ArrayList<String> tows) //function that gets all of the low temperatures in the whole file
	{
		double total = 0;
		for (String tow:tows)
		{
			total = total + getLTemp(tow);
		} return total;
	}
	
	public static double getTotalHighTemp(ArrayList<String> tows) //function that gets all of the high temperatures in the whole file
	{
		double total = 0;
		for (String tow : tows)
		{
			total = total + getHTemp(tow);
			
		} return total;
	}
	
	public static double getAverageHighTemp(ArrayList<String> tows) //gets the average high temperature using the getTotalHighTemp function
	{
		return getTotalHighTemp(tows) / tows.size();
	}
	
	public static double getAverageLowTemp(ArrayList<String> tows) //gets the average low temperature using the getTotalLowTemp function

	{
		return getTotalLowTemp(tows) / tows.size();
	}
	
	public static double showTotalP(ArrayList<String> tows)//shows the total precipitation
	{
		double total = 0;
		for (String tow: tows)
		{
			total = total +getP(tow);
		} return total;
	}
	
	public static String date(String tows) //gets the dates in any given line of the file
	{
		return tows.substring(69,78).trim();
		
	}
	
	public static String userDateAndTMinTMax(ArrayList<String>tows, String dateByUser) //confusing part of the program
	{
		String minTemp = ""; //I asked a classmate on help with this method of code, we
		String maxTemp = ""; //initialize minimum and maximum temperatures as empty strings
		for (String tow: tows) // goes through the text file
		{
			if(dateByUser.equals(date(tow))) //if the date the user inputs matches the date in the file, it grabs min and max
			{
				minTemp = String.valueOf((int)getLTemp(tow));
				maxTemp = String.valueOf((int)getHTemp(tow));
			}
		}
			if (maxTemp.equals("") && minTemp.equals("")) //&& is like an AND logic gate, both must be true for null to be returned
			{
				return null;
			}
		
		else
		{
		System.out.println();
		//Here is where we display min and max through a return line, i use + maxTemp + as a concatenation, along with + minTemp+
				return "On that day, the highest temperature was: " + maxTemp + " and the lowest temperature was: " +minTemp+ ".\n";
		}		
	}
	
	public static void main(String[] args)
	{
		welcomeMenu();
		Scanner sc = new Scanner(System.in); //calling a new scanner for user input
		int choice;
		System.out.println("Please enter the name of the weather file: ");
		String fname = sc.nextLine();
		String userDate;
		ArrayList<String> TextFile = readFile(fname); //reads the file given by the user
		if (TextFile == null)
		{
			System.out.println("An error has occurred."); //just in case the computer cannot read the file
		}
		else //a loop for the menu to keep occurring until the press 10
		{
			do 
			{
				showMenu();
				choice = sc.nextInt();
				if (choice ==1)
				{
					//show average high temperature
					System.out.printf("The average high temperature in the file is: %.2f.", getAverageHighTemp(TextFile));
					System.out.println();
				}
				else if (choice ==2)
				{
					//show the average low temperature
					System.out.printf("The average low temperature in the file is: %.2f.", getAverageLowTemp(TextFile));
					System.out.println();
				}
				else if (choice ==3)
				{
					System.out.printf("The highest temperature in the file is:  %.0f.", showHighestTemp(TextFile));
					System.out.println();
				}
				else if (choice == 4)
				{
					//show the lowest temperature
					System.out.printf("The lowest temperature in the file is:  %.0f.", showLowestTemp(TextFile));
					System.out.println();
				}
				else if (choice ==5)
				{
					//show the total Precipitation
					System.out.printf("The total Precipitation was:  %.2f.", showTotalP(TextFile));
					System.out.println();
				}
				else if (choice ==6)
				{
					//show the number of days of thunder
					System.out.printf("The number of days thunder occurred is: %.0f." , showTotalT(TextFile));
					System.out.println();
				}
				else if (choice ==7)
				{
					//show the high and low temperatures of any given date (trickiest part of the whole program in my opinion)
					System.out.println("Please enter a date in YYYYMMDD format: ");
					userDate=sc.next();
					if (userDateAndTMinTMax(TextFile,userDate) == null) //null is set in the method
					{
						System.out.println();
						System.out.println("There is no data for that"); //in case the user enters the wrong format, allows for the program to still run
					}
					else
					{
					System.out.println();
					System.out.print(userDateAndTMinTMax(TextFile, userDate));
					}
				}
				else if (choice ==8)
				{
					//show the days of pellets!
					System.out.printf("The number of days pellets occurred is: %.0f.", showTotalPellets(TextFile));
					System.out.println();
				}
				else if (choice == 9)
				{
					//show the days of hail!
					System.out.printf("The total days of hail is: %.0f." , showH(TextFile));
					System.out.println();
				}
			} while (choice !=10); //ends the program
			System.out.println("Thank you for using this JAVA WEATHER APP!");
		}
		
	sc.close();
	//the error message "sc is never closed " kept bothering me because it was the only error in over 400 lines of code! so i close it here.
	}

}