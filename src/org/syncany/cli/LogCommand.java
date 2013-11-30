/*
 * Syncany, www.syncany.org
 * Copyright (C) 2011-2013 Philipp C. Heckel <philipp.heckel@gmail.com> 
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.syncany.cli;

import static java.util.Arrays.asList;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import org.syncany.database.persistence.IFileVersion;
import org.syncany.database.persistence.IPartialFileHistory;
import org.syncany.operations.LogOperation;
import org.syncany.operations.LogOperation.LogOperationOptions;
import org.syncany.operations.LogOperation.LogOperationResult;
import org.syncany.util.StringUtil;

public class LogCommand extends Command {
	private static final Logger logger = Logger.getLogger(LogOperation.class.getSimpleName());
	private static final DateFormat dateFormat = new SimpleDateFormat("dd-MM-yy HH:mm:ss");

	@Override
	public boolean initializedLocalDirRequired() {
		return true;
	}

	@Override
	public int execute(String[] operationArgs) throws Exception {
		LogOperationOptions operationOptions = parseOptions(operationArgs);
		LogOperationResult operationResult = client.log(operationOptions);

		printResults(operationResult);

		return 0;
	}

	public LogOperationOptions parseOptions(String[] operationArgs) throws Exception {
		LogOperationOptions operationOptions = new LogOperationOptions();

		OptionParser parser = new OptionParser();
		OptionSpec<String> optionFormat = parser.acceptsAll(asList("f", "format")).withRequiredArg().defaultsTo("full");

		OptionSet options = parser.parse(operationArgs);

		// --format
		String format = options.valueOf(optionFormat);

		if (!getSupportedFormats().contains(format)) {
			throw new Exception("Unrecognized log format " + format);
		}

		// Files
		List<?> nonOptionArgs = options.nonOptionArguments();
		List<String> restoreFilePaths = new ArrayList<String>();

		for (Object nonOptionArg : nonOptionArgs) {
			restoreFilePaths.add(nonOptionArg.toString());
		}

		operationOptions.setPaths(restoreFilePaths);
		operationOptions.setFormat(format);

		return operationOptions;
	}

	private void printOneVersion(IFileVersion fileVersion) {
		String posixPermissions = (fileVersion.getPosixPermissions() != null) ? fileVersion.getPosixPermissions() : "";
		String dosAttributes = (fileVersion.getDosAttributes() != null) ? fileVersion.getDosAttributes() : "";

		out.printf("%4d %-20s %9s %4s %8d %7s %8s %40s", fileVersion.getVersion(), dateFormat.format(fileVersion.getLastModified()),
				posixPermissions, dosAttributes, fileVersion.getSize(), fileVersion.getType(), fileVersion.getStatus(),
				StringUtil.toHex(fileVersion.getChecksum()));
	}

	private int longestPath(List<IPartialFileHistory> fileHistories, boolean lastOnly) {
		int result = 0;
		for (IPartialFileHistory fileHistory : fileHistories) {
			if (lastOnly) {
				result = Math.max(result, fileHistory.getLastVersion().getPath().length());
			} else {
				for (IFileVersion fileVersion : fileHistory.getFileVersions().values()) {
					result = Math.max(result, fileVersion.getPath().length());
				}
			}
		}
		return result;
	}

	private void printResults(LogOperationResult operationResult) {
		int longestPath = 0;
		if (operationResult.getFormat().equals("last")) {
			longestPath = longestPath(operationResult.getFileHistories(), true);
		}
		for (IPartialFileHistory fileHistory : operationResult.getFileHistories()) {
			IFileVersion lastVersion = fileHistory.getLastVersion();
			switch (operationResult.getFormat()) {
			case "full":
				Iterator<Long> fileVersionNumber = fileHistory.getDescendingVersionNumber();
				out.printf("%s %16x\n", lastVersion.getPath(), fileHistory.getFileId());
				while (fileVersionNumber.hasNext()) {
					IFileVersion fileVersion = fileHistory.getFileVersion(fileVersionNumber.next());
					out.print('\t');
					printOneVersion(fileVersion);
					if (fileVersion.getPath().equals(lastVersion.getPath())) {
						out.println();
					} else {
						out.println(" " + fileVersion.getPath());
					}
				}
				break;
			case "last":
				out.printf("%-" + longestPath + "s %16x", lastVersion.getPath(), fileHistory.getFileId());
				printOneVersion(lastVersion);
				out.println();
				break;
			default:
				out.println(" unkown format " + operationResult.getFormat());
				logger.log(Level.SEVERE, "Unrecognized lof format, should have been rejected earlier " + operationResult.getFormat());
			}
		}
	}

	public static List<String> getSupportedFormats() {
		List<String> localFormats = new ArrayList<String>();

		localFormats.add("full");
		localFormats.add("last");

		return Collections.unmodifiableList(localFormats);
	}
}
