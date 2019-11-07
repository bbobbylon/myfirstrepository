package Tlutter;

import java.util.ArrayList;

public class OliverTleet {
	
	//set as private just like the Weather app
	private int day;
	private String time;
	private String name;
	private String text;
	private ArrayList<String> hashtag;
	
	//here create all my gitters n setters(country accent)
	public int getDay()
	{
		return day;
	}
	
	public void setDay(int day)
	{
		this.day=day;
	}
	
	public String getName()
	{
		return name;
	}
	
	public void setName(String name)
	{
		this.name = name;
		
	}
	
	public  String getText()
	{
		return text;
	}
	
	public void setText(String text)
	{
		this.text = text;
	}
	
	public ArrayList<String> getHashtag()
	{
		return hashtag;
	}
	
	public void setHashtag(ArrayList<String> hashtag)
	{
		this.hashtag = hashtag;
	}
	
	public void setTime(String time)
	{
		this.time = time;
	}
	
	
	
	
	@Override
	
	public String toString() //toString function/method that returns a String rep. of Tleet that matches what is shown on sample output
	{
		String result = "At " + time + " on " + String.valueOf(day)+ ", " +name+ " tleeted... \n " + text;
		for (int i = 0; i<hashtag.size(); i++)
		{
			result = result + "#" +hashtag.get(i);
		}
		return result;
	}
	public OliverTleet()
	{
		day = 00000000;
		time = "00:00:00";
		name = "";
		text = "";
		hashtag = null;
	
	}
	
	

	//constructor has to be in specific order throughout each class so you do not get errors
	OliverTleet(String name, int day, String time,String text, ArrayList<String> hashtag)
	{
		setName(name);
		setDay(day);
		setTime(time);
		setText(text);
		setHashtag(hashtag);
	}

}

