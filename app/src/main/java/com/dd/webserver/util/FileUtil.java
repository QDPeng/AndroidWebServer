package com.dd.webserver.util;

import java.io.File;

import android.R.drawable;
import android.os.Environment;
import android.webkit.MimeTypeMap;

public class FileUtil {
	public static String getSDCardRoot() {
		return Environment.getExternalStorageDirectory().getAbsolutePath();
	}

	public static String getAppPath() {
		String dirString = getSDCardRoot() + File.separator + "tcxt";
		File file = new File(dirString);
		if (!file.exists()) {
			file.mkdirs();
		}
		return file.getAbsolutePath();
	}

	private static String getExtension(final File file) {
		String suffix = "";
		String name = file.getName();
		final int idx = name.lastIndexOf(".");
		if (idx > 0) {
			suffix = name.substring(idx + 1);
		}
		return suffix;
	}

	public static String getMimeType(final File file) {
		String extension = getExtension(file);
		return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
	}
}
