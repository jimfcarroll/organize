package com.jiminger;

public class Config {

	// Md5Sifter, Execute
	public static final String actionsFileName = "C:\\Users\\Jim\\Documents\\actions.txt";

	// Md5Sifter
	public static final String dirPrescedence = "C:\\Users\\Jim\\dirs.animations.txt";
	
	// Md5File
	public static final String md5FileToWrite = "I:\\md5.txt";

	// Md5File, Md5Verify, Organize, Md5Sifter
	public static final String[] md5FilesToRead = new String[] { "I:\\md5.pics.txt", "I:\\md5.FamilyMedia.OldHomeMovieFilm.txt" };
	
	// Md5File, Md5Verify
	public static final String[] directoriesToScan = new String[] { "I:\\" };

	// Md5Verify
	public static final String verifyOutputFile = "C:\\Users\\Jim\\Documents\\verify.pics.txt";
	
	// Organize
	public static final String srcDirectoryStr = "X:\\Finance";
	public static final String dstDirectoryStr = "C:\\Users\\Jim\\Pictures\\Finance";
	public static final String dups = "DUPS";
	public static final String outFile = "C:\\Users\\Jim\\Documents\\out.finance.txt";
	
	// Md5File, Md5Verify, Organize
	public static final String failedFile = "C:\\Users\\Jim\\Documents\\failed.txt";
}
