package com.jiminger;

public class Config {

	// Md5Sifter, Execute
	public static final String actionsFileName = "C:\\Users\\Jim\\Documents\\actions.txt";

	// Md5Sifter
	public static final String dirPrescedence = "C:\\Users\\Jim\\dirs.animations.txt";
	
	// Md5File, Organize, Md5Verify(only when deleting)
	public static final String md5FileToWrite = "I:\\md5.wmp3.txt";

	// Md5File, Md5Verify, Organize, Md5Sifter
	public static final String[] md5FilesToRead = new String[] { "I:\\md5.txt" };
	
	// Md5File, Md5Verify
	public static final String[] directoriesToScan = new String[] { "I:\\" };

	// Md5File
	public static final boolean deleteEmptyDirs = true;
	
	// Md5Verify
	public static final String verifyOutputFile = "C:\\Users\\Jim\\Pictures\\verify.txt";
	
	// Organize
	public static final String srcDirectoryStr = "C:\\Users\\Jim\\Pictures\\WordMP3.fromMBL";
	public static final String dstDirectoryStr = "I:\\Audio\\Spoken Word\\WordMP3";
	public static final String dups = "DUPS";
	public static final String outFile = "C:\\Users\\Jim\\Documents\\out.audio.txt";
	public static final boolean appendOutfile = true;
	public static final long byteBufferSize = 4L * 1024L * 1024L * 1024;
	
	// Md5File, Md5Verify, Organize
	public static final String failedFile = "C:\\Users\\Jim\\Documents\\failed.txt";
}
