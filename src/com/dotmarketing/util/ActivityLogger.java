package com.dotmarketing.util;

import com.dotmarketing.beans.Host;
import com.dotmarketing.business.APILocator;
import com.dotmarketing.logConsole.model.LogMapper;

public class ActivityLogger {

	private static String filename = "dotcms-userActivity.log";

	public static synchronized void logInfo(Class cl, String action, String msg) {
		logInfo(cl, action, msg, null);
	}

	public static synchronized void logInfo(Class cl, String action,
			String msg, String host) {
		if (LogMapper.getInstance().isLogEnabled(filename)) {
			if (!UtilMethods.isSet(host)) {
				host = "system";
			}else{
				try {
					Host h = APILocator.getHostAPI().find(host, APILocator.getUserAPI().getSystemUser(), false);
					if(h != null)
						host = h.getHostname();
				} catch (Exception e) {}
			}
				Logger.info(ActivityLogger.class, cl.toString() + ": " + host
					+ " : " + action + " , " + msg);
			}
	}

	public static void logDebug(Class cl, String action, String msg, String host) {

		if (LogMapper.getInstance().isLogEnabled(filename)) {
			if (!UtilMethods.isSet(host)) {
				host = "system";
			} else {
				try {
					Host h = APILocator.getHostAPI().find(host,
							APILocator.getUserAPI().getSystemUser(), false);
					if (h != null)
						host = h.getHostname();
				} catch (Exception e) {}
			}
			Logger.debug(ActivityLogger.class, cl.toString() + ": " + host
					+ " :" + action + " , " + msg);
		}
	}

}
