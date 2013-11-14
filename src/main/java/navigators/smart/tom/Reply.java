/*
 * Copyright (c) 2007-2013 Alysson Bessani, Eduardo Alchieri, Paulo Sousa, 
 * and the authors indicated in the @author tags 
 *  
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at 
 *  
 * http://www.apache.org/licenses/LICENSE-2.0 
 *  
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and 
 * limitations under the License. 
 */
package navigators.smart.tom;

import java.util.List;

/**
 * 
 * This Class holds the reply of an issued request and some information 
 * on the targeted sender.
 * 
 * @author Christian Spann
 *
 */
public class Reply {
	
	public final List<Integer> targets;
	public final byte[] request;
	
	public Reply(List<Integer> targets, byte[] request) {
		super();
		this.targets = targets;
		this.request = request;
	}
}
