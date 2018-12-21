/*******************************************************************************
 *
 *    Copyright (C) 2015-2018 the BBoxDB project
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 *******************************************************************************/
package org.bboxdb.networkproxy;

import java.io.IOException;
import java.io.InputStream;

import org.bboxdb.commons.io.DataEncoderHelper;

import com.google.common.io.ByteStreams;

public class ProxyHelper {

	/**
	 * Read a string from a input stream
	 *
	 * @param inputStream
	 * @return
	 * @throws IOException
	 */
	public static String readStringFromServer(final InputStream inputStream) throws IOException {
		final int stringLength = DataEncoderHelper.readIntFromStream(inputStream);

		final byte[] stringBytes = new byte[stringLength];
		ByteStreams.readFully(inputStream, stringBytes, 0, stringBytes.length);

		return new String(stringBytes);
	}

}