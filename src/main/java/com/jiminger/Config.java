package com.jiminger;

public class Config {

	// Md5Sifter, Execute
	public static final String actionsFileName = "C:\\Users\\Jim\\Documents\\actions.txt";

	// Md5Sifter
	public static final String dirPrescedence = "C:\\Users\\Jim\\dirs.animations.txt";
	
	// Md5File
	public static final String md5FileToWrite = "C:\\Users\\Jim\\Documents\\md5.animations.txt";

	// Md5File, Md5Verify, Organize, Md5Sifter
	public static final String[] md5FilesToRead = new String[] { "C:\\Users\\Jim\\Documents\\md5.animations.txt" };
	
	// Md5File, Md5Verify
	public static final String[] directoriesToScan = new String[] { "C:\\Users\\Jim\\Pictures\\Animations" };

	// Md5File, Md5Verify, Organize
	public static final String failedFile = "C:\\Users\\Jim\\Documents\\failed.txt";
	
	// Md5Verify
	public static final String verifyOutputFile = "C:\\Users\\Jim\\Documents\\verify.pics.txt";
	
	// Organize
	public static final String srcDirectoryStr = "G:\\Family Media\\Animations";
	public static final String dstDirectoryStr = "C:\\Users\\Jim\\Pictures\\Animations";
	public static final String dups = "DUPS";
	public static final String outFile = "C:\\Users\\Jim\\Documents\\out.animations.txt";

}
