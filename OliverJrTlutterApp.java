package Tlutter;


import java.util.ArrayList;
import java.util.Scanner;

//Robert Oliver Jr
//we will make an application that displays information of your choosing
//from a specific file format :everything is separated by tabs
public class OliverJrTlutterApp {
	/*public static printTleets()
	{
	String[]parts;
	while (fsc.hasNextline())
	{
		line =fsc.nextline.trim();
		parts = line.split(\t);
	}
	} from class notes ^^^
	*/

//method that prints out the Menu
	public static void showMenu()
	{
		System.out.println("Here are your options: ");
		System.out.println("1. See a list of all Tleets.");
		System.out.println("2. See a list of al Tleets from a particular day or days.");
		System.out.println("3. See a list of Tleets from a particular user.");
		System.out.println("4. See a list of all tleets with a particular hashtag. ");
		System.out.println("5. Tleet to your peeps! ");
		System.out.println("6. Exit");
		System.out.println("Please enter the number of your choice: ");
	}

	public static void main(String[] args) {
		Scanner sc = new Scanner (System.in);
		int choice =0; //initialize choice to 0
		System.out.println("Please enter the name of the Tleet file: ");
		String fname = sc.nextLine(); //takes in the file name
		ArrayList<String> inputHashtags = new ArrayList<String>(); //turning the hashtags into an ArrayList
		ArrayList<OliverTleet> tleets = OliverFileReader.readFile(fname); //reads the file from the user input
		if (tleets == null)
		{
			System.out.println("An error has occurred."); //just in case the computer cannot read the file
		}
		else //a do loop for the menu to keep occurring until the user enters 6
		{
			do 
			{
				showMenu();
				choice = sc.nextInt();
			
				if (choice == 1)
				{
					System.out.println("Here is a list of all tleets by every user: ");
					for (OliverTleet tleet:tleets) //calls the function that displays all tleets
					{
						System.out.println(tleet);
						System.out.println();
					}
				}
				else if (choice ==2)
				{
					System.out.print("Enter start date: ");
					int start = sc.nextInt();
					System.out.print("Enter end date: ");
					int end = sc.nextInt();
					int day[] = {start, end};
					System.out.println("Here is a list of all tleets between" + start+ "and" + end+ ":");
					//call the Analytic function that gets what you need (start day, end day, tleet
					for (OliverTleet tleet: OliverAnalytic.getADay(tleets, day))
					{
						System.out.println(tleet);
						System.out.println();
					}
				}
				else if (choice == 3) {
					System.out.println("Enter name of user: ");
					String username = sc.next();
					System.out.println("Here is a list of all tleets from user " + username + ":");
					//same as option two, where it calls the Analytic function that gets what you need
					for (OliverTleet tleet: OliverAnalytic.getAName(tleets, username)) {   
						System.out.println(tleet);                                           
						System.out.println();
					}
				}
				else if (choice ==4)
				{
					System.out.println("Enter a hashtag of interest: ");
					String hashtag = sc.next();
					System.out.println("Here is a list of all tleets with hashtag" +hashtag+ ":");
					//same as option two, where it calls the Analytic function that gets what you need

					for (OliverTleet tleet: OliverAnalytic.getAHashtag(tleets, hashtag))
					{
						System.out.println(tleet);
						System.out.println();
					}
				}
				else if (choice ==5)
				{
					//i put input in front of all these variable to not confuse thy self 
					//input meaning what the user will input
					System.out.println("Enter a username: ");
					String inputName = sc.next(); //inputName to differ parameters
					System.out.println("Enter the date: ");
					int inputDay = sc.nextInt();
					System.out.println("Enter the time: ");
					String inputTime = sc.next();
					System.out.println("Enter a tleet: ");
					sc.nextLine();
					String inputText = sc.nextLine();
					System.out.println("Enter hashtags seperated by spaces: ");
					String hash = sc.nextLine();
					String[] inputHash = hash.split(" ");
					//for loop that puts the split hashtags into an Array List
					for (int i=0; i<inputHash.length; i++)
					{
						inputHashtags.add(inputHash[0]);
					}
					//constructor that adds a new tleet to the Array of Tleets
					OliverTleet inputTleet = new OliverTleet ( inputName, inputDay, inputTime, inputText, inputHashtags);
					tleets.add(inputTleet);
					
				}
			}while (choice !=6);
				System.out.println("Tlutter tlanks tlou tlor tlusing tlis tlogram.");
			}
		sc.close();
		}	
	}	
