/*
 Created as part of the StratusLab project (http://stratuslab.eu),
 co-funded by the European Commission under the Grant Agreement
 INSFO-RI-261552.

 Copyright (c) 2011, Centre National de la Recherche Scientifique (CNRS)

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */
package eu.stratuslab.storage.disk.utils;

import java.util.ArrayList;
import java.util.List;

public class Messages {
	private List<String> success = new ArrayList<String>();
	
	public void push(String message) {
		success.add(message);
	}
	
	public String pop() {
		if (success.size() == 0)
			return null;
		
		String msg = success.get(0);
		success.remove(0);
		
		return msg;
	}

}