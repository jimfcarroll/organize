package com.jiminger;

public class Config {

	// Md5Sifter, Execute
	public static final String actionsFileName = "C:\\Users\\Jim\\Documents\\actions.txt";

	// Md5Sifter
	public static final String dirPrescedence = "C:\\Users\\Jim\\dirs.animations.txt";
	
	// Md5File, Organize, Md5Verify(only when deleting)
	public static final String md5FileToWrite = "C:\\Users\\Jim\\Pictures\\md5.Vids.txt";

	// Md5File, Md5Verify, Organize, Md5Sifter
	public static final String[] md5FilesToRead = new String[] { "I:\\md5.txt" };
	
	// Md5File, Md5Verify
	public static final String[] directoriesToScan = new String[] { "C:\\Users\\Jim\\Pictures\\Videos" };

	// Md5Verify
	public static final String verifyOutputFile = "C:\\Users\\Jim\\Pictures\\verify.txt";
	
	// Organize
	public static final String srcDirectoryStr = "C:\\Users\\Jim\\Pictures\\Videos.fromMBLSharedVids";
	public static final String dstDirectoryStr = "C:\\Users\\Jim\\Pictures\\Videos";
	public static final String dups = "DUPS";
	public static final String outFile = "C:\\Users\\Jim\\Documents\\out.txt";
	public static final boolean appendOutfile = true;
	
	// Md5File, Md5Verify, Organize
	public static final String failedFile = "C:\\Users\\Jim\\Documents\\failed.txt";
}
